package com.kun.cfdaily.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import com.kun.cfdaily.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An array drawn as cells, with named pointers (l, r, mid…) that glide between cells.
 * Changed values flash, and an optional range is shaded.
 */
public class TraceView extends View {

    private static final int[] POINTER_COLORS = {R.color.blue, R.color.coral, R.color.violet, R.color.mint, R.color.amber, R.color.pink};

    private final Paint cellFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint index = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;
    private final int ink;

    private List<String> values = new ArrayList<>();
    private final List<Boolean> changed = new ArrayList<>();
    /** Every pointer name in the trace, in first-seen order, so each keeps its colour. */
    private final List<String> names = new ArrayList<>();
    private final Map<String, float[]> from = new LinkedHashMap<>();
    private final Map<String, float[]> to = new LinkedHashMap<>();
    private int rangeFrom = -1, rangeTo = -1;
    private float t = 1;
    private ValueAnimator anim;

    public TraceView(Context c, int ink, List<String> pointerNames) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        this.ink = ink;
        names.addAll(pointerNames);
        cellStroke.setStyle(Paint.Style.STROKE);
        cellStroke.setStrokeWidth(1.5f * d);
        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        index.setTextAlign(Paint.Align.CENTER);
        index.setTextSize(11 * d);
        pillText.setTypeface(getResources().getFont(R.font.hg_extrabold));
        pillText.setTextAlign(Paint.Align.CENTER);
        pillText.setTextSize(12 * d);
        pillText.setColor(0xFFFFFFFF);
    }

    private int slots() {
        return Math.max(1, Math.min(3, names.size()));
    }

    private float pillH() {
        return 22 * d;
    }

    private float top() {
        return slots() * (pillH() + 4 * d) + 8 * d;
    }

    private float cell() {
        int n = Math.max(1, values.size());
        float gap = 6 * d;
        return Math.min(58 * d, (getWidth() - gap * (n - 1)) / n);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int n = Math.max(1, values.size());
        float c = Math.min(58 * d, (w - 6 * d * (n - 1)) / n);
        setMeasuredDimension(w, Math.round(top() + c + 22 * d));
    }

    /** Shows one frame. pointers maps a name to a cell; names missing from it fade out. */
    public void show(List<String> newValues, Map<String, Integer> pointers, int[] range, boolean animate) {
        changed.clear();
        for (int i = 0; i < newValues.size(); i++) changed.add(animate && i < values.size() && !values.get(i).equals(newValues.get(i)));
        boolean resized = newValues.size() != values.size();
        values = new ArrayList<>(newValues);
        rangeFrom = range == null ? -1 : range[0];
        rangeTo = range == null ? -1 : range[1];

        // Pointers on the same cell stack upwards.
        Map<Integer, Integer> perCell = new LinkedHashMap<>();
        // Snapshot where each pointer is drawn now (mid-animation too), then glide from there.
        Map<String, float[]> now = new LinkedHashMap<>();
        for (String name : to.keySet()) now.put(name, current(name));
        from.clear();
        from.putAll(now);
        to.clear();
        for (String name : names) {
            Integer at = pointers.get(name);
            if (at == null) continue;
            int slot = perCell.containsKey(at) ? perCell.get(at) : 0;
            perCell.put(at, slot + 1);
            to.put(name, new float[]{at, Math.min(slot, slots() - 1), 1});
        }
        for (Map.Entry<String, float[]> e : from.entrySet()) {
            if (!to.containsKey(e.getKey())) to.put(e.getKey(), new float[]{e.getValue()[0], e.getValue()[1], 0});
        }
        if (anim != null) anim.cancel();
        if (resized) requestLayout();
        if (!animate) {
            t = 1;
            from.clear();
            invalidate();
            return;
        }
        t = 0;
        anim = ValueAnimator.ofFloat(0, 1);
        anim.setDuration(420);
        anim.setInterpolator(new OvershootInterpolator(1.4f));
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    /** Where a pointer is drawn right now: {cell, slot, alpha}. */
    private float[] current(String name) {
        float[] b = to.get(name);
        float[] a = from.containsKey(name) ? from.get(name) : new float[]{b[0], b[1], 0};
        float k = Math.max(0, Math.min(1.15f, t));
        float alphaK = Math.max(0, Math.min(1, t));
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * alphaK};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Context c = getContext();
        int n = values.size();
        if (n == 0) return;
        float size = cell(), gap = 6 * d;
        float total = n * size + (n - 1) * gap;
        float left = (getWidth() - total) / 2, top = top();
        float radius = 12 * d;
        text.setTextSize(Math.min(18 * d, size * (longest() > 3 ? 0.26f : 0.38f)));
        index.setColor(c.getColor(R.color.faint));
        float flash = Math.max(0, 1 - Math.min(1, t) * 1.2f);

        for (int i = 0; i < n; i++) {
            float x = left + i * (size + gap);
            RectF r = new RectF(x, top, x + size, top + size);
            boolean inRange = rangeFrom >= 0 && i >= rangeFrom && i <= rangeTo;
            boolean dim = rangeFrom >= 0 && !inRange;
            int base = c.getColor(R.color.card);
            cellFill.setColor(inRange ? blend(base, ink, Ui.dark ? 0.32f : 0.2f) : base);
            canvas.drawRoundRect(r, radius, radius, cellFill);
            if (i < changed.size() && changed.get(i) && flash > 0) {
                cellFill.setColor(c.getColor(R.color.amber));
                cellFill.setAlpha((int) (150 * flash));
                canvas.drawRoundRect(r, radius, radius, cellFill);
                cellFill.setAlpha(255);
            }
            cellStroke.setColor(inRange ? ink : c.getColor(R.color.hairline));
            canvas.drawRoundRect(r, radius, radius, cellStroke);
            text.setColor(c.getColor(R.color.text));
            text.setAlpha(dim ? 90 : 255);
            float pop = i < changed.size() && changed.get(i) ? 1 + 0.25f * flash : 1;
            canvas.save();
            canvas.scale(pop, pop, r.centerX(), r.centerY());
            canvas.drawText(values.get(i), r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text);
            canvas.restore();
            canvas.drawText(String.valueOf(i), r.centerX(), r.bottom + 16 * d, index);
        }

        for (Map.Entry<String, float[]> e : to.entrySet()) {
            float[] p = current(e.getKey());
            if (p[2] <= 0.01f) continue;
            int color = c.getColor(POINTER_COLORS[Math.max(0, names.indexOf(e.getKey())) % POINTER_COLORS.length]);
            float cx = left + p[0] * (size + gap) + size / 2;
            float bottom = top - 6 * d - p[1] * (pillH() + 4 * d);
            float w = Math.max(28 * d, pillText.measureText(e.getKey()) + 16 * d);
            RectF r = new RectF(cx - w / 2, bottom - pillH(), cx + w / 2, bottom);
            pill.setColor(color);
            pill.setAlpha((int) (255 * Math.min(1, p[2])));
            canvas.drawRoundRect(r, pillH() / 2, pillH() / 2, pill);
            if (p[1] < 0.5f) {
                Path tip = new Path();
                tip.moveTo(cx - 5 * d, bottom - 1);
                tip.lineTo(cx + 5 * d, bottom - 1);
                tip.lineTo(cx, bottom + 5 * d);
                tip.close();
                canvas.drawPath(tip, pill);
            }
            pillText.setAlpha((int) (255 * Math.min(1, p[2])));
            canvas.drawText(e.getKey(), cx, r.centerY() - (pillText.descent() + pillText.ascent()) / 2, pillText);
        }
    }

    private int longest() {
        int m = 0;
        for (String v : values) m = Math.max(m, v.length());
        return m;
    }

    private static int blend(int a, int b, float k) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | (Math.round(ar + (br - ar) * k) << 16) | (Math.round(ag + (bg - ag) * k) << 8) | Math.round(ab + (bb - ab) * k);
    }
}
