package com.kun.cfdaily.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** LeetCode's public GraphQL endpoint. No login: only data that is public on the site. */
public class LcApi {

    public static JSONObject query(String query, JSONObject variables, boolean fast) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", query);
        if (variables != null) body.put("variables", variables);
        byte[] bytes = body.toString().getBytes("UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL("https://leetcode.com/graphql").openConnection();
        conn.setConnectTimeout(fast ? 6000 : 15000);
        conn.setReadTimeout(fast ? 8000 : 45000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Referer", "https://leetcode.com");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) CFDaily");
        try {
            OutputStream out = conn.getOutputStream();
            out.write(bytes);
            out.close();
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
                throw new Exception("LeetCode returned an unexpected response (HTTP " + code + ")");
            }
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                String msg = "LeetCode error";
                if (root.optJSONArray("errors") != null && root.optJSONArray("errors").length() > 0) {
                    msg = root.optJSONArray("errors").getJSONObject(0).optString("message", msg);
                }
                throw new Exception(msg);
            }
            return data;
        } finally {
            conn.disconnect();
        }
    }
}
