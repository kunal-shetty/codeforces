package com.kun.cfdaily.ui;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Db;
import com.kun.cfdaily.data.Models.Problem;

import java.util.List;

/** Daily review of the tricks behind problems you met: see the problem, recall the trick, flip, grade yourself. */
public class Flashcards {

    private static final int[] FILLS = {R.color.p_lilac, R.color.p_sky, R.color.p_mint, R.color.p_rose, R.color.p_peach};

    private final MainActivity a;
    private final Db db;
    private final LinearLayout box;
    private final Runnable onClose;
    private List<Object[]> deck;
    private int at;
    private int remembered;

    static void show(MainActivity a, Runnable onClose) {
        new Flashcards(a, onClose).start();
    }

    private Flashcards(MainActivity a, Runnable onClose) {
        this.a = a;
        this.db = Db.get(a);
        this.box = new LinearLayout(a);
        this.onClose = onClose;
        box.setOrientation(LinearLayout.VERTICAL);
    }

    private void start() {
        deck = db.dueCards(20);
        a.showSheet("Trick cards", box, null, null);
        draw();
    }

    private TextView text(String s, float size, int font) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(a.getColor(R.color.text));
        t.setTypeface(a.getResources().getFont(font));
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, top);
        return lp;
    }

    private TextView button(String s, boolean primary, int weightColor) {
        TextView t = (TextView) a.getLayoutInflater().inflate(R.layout.chip, null, false);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        if (primary) {
            t.setBackgroundResource(R.drawable.bg_chip);
            t.setTextColor(a.getColor(R.color.on_primary));
        } else {
            Ui.tint(t, a.getColor(weightColor), 22, Ui.dark ? 0x30 : 0x22);
            t.setTextColor(a.getColor(weightColor));
        }
        return t;
    }

    private void draw() {
        box.removeAllViews();
        if (at >= deck.size()) {
            done();
            return;
        }
        Object[] row = deck.get(at);
        final Problem p = (Problem) row[0];
        final String trick = ProblemSheet.tidy((String) row[1]);
        int level = (Integer) row[2];

        TextView count = text((at + 1) + " / " + deck.size() + "   ·   box " + (level + 1), 13, R.font.hg_semibold);
        count.setTextColor(a.getColor(R.color.faint));
        box.addView(count);

        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 22);
        card.setPadding(pad, pad, pad, pad);
        Ui.tint(card, a.getColor(FILLS[at % FILLS.length]), 26, 0xFF);
        TextView meta = text(p.id() + "  ·  " + Ui.levelText(p.rating), 13, R.font.hg_semibold);
        meta.setAlpha(0.7f);
        card.addView(meta);
        card.addView(text(p.name, 24, R.font.hg_extrabold), gap(6));
        StringBuilder tags = new StringBuilder();
        for (int i = 0; i < Math.min(3, p.tags.length); i++) if (!p.tags[i].startsWith("*")) tags.append(tags.length() > 0 ? ", " : "").append(Ui.tag(p.tags[i]));
        if (tags.length() > 0) {
            TextView t = text(tags.toString(), 13, R.font.hg_regular);
            t.setAlpha(0.7f);
            card.addView(t, gap(4));
        }
        final TextView back = text("What's the trick?\nTap to flip", 17, R.font.hg_semibold);
        back.setAlpha(0.55f);
        card.addView(back, gap(22));
        card.setMinimumHeight(Ui.dp(a, 240));
        box.addView(card, gap(10));

        final LinearLayout actions = new LinearLayout(a);
        actions.setVisibility(android.view.View.INVISIBLE);
        TextView forgot = button("Forgot", false, R.color.coral);
        TextView got = button("✓  Got it", true, 0);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        half.rightMargin = Ui.dp(a, 8);
        actions.addView(forgot, half);
        actions.addView(got, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(actions, gap(14));

        TextView open = button("Open problem", false, R.color.blue);
        open.setOnClickListener(v -> Ui.open(a, p.url()));
        LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrap.topMargin = Ui.dp(a, 10);
        box.addView(open, wrap);

        card.setOnClickListener(v -> {
            card.animate().rotationY(90).setDuration(140).withEndAction(() -> {
                back.setText(trick);
                back.setAlpha(1);
                back.setTypeface(a.getResources().getFont(R.font.hg_regular));
                card.setRotationY(-90);
                card.animate().rotationY(0).setDuration(140);
                actions.setVisibility(android.view.View.VISIBLE);
            });
            card.setOnClickListener(null);
        });
        forgot.setOnClickListener(v -> grade(p, false));
        got.setOnClickListener(v -> grade(p, true));
    }

    private void grade(Problem p, boolean ok) {
        db.reviewCard(p.pkey, ok);
        if (ok) remembered++;
        at++;
        box.setAlpha(0);
        draw();
        box.animate().alpha(1).setDuration(180);
    }

    private void done() {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.dp(a, 24);
        card.setPadding(pad, pad, pad, pad);
        Ui.tint(card, a.getColor(R.color.p_mint), 26, 0xFF);
        TextView big = text(deck.isEmpty() ? "✓" : remembered + " / " + deck.size(), 40, R.font.hg_extrabold);
        big.setGravity(Gravity.CENTER);
        card.addView(big);
        TextView sub = text(deck.isEmpty() ? "Nothing due. Hints and Explain add new cards, and so do your notes." : "remembered. See you tomorrow.", 15, R.font.hg_regular);
        sub.setGravity(Gravity.CENTER);
        card.addView(sub, gap(6));
        box.addView(card);
        if (onClose != null) onClose.run();
    }
}
