package com.kun.cfdaily.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;

/** Lesson results and daily XP. One database for both sites: what you learn doesn't depend on where you practise. */
public class LearnDb extends SQLiteOpenHelper {

    // Keep in sync with backend/app/content.py.
    public static final int LESSON_XP = 10;
    public static final int PERFECT_BONUS = 5;
    public static final int DAILY_GOAL = 20;

    private static LearnDb instance;

    public static synchronized LearnDb get(Context c) {
        if (instance == null) instance = new LearnDb(c.getApplicationContext());
        return instance;
    }

    private LearnDb(Context c) {
        super(c, "learn.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE lessons(id TEXT PRIMARY KEY, stars INTEGER, xp INTEGER, completions INTEGER, first_at INTEGER)");
        db.execSQL("CREATE TABLE days(day TEXT PRIMARY KEY, xp INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public static int stars(int mistakes) {
        return mistakes == 0 ? 3 : mistakes <= 2 ? 2 : 1;
    }

    public static int xp(int mistakes) {
        return LESSON_XP + (mistakes == 0 ? PERFECT_BONUS : 0);
    }

    /** Records a finished lesson and returns the XP it earned. Replays earn XP too; the best stars are kept. */
    public synchronized int complete(String lessonId, int mistakes) {
        int stars = stars(mistakes), xp = xp(mistakes);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // No UPSERT: Android 8 to 10 ship SQLite older than 3.24.
            db.execSQL("INSERT OR IGNORE INTO lessons VALUES(?,?,?,0,?)", new Object[]{lessonId, stars, xp, System.currentTimeMillis()});
            db.execSQL("UPDATE lessons SET stars=MAX(stars,?), xp=MAX(xp,?), completions=completions+1 WHERE id=?",
                    new Object[]{stars, xp, lessonId});
            String today = LocalDate.now().toString();
            db.execSQL("INSERT OR IGNORE INTO days VALUES(?,0)", new Object[]{today});
            db.execSQL("UPDATE days SET xp=xp+? WHERE day=?", new Object[]{xp, today});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return xp;
    }

    /** Best stars for a lesson, 0 when not done yet. */
    public int stars(String lessonId) {
        return intQuery("SELECT stars FROM lessons WHERE id=?", lessonId);
    }

    public int todayXp() {
        return intQuery("SELECT xp FROM days WHERE day=?", LocalDate.now().toString());
    }

    public int totalXp() {
        return intQuery("SELECT SUM(xp) FROM days");
    }

    public int lessonsDone() {
        return intQuery("SELECT COUNT(*) FROM lessons");
    }

    public int starsTotal() {
        return intQuery("SELECT SUM(stars) FROM lessons");
    }

    /** Days in a row with a lesson, counting today or, if nothing yet today, up to yesterday. */
    public int streak() {
        LocalDate day = LocalDate.now();
        if (todayXp() == 0) day = day.minusDays(1);
        int n = 0;
        while (intQuery("SELECT xp FROM days WHERE day=?", day.toString()) > 0) {
            n++;
            day = day.minusDays(1);
        }
        return n;
    }

    private int intQuery(String sql, String... args) {
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    // ---------------------------------------------------------------- sync with the server

    /** Everything this phone knows, in the shape of POST /progress/sync. */
    public JSONObject syncBody() throws JSONException {
        JSONArray lessons = new JSONArray();
        Cursor c = getReadableDatabase().rawQuery("SELECT id, stars, xp, completions, first_at FROM lessons", null);
        try {
            while (c.moveToNext()) {
                lessons.put(new JSONObject().put("lesson_id", c.getString(0)).put("stars", c.getInt(1)).put("xp", c.getInt(2))
                        .put("completions", c.getInt(3)).put("completed_at", Instant.ofEpochMilli(c.getLong(4)).toString()));
            }
        } finally {
            c.close();
        }
        JSONArray days = new JSONArray();
        Cursor d = getReadableDatabase().rawQuery("SELECT day, xp FROM days", null);
        try {
            while (d.moveToNext()) days.put(new JSONObject().put("day", d.getString(0)).put("xp", d.getInt(1)));
        } finally {
            d.close();
        }
        return new JSONObject().put("lessons", lessons).put("days", days);
    }

    /** ISO time from the server, with or without an offset (a naive time is UTC). */
    private static long parseTime(String s) {
        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception withoutOffset) {
            try {
                return java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            } catch (Exception e) {
                return System.currentTimeMillis();
            }
        }
    }

    /** Takes the server's merged state: keeps the better of each value, as the server does. */
    public synchronized void merge(JSONObject state) throws JSONException {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            JSONArray lessons = state.getJSONArray("lessons");
            for (int i = 0; i < lessons.length(); i++) {
                JSONObject l = lessons.getJSONObject(i);
                String id = l.getString("lesson_id");
                int stars = l.getInt("stars"), xp = l.getInt("xp"), completions = l.getInt("completions");
                long first = parseTime(l.optString("first_completed_at"));
                db.execSQL("INSERT OR IGNORE INTO lessons VALUES(?,?,?,?,?)", new Object[]{id, stars, xp, completions, first});
                db.execSQL("UPDATE lessons SET stars=MAX(stars,?), xp=MAX(xp,?), completions=MAX(completions,?), first_at=MIN(first_at,?) WHERE id=?",
                        new Object[]{stars, xp, completions, first, id});
            }
            JSONArray days = state.getJSONArray("days");
            for (int i = 0; i < days.length(); i++) {
                JSONObject d = days.getJSONObject(i);
                db.execSQL("INSERT OR IGNORE INTO days VALUES(?,0)", new Object[]{d.getString("day")});
                db.execSQL("UPDATE days SET xp=MAX(xp,?) WHERE day=?", new Object[]{d.getInt("xp"), d.getString("day")});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
