package com.kun.cfdaily.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Groq's OpenAI-style chat endpoint with open models. The key is the user's own (free at console.groq.com)
 * and stays in this app's private preferences.
 */
public class Ai {

    private static final String BASE = "https://api.groq.com/openai/v1/";
    /** Best first. Groq retires models now and then; when the saved one is gone, the next one the account lists is used. */
    private static final String[] PREFERRED = {"openai/gpt-oss-120b", "llama-3.3-70b-versatile", "qwen/qwen3-32b", "openai/gpt-oss-20b", "llama-3.1-8b-instant"};

    public static class AiException extends Exception {
        public AiException(String message) {
            super(message);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ai", 0);
    }

    public static String key(Context c) {
        return prefs(c).getString("groq_key", "");
    }

    public static void setKey(Context c, String key) {
        prefs(c).edit().putString("groq_key", key.trim()).apply();
    }

    public static boolean ready(Context c) {
        return key(c).length() > 10;
    }

    public static String model(Context c) {
        return prefs(c).getString("model", PREFERRED[0]);
    }

    /** One question, one answer. With json=true the reply is a JSON object. Runs on a background thread. */
    public static String ask(Context c, String system, String user, boolean json) throws AiException {
        if (!ready(c)) throw new AiException("Add your free Groq key in Profile first.");
        AiException last = null;
        boolean useJson = json;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return clean(call(c, system, user, useJson));
            } catch (Retry r) {
                last = new AiException(r.getMessage());
                if (r.dropJson) useJson = false;
                if (r.newModel || r.dropJson) continue;
                try {
                    Thread.sleep(2500L * (attempt + 1));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        throw last != null ? last : new AiException("Groq didn't answer. Try again.");
    }

    private static class Retry extends Exception {
        final boolean newModel;
        boolean dropJson;

        Retry(String m, boolean newModel) {
            super(m);
            this.newModel = newModel;
        }

        Retry noJson() {
            dropJson = true;
            return this;
        }
    }

    private static String call(Context c, String system, String user, boolean json) throws AiException, Retry {
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", model(c));
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", system));
            msgs.put(new JSONObject().put("role", "user").put("content", user));
            body.put("messages", msgs);
            body.put("temperature", 0.4);
            body.put("max_completion_tokens", 4096);
            if (json) body.put("response_format", new JSONObject().put("type", "json_object"));

            conn = (HttpURLConnection) new URL(BASE + "chat/completions").openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(90000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + key(c));
            conn.setRequestProperty("Content-Type", "application/json");
            OutputStream out = conn.getOutputStream();
            out.write(body.toString().getBytes("UTF-8"));
            out.close();

            int code = conn.getResponseCode();
            String text = read(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code == 401) throw new AiException("Groq says the key isn't valid. Check it in Profile.");
            if (code == 429) throw new Retry("Groq's free limit is busy. Try again in a minute.", false);
            if (code >= 500) throw new Retry("Groq is having trouble. Try again soon.", false);
            if (code >= 400) {
                String msg = errorMessage(text);
                String low = msg.toLowerCase(Locale.US);
                if (low.contains("model") && (low.contains("decommission") || low.contains("not exist") || low.contains("not found") || low.contains("does not have access"))) {
                    pickModel(c);
                    throw new Retry(msg, true);
                }
                if (json && low.contains("json")) throw new Retry(msg, false).noJson();
                throw new AiException(msg.length() > 0 ? msg : "Groq error " + code);
            }
            JSONObject root = new JSONObject(text);
            String content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
            if (content.trim().length() == 0) throw new Retry("Empty answer. Try again.", false);
            return content;
        } catch (AiException | Retry e) {
            throw e;
        } catch (java.net.UnknownHostException e) {
            throw new AiException("You're offline.");
        } catch (Exception e) {
            throw new Retry("Couldn't reach Groq.", false);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Chooses the best chat model the account can use. */
    private static void pickModel(Context c) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BASE + "models").openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Authorization", "Bearer " + key(c));
            JSONArray data = new JSONObject(read(conn.getInputStream())).getJSONArray("data");
            String fallback = null;
            java.util.HashSet<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject m = data.getJSONObject(i);
                if (!m.optBoolean("active", true)) continue;
                String id = m.optString("id");
                ids.add(id);
                String low = id.toLowerCase(Locale.US);
                if (fallback == null && !low.contains("whisper") && !low.contains("guard") && !low.contains("tts") && !low.contains("playai") && !low.contains("embed")) fallback = id;
            }
            String chosen = fallback;
            String current = model(c);
            for (String p : PREFERRED) {
                if (ids.contains(p) && !p.equals(current)) {
                    chosen = p;
                    break;
                }
            }
            if (chosen != null) prefs(c).edit().putString("model", chosen).apply();
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String errorMessage(String text) {
        try {
            return new JSONObject(text).getJSONObject("error").optString("message", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        InputStreamReader r = new InputStreamReader(in, "UTF-8");
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        r.close();
        return sb.toString();
    }

    /** Drops thinking that some open models print before the answer. */
    private static String clean(String s) {
        return s.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    /** The JSON object inside a reply, even if the model wrapped it in prose or a code fence. */
    public static JSONObject json(String reply) throws AiException {
        int a = reply.indexOf('{'), b = reply.lastIndexOf('}');
        if (a < 0 || b <= a) throw new AiException("Groq's answer was cut off. Try again.");
        try {
            return new JSONObject(reply.substring(a, b + 1));
        } catch (Exception e) {
            throw new AiException("Groq's answer was cut off. Try again.");
        }
    }
}
