package com.kun.cfdaily.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.kun.cfdaily.R;

/** Horizontal bar: a rounded track with a coloured fill. */
public class MeterView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;

    public MeterView(Context c, AttributeSet attrs) {
        super(c, attrs);
        track.setColor(c.getColor(R.color.raised));
        fill.setColor(c.getColor(R.color.accent));
    }

    public void set(float fraction, int color) {
        this.fraction = Math.max(0, Math.min(1, fraction));
        fill.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float r = h / 2;
        canvas.drawRoundRect(0, 0, getWidth(), h, r, r, track);
        if (fraction > 0) canvas.drawRoundRect(0, 0, Math.max(h, getWidth() * fraction), h, r, r, fill);
    }
}
