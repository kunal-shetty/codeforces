package com.kun.cfdaily.data;

import android.content.Context;

/** Which site the app is working with. Each mode has its own database, handle and daily picks. */
public class Mode {

    private static Boolean lc;

    public static synchronized boolean lc(Context c) {
        if (lc == null) lc = c.getSharedPreferences("ui", 0).getBoolean("lc", false);
        return lc;
    }

    /** Valid once anything has opened the database, which every screen and receiver does first. */
    public static boolean lc() {
        return lc != null && lc;
    }

    public static synchronized void set(Context c, boolean leetcode) {
        lc = leetcode;
        c.getSharedPreferences("ui", 0).edit().putBoolean("lc", leetcode).apply();
    }

    public static String site() {
        return lc() ? "LeetCode" : "Codeforces";
    }
}
