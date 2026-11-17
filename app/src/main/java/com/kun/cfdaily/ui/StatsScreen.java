package com.kun.cfdaily.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Models.DailyItem;
import com.kun.cfdaily.data.Models.RatingPoint;
import com.kun.cfdaily.data.Models.TagStat;
import com.kun.cfdaily.data.Recommender;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsScreen extends Screen {

    private final TextView tvGraph;
    private final RatingGraphView graph;
    private final FlowLayout weakTags;
    private final LinearLayout tagList;
    private final LinearLayout historyList;
    private final HeatmapView heatmap;
    private final RatingBarsView bars;
    private final TextView tvHeatmap;
    private final TextView tvBars;
    private Map<String, Integer> solveDays;
    private final Coach coach;
    private static final int[] TAG_COLORS = {R.color.blue, R.color.violet, R.color.pink, R.color.mint, R.color.amber, R.color.coral};

    public StatsScreen(MainActivity a) {
        super(a, R.layout.screen_stats);
        tvGraph = root.findViewById(R.id.tv_graph);
        graph = root.findViewById(R.id.graph);
        weakTags = root.findViewById(R.id.weak_tags);
        tagList = root.findViewById(R.id.tag_list);
        historyList = root.findViewById(R.id.history_list);
        heatmap = root.findViewById(R.id.heatmap);
        bars = root.findViewById(R.id.bars);
        tvHeatmap = root.findViewById(R.id.tv_heatmap);
        tvBars = root.findViewById(R.id.tv_bars);
        coach = new Coach(a, root);
        heatmap.setOnDayTap((day, count) -> tvHeatmap.setText(day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) + "  •  " + count + (Mode.lc() ? " submissions" : " solved")));
        bars.setOnBarTap((rating, count) -> tvBars.setText(rating + "  •  " + count + " solved"));
        // Posted: changing text in the middle of a layout pass would not re-measure the caption.
        heatmap.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) heatmap.post(this::heatmapSummary);
        });
    }

    @Override
    public String title() {
        return "Stats";
    }

    private void tile(int tileId, int valueId, String value, int color) {
        Ui.tint(root.findViewById(tileId), color, 20, 0xFF);
        ((TextView) root.findViewById(valueId)).setText(value);
    }

    private void heatmapSummary() {
        if (solveDays == null || heatmap.getWidth() == 0) return;
        LocalDate d = heatmap.firstDay();
        LocalDate today = LocalDate.now();
        int total = 0;
        int active = 0;
        while (!d.isAfter(today)) {
            Integer n = solveDays.get(d.toString());
            if (n != null) {
                total += n;
                active++;
            }
            d = d.plusDays(1);
        }
        tvHeatmap.setText(total + (Mode.lc() ? " submissions  •  " : " solved  •  ") + active + " active days  •  " + heatmap.weeks() + " weeks");
    }

    @Override
    public void refresh() {
        coach.render();
        int[] s = db.streaks();
        int[] t = db.dailyTotals();
        tile(R.id.tile_streak, R.id.st_streak, String.valueOf(s[0]), a.getColor(R.color.p_butter));
        tile(R.id.tile_best, R.id.st_best, String.valueOf(s[1]), a.getColor(R.color.p_rose));
        tile(R.id.tile_solved, R.id.st_solved, Ui.compact(db.solvedCount()), a.getColor(R.color.p_mint));
        tile(R.id.tile_daily, R.id.st_daily, t[1] + "/" + t[0], a.getColor(R.color.p_sky));

        solveDays = db.solveDays();
        heatmap.setData(solveDays);
        heatmapSummary();

        List<int[]> byRating = Mode.lc() ? new ArrayList<int[]>() : db.solvedByRating();
        bars.setData(byRating);
        bars.setVisibility(byRating.isEmpty() ? View.GONE : View.VISIBLE);
        if (Mode.lc()) {
            ((TextView) root.findViewById(R.id.tv_bars_title)).setText("Solved by difficulty");
            LinearLayout diffList = root.findViewById(R.id.diff_list);
            diffList.setVisibility(View.VISIBLE);
            diffList.removeAllViews();
            String[] names = {"Easy", "Medium", "Hard"};
            for (int d = 1; d <= 3; d++) {
                int solved = db.kvInt("solved_" + d, 0);
                int all = Math.max(1, db.kvInt("all_" + d, 0));
                View v = a.getLayoutInflater().inflate(R.layout.item_meter, diffList, false);
                ((TextView) v.findViewById(R.id.tv_left)).setText(names[d - 1]);
                ((MeterView) v.findViewById(R.id.meter)).set(solved / (float) all, Ui.levelColor(d));
                ((TextView) v.findViewById(R.id.tv_right)).setText(solved + "/" + all);
                diffList.addView(v);
            }
            tvBars.setText(db.solvedCount() + " solved in total");
        } else if (byRating.isEmpty()) {
            tvBars.setText("Nothing rated solved yet");
        } else {
            int n = 0;
            long sum = 0;
            for (int i = 0; i < byRating.size(); i++) {
                n += byRating.get(i)[1];
                sum += (long) byRating.get(i)[0] * byRating.get(i)[1];
            }
            tvBars.setText(n + " rated  •  avg " + (sum / n) + "  •  max " + byRating.get(byRating.size() - 1)[0]);
        }

        List<RatingPoint> pts = db.ratingHistory();
        graph.setData(pts);
        graph.setVisibility(pts.isEmpty() ? View.GONE : View.VISIBLE);
        if (pts.isEmpty()) {
            tvGraph.setText("No rated contests yet");
        } else {
            int max = 0;
            for (int i = 0; i < pts.size(); i++) max = Math.max(max, pts.get(i).newRating);
            RatingPoint last = pts.get(pts.size() - 1);
            int delta = last.newRating - last.oldRating;
            tvGraph.setText(pts.size() + " contests  •  peak " + max + "  •  last " + (delta >= 0 ? "+" : "") + delta);
        }

        // Tags: weakest as chips, then the most practised as bars coloured by their average rating.
        List<TagStat> stats = new ArrayList<TagStat>(db.tagStats().values());
        weakTags.removeAllViews();
        tagList.removeAllViews();
        List<TagStat> weak = new ArrayList<TagStat>();
        for (int i = 0; i < stats.size(); i++) {
            if (stats.get(i).solved + stats.get(i).failed >= 3) weak.add(stats.get(i));
        }
        Collections.sort(weak, (x, y) -> Double.compare(y.weakness(), x.weakness()));
        for (int i = 0; i < Math.min(4, weak.size()); i++) {
            TextView chip = (TextView) a.getLayoutInflater().inflate(R.layout.chip_tag, weakTags, false);
            Ui.badge(chip, "Weak: " + Ui.tag(weak.get(i).tag), a.getColor(R.color.coral));
            weakTags.addView(chip);
        }
        weakTags.setVisibility(weak.isEmpty() ? View.GONE : View.VISIBLE);
        Collections.sort(stats, (x, y) -> y.solved - x.solved);
        int top = stats.isEmpty() ? 1 : Math.max(1, stats.get(0).solved);
        for (int i = 0; i < Math.min(10, stats.size()); i++) {
            TagStat ts = stats.get(i);
            View v = a.getLayoutInflater().inflate(R.layout.item_meter, tagList, false);
            ((TextView) v.findViewById(R.id.tv_left)).setText(Ui.tag(ts.tag));
            ((MeterView) v.findViewById(R.id.meter)).set(ts.solved / (float) top, a.getColor(TAG_COLORS[i % TAG_COLORS.length]));
            ((TextView) v.findViewById(R.id.tv_right)).setText(String.valueOf(ts.solved));
            tagList.addView(v);
        }
        if (stats.isEmpty()) row(tagList, "Sync with a handle to see tags", "", 0);

        historyList.removeAllViews();
        List<DailyItem> hist = db.history(Recommender.today(), 30);
        if (hist.isEmpty()) row(historyList, "Past picks show up here", "", 0);
        for (int i = 0; i < hist.size(); i++) {
            final DailyItem it = hist.get(i);
            boolean solved = it.solved || it.problem.solved;
            View v = row(historyList, it.day.substring(5) + "   " + it.problem.name, solved ? "✓" : Ui.levelText(it.problem.rating),
                    solved ? a.getColor(R.color.good) : Ui.levelColor(it.problem.rating));
            v.setOnClickListener(x -> Ui.open(a, it.problem.url()));
        }
    }

    private View row(LinearLayout parent, String left, String right, int rightColor) {
        View v = a.getLayoutInflater().inflate(R.layout.item_row, parent, false);
        ((TextView) v.findViewById(R.id.tv_left)).setText(left);
        TextView r = v.findViewById(R.id.tv_right);
        r.setText(right);
        if (rightColor != 0) r.setTextColor(rightColor);
        parent.addView(v);
        return v;
    }
}
