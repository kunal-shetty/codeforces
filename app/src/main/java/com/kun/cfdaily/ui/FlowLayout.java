package com.kun.cfdaily.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left to right and wraps to a new row when the line is full. */
public class FlowLayout extends ViewGroup {

    private final int gap;

    public FlowLayout(Context c) {
        this(c, null);
    }

    public FlowLayout(Context c, AttributeSet attrs) {
        super(c, attrs);
        gap = Math.round(6 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int x = 0;
        int y = 0;
        int row = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (x > 0 && x + child.getMeasuredWidth() > width) {
                x = 0;
                y += row + gap;
                row = 0;
            }
            x += child.getMeasuredWidth() + gap;
            row = Math.max(row, child.getMeasuredHeight());
        }
        setMeasuredDimension(width, y + row);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int x = 0;
        int y = 0;
        int row = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            if (x > 0 && x + child.getMeasuredWidth() > width) {
                x = 0;
                y += row + gap;
                row = 0;
            }
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            x += child.getMeasuredWidth() + gap;
            row = Math.max(row, child.getMeasuredHeight());
        }
    }
}
