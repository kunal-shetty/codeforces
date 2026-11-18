package com.kun.cfdaily.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Cloud;
import com.kun.cfdaily.data.LearnDb;
import com.kun.cfdaily.data.Lessons;
import com.kun.cfdaily.data.Lessons.Lesson;
import com.kun.cfdaily.data.Lessons.Track;
import com.kun.cfdaily.data.Lessons.Unit;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Models.Problem;

import java.util.List;
import java.util.Locale;

/** Learn tab: bite-size lessons per topic, then real problems on the same topic. */
public class LearnScreen extends Screen {

    private final LinearLayout units;
    private boolean syncing;

    public LearnScreen(MainActivity a) {
        super(a, R.layout.screen_learn);
        units = root.findViewById(R.id.ln_units);
    }

    @Override
    public String title() {
        return "Learn";
    }

    @Override
    public void refresh() {
        LearnDb ldb = LearnDb.get(a);
        int today = ldb.todayXp();
        ((TextView) root.findViewById(R.id.ln_goal_label)).setText(today >= LearnDb.DAILY_GOAL ? "Daily goal done" : "Daily goal " + LearnDb.DAILY_GOAL + " XP");
        ((TextView) root.findViewById(R.id.ln_goal_label)).setTextColor(today >= LearnDb.DAILY_GOAL ? 0xFF7EE2B4 : a.getColor(R.color.hero_sub));
        ((TextView) root.findViewById(R.id.ln_xp)).setText(ldb.totalXp() + " XP");
        // MainActivity.select fades a page in from alpha 0 and then refreshes it: that's our cue to animate.
        boolean entering = root.getAlpha() < 0.05f;
        RingView ring = root.findViewById(R.id.ln_ring);
        if (entering) {
            ring.set(0);
            ring.animateTo(today / (float) LearnDb.DAILY_GOAL);
        } else {
            ring.set(today / (float) LearnDb.DAILY_GOAL);
        }
        ((TextView) root.findViewById(R.id.ln_today)).setText(Math.min(today, 999) + "/" + LearnDb.DAILY_GOAL);
        ((TextView) root.findViewById(R.id.ln_streak)).setText(String.valueOf(ldb.streak()));
        ((TextView) root.findViewById(R.id.ln_lessons)).setText(String.valueOf(ldb.lessonsDone()));
        ((TextView) root.findViewById(R.id.ln_stars)).setText(String.valueOf(ldb.starsTotal()));

        units.removeAllViews();
        for (Track t : Lessons.tracks(a)) {
            TextView head = new TextView(a, null, 0, R.style.Heading);
            head.setText(t.title);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = Ui.dp(a, 10);
            hp.bottomMargin = Ui.dp(a, 12);
            hp.leftMargin = Ui.dp(a, 4);
            units.addView(head, hp);
            for (Unit u : t.units) units.addView(unitCard(u, ldb));
        }
        if (entering) {
            for (int i = 0; i < units.getChildCount(); i++) {
                View v = units.getChildAt(i);
                v.setAlpha(0);
                v.setTranslationY(Ui.dp(a, 28));
                v.animate().alpha(1).translationY(0).setStartDelay(120 + 70L * i).setDuration(340)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f));
            }
        }
        account();
    }

    /** First lesson of the unit not done yet, or -1 when all are done. */
    private static int nextLesson(Unit u, LearnDb ldb) {
        for (Lesson l : u.lessons) if (ldb.stars(l.id) == 0) return l.index;
        return -1;
    }

    private View unitCard(final Unit u, LearnDb ldb) {
        int fill = Ui.unitFill(a, u.color), ink = Ui.unitInk(a, u.color);
        int done = 0, stars = 0;
        for (Lesson l : u.lessons) {
            int s = ldb.stars(l.id);
            if (s > 0) done++;
            stars += s;
        }

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 18);
        card.setPadding(pad, pad, pad, pad);
        Ui.tint(card, fill, 24, 0xFF);
        card.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(a, R.animator.press));

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = new TextView(a);
        icon.setText(u.icon);
        icon.setTextSize(24);
        icon.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
        icon.setTextColor(0xFFFFFFFF);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ink);
        icon.setBackground(dot);
        top.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52)));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(a);
        title.setText(u.title);
        title.setTextSize(18);
        title.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
        title.setTextColor(a.getColor(R.color.text));
        texts.addView(title);
        TextView sub = new TextView(a);
        sub.setText(u.summary);
        sub.setTextSize(13);
        sub.setTextColor(a.getColor(R.color.text));
        sub.setAlpha(0.7f);
        texts.addView(sub);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = Ui.dp(a, 14);
        top.addView(texts, tp);
        card.addView(top);

        // Progress: bar plus lessons done and stars.
        LinearLayout bottom = new LinearLayout(a);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout bar = new FrameLayout(a);
        Ui.tint(bar, ink, 4, 0x33);
        View part = new View(a);
        Ui.tint(part, ink, 4, 0xFF);
        bar.addView(part, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        final float frac = u.lessons.isEmpty() ? 0 : done / (float) u.lessons.size();
        bar.post(() -> {
            part.getLayoutParams().width = Math.round(bar.getWidth() * frac);
            part.requestLayout();
        });
        bottom.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(a, 8), 1));
        TextView count = new TextView(a);
        boolean all = done == u.lessons.size();
        count.setText(all ? "✓ " + stars + "★" : done + "/" + u.lessons.size());
        count.setTextSize(13);
        count.setTypeface(a.getResources().getFont(R.font.hg_semibold));
        count.setTextColor(ink);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Ui.dp(a, 12);
        bottom.addView(count, cp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(a, 16);
        card.addView(bottom, bp);

        card.setOnClickListener(v -> openUnit(u));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 12);
        card.setLayoutParams(lp);
        return card;
    }

    /** Sheet with the unit's lessons (each unlocks the next) and real problems on the topic. */
    private void openUnit(final Unit u) {
        LearnDb ldb = LearnDb.get(a);
        int ink = Ui.unitInk(a, u.color), fill = Ui.unitFill(a, u.color);
        int next = nextLesson(u, ldb);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);

        for (final Lesson l : u.lessons) {
            int stars = ldb.stars(l.id);
            boolean open = stars > 0 || l.index == next;
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(a, 12);
            row.setPadding(pad, pad, Ui.dp(a, 16), pad);
            Ui.tint(row, l.index == next ? fill : a.getColor(R.color.raised), 18, 0xFF);

            TextView num = new TextView(a);
            num.setText(stars > 0 ? "✓" : open ? String.valueOf(l.index + 1) : "🔒");
            num.setGravity(Gravity.CENTER);
            num.setTextSize(stars > 0 || open ? 16 : 14);
            num.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
            num.setTextColor(open ? 0xFFFFFFFF : a.getColor(R.color.faint));
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(open ? ink : a.getColor(R.color.hairline));
            num.setBackground(dot);
            row.addView(num, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40)));

            LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(a);
            name.setText(l.title);
            name.setTextSize(16);
            name.setTypeface(a.getResources().getFont(R.font.hg_semibold));
            name.setTextColor(a.getColor(open ? R.color.text : R.color.faint));
            texts.addView(name);
            TextView meta = new TextView(a);
            meta.setText(stars > 0 ? stars(stars) : l.steps.length() + " steps");
            meta.setTextSize(13);
            meta.setTextColor(stars > 0 ? a.getColor(R.color.amber) : a.getColor(R.color.faint));
            texts.addView(meta);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tp.leftMargin = Ui.dp(a, 14);
            row.addView(texts, tp);

            if (l.index == next) {
                TextView go = new TextView(a, null, 0, R.style.Chip);
                go.setText("Start");
                go.setBackground(pill(ink));
                go.setTextColor(0xFFFFFFFF);
                row.addView(go);
                breathe(go);
            }
            if (open) {
                row.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(a, R.animator.press));
                row.setOnClickListener(v -> {
                    a.hideSheet();
                    Intent i = new Intent(a, LessonActivity.class).putExtra(LessonActivity.EXTRA_LESSON, l.id);
                    v.postDelayed(() -> a.startActivity(i), 180);
                });
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = Ui.dp(a, 8);
            list.addView(row, rp);
        }

        addPractice(list, u);
        a.showSheet(u.icon + "  " + u.title, list, null, null);
    }

    /** Real unsolved problems with the unit's tag on the current site, easiest first. */
    private void addPractice(LinearLayout list, Unit u) {
        String tag = Mode.lc() ? u.lcTag : u.cfTag;
        if (tag == null || tag.length() == 0) return;
        TextView head = new TextView(a, null, 0, R.style.Heading);
        head.setText("Practice on " + Mode.site());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Ui.dp(a, 14);
        hp.bottomMargin = Ui.dp(a, 8);
        list.addView(head, hp);
        List<Problem> problems = db.trackProblems(tag, 3);
        if (problems.isEmpty()) {
            TextView t = new TextView(a, null, 0, R.style.Body);
            t.setText(db.problemCount() == 0 ? "Sync to get problems here." : "Nothing unsolved with this tag.");
            list.addView(t);
            return;
        }
        for (final Problem p : problems) {
            View v = a.getLayoutInflater().inflate(R.layout.item_step, list, false);
            Ui.badge(v.findViewById(R.id.tv_rating), Ui.levelText(p.rating), Ui.levelColor(p.rating));
            ((TextView) v.findViewById(R.id.tv_left)).setText(p.name);
            v.setOnClickListener(x -> Ui.open(a, p.url()));
            v.setOnLongClickListener(x -> {
                a.hideSheet();
                v.postDelayed(() -> ProblemSheet.show(a, p, ProblemSheet.HINTS_TAB), 220);
                return true;
            });
            list.addView(v);
        }
    }

    /** Slow pulse that says "tap me"; stops when the sheet closes. */
    private static void breathe(final View v) {
        final android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(v,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f));
        pulse.setDuration(700);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                pulse.start();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                pulse.cancel();
            }
        });
    }

    private static String stars(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 3; i++) s.append(i < n ? '★' : '☆');
        return s.toString();
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Ui.dp(a, 100));
        return g;
    }

    // ---------------------------------------------------------------- account and backup

    private void account() {
        View card = root.findViewById(R.id.ln_account);
        if (!Cloud.configured()) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);
        TextView avatar = root.findViewById(R.id.ln_avatar);
        TextView title = root.findViewById(R.id.ln_account_title);
        TextView sub = root.findViewById(R.id.ln_account_sub);
        TextView btn = root.findViewById(R.id.ln_account_btn);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        if (Cloud.signedIn(a)) {
            String name = Cloud.name(a).length() > 0 ? Cloud.name(a) : Cloud.email(a);
            avatar.setText(name.substring(0, 1).toUpperCase(Locale.ROOT));
            avatar.setTextColor(0xFFFFFFFF);
            dot.setColor(a.getColor(R.color.violet));
            title.setText(name);
            long last = Cloud.lastSync(a);
            sub.setText(syncing ? "Backing up…" : last == 0 ? Cloud.email(a) : "Backed up " + Ui.span(System.currentTimeMillis() - last) + " ago");
            btn.setText("Sync");
            btn.setOnClickListener(v -> syncNow());
            card.setOnClickListener(v -> a.showSheet("Account", body(Cloud.email(a)), "Sign out", () -> {
                Cloud.signOut(a);
                a.snack("Signed out");
                refresh();
            }));
        } else {
            avatar.setText("☁");
            avatar.setTextColor(a.getColor(R.color.blue));
            dot.setColor(a.getColor(R.color.p_sky));
            title.setText("Back up your progress");
            sub.setText("Keep XP and lessons on every phone");
            btn.setText("Sign in");
            btn.setOnClickListener(v -> signIn());
            card.setOnClickListener(v -> signIn());
        }
        avatar.setBackground(dot);
    }

    private void syncNow() {
        if (syncing) return;
        syncing = true;
        refresh();
        Cloud.sync(a, error -> {
            syncing = false;
            if (error != null) a.snack(error);
            refresh();
        });
    }

    private TextView body(String text) {
        TextView t = new TextView(a, null, 0, R.style.Body);
        t.setText(text);
        return t;
    }

    private void signIn() {
        LinearLayout form = new LinearLayout(a);
        form.setOrientation(LinearLayout.VERTICAL);

        TextView google = new TextView(a, null, 0, R.style.Chip);
        google.setText("G   Continue with Google");
        google.setGravity(Gravity.CENTER);
        google.setTextSize(15);
        google.setPadding(0, Ui.dp(a, 15), 0, Ui.dp(a, 15));
        google.setOnClickListener(v -> {
            a.hideSheet();
            Ui.open(a, Cloud.googleStartUrl(a).toString());
        });
        form.addView(google, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView or = new TextView(a, null, 0, R.style.Label);
        or.setText("or use email");
        or.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.topMargin = Ui.dp(a, 16);
        op.bottomMargin = Ui.dp(a, 10);
        form.addView(or, op);

        final EditText email = new EditText(a, null, 0, R.style.Input);
        email.setHint("Email");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        form.addView(email, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final EditText password = new EditText(a, null, 0, R.style.Input);
        password.setHint("Password (8+ characters)");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.topMargin = Ui.dp(a, 8);
        form.addView(password, pp);

        LinearLayout row = new LinearLayout(a);
        TextView login = new TextView(a, null, 0, R.style.ChipGhost);
        login.setText("Sign in");
        TextView create = new TextView(a, null, 0, R.style.ChipGhost);
        create.setText("Create account");
        row.addView(login);
        row.addView(create);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Ui.dp(a, 12);
        form.addView(row, rp);

        login.setOnClickListener(v -> emailSignIn(email, password, false));
        create.setOnClickListener(v -> emailSignIn(email, password, true));
        a.showSheet("Back up progress", form, null, null);
    }

    private void emailSignIn(EditText email, EditText password, boolean create) {
        String e = email.getText().toString(), p = password.getText().toString();
        if (e.trim().length() == 0 || p.length() == 0) {
            a.snack("Enter email and password");
            return;
        }
        a.hideSheet();
        syncing = true;
        refresh();
        Cloud.emailSignIn(a, e, p, create, error -> {
            syncing = false;
            a.snack(error != null ? error : create ? "Account created" : "Signed in");
            refresh();
        });
    }

    /** Called by the activity when the browser comes back from Google. */
    void googleReturned(android.net.Uri uri) {
        syncing = true;
        refresh();
        Cloud.googleFinish(a, uri, error -> {
            syncing = false;
            a.snack(error != null ? error : "Signed in as " + Cloud.email(a));
            refresh();
        });
    }
}
