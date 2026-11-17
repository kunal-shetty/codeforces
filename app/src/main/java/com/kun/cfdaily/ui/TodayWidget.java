package com.kun.cfdaily.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Db;
import com.kun.cfdaily.data.Models.DailyItem;
import com.kun.cfdaily.data.Models.Problem;
import com.kun.cfdaily.data.Recommender;

import java.util.List;

/** Home-screen widget: progress ring, streak flame, today's problems, the next one to solve again, and cards due. */
public class TodayWidget extends AppWidgetProvider {

    private static final int[] ROWS = {R.id.w_row0, R.id.w_row1, R.id.w_row2};
    private static final int WHITE = 0xFFFAF9FA, GOOD = 0xFF5EE6B0, DIM = 0xFFB9B7C2;

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        render(c, mgr, ids);
    }

    public static void updateAll(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        if (mgr == null) return;
        int[] ids = mgr.getAppWidgetIds(new ComponentName(c, TodayWidget.class));
        if (ids != null && ids.length > 0) render(c, mgr, ids);
    }

    /** Ring drawn into a bitmap, since widgets can't host custom views. */
    private static Bitmap ring(Context c, int done, int total) {
        float d = c.getResources().getDisplayMetrics().density;
        int size = Math.round(44 * d);
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float stroke = 5 * d;
        RectF r = new RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0x33FFFFFF);
        cv.drawArc(r, 0, 360, false, p);
        if (total > 0 && done > 0) {
            p.setColor(done >= total ? GOOD : 0xFF7CC4FF);
            cv.drawArc(r, -90, 360f * done / total, false, p);
        }
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(WHITE);
        t.setTextSize(13 * d);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextAlign(Paint.Align.CENTER);
        String s = total == 0 ? "–" : done + "/" + total;
        cv.drawText(s, size / 2f, size / 2f - (t.descent() + t.ascent()) / 2, t);
        return b;
    }

    private static void render(Context c, AppWidgetManager mgr, int[] ids) {
        Db db = Db.get(c);
        List<DailyItem> items = Recommender.ensureToday(c);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openApp = PendingIntent.getActivity(c, 50, new Intent(c, MainActivity.class), flags);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_today);
        int streak = db.streaks()[0];
        int done = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).solved || items.get(i).problem.solved) done++;
        }
        v.setImageViewBitmap(R.id.w_ring, ring(c, done, items.size()));
        v.setTextViewText(R.id.w_title, "Upsolve");
        v.setTextViewText(R.id.w_status, items.isEmpty() ? "Sync to get today's set" : done >= items.size() ? "All done today" : (items.size() - done) + " left today");
        v.setTextViewText(R.id.w_streak, "🔥 " + streak);
        v.setOnClickPendingIntent(R.id.w_root, openApp);

        for (int i = 0; i < ROWS.length; i++) {
            if (i < items.size()) {
                DailyItem it = items.get(i);
                boolean solved = it.solved || it.problem.solved;
                v.setViewVisibility(ROWS[i], View.VISIBLE);
                v.setTextViewText(ROWS[i], (solved ? "✓  " : "○  ") + Ui.levelText(it.problem.rating) + "   " + it.problem.name);
                v.setTextColor(ROWS[i], solved ? GOOD : WHITE);
                Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(it.problem.url()));
                v.setOnClickPendingIntent(ROWS[i], PendingIntent.getActivity(c, 51 + i, open, flags));
            } else if (i == 0) {
                v.setViewVisibility(ROWS[i], View.VISIBLE);
                v.setTextViewText(ROWS[i], "Open the app and sync to get today's problems.");
                v.setTextColor(ROWS[i], DIM);
                v.setOnClickPendingIntent(ROWS[i], openApp);
            } else {
                v.setViewVisibility(ROWS[i], View.GONE);
            }
        }

        List<Object[]> due = db.revisitsDue(1);
        if (due.isEmpty()) {
            v.setViewVisibility(R.id.w_revisit, View.GONE);
        } else {
            Problem p = (Problem) due.get(0)[0];
            v.setViewVisibility(R.id.w_revisit, View.VISIBLE);
            v.setTextViewText(R.id.w_revisit, "↻  " + p.name);
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(p.url()));
            v.setOnClickPendingIntent(R.id.w_revisit, PendingIntent.getActivity(c, 60, open, flags));
        }
        int cards = db.cardsDue();
        v.setViewVisibility(R.id.w_cards, cards > 0 ? View.VISIBLE : View.GONE);
        v.setTextViewText(R.id.w_cards, "★ " + cards);
        v.setOnClickPendingIntent(R.id.w_cards, openApp);
        mgr.updateAppWidget(ids, v);
    }
}
