package com.kun.cfdaily.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class CfApi {

    private static class LimitException extends Exception {
        LimitException(String message) {
            super(message);
        }
    }

    /** Retries when Codeforces answers "Call limit exceeded", which happens when calls come too close together. */
    private static JSONObject fetch(String method, String query, int connectMs, int readMs) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                return fetchOnce(method, query, connectMs, readMs);
            } catch (LimitException e) {
                if (attempt >= 2) throw e;
                Thread.sleep(2200);
            }
        }
    }

    private static JSONObject fetchOnce(String method, String query, int connectMs, int readMs) throws Exception {
        String spec = "https://codeforces.com/api/" + method + (query.length() > 0 ? "?" + query : "");
        HttpURLConnection conn = (HttpURLConnection) new URL(spec).openConnection();
        conn.setConnectTimeout(connectMs);
        conn.setReadTimeout(readMs);
        conn.setRequestProperty("User-Agent", "CFDaily/1.0 (Android)");
        conn.setRequestProperty("Accept", "application/json");
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                InputStreamReader r = new InputStreamReader(in, "UTF-8");
                char[] buf = new char[16384];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
                r.close();
            }
            JSONObject root;
            try {
                root = new JSONObject(sb.toString());
            } catch (JSONException e) {
                throw new Exception("Codeforces returned an unexpected response (HTTP " + code + "). Try again in a minute.");
            }
            if (!"OK".equals(root.optString("status"))) {
                String comment = root.optString("comment", "Codeforces API error");
                if (comment.toLowerCase(Locale.US).contains("limit exceeded")) throw new LimitException(comment);
                throw new Exception(comment);
            }
            return root;
        } finally {
            conn.disconnect();
        }
    }

    public static JSONArray getArray(String method, String query) throws Exception {
        return fetch(method, query, 15000, 60000).getJSONArray("result");
    }

    /** For background work that must finish quickly. */
    public static JSONArray getArrayFast(String method, String query) throws Exception {
        return fetch(method, query, 6000, 8000).getJSONArray("result");
    }

    public static JSONObject getObject(String method, String query) throws Exception {
        return fetch(method, query, 15000, 60000).getJSONObject("result");
    }
}
