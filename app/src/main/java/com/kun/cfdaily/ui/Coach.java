package com.kun.cfdaily.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Ai;
import com.kun.cfdaily.data.Db;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Models.Problem;
import com.kun.cfdaily.data.Models.TagStat;
import com.kun.cfdaily.data.Recommender;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Weekly plan on the Stats tab: the AI picks three topics from your record, the app picks real unsolved problems for them. */
public class Coach {

    private static final String KEY = "ai_coach";
    private static final String LC_SYS = "You are a coding interview and LeetCode coach. From a user's stats pick the 3 topics that would help them most next, "
            + "favouring core patterns they are thin on. Use only tag names exactly as given in the list. Suggest a difficulty per topic: Easy, Medium or Hard. "
            + "Be specific and encouraging, short sentences, plain text. Reply as JSON: "
            + "{\"summary\":\"one sentence on where they stand\",\"focus\":[{\"tag\":\"exact tag\",\"why\":\"one short sentence\",\"level\":\"Medium\"}],"
            + "\"tip\":\"one concrete habit for this week\"}";
    private static final int[] FILLS = {R.color.p_rose, R.color.p_sky, R.color.p_mint};
    private static final String SYS = "You are a competitive programming coach. From a contestant's stats pick the 3 topics that would raise their rating fastest. "
            + "Use only tag names exactly as given in the list. Suggest a practice rating per topic near their level (a multiple of 100). "
            + "Be specific and encouraging, short sentences, plain text. Reply as JSON: "
            + "{\"summary\":\"one sentence on where they stand\",\"focus\":[{\"tag\":\"exact tag\",\"why\":\"one short sentence\",\"rating\":1400}],"
            + "\"tip\":\"one concrete habit for this week\"}";

    private final MainActivity a;
    private final Db db;
    private final View card;
    private final LinearLayout body;
    private final TextView button;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;

    Coach(MainActivity a, View root) {
        this.a = a;
        this.db = Db.get(a);
        this.card = root.findViewById(R.id.coach_card);
        this.body = root.findViewById(R.id.coach_body);
        this.button = root.findViewById(R.id.btn_coach);
        button.setOnClickListener(v -> run());
    }

    void render() {
        if (busy) return;
        boolean show = db.kv("handle", "").length() > 0;
        card.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        body.removeAllViews();
        String cached = db.kv(KEY, "");
        if (cached.length() == 0) {
            button.setText("Plan my week");
            line(Ai.ready(a) ? "Three topics to work on this week, with problems." : "Add a free Groq key in Profile to get a weekly plan.", R.color.subtext);
            return;
        }
        try {
            draw(new JSONObject(cached));
            button.setText("Refresh");
        } catch (Exception e) {
            db.setKv(KEY, "");
        }
    }

    private TextView line(String s, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(a.getColor(color));
        t.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 10);
        body.addView(t, lp);
        return t;
    }

    private void run() {
        if (!Ai.ready(a)) {
            a.select(MainActivity.TAB_PROFILE);
            a.snack("Add your Groq key here first");
            return;
        }
        if (busy) return;
        busy = true;
        body.removeAllViews();
        TextView wait = line("Looking at your record…", R.color.blue);
        wait.setAlpha(0.5f);
        wait.animate().alpha(1).setDuration(600);
        final String stats = statsText();
        new Thread(() -> {
            String error = null;
            JSONObject plan = null;
            try {
                plan = Ai.json(Ai.ask(a, Mode.lc() ? LC_SYS : SYS, stats, true));
                if (plan.optJSONArray("focus") == null) throw new Ai.AiException("Groq's answer was cut off. Try again.");
            } catch (Ai.AiException e) {
                error = e.getMessage();
            }
            final JSONObject p = plan;
            final String err = error;
            main.post(() -> {
                busy = false;
                if (err != null) {
                    body.removeAllViews();
                    line("⚠  " + err, R.color.coral);
                    return;
                }
                db.setKv(KEY, p.toString());
                render();
            });
        }).start();
    }

    /** What the model sees: level, recent activity and per-tag record. */
    private String statsText() {
        if (Mode.lc()) return lcStatsText();
        StringBuilder sb = new StringBuilder();
        int rating = db.kvInt("rating", 0);
        sb.append("Codeforces rating: ").append(rating > 0 ? String.valueOf(rating) : "unrated").append('\n');
        sb.append("Practice level (typical solved rating): ").append(Recommender.level(db)).append('\n');
        sb.append("Problems solved: ").append(db.solvedCount()).append(", last 7 days: ").append(db.weekSolved()).append('\n');
        sb.append("Current daily streak: ").append(db.streaks()[0]).append("\n\nTags (solved / tried but unsolved / average rating of solved):\n");
        List<TagStat> tags = new ArrayList<>(db.tagStats().values());
        Collections.sort(tags, (x, y) -> (y.solved + y.failed) - (x.solved + x.failed));
        Set<String> listed = new HashSet<>();
        for (int i = 0; i < Math.min(18, tags.size()); i++) {
            TagStat t = tags.get(i);
            if (t.tag.startsWith("*")) continue;
            listed.add(t.tag);
            sb.append(t.tag).append(": ").append(t.solved).append(" / ").append(t.failed).append(" / ").append(t.avgRating()).append('\n');
        }
        sb.append("\nOther tags they have barely touched: ");
        int n = 0;
        for (String t : db.allTags()) {
            if (t.startsWith("*") || listed.contains(t)) continue;
            sb.append(n++ > 0 ? ", " : "").append(t);
        }
        return sb.toString();
    }

    private String lcStatsText() {
        StringBuilder sb = new StringBuilder();
        int rating = db.kvInt("rating", 0);
        sb.append("LeetCode contest rating: ").append(rating > 0 ? String.valueOf(rating) : "none").append('\n');
        sb.append("Solved Easy ").append(db.kv("solved_1", "0")).append(" of ").append(db.kv("all_1", "?"))
                .append(", Medium ").append(db.kv("solved_2", "0")).append(" of ").append(db.kv("all_2", "?"))
                .append(", Hard ").append(db.kv("solved_3", "0")).append(" of ").append(db.kv("all_3", "?")).append('\n');
        sb.append("Current daily streak: ").append(db.streaks()[0]).append("\n\nSolved per tag:\n");
        List<TagStat> tags = new ArrayList<>(db.tagStats().values());
        Collections.sort(tags, (x, y) -> y.solved - x.solved);
        Set<String> listed = new HashSet<>();
        for (TagStat t : tags) {
            listed.add(t.tag);
            sb.append(t.tag).append(": ").append(t.solved).append('\n');
        }
        sb.append("\nTags with nothing solved yet: ");
        int n = 0;
        for (String t : db.allTags()) {
            if (listed.contains(t)) continue;
            sb.append(n++ > 0 ? ", " : "").append(t);
        }
        return sb.toString();
    }

    private void draw(JSONObject plan) {
        String summary = ProblemSheet.tidy(plan.optString("summary"));
        if (summary.length() > 0) line(summary, R.color.text);
        JSONArray focus = plan.optJSONArray("focus");
        Set<String> known = new HashSet<>(db.allTags());
        int level = Recommender.level(db);
        int shown = 0;
        for (int i = 0; focus != null && i < focus.length() && shown < 3; i++) {
            JSONObject f = focus.optJSONObject(i);
            if (f == null) continue;
            String tag = match(known, f.optString("tag"));
            if (tag == null) continue;
            int r;
            if (Mode.lc()) {
                String l = f.optString("level", "Medium").toLowerCase(Locale.US);
                r = l.startsWith("e") ? 1 : l.startsWith("h") ? 3 : 2;
            } else {
                r = f.optInt("rating", level);
                if (level > 0) r = Math.max(level - 200, Math.min(level + 400, r));
                r = Math.max(800, Math.round(r / 100f) * 100);
            }
            topic(shown++, tag, ProblemSheet.tidy(f.optString("why")), r);
        }
        String tip = ProblemSheet.tidy(plan.optString("tip"));
        if (tip.length() > 0) {
            TextView t = line("★  " + tip, R.color.text);
            int pad = Ui.dp(a, 14);
            t.setPadding(pad, pad, pad, pad);
            Ui.tint(t, a.getColor(R.color.p_butter), 18, 0xFF);
            t.setTextColor(a.getColor(R.color.text));
        }
    }

    /** The tag as the database spells it (LeetCode tags are capitalised, Codeforces ones are not). */
    private static String match(Set<String> known, String tag) {
        String t = tag.trim();
        for (String k : known) if (k.equalsIgnoreCase(t)) return k;
        return null;
    }

    private void topic(int i, String tag, String why, int rating) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 16);
        c.setPadding(pad, pad, pad, pad);
        Ui.tint(c, a.getColor(FILLS[i % FILLS.length]), 20, 0xFF);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView name = new TextView(a);
        name.setText(Ui.tag(tag));
        name.setTextSize(18);
        name.setTextColor(a.getColor(R.color.text));
        name.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
        head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = new TextView(a);
        badge.setTextSize(13);
        badge.setTypeface(a.getResources().getFont(R.font.hg_semibold));
        badge.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
        badge.setText(Ui.levelText(rating));
        badge.setTextColor(a.getColor(R.color.text));
        Ui.tint(badge, a.getColor(R.color.soft), 10, Ui.dark ? 0x24 : 0xB3);
        head.addView(badge);
        c.addView(head);
        if (why.length() > 0) {
            TextView w = new TextView(a);
            w.setText(why);
            w.setTextSize(14);
            w.setTextColor(a.getColor(R.color.text));
            w.setAlpha(0.8f);
            c.addView(w);
        }
        List<Problem> picks = db.ladderPicks(tag, rating, 2);
        if (picks.size() < 2) picks.addAll(db.ladderPicks(tag, rating + (Mode.lc() ? 1 : 100), 2 - picks.size()));
        for (final Problem p : picks) {
            TextView row = new TextView(a);
            row.setText("▸  " + p.name + "   " + p.id());
            row.setTextSize(14);
            row.setTextColor(a.getColor(R.color.text));
            row.setTypeface(a.getResources().getFont(R.font.hg_semibold));
            row.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            Ui.tint(row, a.getColor(R.color.soft), 12, Ui.dark ? 0x24 : 0xB3);
            row.setOnClickListener(v -> ProblemSheet.show(a, p, 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(a, 8);
            c.addView(row, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 12);
        body.addView(c, lp);
    }
}
