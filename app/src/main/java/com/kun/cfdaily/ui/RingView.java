package com.kun.cfdaily.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.kun.cfdaily.R;

/** Small progress ring. */
public class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fraction;

    public RingView(Context c, AttributeSet attrs) {
        super(c, attrs);
        float w = 6f * getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0x26FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(0xFF7EE2B4);
    }

    public void set(float fraction) {
        this.fraction = Math.max(0, Math.min(1, fraction));
        invalidate();
    }

    /** Sweeps from the current value to the new one. */
    public void animateTo(float target) {
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(fraction, Math.max(0, Math.min(1, target)));
        a.setDuration(900);
        a.setInterpolator(new android.view.animation.DecelerateInterpolator(2));
        a.addUpdateListener(x -> set((float) x.getAnimatedValue()));
        a.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = track.getStrokeWidth() / 2 + 1;
        canvas.drawOval(inset, inset, getWidth() - inset, getHeight() - inset, track);
        if (fraction > 0) canvas.drawArc(inset, inset, getWidth() - inset, getHeight() - inset, -90, 360 * fraction, false, arc);
    }
}
