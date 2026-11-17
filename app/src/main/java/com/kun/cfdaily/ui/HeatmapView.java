package com.kun.cfdaily.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.kun.cfdaily.R;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Calendar heatmap of solves per day: one column per week, Monday at the top, this week on the right. */
public class HeatmapView extends View {

    public interface OnDayTap {
        void onDay(LocalDate day, int count);
    }

    private static final int[] ALPHA = {0, 80, 135, 195, 255};

    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final int accent;
    private final int empty;
    private Map<String, Integer> days = new HashMap<String, Integer>();
    private OnDayTap listener;
    private LocalDate selected;

    private float size;
    private float gap;
    private float gridTop;
    private float gridLeft;
    private int weeks;
    private LocalDate firstMonday;

    public HeatmapView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = c.getColor(R.color.mint);
        empty = (c.getColor(R.color.subtext) & 0x00FFFFFF) | 0x24000000;
        text.setColor(c.getColor(R.color.subtext));
        text.setTypeface(getResources().getFont(R.font.hg_regular));
        text.setTextSize(10 * density);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * density);
        ring.setColor(c.getColor(R.color.text));
    }

    public void setData(Map<String, Integer> data) {
        days = data;
        selected = null;
        invalidate();
    }

    public void setOnDayTap(OnDayTap l) {
        listener = l;
    }

    /** Number of week columns that fit; valid after layout. */
    public int weeks() {
        layoutGrid();
        return weeks;
    }

    public LocalDate firstDay() {
        layoutGrid();
        return firstMonday;
    }

    private static int level(int n) {
        if (n <= 0) return 0;
        if (n == 1) return 1;
        if (n <= 3) return 2;
        if (n <= 5) return 3;
        return 4;
    }

    private int color(int level) {
        return level == 0 ? empty : (accent & 0x00FFFFFF) | (ALPHA[level] << 24);
    }

    private void layoutGrid() {
        gap = 2 * density;
        float labelRow = 16 * density;
        float legendRow = 20 * density;
        gridTop = labelRow;
        size = (getHeight() - labelRow - legendRow - 6 * gap) / 7f;
        if (size < 1) size = 1;
        weeks = Math.max(1, (int) ((getWidth() + gap) / (size + gap)));
        gridLeft = getWidth() - (weeks * (size + gap) - gap);
        LocalDate today = LocalDate.now();
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        firstMonday = monday.minusWeeks(weeks - 1);
    }

    private int count(LocalDate d) {
        Integer n = days.get(d.toString());
        return n == null ? 0 : n;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        layoutGrid();
        LocalDate today = LocalDate.now();
        float r = 3 * density;
        float lastLabelEnd = -1;
        for (int w = 0; w < weeks; w++) {
            float x = gridLeft + w * (size + gap);
            LocalDate monday = firstMonday.plusWeeks(w);
            if (monday.getDayOfMonth() <= 7) {
                String m = monday.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault());
                if (x > lastLabelEnd && x + text.measureText(m) <= getWidth()) {
                    canvas.drawText(m, x, 11 * density, text);
                    lastLabelEnd = x + text.measureText(m) + 4 * density;
                }
            }
            for (int d = 0; d < 7; d++) {
                LocalDate day = monday.plusDays(d);
                if (day.isAfter(today)) break;
                float y = gridTop + d * (size + gap);
                rect.set(x, y, x + size, y + size);
                cell.setColor(color(level(count(day))));
                canvas.drawRoundRect(rect, r, r, cell);
                if (day.equals(selected)) {
                    rect.inset(density, density);
                    canvas.drawRoundRect(rect, r, r, ring);
                }
            }
        }

        // Legend: Less [][][][][] More, right aligned under the grid.
        float ls = 10 * density;
        float ly = getHeight() - ls - 2 * density;
        String more = "More";
        String less = "Less";
        float x = getWidth() - text.measureText(more);
        canvas.drawText(more, x, ly + ls - density, text);
        x -= 4 * density;
        for (int i = 4; i >= 0; i--) {
            x -= ls;
            rect.set(x, ly, x + ls, ly + ls);
            cell.setColor(color(i));
            canvas.drawRoundRect(rect, 2 * density, 2 * density, cell);
            x -= gap;
        }
        x -= 2 * density + text.measureText(less);
        canvas.drawText(less, x, ly + ls - density, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return super.onTouchEvent(e);
        layoutGrid();
        int w = (int) Math.floor((e.getX() - gridLeft + gap / 2) / (size + gap));
        int d = (int) Math.floor((e.getY() - gridTop + gap / 2) / (size + gap));
        if (w < 0 || w >= weeks || d < 0 || d > 6) return true;
        LocalDate day = firstMonday.plusWeeks(w).plusDays(d);
        if (day.isAfter(LocalDate.now())) return true;
        selected = day;
        invalidate();
        if (listener != null) listener.onDay(day, count(day));
        return true;
    }
}
