package com.kun.cfdaily.ui;

import android.view.View;

import com.kun.cfdaily.data.Db;

public abstract class Screen {

    protected final MainActivity a;
    protected final View root;
    protected final Db db;

    protected Screen(MainActivity a, int layout) {
        this.a = a;
        this.db = Db.get(a);
        this.root = a.getLayoutInflater().inflate(layout, null);
    }

    public abstract String title();

    public abstract void refresh();
}
