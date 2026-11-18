package com.kun.cfdaily.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.kun.cfdaily.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Upsolve server: accounts and progress backup. Everything here is optional; the app works fully offline.
 * The server address comes from upsolve.api in local.properties (see app/build.gradle).
 */
public class Cloud {

    public interface Done {
        /** Called on the main thread; error is null on success. */
        void done(String error);
    }

    /** One request at a time, so two refreshes can never race (a reused refresh token signs you out). */
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cloud", 0);
    }

    public static boolean configured() {
        return BuildConfig.API_URL.length() > 0;
    }

    private static String base() {
        String b = BuildConfig.API_URL;
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    public static boolean signedIn(Context c) {
        return prefs(c).getString("refresh", "").length() > 0;
    }

    public static String email(Context c) {
        return prefs(c).getString("email", "");
    }

    public static String name(Context c) {
        return prefs(c).getString("name", "");
    }

    public static long lastSync(Context c) {
        return prefs(c).getLong("last_sync", 0);
    }

    // ---------------------------------------------------------------- sign in

    /** Email and password; create makes a new account. Syncs right after. */
    public static void emailSignIn(Context c, String email, String password, boolean create, Done done) {
        final Context app = c.getApplicationContext();
        run(done, () -> {
            JSONObject body = new JSONObject().put("email", email.trim()).put("password", password);
            Response r = call("POST", create ? "/auth/register" : "/auth/login", body, null);
            if (!r.ok()) return r.error();
            saveTokens(app, r.json());
            return syncNow(app);
        });
    }

    /** Opens the browser at Google's sign-in. The server sends the browser back to upsolve://auth. */
    public static Uri googleStartUrl(Context c) {
        byte[] raw = new byte[48];
        new SecureRandom().nextBytes(raw);
        String verifier = b64(raw);
        prefs(c).edit().putString("verifier", verifier).apply();
        return Uri.parse(base() + "/auth/google/start").buildUpon().appendQueryParameter("challenge", challenge(verifier)).build();
    }

    /** Handles upsolve://auth?code=… (or ?error=…) when the browser comes back. */
    public static void googleFinish(Context c, Uri uri, Done done) {
        final Context app = c.getApplicationContext();
        final String error = uri.getQueryParameter("error");
        final String code = uri.getQueryParameter("code");
        final String verifier = prefs(app).getString("verifier", "");
        run(done, () -> {
            if (error != null) return error.equals("access_denied") ? "Sign-in cancelled" : "Google sign-in failed (" + error + ")";
            if (code == null || verifier.length() == 0) return "Sign-in expired, try again";
            Response r = call("POST", "/auth/google/exchange", new JSONObject().put("code", code).put("verifier", verifier), null);
            prefs(app).edit().remove("verifier").apply();
            if (!r.ok()) return r.error();
            saveTokens(app, r.json());
            return syncNow(app);
        });
    }

    public static void signOut(Context c) {
        final Context app = c.getApplicationContext();
        final String refresh = prefs(app).getString("refresh", "");
        prefs(app).edit().clear().apply();
        if (refresh.length() > 0) {
            worker.execute(() -> {
                try {
                    call("POST", "/auth/logout", new JSONObject().put("refresh_token", refresh), null);
                } catch (Exception offline) {
                    // The token expires on its own.
                }
            });
        }
    }

    // ---------------------------------------------------------------- progress

    /** Sends local progress, merges the server's answer. Does nothing when signed out. */
    public static void sync(Context c, Done done) {
        final Context app = c.getApplicationContext();
        if (!configured() || !signedIn(app)) {
            if (done != null) main.post(() -> done.done(null));
            return;
        }
        run(done, () -> syncNow(app));
    }

    private static String syncNow(Context app) throws Exception {
        Response r = authed(app, "POST", "/progress/sync", LearnDb.get(app).syncBody());
        if (!r.ok()) return r.error();
        LearnDb.get(app).merge(r.json());
        prefs(app).edit().putLong("last_sync", System.currentTimeMillis()).apply();
        return null;
    }

    /** A request with the access token; refreshes it once when the server says it has expired. */
    private static Response authed(Context app, String method, String path, JSONObject body) throws Exception {
        Response r = call(method, path, body, prefs(app).getString("access", ""));
        if (r.code != 401) return r;
        String refresh = prefs(app).getString("refresh", "");
        Response t = call("POST", "/auth/refresh", new JSONObject().put("refresh_token", refresh), null);
        if (t.code == 401) {
            prefs(app).edit().clear().apply();
            return t;
        }
        if (!t.ok()) return t;
        saveTokens(app, t.json());
        return call(method, path, body, prefs(app).getString("access", ""));
    }

    private static void saveTokens(Context app, JSONObject tokens) throws Exception {
        JSONObject user = tokens.getJSONObject("user");
        prefs(app).edit()
                .putString("access", tokens.getString("access_token"))
                .putString("refresh", tokens.getString("refresh_token"))
                .putString("email", user.optString("email"))
                .putString("name", user.optString("name"))
                .apply();
    }

    // ---------------------------------------------------------------- plumbing

    private interface Job {
        String run() throws Exception;
    }

    private static void run(Done done, Job job) {
        if (!configured()) {
            if (done != null) main.post(() -> done.done("No server set up"));
            return;
        }
        worker.execute(() -> {
            String error;
            try {
                error = job.run();
            } catch (java.io.IOException e) {
                error = "Can't reach the server";
            } catch (Exception e) {
                error = "Something went wrong";
            }
            final String result = error;
            if (done != null) main.post(() -> done.done(result));
        });
    }

    private static class Response {
        int code;
        String body = "";

        boolean ok() {
            return code >= 200 && code < 300;
        }

        JSONObject json() throws Exception {
            return new JSONObject(body);
        }

        /** FastAPI puts the message in detail: a string, or a list of field errors. */
        String error() {
            try {
                Object detail = new JSONObject(body).get("detail");
                if (detail instanceof String) return (String) detail;
                if (detail instanceof JSONArray) {
                    JSONObject first = ((JSONArray) detail).getJSONObject(0);
                    JSONArray loc = first.optJSONArray("loc");
                    String field = loc != null && loc.length() > 0 ? loc.optString(loc.length() - 1) : "";
                    if (field.equals("password")) return "Password needs 8 or more characters";
                    if (field.equals("email")) return "That email doesn't look right";
                    return first.optString("msg", "Check what you typed");
                }
            } catch (Exception notJson) {
                // fall through
            }
            return "Server error (" + code + ")";
        }
    }

    private static Response call(String method, String path, JSONObject body, String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(base() + path).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        if (bearer != null && bearer.length() > 0) conn.setRequestProperty("Authorization", "Bearer " + bearer);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(bytes);
                }
            }
            Response r = new Response();
            r.code = conn.getResponseCode();
            InputStream in = r.code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in != null) {
                try (InputStream s = in) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = s.read(chunk)) > 0) buf.write(chunk, 0, n);
                    r.body = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return r;
        } finally {
            conn.disconnect();
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** PKCE S256, as the server checks it. */
    static String challenge(String verifier) {
        try {
            return b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
