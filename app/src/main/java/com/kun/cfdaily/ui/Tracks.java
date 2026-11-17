package com.kun.cfdaily.ui;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Db;
import com.kun.cfdaily.data.Models.Problem;

import java.util.List;

/** LeetCode pattern tracks: one tile per pattern with progress, opening the next problems from easy to hard. */
public class Tracks {

    /** Display name, then LeetCode's tag names for it (it has renamed some, e.g. Graph to Graph Theory). */
    private static final String[][] TRACKS = {
            {"Hashing", "Hash Table"}, {"Two Pointers", "Two Pointers"}, {"Sliding Window", "Sliding Window"},
            {"Stack", "Stack", "Monotonic Stack"}, {"Binary Search", "Binary Search"}, {"Linked List", "Linked List"},
            {"Trees", "Tree", "Binary Tree"}, {"Heap", "Heap (Priority Queue)"}, {"Backtracking", "Backtracking"},
            {"Graphs", "Graph Theory", "Graph"}, {"Greedy", "Greedy"}, {"DP", "Dynamic Programming"},
            {"Prefix Sum", "Prefix Sum"}, {"Bits", "Bit Manipulation"}, {"Trie", "Trie"}, {"Union Find", "Union-Find", "Union Find"}};
    /** Goal tiers: the bar fills toward the next one. */
    private static final int[] GOALS = {15, 40, 100, 200, 400};

    private static int solved(Db db, String[] track) {
        int best = 0;
        for (int k = 1; k < track.length; k++) best = Math.max(best, db.tagSolved(track[k]));
        return best;
    }

    private static int goal(int solved) {
        for (int g : GOALS) if (solved < g) return g;
        return GOALS[GOALS.length - 1];
    }
    private static final int[] FILLS = {R.color.p_mint, R.color.p_sky, R.color.p_lilac, R.color.p_peach, R.color.p_butter, R.color.p_rose};
    private static final int[] INKS = {R.color.mint, R.color.blue, R.color.violet, R.color.coral, R.color.amber, R.color.pink};

    /** Fills the grid; two tiles per row. */
    static void render(final MainActivity a, LinearLayout grid) {
        grid.removeAllViews();
        Db db = Db.get(a);
        LinearLayout row = null;
        for (int i = 0; i < TRACKS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(a);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = Ui.dp(a, i == 0 ? 0 : 10);
                grid.addView(row, lp);
            }
            final String name = TRACKS[i][0];
            final String[] track = TRACKS[i];
            int solved = solved(db, track);
            int goal = goal(solved);
            int tier = 0;
            while (tier < GOALS.length && solved >= GOALS[tier]) tier++;
            int fill = a.getColor(FILLS[i % FILLS.length]), ink = a.getColor(INKS[i % INKS.length]);

            LinearLayout tile = new LinearLayout(a);
            tile.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(a, 14);
            tile.setPadding(pad, pad, pad, pad);
            Ui.tint(tile, fill, 20, 0xFF);
            TextView title = new TextView(a);
            title.setText(name);
            title.setTextSize(15);
            title.setTextColor(a.getColor(R.color.text));
            title.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
            title.setMaxLines(1);
            tile.addView(title);
            TextView count = new TextView(a);
            boolean maxed = solved >= GOALS[GOALS.length - 1];
            String stars = tier == 0 ? "" : "★".repeat(tier) + "  ";
            count.setText(maxed ? stars + solved + " solved" : stars + solved + " / " + goal);
            count.setTextSize(13);
            count.setTextColor(a.getColor(R.color.text));
            count.setAlpha(0.7f);
            tile.addView(count);
            // Progress bar: a track and a fill in the pattern's colour.
            FrameLayout bar = new FrameLayout(a);
            Ui.tint(bar, ink, 4, 0x33);
            View part = new View(a);
            Ui.tint(part, ink, 4, 0xFF);
            bar.addView(part, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            final float frac = Math.min(1f, solved / (float) goal);
            bar.post(() -> {
                part.getLayoutParams().width = Math.round(bar.getWidth() * frac);
                part.requestLayout();
            });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 6));
            bp.topMargin = Ui.dp(a, 10);
            tile.addView(bar, bp);
            tile.setOnClickListener(v -> open(a, name, track));

            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i % 2 == 0) tp.rightMargin = Ui.dp(a, 10);
            row.addView(tile, tp);
        }
    }

    /** Sheet with the next problems in a pattern. */
    private static void open(final MainActivity a, String name, String[] track) {
        Db db = Db.get(a);
        List<Problem> next = new java.util.ArrayList<>();
        for (int k = 1; k < track.length && next.size() < 10; k++) {
            for (Problem p : db.trackProblems(track[k], 10)) {
                boolean dup = false;
                for (Problem q : next) if (q.pkey.equals(p.pkey)) dup = true;
                if (!dup && next.size() < 10) next.add(p);
            }
        }
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        if (next.isEmpty()) {
            TextView t = new TextView(a);
            t.setText("Nothing unsolved here. Sync to refresh the list.");
            t.setTextColor(a.getColor(R.color.subtext));
            list.addView(t);
        }
        for (final Problem p : next) {
            View v = a.getLayoutInflater().inflate(R.layout.item_step, list, false);
            Ui.badge(v.findViewById(R.id.tv_rating), Ui.levelText(p.rating), Ui.levelColor(p.rating));
            ((TextView) v.findViewById(R.id.tv_left)).setText(p.name);
            v.setOnClickListener(x -> Ui.open(a, p.url()));
            v.setOnLongClickListener(x -> {
                a.hideSheet();
                v.postDelayed(() -> ProblemSheet.show(a, p, ProblemSheet.HINTS_TAB), 220);
                return true;
            });
            list.addView(v);
        }
        TextView tip = new TextView(a);
        tip.setText("Tap to solve  ·  hold for hints");
        tip.setTextSize(12);
        tip.setTextColor(a.getColor(R.color.faint));
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 10);
        list.addView(tip, lp);
        a.showSheet(name, list, null, null);
    }
}
