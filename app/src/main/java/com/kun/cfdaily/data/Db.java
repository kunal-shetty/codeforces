package com.kun.cfdaily.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import com.kun.cfdaily.data.Models.Contest;
import com.kun.cfdaily.data.Models.DailyItem;
import com.kun.cfdaily.data.Models.Friend;
import com.kun.cfdaily.data.Models.Problem;
import com.kun.cfdaily.data.Models.RatingPoint;
import com.kun.cfdaily.data.Models.TagStat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class Db extends SQLiteOpenHelper {

    private static Db cfDb;
    private static Db lcDb;

    /** True for the LeetCode database. Same tables; a problem's rating column holds 1, 2 or 3 (Easy, Medium, Hard). */
    public final boolean lc;

    private static final String PCOLS = "p.pkey,p.contest_id,p.idx,p.name,p.rating,p.tags,p.solved_count,IFNULL(u.solved,0),(b.pkey IS NOT NULL),IFNULL(n.note,'')";
    private static final String PJOIN = " LEFT JOIN user_problems u ON u.pkey=p.pkey LEFT JOIN bookmarks b ON b.pkey=p.pkey LEFT JOIN notes n ON n.pkey=p.pkey ";

    /** Database of the mode the app is in. */
    public static Db get(Context c) {
        return forMode(c, Mode.lc(c));
    }

    public static synchronized Db forMode(Context c, boolean leetcode) {
        if (leetcode) {
            if (lcDb == null) lcDb = new Db(c.getApplicationContext(), true);
            return lcDb;
        }
        if (cfDb == null) cfDb = new Db(c.getApplicationContext(), false);
        return cfDb;
    }

    private Db(Context c, boolean leetcode) {
        super(c, leetcode ? "lcdaily.db" : "cfdaily.db", null, 5);
        lc = leetcode;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE problems(pkey TEXT PRIMARY KEY, contest_id INTEGER, idx TEXT, name TEXT, rating INTEGER, tags TEXT, solved_count INTEGER)");
        db.execSQL("CREATE INDEX problems_rating ON problems(rating)");
        db.execSQL("CREATE TABLE user_problems(pkey TEXT PRIMARY KEY, solved INTEGER, attempts INTEGER, last_time INTEGER)");
        db.execSQL("CREATE TABLE contests(id INTEGER PRIMARY KEY, name TEXT, start_time INTEGER, duration INTEGER, remind INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE daily(day TEXT, slot INTEGER, pkey TEXT, solved INTEGER DEFAULT 0, PRIMARY KEY(day, slot))");
        db.execSQL("CREATE TABLE bookmarks(pkey TEXT PRIMARY KEY, added INTEGER)");
        db.execSQL("CREATE TABLE rating_history(contest_id INTEGER PRIMARY KEY, contest_name TEXT, time INTEGER, old_rating INTEGER, new_rating INTEGER)");
        db.execSQL("CREATE TABLE kv(k TEXT PRIMARY KEY, v TEXT)");
        createExtras(db);
    }

    /** Tables and columns added after version 1. Safe to run on any older database. */
    private static void createExtras(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notes(pkey TEXT PRIMARY KEY, note TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS friends(handle TEXT PRIMARY KEY, rating INTEGER, rank TEXT, week INTEGER, updated INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS past_contests(id INTEGER PRIMARY KEY, name TEXT, start_time INTEGER, duration INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS calendar(day TEXT PRIMARY KEY, n INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS tag_counts(tag TEXT PRIMARY KEY, solved INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS revisits(pkey TEXT PRIMARY KEY, rounds INTEGER, last INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS cards(pkey TEXT PRIMARY KEY, back TEXT, source TEXT, box INTEGER, due INTEGER)");
        addColumn(db, "contests", "slug TEXT");
        addColumn(db, "past_contests", "slug TEXT");
    }

    private static void addColumn(SQLiteDatabase db, String table, String column) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column);
        } catch (Exception alreadyThere) {
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createExtras(db);
        // Forces a sync on the next launch so the new tables get filled.
        db.execSQL("DELETE FROM kv WHERE k='last_sync'");
    }

    public String kv(String k, String def) {
        Cursor c = getReadableDatabase().rawQuery("SELECT v FROM kv WHERE k=?", new String[]{k});
        try {
            return c.moveToFirst() ? c.getString(0) : def;
        } finally {
            c.close();
        }
    }

    public long kvLong(String k, long def) {
        try {
            return Long.parseLong(kv(k, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int kvInt(String k, int def) {
        return (int) kvLong(k, def);
    }

    public void setKv(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        getWritableDatabase().insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void clearUser() {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM user_problems");
        db.execSQL("DELETE FROM rating_history");
        db.execSQL("DELETE FROM daily WHERE day=?", new Object[]{LocalDate.now().toString()});
        db.execSQL("UPDATE daily SET solved=0");
        db.execSQL("DELETE FROM calendar");
        db.execSQL("DELETE FROM tag_counts");
        db.execSQL("DELETE FROM kv WHERE k IN ('rating','max_rating','rank','est','solved_total','solved_1','solved_2','solved_3')");
    }

    public void saveProblems(JSONArray problems, JSONArray stats) throws JSONException {
        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        for (int i = 0; i < stats.length(); i++) {
            JSONObject o = stats.getJSONObject(i);
            if (!o.has("contestId")) continue;
            counts.put(o.getInt("contestId") + o.getString("index"), o.optInt("solvedCount", 0));
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM problems");
            SQLiteStatement st = db.compileStatement("INSERT OR REPLACE INTO problems VALUES(?,?,?,?,?,?,?)");
            for (int i = 0; i < problems.length(); i++) {
                JSONObject o = problems.getJSONObject(i);
                if (!o.has("contestId")) continue;
                int cid = o.getInt("contestId");
                String idx = o.getString("index");
                String key = cid + idx;
                StringBuilder tags = new StringBuilder("|");
                JSONArray ta = o.optJSONArray("tags");
                if (ta != null) {
                    for (int j = 0; j < ta.length(); j++) tags.append(ta.getString(j)).append('|');
                }
                Integer cnt = counts.get(key);
                st.clearBindings();
                st.bindString(1, key);
                st.bindLong(2, cid);
                st.bindString(3, idx);
                st.bindString(4, o.optString("name", key));
                st.bindLong(5, o.optInt("rating", 0));
                st.bindString(6, tags.toString());
                st.bindLong(7, cnt == null ? 0 : cnt);
                st.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveContests(JSONArray contests) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        HashSet<Integer> remind = new HashSet<Integer>();
        Cursor c = db.rawQuery("SELECT id FROM contests WHERE remind=1", null);
        while (c.moveToNext()) remind.add(c.getInt(0));
        c.close();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM contests");
            db.execSQL("DELETE FROM past_contests");
            SQLiteStatement past = db.compileStatement("INSERT OR REPLACE INTO past_contests(id,name,start_time,duration) VALUES(?,?,?,?)");
            for (int i = 0; i < contests.length(); i++) {
                JSONObject o = contests.getJSONObject(i);
                if ("FINISHED".equals(o.optString("phase")) && o.has("startTimeSeconds")) {
                    past.clearBindings();
                    past.bindLong(1, o.getInt("id"));
                    past.bindString(2, o.optString("name"));
                    past.bindLong(3, o.getLong("startTimeSeconds"));
                    past.bindLong(4, o.optLong("durationSeconds", 0));
                    past.executeInsert();
                    continue;
                }
                if (!"BEFORE".equals(o.optString("phase")) || !o.has("startTimeSeconds")) continue;
                ContentValues cv = new ContentValues();
                int id = o.getInt("id");
                cv.put("id", id);
                cv.put("name", o.optString("name"));
                cv.put("start_time", o.getLong("startTimeSeconds"));
                cv.put("duration", o.optLong("durationSeconds", 0));
                cv.put("remind", remind.contains(id) ? 1 : 0);
                db.insertWithOnConflict("contests", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveUserStatus(JSONArray subs) throws JSONException {
        HashMap<String, long[]> m = new HashMap<String, long[]>();
        for (int i = 0; i < subs.length(); i++) {
            JSONObject o = subs.getJSONObject(i);
            JSONObject p = o.optJSONObject("problem");
            if (p == null || !p.has("contestId")) continue;
            String key = p.getInt("contestId") + p.optString("index");
            long time = o.optLong("creationTimeSeconds", 0);
            long[] v = m.get(key);
            if (v == null) {
                v = new long[3];
                m.put(key, v);
            }
            String verdict = o.optString("verdict");
            if (verdict.length() == 0 || "TESTING".equals(verdict)) continue;
            v[1]++;
            if ("OK".equals(verdict)) {
                if (v[0] == 0 || time < v[2]) v[2] = time;
                v[0] = 1;
            } else if (v[0] == 0 && time > v[2]) {
                v[2] = time;
            }
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM user_problems");
            SQLiteStatement st = db.compileStatement("INSERT OR REPLACE INTO user_problems VALUES(?,?,?,?)");
            for (Map.Entry<String, long[]> e : m.entrySet()) {
                long[] v = e.getValue();
                st.clearBindings();
                st.bindString(1, e.getKey());
                st.bindLong(2, v[0]);
                st.bindLong(3, v[1]);
                st.bindLong(4, v[2]);
                st.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ------------------------------------------------------------------ LeetCode

    private static int lcContestId(String slug) {
        return 5000000 + (slug.hashCode() & 0xFFFFF);
    }

    public void saveLcContests(JSONArray all) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        HashSet<Integer> remind = new HashSet<Integer>();
        Cursor c = db.rawQuery("SELECT id FROM contests WHERE remind=1", null);
        while (c.moveToNext()) remind.add(c.getInt(0));
        c.close();
        long now = System.currentTimeMillis() / 1000;
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM contests");
            db.execSQL("DELETE FROM past_contests");
            int past = 0;
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.getJSONObject(i);
                String slug = o.optString("titleSlug");
                long start = o.optLong("startTime", 0);
                if (slug.length() == 0 || start == 0) continue;
                boolean upcoming = start > now;
                if (!upcoming && ++past > 40) continue;
                int id = lcContestId(slug);
                ContentValues cv = new ContentValues();
                cv.put("id", id);
                cv.put("name", o.optString("title"));
                cv.put("start_time", start);
                cv.put("duration", o.optLong("duration", 0));
                cv.put("slug", slug);
                if (upcoming) cv.put("remind", remind.contains(id) ? 1 : 0);
                db.insertWithOnConflict(upcoming ? "contests" : "past_contests", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveLcProblems(JSONArray questions) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM problems");
            SQLiteStatement st = db.compileStatement("INSERT OR REPLACE INTO problems VALUES(?,?,?,?,?,?,?)");
            for (int i = 0; i < questions.length(); i++) {
                JSONObject o = questions.getJSONObject(i);
                if (o.optBoolean("paid", false)) continue;
                String slug = o.optString("titleSlug");
                if (slug.length() == 0) continue;
                String diff = o.optString("difficulty");
                int level = "Easy".equalsIgnoreCase(diff) ? 1 : "Medium".equalsIgnoreCase(diff) ? 2 : 3;
                int number = 0;
                try {
                    number = Integer.parseInt(o.optString("id", "0"));
                } catch (NumberFormatException ignored) {
                }
                StringBuilder tags = new StringBuilder("|");
                JSONArray ta = o.optJSONArray("topicTags");
                if (ta != null) {
                    for (int j = 0; j < ta.length(); j++) tags.append(ta.getJSONObject(j).optString("name")).append('|');
                }
                st.clearBindings();
                st.bindString(1, slug);
                st.bindLong(2, number);
                st.bindString(3, "");
                st.bindString(4, o.optString("title", slug));
                st.bindLong(5, level);
                st.bindString(6, tags.toString());
                st.bindLong(7, Math.round(o.optDouble("acRate", 0) * 10));
                st.executeInsert();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** LeetCode only exposes the latest accepted submissions, so the solved list grows sync by sync. */
    public void mergeLcRecent(JSONArray recent) throws JSONException {
        if (recent == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < recent.length(); i++) {
                JSONObject o = recent.getJSONObject(i);
                String slug = o.optString("titleSlug");
                if (slug.length() == 0) continue;
                db.execSQL("INSERT OR IGNORE INTO user_problems VALUES(?,1,1,?)", new Object[]{slug, o.optLong("timestamp", 0)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveLcUser(JSONObject data) throws JSONException {
        JSONObject user = data.getJSONObject("matchedUser");
        setKv("handle", user.optString("username", kv("handle", "")));

        JSONObject stats = user.optJSONObject("submitStats");
        JSONArray ac = stats == null ? null : stats.optJSONArray("acSubmissionNum");
        saveCounts(ac, "solved_");
        saveCounts(data.optJSONArray("allQuestionsCount"), "all_");

        JSONObject ranking = data.optJSONObject("userContestRanking");
        int rating = ranking == null ? 0 : (int) Math.round(ranking.optDouble("rating", 0));
        double top = ranking == null ? 0 : ranking.optDouble("topPercentage", 0);
        setKv("rating", String.valueOf(rating));
        setKv("rank", top > 0 ? "Top " + top + "%" : "Unrated");

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM rating_history");
            JSONArray hist = data.optJSONArray("userContestRankingHistory");
            int prev = 1500;
            int max = 0;
            int n = 0;
            if (hist != null) {
                for (int i = 0; i < hist.length(); i++) {
                    JSONObject o = hist.getJSONObject(i);
                    if (!o.optBoolean("attended", false)) continue;
                    JSONObject ct = o.optJSONObject("contest");
                    int r = (int) Math.round(o.optDouble("rating", 0));
                    ContentValues cv = new ContentValues();
                    cv.put("contest_id", ++n);
                    cv.put("contest_name", ct == null ? "" : ct.optString("title"));
                    cv.put("time", ct == null ? 0 : ct.optLong("startTime", 0));
                    cv.put("old_rating", prev);
                    cv.put("new_rating", r);
                    db.insertWithOnConflict("rating_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    prev = r;
                    max = Math.max(max, r);
                }
            }
            db.execSQL("INSERT OR REPLACE INTO kv VALUES('max_rating',?)", new Object[]{String.valueOf(max)});

            db.execSQL("DELETE FROM calendar");
            JSONObject cal = user.optJSONObject("userCalendar");
            String raw = cal == null ? "" : cal.optString("submissionCalendar", "");
            if (raw.length() > 2) {
                JSONObject days = new JSONObject(raw);
                java.util.Iterator<String> it = days.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    String day = Instant.ofEpochSecond(Long.parseLong(key)).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
                    db.execSQL("INSERT OR REPLACE INTO calendar VALUES(?,?)", new Object[]{day, days.optInt(key, 0)});
                }
            }

            db.execSQL("DELETE FROM tag_counts");
            JSONObject tags = user.optJSONObject("tagProblemCounts");
            String[] groups = {"fundamental", "intermediate", "advanced"};
            for (int g = 0; tags != null && g < groups.length; g++) {
                JSONArray arr = tags.optJSONArray(groups[g]);
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    db.execSQL("INSERT OR REPLACE INTO tag_counts VALUES(?,?)", new Object[]{o.optString("tagName"), o.optInt("problemsSolved", 0)});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        mergeLcRecent(data.optJSONArray("recentAcSubmissionList"));
    }

    private void saveCounts(JSONArray arr, String prefix) throws JSONException {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String d = o.optString("difficulty");
            String key = "All".equals(d) ? "total" : "Easy".equals(d) ? "1" : "Medium".equals(d) ? "2" : "3";
            setKv(prefix + key, String.valueOf(o.optInt("count", 0)));
        }
    }

    // ------------------------------------------------------------------ trick flashcards

    /** Days between reviews for each Leitner box. */
    private static final int[] BOX_DAYS = {1, 3, 7, 16, 35, 80};

    public static long epochDay() {
        return java.time.LocalDate.now().toEpochDay();
    }

    /** Adds or updates the trick for a problem; a note you wrote wins over an AI trick. New cards are due tomorrow. */
    public void putCard(String pkey, String back, String source) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT source FROM cards WHERE pkey=?", new String[]{pkey});
        String old = null;
        try {
            if (c.moveToFirst()) old = c.getString(0);
        } finally {
            c.close();
        }
        if (old == null) {
            db.execSQL("INSERT INTO cards VALUES(?,?,?,0,?)", new Object[]{pkey, back, source, epochDay() + 1});
        } else if (!("note".equals(old) && "ai".equals(source))) {
            db.execSQL("UPDATE cards SET back=?, source=? WHERE pkey=?", new Object[]{back, source, pkey});
        }
    }

    public void removeCard(String pkey) {
        getWritableDatabase().execSQL("DELETE FROM cards WHERE pkey=? AND source='note'", new Object[]{pkey});
    }

    public int cardsDue() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cards WHERE due<=?", new String[]{String.valueOf(epochDay())});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int cardCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM cards", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Due cards as {problem, trick, box}, oldest due first. */
    public List<Object[]> dueCards(int limit) {
        String sql = "SELECT " + PCOLS + ", k.back, k.box FROM cards k JOIN problems p ON p.pkey=k.pkey" + PJOIN.replace(" LEFT JOIN", " LEFT JOIN")
                + " WHERE k.due<=? ORDER BY k.due, k.box LIMIT " + limit;
        ArrayList<Object[]> out = new ArrayList<Object[]>();
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(epochDay())});
        try {
            while (c.moveToNext()) out.add(new Object[]{readProblem(c, 0), c.getString(10), c.getInt(11)});
        } finally {
            c.close();
        }
        return out;
    }

    /** Remembered: the card waits longer next time. Forgot: back to tomorrow. */
    public void reviewCard(String pkey, boolean remembered) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT box FROM cards WHERE pkey=?", new String[]{pkey});
        int box = 0;
        try {
            if (c.moveToFirst()) box = c.getInt(0);
        } finally {
            c.close();
        }
        box = remembered ? Math.min(BOX_DAYS.length - 1, box + 1) : 0;
        db.execSQL("UPDATE cards SET box=?, due=? WHERE pkey=?", new Object[]{box, epochDay() + (remembered ? BOX_DAYS[box] : 1), pkey});
    }

    // ------------------------------------------------------------------ pattern tracks (LeetCode)

    /** Problems you have solved with this tag. LeetCode's own per-tag count when it has one, since its solve history is partial. */
    public int tagSolved(String tag) {
        int local = 0;
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM user_problems u JOIN problems p ON p.pkey=u.pkey WHERE u.solved=1 AND p.tags LIKE ?",
                new String[]{"%|" + tag + "|%"});
        try {
            if (c.moveToFirst()) local = c.getInt(0);
        } finally {
            c.close();
        }
        if (!lc) return local;
        Cursor t = getReadableDatabase().rawQuery("SELECT solved FROM tag_counts WHERE tag=?", new String[]{tag});
        try {
            return t.moveToFirst() ? Math.max(local, t.getInt(0)) : local;
        } finally {
            t.close();
        }
    }

    /** Next unsolved problems in a track: easiest level first, then the most accepted. */
    public List<Problem> trackProblems(String tag, int limit) {
        String sql = "SELECT " + PCOLS + " FROM problems p" + PJOIN
                + "WHERE p.tags LIKE ? AND IFNULL(u.solved,0)=0 ORDER BY p.rating ASC, p.solved_count DESC LIMIT " + limit;
        return readProblems(sql, new String[]{"%|" + tag + "|%"});
    }

    // ------------------------------------------------------------------ revisit queue

    /** Solved problems due to be solved again from memory: about 2 weeks after the solve, then again after 6 weeks. */
    public List<Object[]> revisitsDue(int limit) {
        long now = System.currentTimeMillis() / 1000;
        long day = 86400;
        String sql = "SELECT " + PCOLS + ", u.last_time FROM problems p" + PJOIN
                + " LEFT JOIN revisits r ON r.pkey=p.pkey"
                + " WHERE u.solved=1 AND u.last_time>0 AND ("
                + " (IFNULL(r.rounds,0)=0 AND u.last_time BETWEEN ? AND ? AND IFNULL(r.last,0) < ?)"
                + " OR (r.rounds=1 AND u.last_time BETWEEN ? AND ? AND r.last < ?))"
                + " ORDER BY u.last_time ASC LIMIT " + limit;
        String[] args = {String.valueOf(now - 75 * day), String.valueOf(now - 13 * day), String.valueOf(now - 7 * day),
                String.valueOf(now - 150 * day), String.valueOf(now - 42 * day), String.valueOf(now - 21 * day)};
        ArrayList<Object[]> out = new ArrayList<Object[]>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) out.add(new Object[]{readProblem(c, 0), c.getLong(10)});
        } finally {
            c.close();
        }
        return out;
    }

    public void markRevisited(String pkey) {
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO revisits VALUES(?, IFNULL((SELECT rounds FROM revisits WHERE pkey=?),0)+1, ?)",
                new Object[]{pkey, pkey, System.currentTimeMillis() / 1000});
    }

    /** Pushes a revisit back by a week without counting it. */
    public void snoozeRevisit(String pkey) {
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO revisits VALUES(?, IFNULL((SELECT rounds FROM revisits WHERE pkey=?),0), ?)",
                new Object[]{pkey, pkey, System.currentTimeMillis() / 1000});
    }

    public Problem problem(String pkey) {
        List<Problem> r = readProblems("SELECT " + PCOLS + " FROM problems p" + PJOIN + "WHERE p.pkey=?", new String[]{pkey});
        return r.isEmpty() ? null : r.get(0);
    }

    // ------------------------------------------------------------------ shared

    /** Merges a handful of recent submissions without rebuilding the whole table. */
    public void mergeRecentStatus(JSONArray subs) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < subs.length(); i++) {
                JSONObject o = subs.getJSONObject(i);
                JSONObject p = o.optJSONObject("problem");
                if (p == null || !p.has("contestId")) continue;
                String verdict = o.optString("verdict");
                if (verdict.length() == 0 || "TESTING".equals(verdict)) continue;
                String key = p.getInt("contestId") + p.optString("index");
                long time = o.optLong("creationTimeSeconds", 0);
                boolean ok = "OK".equals(verdict);
                int state = -1;
                Cursor c = db.rawQuery("SELECT solved FROM user_problems WHERE pkey=?", new String[]{key});
                if (c.moveToFirst()) state = c.getInt(0);
                c.close();
                if (state == -1) {
                    db.execSQL("INSERT INTO user_problems VALUES(?,?,1,?)", new Object[]{key, ok ? 1 : 0, time});
                } else if (state == 0 && ok) {
                    db.execSQL("UPDATE user_problems SET solved=1, last_time=? WHERE pkey=?", new Object[]{time, key});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void saveRatingHistory(JSONArray arr) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM rating_history");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ContentValues cv = new ContentValues();
                cv.put("contest_id", o.getInt("contestId"));
                cv.put("contest_name", o.optString("contestName"));
                cv.put("time", o.optLong("ratingUpdateTimeSeconds", 0));
                cv.put("old_rating", o.optInt("oldRating", 0));
                cv.put("new_rating", o.optInt("newRating", 0));
                db.insertWithOnConflict("rating_history", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public int problemCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM problems", null);
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

    public int solvedCount() {
        if (lc && kvInt("solved_total", 0) > 0) return kvInt("solved_total", 0);
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM user_problems WHERE solved=1", null);
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

    private static String[] splitTags(String s) {
        ArrayList<String> out = new ArrayList<String>();
        if (s != null) {
            String[] parts = s.split("\\|");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].length() > 0) out.add(parts[i]);
            }
        }
        return out.toArray(new String[0]);
    }

    private static Problem readProblem(Cursor c, int off) {
        Problem p = new Problem();
        p.pkey = c.getString(off);
        p.contestId = c.getInt(off + 1);
        p.index = c.getString(off + 2);
        p.name = c.getString(off + 3);
        p.rating = c.getInt(off + 4);
        p.tags = splitTags(c.getString(off + 5));
        p.solvedCount = c.getInt(off + 6);
        p.solved = c.getInt(off + 7) == 1;
        p.bookmarked = c.getInt(off + 8) == 1;
        p.note = c.getString(off + 9);
        return p;
    }

    public List<Problem> queryProblems(int min, int max, String tag, String search, boolean hideSolved, boolean attemptedOnly, boolean bookmarksOnly, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + PCOLS + " FROM problems p" + PJOIN + "WHERE p.rating BETWEEN ? AND ?");
        ArrayList<String> args = new ArrayList<String>();
        args.add(String.valueOf(min));
        args.add(String.valueOf(max));
        if (tag != null) {
            sql.append(" AND p.tags LIKE ?");
            args.add("%|" + tag + "|%");
        }
        if (search != null && search.length() > 0) {
            sql.append(" AND (p.name LIKE ? OR p.pkey LIKE ? OR n.note LIKE ?)");
            args.add("%" + search + "%");
            args.add(search + "%");
            args.add("%" + search + "%");
        }
        if (hideSolved) sql.append(" AND IFNULL(u.solved,0)=0");
        if (attemptedOnly) sql.append(" AND u.solved=0 AND u.attempts>0");
        if (bookmarksOnly) sql.append(" AND b.pkey IS NOT NULL");
        sql.append(attemptedOnly ? " ORDER BY u.last_time DESC" : " ORDER BY p.contest_id DESC, p.idx");
        sql.append(" LIMIT ").append(limit);
        return readProblems(sql.toString(), args.toArray(new String[0]));
    }

    public List<Problem> candidates(int lo, int hi) {
        String sql = "SELECT " + PCOLS + " FROM problems p" + PJOIN
                + "WHERE p.rating BETWEEN ? AND ? AND IFNULL(u.solved,0)=0"
                + " AND p.tags NOT LIKE '%|*special|%'"
                + " AND p.pkey NOT IN (SELECT pkey FROM daily)"
                + " AND p.name NOT IN (SELECT p2.name FROM problems p2 JOIN user_problems u2 ON u2.pkey=p2.pkey WHERE u2.solved=1)"
                + " ORDER BY p.contest_id DESC LIMIT 600";
        return readProblems(sql, new String[]{String.valueOf(lo), String.valueOf(hi)});
    }

    private List<Problem> readProblems(String sql, String[] args) {
        ArrayList<Problem> out = new ArrayList<Problem>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) out.add(readProblem(c, 0));
        } finally {
            c.close();
        }
        return out;
    }

    /** Most popular unsolved problem with this tag at exactly this rating, or null. */
    public Problem ladderPick(String tag, int rating) {
        List<Problem> r = ladderPicks(tag, rating, 1);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Problem> ladderPicks(String tag, int rating, int limit) {
        String sql = "SELECT " + PCOLS + " FROM problems p" + PJOIN
                + "WHERE p.rating=? AND p.tags LIKE ? AND IFNULL(u.solved,0)=0"
                + " AND p.tags NOT LIKE '%|*special|%'"
                + " AND p.pkey NOT IN (SELECT pkey FROM daily)"
                + " ORDER BY p.solved_count DESC LIMIT " + limit;
        return readProblems(sql, new String[]{String.valueOf(rating), "%|" + tag + "|%"});
    }

    public void setNote(String pkey, String note) {
        SQLiteDatabase db = getWritableDatabase();
        if (note == null || note.trim().length() == 0) {
            db.delete("notes", "pkey=?", new String[]{pkey});
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put("pkey", pkey);
        cv.put("note", note.trim());
        db.insertWithOnConflict("notes", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void putFriend(String handle, int rating, String rank, int week) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("DELETE FROM friends WHERE handle=? COLLATE NOCASE", new Object[]{handle});
        ContentValues cv = new ContentValues();
        cv.put("handle", handle);
        cv.put("rating", rating);
        cv.put("rank", rank);
        cv.put("week", week);
        cv.put("updated", System.currentTimeMillis());
        db.insert("friends", null, cv);
    }

    public void removeFriend(String handle) {
        getWritableDatabase().delete("friends", "handle=?", new String[]{handle});
    }

    public List<Friend> friends() {
        ArrayList<Friend> out = new ArrayList<Friend>();
        Cursor c = getReadableDatabase().rawQuery("SELECT handle,rating,rank,week FROM friends ORDER BY handle COLLATE NOCASE", null);
        try {
            while (c.moveToNext()) {
                Friend f = new Friend();
                f.handle = c.getString(0);
                f.rating = c.getInt(1);
                f.rank = c.getString(2);
                f.week = c.getInt(3);
                out.add(f);
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Problems you first solved in the last 7 days. */
    public int weekSolved() {
        long since = System.currentTimeMillis() / 1000 - 7L * 24 * 3600;
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM user_problems WHERE solved=1 AND last_time>?", new String[]{String.valueOf(since)});
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

    /** Finished rounds with problems in the problem set that you never submitted to and were not rated in, newest first. */
    public List<Contest> untouchedPastContests(int limit) {
        ArrayList<Contest> out = new ArrayList<Contest>();
        String sql = lc
                ? "SELECT id,name,start_time,duration,IFNULL(slug,'') FROM past_contests"
                + " WHERE name NOT IN (SELECT contest_name FROM rating_history)"
                + " ORDER BY start_time DESC LIMIT " + limit
                : "SELECT id,name,start_time,duration,IFNULL(slug,'') FROM past_contests"
                + " WHERE id NOT IN (SELECT CAST(pkey AS INTEGER) FROM user_problems)"
                + " AND id NOT IN (SELECT contest_id FROM rating_history)"
                + " AND id IN (SELECT contest_id FROM problems WHERE rating>0)"
                + " ORDER BY start_time DESC LIMIT " + limit;
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        try {
            while (c.moveToNext()) {
                Contest ct = new Contest();
                ct.id = c.getInt(0);
                ct.name = c.getString(1);
                ct.startSeconds = c.getLong(2);
                ct.durationSeconds = c.getLong(3);
                ct.slug = c.getString(4);
                out.add(ct);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public int maxContestId() {
        Cursor c = getReadableDatabase().rawQuery("SELECT IFNULL(MAX(contest_id),1) FROM problems", null);
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

    public boolean toggleBookmark(String pkey) {
        SQLiteDatabase db = getWritableDatabase();
        int removed = db.delete("bookmarks", "pkey=?", new String[]{pkey});
        if (removed > 0) return false;
        ContentValues cv = new ContentValues();
        cv.put("pkey", pkey);
        cv.put("added", System.currentTimeMillis());
        db.insert("bookmarks", null, cv);
        return true;
    }

    public List<String> allTags() {
        TreeSet<String> set = new TreeSet<String>();
        Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT tags FROM problems", null);
        try {
            while (c.moveToNext()) {
                String[] t = splitTags(c.getString(0));
                for (int i = 0; i < t.length; i++) set.add(t[i]);
            }
        } finally {
            c.close();
        }
        return new ArrayList<String>(set);
    }

    public List<Contest> contests(boolean remindOnly) {
        ArrayList<Contest> out = new ArrayList<Contest>();
        long now = System.currentTimeMillis() / 1000;
        String sql = "SELECT id,name,start_time,duration,remind,IFNULL(slug,'') FROM contests WHERE start_time>?"
                + (remindOnly ? " AND remind=1" : "") + " ORDER BY start_time";
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(now)});
        try {
            while (c.moveToNext()) {
                Contest ct = new Contest();
                ct.id = c.getInt(0);
                ct.name = c.getString(1);
                ct.startSeconds = c.getLong(2);
                ct.durationSeconds = c.getLong(3);
                ct.remind = c.getInt(4) == 1;
                ct.slug = c.getString(5);
                out.add(ct);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void setRemind(int id, boolean on) {
        getWritableDatabase().execSQL("UPDATE contests SET remind=? WHERE id=?", new Object[]{on ? 1 : 0, id});
    }

    private List<DailyItem> readDaily(String where, String[] args) {
        ArrayList<DailyItem> out = new ArrayList<DailyItem>();
        String sql = "SELECT d.day,d.slot,d.solved," + PCOLS + " FROM daily d JOIN problems p ON p.pkey=d.pkey" + PJOIN + where;
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                DailyItem it = new DailyItem();
                it.day = c.getString(0);
                it.slot = c.getInt(1);
                it.solved = c.getInt(2) == 1;
                it.problem = readProblem(c, 3);
                out.add(it);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public List<DailyItem> daily(String day) {
        return readDaily("WHERE d.day=? ORDER BY d.slot", new String[]{day});
    }

    public List<DailyItem> history(String beforeDay, int limit) {
        return readDaily("WHERE d.day<? ORDER BY d.day DESC, d.slot LIMIT " + limit, new String[]{beforeDay});
    }

    public void putDaily(String day, int slot, String pkey) {
        ContentValues cv = new ContentValues();
        cv.put("day", day);
        cv.put("slot", slot);
        cv.put("pkey", pkey);
        cv.put("solved", 0);
        getWritableDatabase().insertWithOnConflict("daily", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void markDailySolved() {
        getWritableDatabase().execSQL("UPDATE daily SET solved=1 WHERE pkey IN (SELECT pkey FROM user_problems WHERE solved=1)");
    }

    public int[] dailyTotals() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*), IFNULL(SUM(solved),0) FROM daily", null);
        try {
            c.moveToFirst();
            return new int[]{c.getInt(0), c.getInt(1)};
        } finally {
            c.close();
        }
    }

    public int[] streaks() {
        HashSet<String> days = new HashSet<String>();
        ArrayList<String> sorted = new ArrayList<String>();
        Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT day FROM daily WHERE solved=1 ORDER BY day", null);
        try {
            while (c.moveToNext()) {
                days.add(c.getString(0));
                sorted.add(c.getString(0));
            }
        } finally {
            c.close();
        }
        LocalDate d = LocalDate.now();
        if (!days.contains(d.toString())) d = d.minusDays(1);
        int current = 0;
        while (days.contains(d.toString())) {
            current++;
            d = d.minusDays(1);
        }
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (int i = 0; i < sorted.size(); i++) {
            LocalDate cur = LocalDate.parse(sorted.get(i));
            run = (prev != null && prev.plusDays(1).equals(cur)) ? run + 1 : 1;
            if (run > best) best = run;
            prev = cur;
        }
        return new int[]{current, best};
    }

    public List<RatingPoint> ratingHistory() {
        ArrayList<RatingPoint> out = new ArrayList<RatingPoint>();
        Cursor c = getReadableDatabase().rawQuery("SELECT contest_name,time,old_rating,new_rating FROM rating_history ORDER BY time", null);
        try {
            while (c.moveToNext()) {
                RatingPoint r = new RatingPoint();
                r.contestName = c.getString(0);
                r.timeSeconds = c.getLong(1);
                r.oldRating = c.getInt(2);
                r.newRating = c.getInt(3);
                out.add(r);
            }
        } finally {
            c.close();
        }
        return out;
    }

    public Map<String, TagStat> tagStats() {
        HashMap<String, TagStat> m = new HashMap<String, TagStat>();
        if (lc) {
            Cursor tc = getReadableDatabase().rawQuery("SELECT tag,solved FROM tag_counts", null);
            try {
                while (tc.moveToNext()) {
                    TagStat t = new TagStat();
                    t.tag = tc.getString(0);
                    t.solved = tc.getInt(1);
                    m.put(t.tag, t);
                }
            } finally {
                tc.close();
            }
            return m;
        }
        Cursor c = getReadableDatabase().rawQuery("SELECT p.tags,u.solved,p.rating FROM user_problems u JOIN problems p ON p.pkey=u.pkey", null);
        try {
            while (c.moveToNext()) {
                String[] tags = splitTags(c.getString(0));
                boolean solved = c.getInt(1) == 1;
                int rating = c.getInt(2);
                for (int i = 0; i < tags.length; i++) {
                    TagStat t = m.get(tags[i]);
                    if (t == null) {
                        t = new TagStat();
                        t.tag = tags[i];
                        m.put(tags[i], t);
                    }
                    if (solved) {
                        t.solved++;
                        if (rating > 0) {
                            t.ratingSum += rating;
                            t.ratedCount++;
                        }
                    } else {
                        t.failed++;
                    }
                }
            }
        } finally {
            c.close();
        }
        return m;
    }

    /** Local day (yyyy-MM-dd) -> number of problems first solved that day. */
    public Map<String, Integer> solveDays() {
        HashMap<String, Integer> m = new HashMap<String, Integer>();
        if (lc) {
            // LeetCode publishes submissions per day, which is the closest public signal.
            Cursor cal = getReadableDatabase().rawQuery("SELECT day,n FROM calendar", null);
            try {
                while (cal.moveToNext()) m.put(cal.getString(0), cal.getInt(1));
            } finally {
                cal.close();
            }
            return m;
        }
        ZoneId zone = ZoneId.systemDefault();
        Cursor c = getReadableDatabase().rawQuery("SELECT last_time FROM user_problems WHERE solved=1 AND last_time>0", null);
        try {
            while (c.moveToNext()) {
                String day = Instant.ofEpochSecond(c.getLong(0)).atZone(zone).toLocalDate().toString();
                Integer n = m.get(day);
                m.put(day, n == null ? 1 : n + 1);
            }
        } finally {
            c.close();
        }
        return m;
    }

    /** Rows of {rating, solved count}, ascending by rating. */
    public List<int[]> solvedByRating() {
        ArrayList<int[]> out = new ArrayList<int[]>();
        Cursor c = getReadableDatabase().rawQuery("SELECT p.rating, COUNT(*) FROM user_problems u JOIN problems p ON p.pkey=u.pkey WHERE u.solved=1 AND p.rating>0 GROUP BY p.rating ORDER BY p.rating", null);
        try {
            while (c.moveToNext()) out.add(new int[]{c.getInt(0), c.getInt(1)});
        } finally {
            c.close();
        }
        return out;
    }

    public int estimateFromSolved() {
        ArrayList<Integer> r = new ArrayList<Integer>();
        Cursor c = getReadableDatabase().rawQuery("SELECT p.rating FROM user_problems u JOIN problems p ON p.pkey=u.pkey WHERE u.solved=1 AND p.rating>0 ORDER BY u.last_time DESC LIMIT 20", null);
        try {
            while (c.moveToNext()) r.add(c.getInt(0));
        } finally {
            c.close();
        }
        if (r.isEmpty()) return 0;
        Collections.sort(r);
        int i = Math.min(r.size() - 1, (r.size() * 3) / 4);
        return r.get(i);
    }
}
