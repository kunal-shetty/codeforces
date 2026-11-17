package com.kun.cfdaily.data;

import android.content.Context;

import com.kun.cfdaily.data.Models.Contest;
import com.kun.cfdaily.data.Models.DailyItem;
import com.kun.cfdaily.data.Models.Problem;
import com.kun.cfdaily.data.Models.TagStat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Recommender {

    public static final String[] SLOT_NAMES = {"Warm-up", "Target", "Stretch"};
    private static final int[] OFFSETS = {-100, 100, 300};
    private static final int[] WIDEN = {0, 100, 200, 300, 500, 800};

    public static String today() {
        return LocalDate.now().toString();
    }

    public static int estimate(Db db) {
        int rating = db.kvInt("rating", 0);
        if (rating > 0) return rating;
        int fromSolved = db.estimateFromSolved();
        return fromSolved > 0 ? fromSolved : 800;
    }

    /** Level the picks are built around: the estimate shifted by the difficulty setting (-1, 0, +1). */
    public static int level(Db db) {
        if (db.lc) return db.kvInt("rating", 0);
        int d = Math.max(-1, Math.min(1, db.kvInt("difficulty", 0)));
        return Math.max(800, db.kvInt("est", estimate(db)) + 100 * d);
    }

    /** First rating of the topic ladder: two steps under your level. */
    public static int ladderStart(Db db) {
        return Math.max(800, Math.round(level(db) / 100f) * 100 - 200);
    }

    public static final int LADDER_STEPS = 6;

    /** One unsolved problem per 100 rating points for the tag, climbing from ladderStart. */
    public static List<Problem> ladder(Db db, String tag) {
        List<Problem> out = new ArrayList<Problem>();
        if (db.lc) {
            out.addAll(db.ladderPicks(tag, 1, 2));
            out.addAll(db.ladderPicks(tag, 2, 3));
            out.addAll(db.ladderPicks(tag, 3, 1));
            return out;
        }
        int from = ladderStart(db);
        for (int i = 0; i < LADDER_STEPS; i++) {
            int r = from + 100 * i;
            if (r > 3500) break;
            Problem p = db.ladderPick(tag, r);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Whether a round with this name suits someone at this rating. */
    static boolean fitsLevel(String name, int r) {
        String n = name.toLowerCase(Locale.US);
        boolean combined = n.contains("div. 1 + div. 2") || n.contains("global round");
        boolean d4 = n.contains("div. 4");
        boolean d3 = n.contains("div. 3");
        boolean edu = n.contains("educational");
        boolean d2 = n.contains("div. 2") && !combined;
        boolean d1 = n.contains("div. 1") && !combined && !n.contains("div. 2");
        if (r < 1200) return d4 || d3;
        if (r < 1400) return d4 || d3 || d2;
        if (r < 1600) return d3 || d2 || edu;
        if (r < 1900) return d2 || edu;
        if (r < 2100) return d2 || edu || combined;
        return d1 || combined;
    }

    /** Recent finished rounds for your division that you have not touched. */
    public static List<Contest> virtualIdeas(Db db, int limit) {
        List<Contest> out = new ArrayList<Contest>();
        int level = level(db);
        List<Contest> all = db.untouchedPastContests(400);
        for (int i = 0; i < all.size() && out.size() < limit; i++) {
            if (db.lc || fitsLevel(all.get(i).name, level)) out.add(all.get(i));
        }
        return out;
    }

    public static int rerollUnsolved(Context ctx) {
        Db db = Db.get(ctx);
        if (db.problemCount() == 0) return 0;
        List<DailyItem> items = db.daily(today());
        int n = 0;
        for (int i = 0; i < items.size(); i++) {
            DailyItem it = items.get(i);
            if (it.solved || it.problem.solved) continue;
            if (swap(ctx, it)) n++;
        }
        return n;
    }

    private static final int[][] LC_TARGETS = {{1, 1, 2}, {1, 2, 2}, {2, 2, 3}, {2, 3, 3}};

    /** What a slot aims at: a rating on Codeforces, a difficulty (1 Easy, 2 Medium, 3 Hard) on LeetCode. */
    public static int slotTarget(Db db, int slot) {
        if (db.lc) {
            int d = Math.max(-1, Math.min(1, db.kvInt("difficulty", 0)));
            // Experienced solvers start one step up, so the warm-up is not a throwaway.
            int tier = db.kvInt("rating", 0) >= 1900 || db.kvInt("solved_total", 0) >= 400 ? 1 : 0;
            return LC_TARGETS[d + 1 + tier][slot];
        }
        return target(level(db), slot);
    }

    public static int target(int est, int slot) {
        int base = Math.round(est / 100f) * 100;
        int t = base + OFFSETS[slot];
        return Math.max(800, Math.min(3500, t));
    }

    public static List<DailyItem> ensureToday(Context ctx) {
        Db db = Db.get(ctx);
        String day = today();
        List<DailyItem> items = db.daily(day);
        if (!items.isEmpty()) return items;
        if (Sync.isRunning() || db.problemCount() == 0) return items;
        int est = level(db);
        Map<String, TagStat> stats = db.tagStats();
        Random rnd = new Random(day.hashCode() * 31L + db.kv("handle", "").hashCode());
        Set<String> used = new HashSet<String>();
        int maxContest = db.maxContestId();
        for (int slot = 0; slot < 3; slot++) {
            Problem p = pick(db, slotTarget(db, slot), slot, stats, rnd, used, maxContest);
            if (p != null) {
                used.add(p.pkey);
                db.putDaily(day, slot, p.pkey);
            }
        }
        return db.daily(day);
    }

    public static boolean swap(Context ctx, DailyItem item) {
        Db db = Db.get(ctx);
        int est = level(db);
        Set<String> used = new HashSet<String>();
        List<DailyItem> todays = db.daily(item.day);
        for (int i = 0; i < todays.size(); i++) used.add(todays.get(i).problem.pkey);
        Problem p = pick(db, slotTarget(db, item.slot), item.slot, db.tagStats(), new Random(), used, db.maxContestId());
        if (p == null) return false;
        db.putDaily(item.day, item.slot, p.pkey);
        return true;
    }

    private static Problem pick(Db db, int target, int slot, Map<String, TagStat> stats, Random rnd, Set<String> exclude, int maxContest) {
        for (int w = 0; w < WIDEN.length; w++) {
            List<Problem> raw = db.candidates(target - WIDEN[w], target + WIDEN[w]);
            List<Problem> cands = new ArrayList<Problem>();
            for (int i = 0; i < raw.size(); i++) {
                if (!exclude.contains(raw.get(i).pkey)) cands.add(raw.get(i));
            }
            if (cands.isEmpty()) continue;
            int maxSolved = 1;
            for (int i = 0; i < cands.size(); i++) maxSolved = Math.max(maxSolved, cands.get(i).solvedCount);
            Problem best = null;
            double bestScore = -1;
            double weakWeight = slot == 2 ? 0.8 : 2.0;
            for (int i = 0; i < cands.size(); i++) {
                Problem p = cands.get(i);
                double score = weakWeight * weakness(p, stats)
                        + 0.6 * p.contestId / (double) maxContest
                        + 0.6 * Math.log(p.solvedCount + 1.0) / Math.log(maxSolved + 1.0)
                        - Math.abs(p.rating - target) / 400.0
                        + rnd.nextDouble();
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
            return best;
        }
        return null;
    }

    private static double weakness(Problem p, Map<String, TagStat> stats) {
        if (stats.isEmpty() || p.tags.length == 0) return 0.3;
        double sum = 0;
        for (int i = 0; i < p.tags.length; i++) {
            TagStat t = stats.get(p.tags[i]);
            sum += t == null ? 1.0 : t.weakness();
        }
        return sum / p.tags.length;
    }
}
