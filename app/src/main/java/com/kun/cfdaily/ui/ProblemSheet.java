package com.kun.cfdaily.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Ai;
import com.kun.cfdaily.data.LcApi;
import com.kun.cfdaily.data.Db;
import com.kun.cfdaily.data.Mode;
import com.kun.cfdaily.data.Models.Problem;
import com.kun.cfdaily.data.Template;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.Callable;

/** Per-problem toolbox: a C++ starter (Codeforces), AI hints and a short editorial (both sites). */
public class ProblemSheet {

    public static final int CODE = 0, HINTS_TAB = 1, EXPLAIN_TAB = 2;
    private static final String TPL = "tpl2_";
    private static final String HINTS = "ai_h_";
    private static final String EXPLAIN = "ai_e2_";
    private static final String[] TAB_NAMES = {"</> Code", "Hints", "Explain"};
    private static final int[] TAB_COLORS = {R.color.violet, R.color.amber, R.color.mint};
    private static final int[] HINT_FILLS = {R.color.p_mint, R.color.p_sky, R.color.p_lilac};
    private static final String[] HINT_NAMES = {"Nudge", "Approach", "Key trick"};

    private static final String HINTS_SYS = "You are a friendly competitive programming coach. Give exactly 3 progressive hints for the problem. "
            + "Hint 1: a small observation that gets the solver started. Hint 2: the approach or technique. Hint 3: the key trick that makes it work. "
            + "Each hint is at most 2 short sentences. Never write code and never state the whole solution. Plain text, no LaTeX. "
            + "Reply as JSON: {\"hints\":[\"...\",\"...\",\"...\"]}";
    private static final String EXPLAIN_SYS = "You are a competitive programming editorialist. Explain the intended solution in at most 5 short steps, "
            + "one sentence each, so someone who got stuck can solve it. Then give the time complexity, and the one key insight as a single sentence "
            + "someone could remember for similar problems. No code. Plain text, no LaTeX. "
            + "Reply as JSON: {\"steps\":[\"...\"],\"complexity\":\"O(...)\",\"trick\":\"...\"}";

    private final MainActivity a;
    private final Problem p;
    private final Db db;
    private final FrameLayout page;
    /** Tab kinds shown for this site: Codeforces has the template, LeetCode already gives its own starter. */
    private final int[] kinds;
    private final TextView[] tabs;
    private final Handler main = new Handler(Looper.getMainLooper());

    public static void show(MainActivity a, Problem p, int tab) {
        new ProblemSheet(a, p).open(tab);
    }

    static void bindButton(TextView button, final MainActivity a, final Problem p) {
        boolean lc = Mode.lc();
        button.setText(lc ? "✦" : "</>");
        button.setOnClickListener(v -> show(a, p, lc ? HINTS_TAB : CODE));
    }

    private ProblemSheet(MainActivity a, Problem p) {
        this.a = a;
        this.p = p;
        this.db = Db.get(a);
        this.page = new FrameLayout(a);
        this.kinds = Mode.lc() ? new int[]{HINTS_TAB, EXPLAIN_TAB} : new int[]{CODE, HINTS_TAB, EXPLAIN_TAB};
        this.tabs = new TextView[kinds.length];
    }

    private void open(int tab) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView strip = new HorizontalScrollView(a);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        for (int i = 0; i < kinds.length; i++) {
            final int kind = kinds[i];
            TextView t = (TextView) a.getLayoutInflater().inflate(R.layout.chip, row, false);
            t.setText(TAB_NAMES[kind]);
            if (kind == CODE) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            t.setPadding(Ui.dp(a, 16), t.getPaddingTop(), Ui.dp(a, 16), t.getPaddingBottom());
            t.setOnClickListener(v -> select(kind));
            tabs[i] = t;
            row.addView(t);
        }
        strip.addView(row);
        box.addView(strip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 14);
        box.addView(page, lp);
        a.showSheet(p.id() + "  " + p.name, box, null, null);
        select(Mode.lc() && tab == CODE ? HINTS_TAB : tab);
    }

    private void select(int tab) {
        for (int i = 0; i < tabs.length; i++) {
            int color = a.getColor(TAB_COLORS[kinds[i]]);
            if (kinds[i] == tab) {
                Ui.tint(tabs[i], color, 22, 0xFF);
                tabs[i].setTextColor(a.getColor(R.color.on_primary));
            } else {
                Ui.tint(tabs[i], color, 22, Ui.dark ? 0x30 : 0x22);
                tabs[i].setTextColor(color);
            }
        }
        page.removeAllViews();
        if (tab == CODE) code();
        else if (tab == HINTS_TAB) hints(false);
        else explain(false);
    }

    // ------------------------------------------------------------------ small views

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private TextView badge(String s, int color) {
        TextView t = text("", 13, color);
        t.setTypeface(a.getResources().getFont(R.font.hg_semibold));
        int pad = Ui.dp(a, 12);
        t.setPadding(pad, Ui.dp(a, 7), pad, Ui.dp(a, 7));
        Ui.badge(t, s, color);
        return t;
    }

    private TextView chip(String s, boolean primary, View.OnClickListener click) {
        TextView t = (TextView) a.getLayoutInflater().inflate(R.layout.chip, null, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(a, 8);
        t.setLayoutParams(lp);
        t.setText(s);
        t.setBackgroundResource(primary ? R.drawable.bg_chip : R.drawable.bg_chip_ghost);
        t.setTextColor(a.getColor(primary ? R.color.on_primary : R.color.text));
        t.setOnClickListener(click);
        return t;
    }

    private View codeBox(String code) {
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        TextView t = text(code, 12.5f, a.getColor(R.color.text));
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        scroll.addView(t);
        Ui.tint(scroll, a.getColor(R.color.raised), 18, 0xFF);
        return scroll;
    }

    private static LinearLayout.LayoutParams gap(Context c, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, top);
        return lp;
    }

    private static LinearLayout.LayoutParams wrapGap(Context c, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, top);
        return lp;
    }

    private void loading(String s) {
        page.removeAllViews();
        page.addView(badge(s, a.getColor(R.color.blue)));
    }

    private void failed(String message, Runnable retry) {
        page.removeAllViews();
        LinearLayout col = column();
        col.addView(badge("⚠  " + message, a.getColor(R.color.coral)), wrapGap(a, 0));
        if (retry != null) col.addView(chip("Try again", false, v -> retry.run()), wrapGap(a, 12));
        page.addView(col);
    }

    /** Asks for a key first; true when the AI can be used. */
    private boolean needKey() {
        if (Ai.ready(a)) return false;
        page.removeAllViews();
        LinearLayout card = column();
        Ui.tint(card, a.getColor(R.color.p_butter), 22, 0xFF);
        int pad = Ui.dp(a, 18);
        card.setPadding(pad, pad, pad, pad);
        TextView title = text("Add a free Groq key", 18, a.getColor(R.color.text));
        title.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
        card.addView(title);
        card.addView(text("Takes a minute at console.groq.com. Paste it in Profile.", 14, a.getColor(R.color.text)), gap(a, 4));
        LinearLayout row = new LinearLayout(a);
        TextView get = chip("Get key", true, v -> Ui.open(a, "https://console.groq.com/keys"));
        TextView go = chip("Profile", false, v -> {
            a.hideSheet();
            a.select(MainActivity.TAB_PROFILE);
        });
        go.setBackgroundResource(R.drawable.bg_chip_soft);
        row.addView(get);
        row.addView(go);
        card.addView(row, gap(a, 14));
        page.addView(card);
        return true;
    }

    /** Runs work off the main thread; results land only while this sheet is still showing. */
    private <T> void bg(Callable<T> work, java.util.function.Consumer<T> done, java.util.function.Consumer<String> fail) {
        final View anchor = page;
        new Thread(() -> {
            T result = null;
            String error = null;
            try {
                result = work.call();
            } catch (Ai.AiException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "Something went wrong. Try again.";
            }
            final T r = result;
            final String err = error;
            main.post(() -> {
                if (!anchor.isAttachedToWindow()) return;
                if (err != null) fail.accept(err);
                else done.accept(r);
            });
        }).start();
    }

    private String statementOrThrow() throws Ai.AiException {
        String s = Mode.lc() ? lcStatement(p.pkey) : Template.statement(p.contestId, p.index);
        if (s == null || s.trim().length() == 0) throw new Ai.AiException("Couldn't load the statement.");
        String level = Mode.lc() ? " (LeetCode " + Ui.levelText(p.rating).replace("Med", "Medium") + ")" : p.rating > 0 ? " (Codeforces rating " + p.rating + ")" : "";
        return "Problem " + p.id() + ": " + p.name + level + "\n\n" + s;
    }

    /** LeetCode's statement from its public GraphQL, as plain text. */
    private static String lcStatement(String slug) {
        try {
            JSONObject q = LcApi.query("query($s:String!){ question(titleSlug:$s){ content } }", new JSONObject().put("s", slug), false).optJSONObject("question");
            if (q == null) return null;
            String html = q.optString("content", "");
            String t = html.replaceAll("(?i)<sup>(.*?)</sup>", "^$1").replaceAll("(?i)</p>|<br\\s*/?>|</li>|</pre>", "\n").replaceAll("(?i)<li>", "- ")
                    .replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .replace("&#39;", "'").replace("&amp;", "&").replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n(\\s*\\n)+", "\n\n").trim();
            return t.length() > 12000 ? t.substring(0, 12000) : t;
        } catch (Exception e) {
            return null;
        }
    }

    /** "What's the trick?" card for the flashcard deck. */
    private void saveCard(String trick) {
        if (trick == null || trick.trim().length() < 8) return;
        db.putCard(p.pkey, trick.trim(), "ai");
    }

    /** LaTeX bits and markdown marks that open models still slip in. */
    static String tidy(String s) {
        return s.replace("\\le", "≤").replace("\\ge", "≥").replace("\\ne", "≠").replace("\\cdot", "·").replace("\\times", "×")
                .replace("\\oplus", "⊕").replace("\\sum", "Σ").replace("\\lfloor", "⌊").replace("\\rfloor", "⌋").replace("\\log", "log")
                .replace("\\{", "{").replace("\\}", "}").replace("\\dots", "…").replace("\\ldots", "…").replace("$", "")
                .replace("**", "").replaceAll("(?m)^#+\\s*", "").trim();
    }

    // ------------------------------------------------------------------ Code

    private static String header(Context c) {
        return c.getSharedPreferences("ui", 0).getString("tpl_header", "");
    }

    private void code() {
        final String head = header(a);
        final String key = TPL + p.pkey + "_" + Integer.toHexString(head.hashCode());
        String cached = db.kv(key, "");
        if (cached.length() > 2) {
            showCode(cached.charAt(0) == '1', cached.substring(2), null);
            return;
        }
        loading("Reading the statement…");
        bg(() -> Template.build(p.contestId, p.index, p.tags, head), r -> {
            if (r.note == null || !r.note.startsWith("Couldn't load")) db.setKv(key, (r.checked ? "1" : "0") + "|" + r.code);
            showCode(r.checked, r.code, r.note);
        }, err -> failed(err, this::code));
    }

    private void showCode(boolean checked, final String code, String note) {
        page.removeAllViews();
        LinearLayout col = column();
        int good = a.getColor(R.color.good), warn = a.getColor(R.color.amber);
        col.addView(checked ? badge("✓  Reads the sample input", good)
                : badge("⚠  " + (note != null && note.length() > 0 ? note : "Check the reading part"), warn), wrapGap(a, 0));
        col.addView(codeBox(code), gap(a, 12));
        LinearLayout row = new LinearLayout(a);
        row.addView(chip("Copy", true, v -> {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(p.id(), code));
            a.snack("Template copied");
        }));
        row.addView(chip("Open problem", false, v -> Ui.open(a, p.url())));
        col.addView(row, gap(a, 14));
        page.addView(col);
    }

    // ------------------------------------------------------------------ Hints

    private void hints(boolean fresh) {
        if (needKey()) return;
        String cached = fresh ? "" : db.kv(HINTS + p.pkey, "");
        if (cached.length() > 0) {
            try {
                showHints(new JSONArray(cached));
                return;
            } catch (Exception ignored) {
            }
        }
        loading("Thinking of hints…");
        bg(() -> {
            JSONArray h = Ai.json(Ai.ask(a, HINTS_SYS, statementOrThrow(), true)).getJSONArray("hints");
            if (h.length() == 0) throw new Ai.AiException("No hints came back. Try again.");
            return h;
        }, h -> {
            db.setKv(HINTS + p.pkey, h.toString());
            showHints(h);
        }, err -> failed(err, () -> hints(true)));
    }

    private void showHints(JSONArray h) {
        page.removeAllViews();
        LinearLayout col = column();
        for (int i = 0; i < Math.min(3, h.length()); i++) {
            final LinearLayout card = column();
            Ui.tint(card, a.getColor(HINT_FILLS[i]), 22, 0xFF);
            int pad = Ui.dp(a, 18);
            card.setPadding(pad, pad, pad, pad);
            TextView title = text((i + 1) + "  ·  " + HINT_NAMES[i], 13, a.getColor(R.color.text));
            title.setTypeface(a.getResources().getFont(R.font.hg_semibold));
            title.setAlpha(0.7f);
            card.addView(title);
            final TextView body = text("Tap to reveal", 16, a.getColor(R.color.text));
            body.setTypeface(a.getResources().getFont(R.font.hg_semibold));
            final String hint = tidy(h.optString(i));
            card.addView(body, gap(a, 6));
            final boolean last = i == Math.min(3, h.length()) - 1;
            card.setOnClickListener(v -> {
                if (last) saveCard(hint);
                body.setText(hint);
                body.setTypeface(a.getResources().getFont(R.font.hg_regular));
                body.setAlpha(0);
                body.animate().alpha(1).setDuration(200);
                card.setOnClickListener(null);
            });
            col.addView(card, gap(a, i == 0 ? 0 : 10));
        }
        LinearLayout row = new LinearLayout(a);
        row.addView(chip("New hints", false, v -> hints(true)));
        col.addView(row, gap(a, 14));
        page.addView(col);
    }

    // ------------------------------------------------------------------ Explain

    private void explain(boolean go) {
        if (needKey()) return;
        String cached = db.kv(EXPLAIN + p.pkey, "");
        if (cached.length() > 0) {
            try {
                showExplain(new JSONObject(cached));
                return;
            } catch (Exception ignored) {
            }
        }
        if (!go) {
            page.removeAllViews();
            LinearLayout col = column();
            LinearLayout card = column();
            Ui.tint(card, a.getColor(R.color.p_mint), 22, 0xFF);
            int pad = Ui.dp(a, 18);
            card.setPadding(pad, pad, pad, pad);
            TextView t = text("Spoiler: the full idea in 5 lines", 17, a.getColor(R.color.text));
            t.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
            card.addView(t);
            card.addView(text("Best after you've tried. Hints come first.", 14, a.getColor(R.color.text)), gap(a, 4));
            LinearLayout row = new LinearLayout(a);
            row.addView(chip("Show solution", true, v -> explain(true)));
            card.addView(row, gap(a, 14));
            col.addView(card);
            page.addView(col);
            return;
        }
        loading("Writing a short editorial…");
        bg(() -> {
            JSONObject o = Ai.json(Ai.ask(a, EXPLAIN_SYS, statementOrThrow(), true));
            if (o.optJSONArray("steps") == null) throw new Ai.AiException("Groq's answer was cut off. Try again.");
            return o;
        }, o -> {
            db.setKv(EXPLAIN + p.pkey, o.toString());
            showExplain(o);
        }, err -> failed(err, () -> explain(true)));
    }

    private void showExplain(JSONObject o) {
        page.removeAllViews();
        LinearLayout col = column();
        JSONArray steps = o.optJSONArray("steps");
        int green = a.getColor(R.color.mint);
        for (int i = 0; steps != null && i < Math.min(6, steps.length()); i++) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.TOP);
            TextView n = text(String.valueOf(i + 1), 14, a.getColor(R.color.on_primary));
            n.setTypeface(a.getResources().getFont(R.font.hg_extrabold));
            n.setGravity(Gravity.CENTER);
            Ui.tint(n, green, 14, 0xFF);
            int size = Ui.dp(a, 28);
            row.addView(n, new LinearLayout.LayoutParams(size, size));
            TextView body = text(tidy(steps.optString(i)), 15, a.getColor(R.color.text));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = Ui.dp(a, 12);
            row.addView(body, lp);
            col.addView(row, gap(a, i == 0 ? 0 : 12));
        }
        String trick = tidy(o.optString("trick", ""));
        if (trick.length() > 0) {
            saveCard(trick);
            TextView t = text("★  " + trick, 15, a.getColor(R.color.text));
            int pad = Ui.dp(a, 14);
            t.setPadding(pad, pad, pad, pad);
            Ui.tint(t, a.getColor(R.color.p_butter), 18, 0xFF);
            col.addView(t, gap(a, 16));
        }
        String cx = tidy(o.optString("complexity", ""));
        if (cx.length() > 0) col.addView(badge("⏱  " + cx, a.getColor(R.color.blue)), wrapGap(a, 16));
        col.addView(text("AI summary. It can be wrong; the official editorial is on the contest page.", 12, a.getColor(R.color.faint)), gap(a, 12));
        page.addView(col);
    }
}
