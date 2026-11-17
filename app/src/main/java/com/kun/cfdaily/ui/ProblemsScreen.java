package com.kun.cfdaily.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Models.Problem;

import java.util.ArrayList;
import java.util.List;

public class ProblemsScreen extends Screen {

    private final EditText etMin;
    private final EditText etMax;
    private final EditText etSearch;
    private final TextView btnTag;
    private final TextView tgUnsolved;
    private final TextView tgAttempted;
    private final TextView tgSaved;
    private final TextView tvEmpty;
    private final ProblemAdapter adapter = new ProblemAdapter();
    private List<Problem> problems = new ArrayList<Problem>();
    private String tag;
    private boolean unsolved = true;
    private boolean attempted;
    private boolean saved;
    private int diff;
    private final TextView[] diffChips;

    private class ProblemAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return problems.size();
        }

        @Override
        public Object getItem(int position) {
            return problems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (v == null) v = a.getLayoutInflater().inflate(R.layout.item_problem, parent, false);
            final Problem p = problems.get(position);
            Ui.badge(v.findViewById(R.id.tv_rating), Ui.levelText(p.rating), Ui.levelColor(p.rating));
            TextView name = v.findViewById(R.id.tv_name);
            name.setText(p.name + (p.solved ? "  ✓" : ""));
            name.setTextColor(a.getColor(p.solved ? R.color.good : R.color.text));
            ((TextView) v.findViewById(R.id.tv_meta)).setText(p.id() + (p.tags.length > 0 ? "  •  " + tags(p) : ""));
            TextView note = v.findViewById(R.id.tv_note);
            note.setVisibility(p.note.length() > 0 ? View.VISIBLE : View.GONE);
            note.setText(p.note);
            ImageView star = v.findViewById(R.id.btn_star);
            star.setImageResource(p.bookmarked ? R.drawable.ic_star : R.drawable.ic_star_off);
            star.setColorFilter(a.getColor(p.bookmarked ? R.color.amber : R.color.faint));
            star.setOnClickListener(x -> {
                p.bookmarked = db.toggleBookmark(p.pkey);
                notifyDataSetChanged();
            });
            ProblemSheet.bindButton((TextView) v.findViewById(R.id.btn_code), a, p);
            v.setOnClickListener(x -> Ui.open(a, p.url()));
            v.setOnLongClickListener(x -> {
                editNote(p);
                return true;
            });
            return v;
        }
    }

    public ProblemsScreen(MainActivity a) {
        super(a, R.layout.screen_problems);
        etMin = root.findViewById(R.id.et_min);
        etMax = root.findViewById(R.id.et_max);
        etSearch = root.findViewById(R.id.et_search);
        btnTag = root.findViewById(R.id.btn_tag);
        tgUnsolved = root.findViewById(R.id.tg_unsolved);
        tgAttempted = root.findViewById(R.id.tg_attempted);
        tgSaved = root.findViewById(R.id.tg_saved);
        tvEmpty = root.findViewById(R.id.tv_empty);
        ListView lv = root.findViewById(R.id.list);
        lv.setAdapter(adapter);

        TextWatcher live = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                load();
            }
        };
        etSearch.addTextChangedListener(live);
        etMin.addTextChangedListener(live);
        etMax.addTextChangedListener(live);
        btnTag.setOnClickListener(v -> pickTag());
        diffChips = new TextView[]{root.findViewById(R.id.tg_easy), root.findViewById(R.id.tg_med), root.findViewById(R.id.tg_hard)};
        for (int i = 0; i < 3; i++) {
            final int level = i + 1;
            diffChips[i].setOnClickListener(v -> {
                diff = diff == level ? 0 : level;
                load();
            });
        }
        // LeetCode has difficulties instead of ratings, and no public record of failed attempts.
        if (Mode.lc()) {
            etMin.setVisibility(View.GONE);
            etMax.setVisibility(View.GONE);
            tgAttempted.setVisibility(View.GONE);
            root.findViewById(R.id.row_diff).setVisibility(View.VISIBLE);
        }
        tgUnsolved.setOnClickListener(v -> {
            unsolved = !unsolved;
            load();
        });
        tgAttempted.setOnClickListener(v -> {
            attempted = !attempted;
            load();
        });
        tgSaved.setOnClickListener(v -> {
            saved = !saved;
            load();
        });
    }

    @Override
    public String title() {
        return "Problems";
    }

    private static String tags(Problem p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.tags.length; i++) sb.append(i > 0 ? ", " : "").append(Ui.tag(p.tags[i]));
        return sb.toString();
    }

    private void editNote(final Problem p) {
        final EditText input = (EditText) a.getLayoutInflater().inflate(R.layout.sheet_note, null);
        input.setText(p.note);
        input.setSelection(p.note.length());
        a.showSheet(p.id() + "  " + p.name, input, "Save note", () -> {
            p.note = input.getText().toString().trim();
            db.setNote(p.pkey, p.note);
            // Your notes double as trick flashcards.
            if (p.note.length() > 0) db.putCard(p.pkey, p.note, "note");
            else db.removeCard(p.pkey);
            adapter.notifyDataSetChanged();
        });
    }

    private void pickTag() {
        FlowLayout flow = new FlowLayout(a);
        addTag(flow, null);
        List<String> tags = db.allTags();
        for (int i = 0; i < tags.size(); i++) addTag(flow, tags.get(i));
        a.showSheet("Filter by tag", flow, null, null);
    }

    private void addTag(FlowLayout flow, final String value) {
        TextView chip = (TextView) a.getLayoutInflater().inflate(R.layout.chip, flow, false);
        chip.setText(value == null ? "Any tag" : Ui.tag(value));
        toggle(chip, value == null ? tag == null : value.equals(tag));
        chip.setOnClickListener(v -> {
            tag = value;
            a.hideSheet();
            load();
        });
        flow.addView(chip);
    }

    private void toggle(TextView chip, boolean on) {
        chip.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_ghost);
        chip.setTextColor(a.getColor(on ? R.color.on_primary : R.color.text));
    }

    private static int parse(EditText et, int def) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        btnTag.setText(tag == null ? "Any tag" : Ui.tag(tag));
        toggle(tgUnsolved, unsolved && !saved);
        toggle(tgAttempted, attempted);
        toggle(tgSaved, saved);
        String search = etSearch.getText().toString().trim();
        for (int i = 0; i < 3; i++) toggle(diffChips[i], diff == i + 1);
        int min = Mode.lc() ? (diff == 0 ? 0 : diff) : parse(etMin, 0);
        int max = Mode.lc() ? (diff == 0 ? 5000 : diff) : parse(etMax, 5000);
        problems = db.queryProblems(min, max, tag, search, unsolved && !saved, attempted && !Mode.lc(), saved, 300);
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(problems.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText(saved ? "Nothing saved matches." : attempted ? "Nothing to upsolve. Nice." : "No matches.");
    }
}
