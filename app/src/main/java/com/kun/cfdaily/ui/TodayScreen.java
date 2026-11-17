package com.kun.cfdaily.ui;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Models.DailyItem;
import com.kun.cfdaily.data.Models.Problem;
import com.kun.cfdaily.data.Recommender;
import com.kun.cfdaily.data.Sync;

import java.util.List;
import java.util.Locale;

public class TodayScreen extends Screen {

    private static final int[] SLOT_COLORS = {R.color.p_mint, R.color.p_sky, R.color.p_lilac};

    private static String cap(String s) {
        return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private final TextView tvHandle;
    private final TextView tvRank;
    private final TextView tvStreak;
    private final TextView tvToday;
    private final TextView tvLevel;
    private final RingView ring;
    private final TextView tvEmpty;
    private final LinearLayout list;
    private final TextView btnTopic;
    private final TextView tvTrain;
    private final LinearLayout trainList;

    public TodayScreen(MainActivity a) {
        super(a, R.layout.screen_today);
        tvHandle = root.findViewById(R.id.tv_handle);
        tvRank = root.findViewById(R.id.tv_rank);
        tvStreak = root.findViewById(R.id.tv_streak);
        tvToday = root.findViewById(R.id.tv_today);
        tvLevel = root.findViewById(R.id.tv_level);
        ring = root.findViewById(R.id.ring);
        tvEmpty = root.findViewById(R.id.tv_empty);
        list = root.findViewById(R.id.daily_list);
        btnTopic = root.findViewById(R.id.btn_topic);
        tvTrain = root.findViewById(R.id.tv_train);
        trainList = root.findViewById(R.id.train_list);
        btnTopic.setOnClickListener(v -> pickTopic());
    }

    @Override
    public String title() {
        return "Today";
    }

    /** Sheet of topic chips. */
    private void pickTopic() {
        FlowLayout flow = new FlowLayout(a);
        String current = db.kv("train_tag", "");
        addTopic(flow, "", current);
        List<String> tags = db.allTags();
        for (int i = 0; i < tags.size(); i++) {
            if (!tags.get(i).startsWith("*")) addTopic(flow, tags.get(i), current);
        }
        a.showSheet("Pick a topic", flow, null, null);
    }

    private void addTopic(FlowLayout flow, final String tag, String current) {
        TextView chip = (TextView) a.getLayoutInflater().inflate(R.layout.chip, flow, false);
        chip.setText(tag.length() == 0 ? "None" : Ui.tag(tag));
        if (tag.equals(current)) {
            chip.setBackgroundResource(R.drawable.bg_chip_on);
            chip.setTextColor(a.getColor(R.color.on_primary));
        }
        chip.setOnClickListener(v -> {
            db.setKv("train_tag", tag);
            a.hideSheet();
            showTraining();
        });
        flow.addView(chip);
    }

    private void showTraining() {
        trainList.removeAllViews();
        String tag = db.kv("train_tag", "");
        int from = Recommender.ladderStart(db);
        int to = from + 100 * (Recommender.LADDER_STEPS - 1);
        btnTopic.setText(tag.length() == 0 ? "Pick topic" : Ui.tag(tag));
        if (tag.length() == 0) {
            tvTrain.setVisibility(View.VISIBLE);
            tvTrain.setText(Mode.lc() ? "Easy to Hard, one topic at a time." : "One problem per step, " + from + " to " + to + ".");
            return;
        }
        List<Problem> ladder = Recommender.ladder(db, tag);
        tvTrain.setVisibility(ladder.isEmpty() ? View.VISIBLE : View.GONE);
        tvTrain.setText("Nothing unsolved here. Try another topic.");
        for (int i = 0; i < ladder.size(); i++) {
            final Problem p = ladder.get(i);
            View v = a.getLayoutInflater().inflate(R.layout.item_step, trainList, false);
            Ui.badge(v.findViewById(R.id.tv_rating), Ui.levelText(p.rating), Ui.levelColor(p.rating));
            ((TextView) v.findViewById(R.id.tv_left)).setText(p.name);
            v.setOnClickListener(x -> Ui.open(a, p.url()));
            trainList.addView(v);
        }
    }

    /** Banner for trick flashcards that are due today. */
    private void showCards() {
        View banner = root.findViewById(R.id.cards_banner);
        int due = db.cardsDue();
        banner.setVisibility(due > 0 ? View.VISIBLE : View.GONE);
        if (due == 0) return;
        Ui.tint(banner, a.getColor(R.color.p_lilac), 26, 0xFF);
        Ui.tint(root.findViewById(R.id.cards_icon), a.getColor(R.color.violet), 22, 0xFF);
        ((TextView) root.findViewById(R.id.cards_title)).setText(due == 1 ? "1 trick card" : due + " trick cards");
        View.OnClickListener go = v -> Flashcards.show(a, this::refresh);
        banner.setOnClickListener(go);
        root.findViewById(R.id.cards_go).setOnClickListener(go);
    }

    /** Problems solved a couple of weeks ago, to redo without looking. */
    private void showRevisits() {
        LinearLayout box = root.findViewById(R.id.revisit_box);
        LinearLayout rl = root.findViewById(R.id.revisit_list);
        rl.removeAllViews();
        List<Object[]> due = db.revisitsDue(2);
        box.setVisibility(due.isEmpty() ? View.GONE : View.VISIBLE);
        long now = System.currentTimeMillis() / 1000;
        for (Object[] row : due) {
            final Problem p = (Problem) row[0];
            long days = (now - (Long) row[1]) / 86400;
            View v = a.getLayoutInflater().inflate(R.layout.item_daily, rl, false);
            Ui.tint(v, a.getColor(R.color.p_peach), 26, 0xFF);
            ((TextView) v.findViewById(R.id.tv_slot)).setText("↻  Solved " + days + " days ago");
            v.findViewById(R.id.tv_status).setVisibility(View.GONE);
            ((TextView) v.findViewById(R.id.tv_rating)).setText(Ui.levelText(p.rating));
            ((TextView) v.findViewById(R.id.tv_name)).setText(p.name);
            ((TextView) v.findViewById(R.id.tv_meta)).setText(p.id() + "  ·  Code it again without your old solution");
            TextView open = v.findViewById(R.id.btn_open);
            open.setText("Solve");
            open.setOnClickListener(x -> Ui.open(a, p.url()));
            TextView done = v.findViewById(R.id.btn_bookmark);
            done.setText("✓ Done");
            done.setOnClickListener(x -> {
                db.markRevisited(p.pkey);
                a.snack("Nice. It comes back in a few weeks.");
                showRevisits();
            });
            TextView later = v.findViewById(R.id.btn_swap);
            later.setText("Later");
            later.setOnClickListener(x -> {
                db.snoozeRevisit(p.pkey);
                showRevisits();
            });
            v.findViewById(R.id.btn_code).setVisibility(View.GONE);
            rl.addView(v);
        }
    }

    @Override
    public void refresh() {
        String handle = db.kv("handle", "");
        int rating = db.kvInt("rating", 0);
        tvHandle.setText(handle.length() == 0 ? "Welcome" : handle);
        if (handle.length() == 0) tvRank.setText("Add your handle in Profile");
        else if (rating > 0) tvRank.setText((Mode.lc() ? "Contest rating" : cap(db.kv("rank", ""))) + " \u00b7 " + rating);
        else tvRank.setText(Mode.lc() ? "LeetCode" : "Unrated");
        // The hero is dark in both themes, so the rank colour comes from the bright palette.
        boolean wasDark = Ui.dark;
        Ui.dark = true;
        tvRank.setTextColor(rating > 0 ? Ui.ratingColor(rating) : a.getColor(R.color.hero_sub));
        Ui.dark = wasDark;

        List<DailyItem> items = Recommender.ensureToday(a);
        int done = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).solved || items.get(i).problem.solved) done++;
        }
        tvStreak.setText(String.valueOf(db.streaks()[0]));
        tvToday.setText(done + "/" + Math.max(3, items.size()));
        ((TextView) root.findViewById(R.id.tv_solved)).setText(Ui.compact(db.solvedCount()));
        ((TextView) root.findViewById(R.id.tv_section)).setText(items.isEmpty() ? "Today's set" : done == items.size() ? "All done today" : "Today's set");
        ring.set(items.isEmpty() ? 0 : done / (float) items.size());
        int level = Recommender.level(db);
        tvLevel.setText(level > 0 ? String.valueOf(level) : "–");

        showTraining();
        showRevisits();
        showCards();
        root.findViewById(R.id.tracks_card).setVisibility(Mode.lc() ? View.VISIBLE : View.GONE);
        if (Mode.lc()) Tracks.render(a, root.findViewById(R.id.tracks_grid));
        list.removeAllViews();
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(Sync.isRunning() ? "Downloading problems..." : "No problems yet. Tap sync while online.");
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        // LeetCode's own problem of the day rides along as a fourth card.
        Problem official = Mode.lc() && Recommender.today().equals(db.kv("lc_daily_date", "")) ? db.problem(db.kv("lc_daily", "")) : null;
        for (int i = 0; i < items.size() + (official == null ? 0 : 1); i++) {
            final boolean extra = i >= items.size();
            final DailyItem it;
            if (extra) {
                it = new DailyItem();
                it.problem = official;
                it.slot = 3;
            } else {
                it = items.get(i);
            }
            final Problem p = it.problem;
            boolean solved = it.solved || p.solved;
            // Each slot is a block of its own colour; a solved one settles back to plain.
            int color = a.getColor(solved ? R.color.card : extra ? R.color.p_butter : SLOT_COLORS[it.slot % SLOT_COLORS.length]);
            View v = a.getLayoutInflater().inflate(R.layout.item_daily, list, false);
            Ui.tint(v, color, 26, 0xFF);
            ((TextView) v.findViewById(R.id.tv_slot)).setText(extra ? "LeetCode daily" : Recommender.SLOT_NAMES[it.slot]);
            TextView status = v.findViewById(R.id.tv_status);
            status.setText(solved ? "\u2713 Solved" : "");
            status.setTextColor(a.getColor(R.color.good));
            ((TextView) v.findViewById(R.id.tv_name)).setText(p.name);
            ((TextView) v.findViewById(R.id.tv_rating)).setText(Ui.levelText(p.rating));
            if (solved) {
                // On the plain card the translucent pills would vanish, so they take the grey fill.
                v.findViewById(R.id.tv_rating).setBackgroundResource(R.drawable.bg_chip_ghost);
                v.findViewById(R.id.btn_bookmark).setBackgroundResource(R.drawable.bg_chip_ghost);
            }
            StringBuilder meta = new StringBuilder(p.id()).append(" \u00b7 ").append(Mode.lc() ? (p.solvedCount / 10) + "% accepted" : Ui.compact(p.solvedCount) + " solved");
            for (int t = 0; t < Math.min(3, p.tags.length); t++) meta.append(t == 0 ? "\n" : ", ").append(Ui.tag(p.tags[t]));
            if (p.tags.length > 3) meta.append(" +").append(p.tags.length - 3);
            ((TextView) v.findViewById(R.id.tv_meta)).setText(meta);
            v.findViewById(R.id.btn_open).setOnClickListener(x -> Ui.open(a, p.url()));
            TextView bm = v.findViewById(R.id.btn_bookmark);
            bm.setText(p.bookmarked ? "Saved" : "Save");
            bm.setOnClickListener(x -> {
                db.toggleBookmark(p.pkey);
                refresh();
            });
            TextView code = v.findViewById(R.id.btn_code);
            ProblemSheet.bindButton(code, a, p);
            if (solved) code.setBackgroundResource(R.drawable.bg_chip_ghost);
            View swap = v.findViewById(R.id.btn_swap);
            swap.setVisibility(solved || extra ? View.GONE : View.VISIBLE);
            swap.setOnClickListener(x -> {
                if (!Recommender.swap(a, it)) a.snack("Nothing else at this level");
                refresh();
            });
            list.addView(v);
        }
    }
}
