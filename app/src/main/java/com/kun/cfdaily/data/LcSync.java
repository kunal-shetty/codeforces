package com.kun.cfdaily.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;

/** The LeetCode side of Sync. */
class LcSync {

    private static final String Q_HOME = "{ allContests { title titleSlug startTime duration }"
            + " activeDailyCodingChallengeQuestion { date question { titleSlug } } }";

    private static final String Q_PROBLEMS = "query($l:Int,$s:Int){ list: questionList(categorySlug:\"\", limit:$l, skip:$s, filters:{})"
            + " { total: totalNum questions: data { id: questionFrontendId title titleSlug difficulty acRate paid: isPaidOnly topicTags { name } } } }";

    private static final String Q_USER = "query($u:String!){ matchedUser(username:$u){ username"
            + " submitStats{acSubmissionNum{difficulty count}} userCalendar{submissionCalendar}"
            + " tagProblemCounts{advanced{tagName problemsSolved} intermediate{tagName problemsSolved} fundamental{tagName problemsSolved}} }"
            + " userContestRanking(username:$u){rating topPercentage}"
            + " userContestRankingHistory(username:$u){attended rating contest{title startTime}}"
            + " recentAcSubmissionList(username:$u, limit:20){titleSlug timestamp}"
            + " allQuestionsCount{difficulty count} }";

    private static final String Q_RECENT = "query($u:String!){ recentAcSubmissionList(username:$u, limit:20){titleSlug timestamp} }";

    private static final String Q_FRIEND = "query($u:String!){ matchedUser(username:$u){ username }"
            + " userContestRanking(username:$u){rating}"
            + " recentAcSubmissionList(username:$u, limit:20){titleSlug timestamp} }";

    private static JSONObject user(String handle) throws Exception {
        return new JSONObject().put("u", handle);
    }

    static void full(Context app, boolean forceProblems) throws Exception {
        Db db = Db.forMode(app, true);
        long now = System.currentTimeMillis();

        JSONObject home = LcApi.query(Q_HOME, null, false);
        db.saveLcContests(home.getJSONArray("allContests"));
        JSONObject daily = home.optJSONObject("activeDailyCodingChallengeQuestion");
        if (daily != null && daily.optJSONObject("question") != null) {
            db.setKv("lc_daily", daily.getJSONObject("question").optString("titleSlug"));
            db.setKv("lc_daily_date", daily.optString("date"));
        }

        // The list only comes 100 at a time, so it is refreshed weekly rather than daily.
        long last = db.kvLong("problems_time", 0);
        if (forceProblems || db.problemCount() == 0 || now - last > 7L * 24 * 3600 * 1000) {
            JSONArray all = new JSONArray();
            int total = Integer.MAX_VALUE;
            for (int skip = 0; skip < total && skip < 8000; skip += 100) {
                JSONObject vars = new JSONObject().put("l", 100).put("s", skip);
                JSONObject list = LcApi.query(Q_PROBLEMS, vars, false).getJSONObject("list");
                total = list.optInt("total", 0);
                JSONArray page = list.getJSONArray("questions");
                if (page.length() == 0) break;
                for (int i = 0; i < page.length(); i++) all.put(page.getJSONObject(i));
            }
            if (all.length() > 0) {
                db.saveLcProblems(all);
                db.setKv("problems_time", String.valueOf(now));
            }
        }

        String handle = db.kv("handle", "");
        if (handle.length() > 0) {
            JSONObject u = LcApi.query(Q_USER, user(handle), false);
            if (u.isNull("matchedUser")) throw new Exception("LeetCode user " + handle + " not found");
            db.saveLcUser(u);
            db.markDailySolved();
        }

        db.setKv("last_sync", String.valueOf(now));
        Reminders.rescheduleAll(app);

        java.util.List<Models.Friend> friends = db.friends();
        for (int i = 0; i < friends.size(); i++) {
            try {
                friend(db, friends.get(i).handle);
            } catch (java.net.UnknownHostException e) {
                break;
            } catch (Exception ignored) {
            }
        }
    }

    static void quick(Db db, boolean fast) throws Exception {
        String handle = db.kv("handle", "");
        if (handle.length() == 0) return;
        JSONObject d = LcApi.query(Q_RECENT, user(handle), fast);
        db.mergeLcRecent(d.optJSONArray("recentAcSubmissionList"));
        db.markDailySolved();
    }

    static void friend(Db db, String handle) throws Exception {
        JSONObject d = LcApi.query(Q_FRIEND, user(handle), false);
        if (d.isNull("matchedUser")) throw new Exception("not found");
        JSONObject ranking = d.optJSONObject("userContestRanking");
        int rating = ranking == null ? 0 : (int) Math.round(ranking.optDouble("rating", 0));
        long weekAgo = System.currentTimeMillis() / 1000 - 7L * 24 * 3600;
        HashSet<String> solved = new HashSet<String>();
        JSONArray recent = d.optJSONArray("recentAcSubmissionList");
        if (recent != null) {
            for (int i = 0; i < recent.length(); i++) {
                JSONObject o = recent.getJSONObject(i);
                if (o.optLong("timestamp", 0) >= weekAgo) solved.add(o.optString("titleSlug"));
            }
        }
        db.putFriend(d.getJSONObject("matchedUser").optString("username", handle), rating, "", solved.size());
    }
}
