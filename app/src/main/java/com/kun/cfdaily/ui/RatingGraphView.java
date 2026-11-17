package com.kun.cfdaily.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Models.RatingPoint;

import java.util.ArrayList;
import java.util.List;

public class RatingGraphView extends View {

    private static final int[] BOUNDS = {0, 1200, 1400, 1600, 1900, 2100, 2400, 6000};

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private List<RatingPoint> pts = new ArrayList<RatingPoint>();

    public RatingGraphView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * density);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(c.getColor(R.color.accent));
        dot.setColor(c.getColor(R.color.text));
        text.setColor(c.getColor(R.color.subtext));
        text.setTypeface(getResources().getFont(R.font.hg_regular));
        text.setTextSize(11 * density);
    }

    public void setData(List<RatingPoint> data) {
        pts = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pts.isEmpty()) return;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < pts.size(); i++) {
            min = Math.min(min, pts.get(i).newRating);
            max = Math.max(max, pts.get(i).newRating);
        }
        min = Math.max(0, min - 100);
        max = max + 100;
        float left = 36 * density;
        float right = getWidth() - 8 * density;
        float top = 8 * density;
        float bottom = getHeight() - 8 * density;
        float range = max - min;

        for (int i = 0; i < BOUNDS.length - 1; i++) {
            int lo = Math.max(min, BOUNDS[i]);
            int hi = Math.min(max, BOUNDS[i + 1]);
            if (lo >= hi) continue;
            band.setColor((Ui.ratingColor(BOUNDS[i]) & 0x00FFFFFF) | 0x38000000);
            float y1 = bottom - (hi - min) / range * (bottom - top);
            float y2 = bottom - (lo - min) / range * (bottom - top);
            canvas.drawRect(left, y1, right, y2, band);
            if (BOUNDS[i] > min) canvas.drawText(String.valueOf(BOUNDS[i]), 2 * density, y2 + 4 * density, text);
        }

        Path path = new Path();
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            float x = n == 1 ? (left + right) / 2 : left + (right - left) * i / (n - 1);
            float y = bottom - (pts.get(i).newRating - min) / range * (bottom - top);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
            if (n <= 60 || i == n - 1) canvas.drawCircle(x, y, 3 * density, dot);
        }
        canvas.drawPath(path, line);
    }
}
