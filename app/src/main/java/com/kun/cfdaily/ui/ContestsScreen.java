package com.kun.cfdaily.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Models.Contest;
import com.kun.cfdaily.data.Recommender;
import com.kun.cfdaily.data.Reminders;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ContestsScreen extends Screen {

    private static final int TYPE_CONTEST = 0;
    private static final int TYPE_HEADER = 1;

    /** Either a contest (upcoming, or a past one suggested for virtual participation) or a section header. */
    private static class Row {
        Contest contest;
        boolean past;
        String header;
    }

    private final TextView tvEmpty;
    private final ContestAdapter adapter = new ContestAdapter();
    private List<Row> rows = new ArrayList<Row>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("EEE d MMM  •  h:mm a", Locale.getDefault());
    private final SimpleDateFormat pastFmt = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    private class ContestAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).contest == null ? TYPE_HEADER : TYPE_CONTEST;
        }

        @Override
        public boolean isEnabled(int position) {
            return rows.get(position).contest != null;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.contest == null) {
                if (v == null) v = a.getLayoutInflater().inflate(R.layout.item_header, parent, false);
                ((TextView) v).setText(row.header);
                return v;
            }
            if (v == null) v = a.getLayoutInflater().inflate(R.layout.item_contest, parent, false);
            final Contest c = row.contest;
            long start = c.startSeconds * 1000;
            String div = Ui.divLabel(c.name);
            Ui.badge(v.findViewById(R.id.tv_div), div, Ui.divColor(div));
            ((TextView) v.findViewById(R.id.tv_name)).setText(c.name);
            TextView time = v.findViewById(R.id.tv_time);
            TextView countdown = v.findViewById(R.id.tv_countdown);
            TextView button = v.findViewById(R.id.btn_remind);
            if (row.past) {
                final String url = c.pastUrl();
                time.setText(pastFmt.format(new Date(start)) + "  •  " + Ui.span(c.durationSeconds * 1000));
                Ui.badge(countdown, "Virtual", a.getColor(R.color.violet));
                button.setText("Open");
                button.setBackgroundResource(R.drawable.bg_chip_ghost);
                button.setTextColor(a.getColor(R.color.text));
                button.setOnClickListener(x -> Ui.open(a, url));
                v.setOnClickListener(x -> Ui.open(a, url));
            } else {
                time.setText(fmt.format(new Date(start)) + "  •  " + Ui.span(c.durationSeconds * 1000));
                Ui.badge(countdown, "In " + Ui.span(start - System.currentTimeMillis()), a.getColor(R.color.accent));
                button.setText(c.remind ? "Reminder on ✓" : "Remind me");
                button.setBackgroundResource(c.remind ? R.drawable.bg_chip_on : R.drawable.bg_chip_ghost);
                button.setTextColor(a.getColor(c.remind ? R.color.on_primary : R.color.text));
                button.setOnClickListener(x -> {
                    c.remind = !c.remind;
                    db.setRemind(c.id, c.remind);
                    if (c.remind) {
                        a.requestNotifications();
                        Reminders.schedule(a, c);
                        a.snack("Reminder set, 30 min before");
                    } else {
                        Reminders.cancel(a, c);
                    }
                    notifyDataSetChanged();
                });
                v.setOnClickListener(x -> Ui.open(a, c.url()));
            }
            return v;
        }
    }

    public ContestsScreen(MainActivity a) {
        super(a, R.layout.screen_list);
        tvEmpty = root.findViewById(R.id.tv_empty);
        ListView lv = root.findViewById(R.id.list);
        lv.setAdapter(adapter);
    }

    @Override
    public String title() {
        return "Contests";
    }

    private static Row header(String title) {
        Row r = new Row();
        r.header = title;
        return r;
    }

    @Override
    public void refresh() {
        List<Row> out = new ArrayList<Row>();
        List<Contest> upcoming = db.contests(false);
        List<Contest> ideas = Recommender.virtualIdeas(db, 6);
        if (!upcoming.isEmpty()) out.add(header("Upcoming"));
        for (int i = 0; i < upcoming.size(); i++) {
            Row r = new Row();
            r.contest = upcoming.get(i);
            out.add(r);
        }
        if (!ideas.isEmpty()) out.add(header("Try as virtual"));
        for (int i = 0; i < ideas.size(); i++) {
            Row r = new Row();
            r.contest = ideas.get(i);
            r.past = true;
            out.add(r);
        }
        rows = out;
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText("No contests yet. Tap sync while online.");
    }
}
