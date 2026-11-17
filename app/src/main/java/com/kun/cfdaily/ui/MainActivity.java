package com.kun.cfdaily.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Db;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Reminders;
import com.kun.cfdaily.data.Sync;

public class MainActivity extends Activity {

    private static final int[] TAB_IDS = {R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4};

    private Screen[] screens;
    private ViewGroup[] tabs;
    private int current = -1;
    private TextView title;
    private ProgressBar progress;
    private FrameLayout content;
    private View scrim;
    private View sheet;
    private TextView sheetTitle;
    private FrameLayout sheetContent;
    private TextView sheetCancel;
    private TextView sheetAction;
    private TextView snack;
    private boolean alive;
    private long lastQuick;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (current == 1) screens[current].refresh();
            ticker.postDelayed(this, 30000);
        }
    };
    private final Runnable hideSnack = () -> snack.animate().alpha(0).setDuration(200).withEndAction(() -> snack.setVisibility(View.GONE));

    /** The look is the user's choice in Profile, not the phone's dark-mode setting. */
    @Override
    protected void attachBaseContext(Context base) {
        boolean dark = base.getSharedPreferences("ui", 0).getBoolean("dark", false);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    /** One button in the top bar; the sheet offers the two sites. */
    private void pickMode() {
        FlowLayout flow = new FlowLayout(this);
        String[] names = {"Codeforces", "LeetCode"};
        for (int i = 0; i < 2; i++) {
            final boolean leetcode = i == 1;
            TextView chip = (TextView) getLayoutInflater().inflate(R.layout.chip, flow, false);
            chip.setText(names[i]);
            if (leetcode == Mode.lc(this)) {
                chip.setBackgroundResource(R.drawable.bg_chip_on);
                chip.setTextColor(getColor(R.color.on_primary));
            }
            chip.setOnClickListener(v -> {
                if (leetcode == Mode.lc(this)) {
                    hideSheet();
                    return;
                }
                Mode.set(this, leetcode);
                recreate();
            });
            flow.addView(chip);
        }
        showSheet("Practice on", flow, null, null);
    }

    public void setDark(boolean dark) {
        if (Ui.dark == dark) return;
        getSharedPreferences("ui", 0).edit().putBoolean("dark", dark).apply();
        recreate();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.dark = getSharedPreferences("ui", 0).getBoolean("dark", false);
        setContentView(R.layout.activity_main);
        alive = true;
        title = findViewById(R.id.title);
        progress = findViewById(R.id.progress);
        content = findViewById(R.id.content);
        scrim = findViewById(R.id.scrim);
        sheet = findViewById(R.id.sheet);
        sheetTitle = findViewById(R.id.sheet_title);
        sheetContent = findViewById(R.id.sheet_content);
        sheetCancel = findViewById(R.id.sheet_cancel);
        sheetAction = findViewById(R.id.sheet_action);
        snack = findViewById(R.id.snack);
        scrim.setOnClickListener(v -> hideSheet());
        sheetCancel.setOnClickListener(v -> hideSheet());

        screens = new Screen[]{
                new TodayScreen(this),
                new ContestsScreen(this),
                new ProblemsScreen(this),
                new StatsScreen(this),
                new ProfileScreen(this)
        };
        tabs = new ViewGroup[TAB_IDS.length];
        for (int i = 0; i < TAB_IDS.length; i++) {
            final int index = i;
            tabs[i] = findViewById(TAB_IDS[i]);
            tabs[i].setOnClickListener(v -> select(index));
        }
        findViewById(R.id.btn_sync).setOnClickListener(v -> startSync(true));
        TextView mode = findViewById(R.id.btn_mode);
        mode.setText(Mode.lc(this) ? "LeetCode" : "Codeforces");
        mode.setOnClickListener(v -> pickMode());

        Reminders.ensureChannels(this);
        Reminders.scheduleDaily(this);
        requestNotifications();

        Db db = Db.get(this);
        select(db.kv("handle", "").length() == 0 ? 4 : (state != null ? state.getInt("tab", 0) : 0));
        long last = db.kvLong("last_sync", 0);
        if (System.currentTimeMillis() - last > 6L * 3600 * 1000) startSync(false);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        progress.setVisibility(Sync.isRunning() ? View.VISIBLE : View.GONE);
        if (current >= 0) screens[current].refresh();
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, 30000);
        quickCheck();
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        TodayWidget.updateAll(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        ticker.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (sheet.getVisibility() == View.VISIBLE) hideSheet();
        else super.onBackPressed();
    }

    /** Picks up fresh accepted submissions when you come back from the browser. */
    private void quickCheck() {
        Db db = Db.get(this);
        long now = System.currentTimeMillis();
        if (db.kv("handle", "").length() == 0 || db.problemCount() == 0 || Sync.isRunning()) return;
        if (now - lastQuick < 45000 || now - db.kvLong("last_sync", 0) < 45000) return;
        lastQuick = now;
        boolean started = Sync.quick(this, error -> {
            if (!alive) return;
            progress.setVisibility(Sync.isRunning() ? View.VISIBLE : View.GONE);
            if (error == null && current >= 0) screens[current].refresh();
        });
        if (started) progress.setVisibility(View.VISIBLE);
    }

    public void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    public void select(int index) {
        current = index;
        content.removeAllViews();
        content.addView(screens[index].root);
        title.setText(screens[index].title());
        View page = screens[index].root;
        page.setAlpha(0);
        page.setTranslationY(Ui.dp(this, 14));
        page.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator());
        for (int i = 0; i < tabs.length; i++) {
            ImageView icon = (ImageView) tabs[i].getChildAt(0);
            icon.setColorFilter(i == index ? 0xFF15141A : getColor(R.color.nav_icon));
            icon.setBackgroundResource(i == index ? R.drawable.bg_nav_on : 0);
        }
        screens[index].refresh();
    }

    public void startSync(final boolean manual) {
        boolean started = Sync.start(this, false, error -> {
            TodayWidget.updateAll(getApplicationContext());
            if (!alive) return;
            progress.setVisibility(Sync.isRunning() ? View.VISIBLE : View.GONE);
            if (error != null) snack("Sync failed: " + error);
            else if (manual) snack("Synced");
            if (current >= 0) screens[current].refresh();
        });
        if (started || Sync.isRunning()) progress.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------- sheet and snack

    /** Slide-up sheet. With a null action it only offers Close. */
    public void showSheet(String heading, View body, String action, final Runnable onAction) {
        sheetTitle.setText(heading);
        sheetContent.removeAllViews();
        if (body != null) sheetContent.addView(body);
        sheetCancel.setText(action == null ? "Close" : "Cancel");
        sheetAction.setVisibility(action == null ? View.GONE : View.VISIBLE);
        sheetAction.setText(action);
        sheetAction.setOnClickListener(v -> {
            hideSheet();
            if (onAction != null) onAction.run();
        });
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0);
        scrim.animate().alpha(1).setDuration(180);
        sheet.setVisibility(View.VISIBLE);
        sheet.setTranslationY(3000);
        sheet.post(() -> {
            sheet.setTranslationY(sheet.getHeight());
            sheet.animate().translationY(0).setDuration(240);
        });
    }

    public void hideSheet() {
        if (sheet.getVisibility() != View.VISIBLE) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(sheet.getWindowToken(), 0);
        scrim.animate().alpha(0).setDuration(180).withEndAction(() -> scrim.setVisibility(View.GONE));
        sheet.animate().translationY(sheet.getHeight()).setDuration(200).withEndAction(() -> sheet.setVisibility(View.GONE));
    }

    /** Short message pill above the tab bar. */
    public void snack(String text) {
        if (!alive) return;
        snack.setText(text);
        snack.setVisibility(View.VISIBLE);
        snack.animate().cancel();
        snack.setAlpha(0);
        snack.animate().alpha(1).setDuration(160);
        ticker.removeCallbacks(hideSnack);
        ticker.postDelayed(hideSnack, 2400);
    }
}
