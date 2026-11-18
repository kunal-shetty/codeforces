package com.kun.cfdaily.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lesson content bundled from content/learn at the repo root (the backend serves the same files). */
public class Lessons {

    public static class Track {
        public String id;
        public String title;
        public final List<Unit> units = new ArrayList<>();
    }

    public static class Unit {
        public String id;
        public String title;
        public String summary;
        public String icon;
        public String color;
        /** Problem tag for real practice on each site, or null when the site has no matching tag. */
        public String cfTag;
        public String lcTag;
        public final List<Lesson> lessons = new ArrayList<>();
    }

    public static class Lesson {
        public String id;
        public String title;
        public JSONArray steps;
        public Unit unit;
        /** Position inside the unit, from 0. */
        public int index;
    }

    private static List<Track> tracks;

    public static synchronized List<Track> tracks(Context c) {
        if (tracks != null) return tracks;
        List<Track> out = new ArrayList<>();
        try {
            JSONObject index = new JSONObject(read(c, "learn/index.json"));
            JSONArray ts = index.getJSONArray("tracks");
            for (int i = 0; i < ts.length(); i++) {
                JSONObject t = ts.getJSONObject(i);
                Track track = new Track();
                track.id = t.getString("id");
                track.title = t.getString("title");
                JSONArray ids = t.getJSONArray("units");
                for (int k = 0; k < ids.length(); k++) {
                    try {
                        track.units.add(unit(new JSONObject(read(c, "learn/units/" + ids.getString(k) + ".json"))));
                    } catch (Exception broken) {
                        // One bad file must not hide every other unit.
                    }
                }
                out.add(track);
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
        tracks = out;
        return tracks;
    }

    public static Lesson lesson(Context c, String id) {
        for (Track t : tracks(c))
            for (Unit u : t.units)
                for (Lesson l : u.lessons)
                    if (l.id.equals(id)) return l;
        return null;
    }

    private static Unit unit(JSONObject j) throws Exception {
        Unit u = new Unit();
        u.id = j.getString("id");
        u.title = j.getString("title");
        u.summary = j.optString("summary", "");
        u.icon = j.optString("icon", "•");
        u.color = j.optString("color", "mint");
        JSONObject practice = j.optJSONObject("practice");
        if (practice != null) {
            u.cfTag = practice.optString("cf", null);
            u.lcTag = practice.optString("lc", null);
        }
        JSONArray ls = j.getJSONArray("lessons");
        for (int i = 0; i < ls.length(); i++) {
            JSONObject o = ls.getJSONObject(i);
            Lesson l = new Lesson();
            l.id = o.getString("id");
            l.title = o.getString("title");
            l.steps = o.getJSONArray("steps");
            l.unit = u;
            l.index = i;
            u.lessons.add(l);
        }
        return u;
    }

    private static String read(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
