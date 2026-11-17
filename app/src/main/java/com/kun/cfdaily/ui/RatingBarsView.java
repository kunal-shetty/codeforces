package com.kun.cfdaily.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.kun.cfdaily.R;

import java.util.ArrayList;
import java.util.List;

/** Bar chart of solved problems per problem rating (one bar per 100 points). */
public class RatingBarsView extends View {

    public interface OnBarTap {
        void onBar(int rating, int count);
    }

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final int accent;
    private int first;
    private int[] counts = new int[0];
    private int selected = -1;
    private OnBarTap listener;

    public RatingBarsView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = c.getColor(R.color.accent);
        axis.setColor((c.getColor(R.color.subtext) & 0x00FFFFFF) | 0x55000000);
        axis.setStrokeWidth(density);
        text.setColor(c.getColor(R.color.subtext));
        text.setTypeface(getResources().getFont(R.font.hg_regular));
        text.setTextSize(10 * density);
        text.setTextAlign(Paint.Align.CENTER);
        value.setColor(c.getColor(R.color.text));
        value.setTextSize(11 * density);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(getResources().getFont(R.font.hg_semibold));
    }

    /** rows: {rating, count} ascending by rating. */
    public void setData(List<int[]> rows) {
        selected = -1;
        if (rows.isEmpty()) {
            counts = new int[0];
        } else {
            first = rows.get(0)[0] / 100 * 100;
            int last = rows.get(rows.size() - 1)[0] / 100 * 100;
            counts = new int[(last - first) / 100 + 1];
            for (int i = 0; i < rows.size(); i++) counts[(rows.get(i)[0] - first) / 100] += rows.get(i)[1];
        }
        invalidate();
    }

    public void setOnBarTap(OnBarTap l) {
        listener = l;
    }

    private float slot() {
        return getWidth() / (float) Math.max(1, counts.length);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = counts.length;
        if (n == 0 || getWidth() == 0) return;
        int max = 1;
        int maxAt = 0;
        for (int i = 0; i < n; i++) {
            if (counts[i] > max) {
                max = counts[i];
                maxAt = i;
            }
        }
        float top = 18 * density;
        float bottom = getHeight() - 18 * density;
        float slot = slot();
        float gap = 2 * density;
        float width = Math.min(slot - gap, 28 * density);
        float r = Math.min(4 * density, width / 2);
        float[] radii = {r, r, r, r, 0, 0, 0, 0};

        for (int i = 0; i < n; i++) {
            if (counts[i] == 0) continue;
            float cx = slot * i + slot / 2;
            float h = Math.max(2 * density, (bottom - top) * counts[i] / max);
            rect.set(cx - width / 2, bottom - h, cx + width / 2, bottom);
            path.reset();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            int col = Ui.ratingColor(first + i * 100);
            bar.setColor(selected == -1 || selected == i ? col : (col & 0x00FFFFFF) | 0x59000000);
            canvas.drawPath(path, bar);
            if (i == (selected == -1 ? maxAt : selected)) {
                canvas.drawText(String.valueOf(counts[i]), cx, bottom - h - 5 * density, value);
            }
        }
        canvas.drawLine(0, bottom, getWidth(), bottom, axis);

        // X labels: as many as fit without touching.
        float labelWidth = text.measureText("3500") + 6 * density;
        int step = Math.max(1, (int) Math.ceil(labelWidth / slot));
        if (step == 3) step = 4;
        List<Integer> shown = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            if ((first / 100 + i) % step == 0) shown.add(i);
        }
        if (shown.isEmpty()) shown.add(0);
        for (int k = 0; k < shown.size(); k++) {
            int i = shown.get(k);
            float cx = slot * i + slot / 2;
            float half = text.measureText(String.valueOf(first + i * 100)) / 2;
            cx = Math.max(half, Math.min(getWidth() - half, cx));
            canvas.drawText(String.valueOf(first + i * 100), cx, getHeight() - 4 * density, text);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP) return super.onTouchEvent(e);
        if (counts.length == 0) return true;
        int i = Math.max(0, Math.min(counts.length - 1, (int) (e.getX() / slot())));
        selected = selected == i ? -1 : i;
        invalidate();
        if (listener != null && selected >= 0) listener.onBar(first + i * 100, counts[i]);
        return true;
    }
}
