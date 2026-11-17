package com.kun.cfdaily.ui;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Ai;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Models.Friend;
import com.kun.cfdaily.data.Recommender;
import com.kun.cfdaily.data.Reminders;
import com.kun.cfdaily.data.Sync;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProfileScreen extends Screen {

    private final EditText etHandle;
    private final TextView tvInfo;
    private final Switch swDaily;
    private final Switch swEvening;
    private final TextView btnTime;
    private final TextView[] diffChips;
    private final EditText etFriend;
    private final LinearLayout friendsList;
    private final TextView tvFriends;
    private boolean binding;

    public ProfileScreen(MainActivity a) {
        super(a, R.layout.screen_profile);
        etHandle = root.findViewById(R.id.et_handle);
        tvInfo = root.findViewById(R.id.tv_info);
        swDaily = root.findViewById(R.id.sw_daily);
        swEvening = root.findViewById(R.id.sw_evening);
        btnTime = root.findViewById(R.id.btn_time);
        etFriend = root.findViewById(R.id.et_friend);
        friendsList = root.findViewById(R.id.friends_list);
        tvFriends = root.findViewById(R.id.tv_friends);
        diffChips = new TextView[]{root.findViewById(R.id.diff_easy), root.findViewById(R.id.diff_normal), root.findViewById(R.id.diff_hard)};
        for (int i = 0; i < 3; i++) {
            final int value = i - 1;
            diffChips[i].setOnClickListener(v -> setDifficulty(value));
        }
        root.findViewById(R.id.btn_save).setOnClickListener(v -> save());
        root.findViewById(R.id.theme_light).setOnClickListener(v -> this.a.setDark(false));
        root.findViewById(R.id.theme_dark).setOnClickListener(v -> this.a.setDark(true));
        root.findViewById(R.id.btn_add_friend).setOnClickListener(v -> addFriend());
        btnTime.setOnClickListener(v -> pickTime());
        root.findViewById(R.id.btn_groq).setOnClickListener(v -> saveKey());
        root.findViewById(R.id.btn_groq_get).setOnClickListener(v -> Ui.open(this.a, "https://console.groq.com/keys"));
        root.findViewById(R.id.btn_tpl).setOnClickListener(v -> editHeader());
        root.findViewById(R.id.btn_tpl_reset).setOnClickListener(v -> {
            this.a.getSharedPreferences("ui", 0).edit().remove("tpl_header").apply();
            this.a.snack("Back to the default header");
            refresh();
        });
        swDaily.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            db.setKv("daily_notify", checked ? "1" : "0");
            if (checked) this.a.requestNotifications();
            Reminders.scheduleDaily(this.a);
        });
        swEvening.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            db.setKv("evening_notify", checked ? "1" : "0");
            if (checked) this.a.requestNotifications();
            Reminders.scheduleDaily(this.a);
        });
    }

    @Override
    public String title() {
        return "Profile";
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void setDifficulty(int value) {
        if (db.kvInt("difficulty", 0) == value) return;
        db.setKv("difficulty", String.valueOf(value));
        int n = Recommender.rerollUnsolved(a);
        if (n > 0) a.snack("Swapped " + n + " of today's picks");
        refresh();
    }

    private void pickTime() {
        final TimePicker picker = (TimePicker) a.getLayoutInflater().inflate(R.layout.sheet_time, null);
        picker.setIs24HourView(false);
        picker.setHour(db.kvInt("notify_hour", 9));
        picker.setMinute(db.kvInt("notify_min", 0));
        a.showSheet("Daily picks at", picker, "Set time", () -> {
            db.setKv("notify_hour", String.valueOf(picker.getHour()));
            db.setKv("notify_min", String.valueOf(picker.getMinute()));
            Reminders.scheduleDaily(a);
            refresh();
        });
    }

    private void saveKey() {
        EditText et = root.findViewById(R.id.et_groq);
        Ai.setKey(a, et.getText().toString());
        hideKeyboard(et);
        et.clearFocus();
        a.snack(Ai.ready(a) ? "Key saved" : "Key removed");
        refresh();
    }

    /** Your own includes and macros on top of every template. */
    private void editHeader() {
        final EditText input = new EditText(a);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setTextSize(13);
        input.setGravity(android.view.Gravity.TOP);
        input.setMinLines(8);
        input.setHorizontallyScrolling(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setBackgroundResource(R.drawable.bg_input);
        int pad = Ui.dp(a, 14);
        input.setPadding(pad, pad, pad, pad);
        input.setTextColor(a.getColor(R.color.text));
        String head = a.getSharedPreferences("ui", 0).getString("tpl_header", "");
        input.setText(head.length() > 0 ? head : "#include <bits/stdc++.h>\nusing namespace std;\n\n#define ll long long\n#define all(x) (x).begin(), (x).end()\n");
        a.showSheet("Template header", input, "Save", () -> {
            a.getSharedPreferences("ui", 0).edit().putString("tpl_header", input.getText().toString()).apply();
            a.snack("Header saved. New templates use it.");
            refresh();
        });
    }

    private void save() {
        if (Sync.isRunning()) {
            a.snack("A sync is already running");
            return;
        }
        String handle = etHandle.getText().toString().trim();
        if (!handle.equalsIgnoreCase(db.kv("handle", ""))) {
            db.clearUser();
            db.setKv("handle", handle);
        }
        hideKeyboard(etHandle);
        etHandle.clearFocus();
        a.startSync(true);
        refresh();
    }

    private void addFriend() {
        final String handle = etFriend.getText().toString().trim();
        if (handle.length() == 0) return;
        if (handle.equalsIgnoreCase(db.kv("handle", ""))) {
            a.snack("That is you");
            return;
        }
        if (db.friends().size() >= 8) {
            a.snack("Up to 8 friends");
            return;
        }
        hideKeyboard(etFriend);
        boolean started = Sync.addFriend(a, handle, error -> {
            if (error != null) a.snack("Could not add " + handle);
            else etFriend.setText("");
            showFriends();
        });
        a.snack(started ? "Looking up " + handle + "..." : "A sync is running, try again");
    }

    private void showFriends() {
        friendsList.removeAllViews();
        List<Friend> all = new ArrayList<Friend>(db.friends());
        tvFriends.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
        tvFriends.setText("Solved in 7 days  •  tap to remove");
        if (all.isEmpty()) return;
        String handle = db.kv("handle", "");
        if (handle.length() > 0) {
            Friend me = new Friend();
            me.handle = handle;
            me.rating = db.kvInt("rating", 0);
            me.week = db.weekSolved();
            me.me = true;
            all.add(me);
        }
        Collections.sort(all, (x, y) -> x.week != y.week ? y.week - x.week : y.rating - x.rating);
        int top = Math.max(1, all.get(0).week);
        for (int i = 0; i < all.size(); i++) {
            final Friend f = all.get(i);
            View v = a.getLayoutInflater().inflate(R.layout.item_meter, friendsList, false);
            TextView left = v.findViewById(R.id.tv_left);
            left.setText((i + 1) + "  " + f.handle);
            left.setTextColor(Ui.ratingColor(f.rating));
            ((MeterView) v.findViewById(R.id.meter)).set(f.week / (float) top, a.getColor(f.me ? R.color.mint : R.color.violet));
            ((TextView) v.findViewById(R.id.tv_right)).setText(String.valueOf(f.week));
            if (!f.me) {
                v.setOnClickListener(x -> a.showSheet("Remove " + f.handle + "?", null, "Remove", () -> {
                    db.removeFriend(f.handle);
                    showFriends();
                }));
            }
            friendsList.addView(v);
        }
    }

    @Override
    public void refresh() {
        String handle = db.kv("handle", "");
        boolean keyed = Ai.ready(a);
        TextView tvAi = root.findViewById(R.id.tv_ai);
        tvAi.setText(keyed ? "✓  Connected. Hints, Debug, Explain and Coach are on." : "Free key from Groq turns on hints, bug finding, editorials and a weekly coach.");
        tvAi.setTextColor(a.getColor(keyed ? R.color.good : R.color.subtext));
        root.findViewById(R.id.btn_groq_get).setVisibility(keyed ? View.GONE : View.VISIBLE);
        EditText etGroq = root.findViewById(R.id.et_groq);
        if (!etGroq.hasFocus()) etGroq.setText(Ai.key(a));
        String head = a.getSharedPreferences("ui", 0).getString("tpl_header", "");
        ((TextView) root.findViewById(R.id.tv_tpl)).setText(head.trim().length() > 0 ? head.trim() : "#include <bits/stdc++.h>\nusing namespace std;");
        // Templates are Codeforces only.
        root.findViewById(R.id.card_tpl).setVisibility(Mode.lc() ? View.GONE : View.VISIBLE);
        ((TextView) root.findViewById(R.id.tv_handle_label)).setText(Mode.lc() ? "LeetCode username" : "Codeforces handle");
        if (!etHandle.hasFocus() || etHandle.getText().length() == 0) etHandle.setText(handle);
        binding = true;
        swDaily.setChecked("1".equals(db.kv("daily_notify", "1")));
        swEvening.setChecked("1".equals(db.kv("evening_notify", "1")));
        binding = false;
        int hour = db.kvInt("notify_hour", 9);
        btnTime.setText(String.format(Locale.US, "%d:%02d %s", hour % 12 == 0 ? 12 : hour % 12, db.kvInt("notify_min", 0), hour < 12 ? "AM" : "PM"));

        int rating = db.kvInt("rating", 0);
        TextView pfRating = root.findViewById(R.id.pf_rating);
        pfRating.setText(rating > 0 ? String.valueOf(rating) : "–");
        int peak = db.kvInt("max_rating", 0);
        ((TextView) root.findViewById(R.id.pf_peak)).setText(peak > 0 ? String.valueOf(peak) : "–");
        ((TextView) root.findViewById(R.id.pf_solved)).setText(String.valueOf(db.solvedCount()));

        long last = db.kvLong("last_sync", 0);
        String when = last == 0 ? "never" : new SimpleDateFormat("d MMM h:mm a", Locale.getDefault()).format(new Date(last));
        tvInfo.setText(Sync.isRunning() ? "Syncing..." : "Synced " + when + "  •  " + Ui.compact(db.problemCount()) + " problems");

        int diff = Math.max(-1, Math.min(1, db.kvInt("difficulty", 0)));
        for (int i = 0; i < 3; i++) {
            boolean on = i - 1 == diff;
            diffChips[i].setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_ghost);
            diffChips[i].setTextColor(a.getColor(on ? R.color.on_primary : R.color.text));
        }
        TextView[] themes = {root.findViewById(R.id.theme_light), root.findViewById(R.id.theme_dark)};
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == Ui.dark;
            themes[i].setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_ghost);
            themes[i].setTextColor(a.getColor(on ? R.color.on_primary : R.color.text));
        }
        Ui.badge(root.findViewById(R.id.df_warm), "Warm " + Ui.levelText(Recommender.slotTarget(db, 0)), a.getColor(R.color.mint));
        Ui.badge(root.findViewById(R.id.df_target), "Target " + Ui.levelText(Recommender.slotTarget(db, 1)), a.getColor(R.color.blue));
        Ui.badge(root.findViewById(R.id.df_stretch), "Stretch " + Ui.levelText(Recommender.slotTarget(db, 2)), a.getColor(R.color.pink));

        if (!Sync.isRunning()) showFriends();
    }
}
