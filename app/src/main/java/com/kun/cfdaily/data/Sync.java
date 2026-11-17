package com.kun.cfdaily.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;

public class Sync {

    public interface Done {
        void onDone(String error);
    }

    private static final int FULL = 0;
    private static final int QUICK = 1;
    private static final int FRIEND = 2;

    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    public static boolean start(Context ctx, boolean forceProblems, Done done) {
        return launch(ctx, FULL, forceProblems, null, done);
    }

    /** Light check of recent submissions only, so solves show up right after returning from the browser. */
    public static boolean quick(Context ctx, Done done) {
        return launch(ctx, QUICK, false, null, done);
    }

    /** Looks the handle up on Codeforces and stores it as a friend; fails with a message if it does not exist. */
    public static boolean addFriend(Context ctx, String handle, Done done) {
        return launch(ctx, FRIEND, false, handle, done);
    }

    private static boolean launch(Context ctx, final int mode, final boolean forceProblems, final String arg, final Done done) {
        if (running) return false;
        running = true;
        final Context app = ctx.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    if (mode == QUICK) quickBlocking(app, false);
                    else if (mode == FRIEND) fetchFriend(Db.get(app), arg);
                    else doSync(app, forceProblems);
                } catch (UnknownHostException e) {
                    err = "No internet connection";
                } catch (Exception e) {
                    err = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final String result = err;
                running = false;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        done.onDone(result);
                    }
                });
            }
        }).start();
        return true;
    }

    /** Runs on the calling thread. With fast=true it gives up after a few seconds (for broadcast receivers). */
    public static void quickBlocking(Context app, boolean fast) throws Exception {
        Db db = Db.get(app);
        if (db.lc) {
            LcSync.quick(db, fast);
            return;
        }
        String handle = db.kv("handle", "");
        if (handle.length() == 0) return;
        String query = "handle=" + URLEncoder.encode(handle, "UTF-8") + "&from=1&count=60";
        db.mergeRecentStatus(fast ? CfApi.getArrayFast("user.status", query) : CfApi.getArray("user.status", query));
        db.markDailySolved();
    }

    private static void fetchFriend(Db db, String handle) throws Exception {
        if (db.lc) {
            LcSync.friend(db, handle);
            return;
        }
        String h = URLEncoder.encode(handle, "UTF-8");
        JSONObject u = CfApi.getArray("user.info", "handles=" + h).getJSONObject(0);
        JSONArray subs = CfApi.getArray("user.status", "handle=" + h + "&from=1&count=200");
        long weekAgo = System.currentTimeMillis() / 1000 - 7L * 24 * 3600;
        HashSet<String> solved = new HashSet<String>();
        for (int i = 0; i < subs.length(); i++) {
            JSONObject o = subs.getJSONObject(i);
            JSONObject p = o.optJSONObject("problem");
            if (p == null || !"OK".equals(o.optString("verdict"))) continue;
            if (o.optLong("creationTimeSeconds", 0) < weekAgo) continue;
            solved.add(p.optInt("contestId", 0) + p.optString("index") + p.optString("name"));
        }
        db.putFriend(u.optString("handle", handle), u.optInt("rating", 0), u.optString("rank", "unrated"), solved.size());
    }

    private static void doSync(Context app, boolean forceProblems) throws Exception {
        if (Mode.lc(app)) {
            LcSync.full(app, forceProblems);
            return;
        }
        Db db = Db.get(app);
        long now = System.currentTimeMillis();

        db.saveContests(CfApi.getArray("contest.list", "gym=false"));

        long last = db.kvLong("problems_time", 0);
        if (forceProblems || db.problemCount() == 0 || now - last > 24L * 3600 * 1000) {
            JSONObject r = CfApi.getObject("problemset.problems", "");
            db.saveProblems(r.getJSONArray("problems"), r.getJSONArray("problemStatistics"));
            db.setKv("problems_time", String.valueOf(now));
        }

        String handle = db.kv("handle", "");
        if (handle.length() > 0) {
            String h = URLEncoder.encode(handle, "UTF-8");
            JSONArray info = CfApi.getArray("user.info", "handles=" + h);
            JSONObject u = info.getJSONObject(0);
            db.setKv("handle", u.optString("handle", handle));
            db.setKv("rating", String.valueOf(u.optInt("rating", 0)));
            db.setKv("max_rating", String.valueOf(u.optInt("maxRating", 0)));
            db.setKv("rank", u.optString("rank", "unrated"));
            db.saveRatingHistory(CfApi.getArray("user.rating", "handle=" + h));
            db.saveUserStatus(CfApi.getArray("user.status", "handle=" + h));
            db.markDailySolved();
        }

        db.setKv("est", String.valueOf(Recommender.estimate(db)));
        db.setKv("last_sync", String.valueOf(now));
        Reminders.rescheduleAll(app);

        // Friends are a nice-to-have: one that was renamed or deleted must not fail the whole sync.
        List<Models.Friend> friends = db.friends();
        for (int i = 0; i < friends.size(); i++) {
            try {
                fetchFriend(db, friends.get(i).handle);
            } catch (UnknownHostException e) {
                break;
            } catch (Exception ignored) {
            }
        }
    }
}
