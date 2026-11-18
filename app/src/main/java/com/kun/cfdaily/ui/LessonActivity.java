package com.kun.cfdaily.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Cloud;
import com.kun.cfdaily.data.LearnDb;
import com.kun.cfdaily.data.Lessons;
import com.kun.cfdaily.data.Lessons.Lesson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plays one lesson. Steps: idea cards, animated traces, and questions (choice, blank, order, match, type).
 * A wrong answer comes back at the end until it's right. Sounds, read-aloud, combos and confetti on top.
 */
public class LessonActivity extends Activity {

    public static final String EXTRA_LESSON = "lesson";

    private Lesson lesson;
    private final List<JSONObject> queue = new ArrayList<>();
    private int pos;
    private int done;
    private int mistakes;
    private int questions;
    private int combo;
    private int bestCombo;
    private boolean checked;
    private boolean finished;

    private FrameLayout container;
    private Confetti confetti;
    private View progressFill;
    private FrameLayout progressTrack;
    private TextView comboView;
    private TextView soundView;
    private ScrollView scroll;
    private LinearLayout body;
    private LinearLayout feedback;
    private TextView action;
    private int ink;
    private int fill;

    // Choice and blank.
    private final List<String> options = new ArrayList<>();
    private final List<TextView> optionViews = new ArrayList<>();
    private int correct;
    private int selected = -1;
    // Order.
    private final List<String> picked = new ArrayList<>();
    private final List<String> bank = new ArrayList<>();
    private LinearLayout answerArea;
    private FlowLayout bankArea;
    // Type.
    private EditText input;
    // Trace.
    private TraceView trace;
    private TextView traceNote;
    private TextView traceCounter;
    private List<String> traceValues;
    private int frame;
    // Match.
    private TextView[] leftTiles;
    private TextView[] rightTiles;
    private int pickLeft = -1;
    private int pickRight = -1;
    private int matched;
    private int matchMistakes;
    private boolean matchBusy;

    // Read aloud.
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSpeech;

    @Override
    protected void attachBaseContext(Context base) {
        boolean dark = base.getSharedPreferences("ui", 0).getBoolean("dark", false);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.dark = getSharedPreferences("ui", 0).getBoolean("dark", false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        lesson = Lessons.lesson(this, getIntent().getStringExtra(EXTRA_LESSON));
        if (lesson == null) {
            finish();
            return;
        }
        Sfx.init(this);
        ink = Ui.unitInk(this, lesson.unit.color);
        fill = Ui.unitFill(this, lesson.unit.color);
        for (int i = 0; i < lesson.steps.length(); i++) {
            JSONObject s = lesson.steps.optJSONObject(i);
            if (s != null) queue.add(s);
        }
        buildFrame();
        render();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (tts != null) tts.stop();
        super.onPause();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private LinearLayout.LayoutParams wide(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMargin);
        return lp;
    }

    private LinearLayout.LayoutParams wrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMargin);
        return lp;
    }

    // ---------------------------------------------------------------- frame

    private void buildFrame() {
        container = new FrameLayout(this);
        container.setBackgroundColor(getColor(R.color.bg));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(12), dp(10), dp(4));
        TextView close = iconButton("✕", "Close lesson");
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        progressTrack = new FrameLayout(this);
        Ui.tint(progressTrack, getColor(R.color.hairline), 8, 0xFF);
        progressFill = new View(this);
        Ui.tint(progressFill, ink, 8, 0xFF);
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(14), 1);
        pp.leftMargin = dp(6);
        top.addView(progressTrack, pp);
        comboView = new TextView(this);
        comboView.setTypeface(getResources().getFont(R.font.hg_extrabold));
        comboView.setTextSize(15);
        comboView.setTextColor(getColor(R.color.amber));
        comboView.setVisibility(View.GONE);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = dp(10);
        top.addView(comboView, cp);
        soundView = iconButton(Sfx.enabled(this) ? "🔊" : "🔇", "Sound on or off");
        soundView.setTextSize(17);
        soundView.setOnClickListener(v -> {
            boolean on = !Sfx.enabled(this);
            Sfx.setEnabled(this, on);
            soundView.setText(on ? "🔊" : "🔇");
            if (on) Sfx.play(this, Sfx.TAP);
        });
        top.addView(soundView, new LinearLayout.LayoutParams(dp(44), dp(44)));
        column.addView(top);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(16), dp(22), dp(24));
        scroll.addView(body);
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(16));
        feedback = new LinearLayout(this);
        feedback.setOrientation(LinearLayout.VERTICAL);
        feedback.setPadding(dp(18), dp(16), dp(18), dp(16));
        feedback.setVisibility(View.GONE);
        LinearLayout.LayoutParams fp = wide(0);
        fp.bottomMargin = dp(10);
        bottom.addView(feedback, fp);
        action = new TextView(this, null, 0, R.style.Chip);
        action.setGravity(Gravity.CENTER);
        action.setTextSize(16);
        action.setPadding(0, dp(16), 0, dp(16));
        action.setOnClickListener(v -> onAction());
        bottom.addView(action, wide(0));
        column.addView(bottom);

        container.addView(column);
        confetti = new Confetti(this);
        container.addView(confetti, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(container);
    }

    private TextView iconButton(String glyph, String description) {
        TextView t = new TextView(this);
        t.setText(glyph);
        t.setTextSize(20);
        t.setTextColor(getColor(R.color.subtext));
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        return t;
    }

    private void setProgress() {
        final float frac = finished ? 1f : done / (float) Math.max(1, done + queue.size() - pos);
        progressTrack.post(() -> {
            ValueAnimator anim = ValueAnimator.ofInt(progressFill.getWidth(), Math.round(progressTrack.getWidth() * frac));
            anim.setDuration(320);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(x -> {
                progressFill.getLayoutParams().width = (int) x.getAnimatedValue();
                progressFill.requestLayout();
            });
            anim.start();
        });
    }

    private void setAction(String text, boolean enabled) {
        action.setText(text);
        action.setEnabled(enabled);
        action.animate().alpha(enabled ? 1f : 0.35f).setDuration(150);
    }

    /** 🔥 counter for right-first-time answers in a row; the bar turns amber while it's hot. */
    private void showCombo() {
        boolean hot = combo >= 3;
        Ui.tint(progressFill, hot ? getColor(R.color.amber) : ink, 8, 0xFF);
        if (combo < 2) {
            if (comboView.getVisibility() == View.VISIBLE) {
                comboView.animate().scaleX(0).scaleY(0).setDuration(180).withEndAction(() -> comboView.setVisibility(View.GONE));
            }
            return;
        }
        comboView.setText("🔥" + combo);
        comboView.setVisibility(View.VISIBLE);
        comboView.setScaleX(1.6f);
        comboView.setScaleY(1.6f);
        comboView.animate().scaleX(1).scaleY(1).setDuration(380).setInterpolator(new OvershootInterpolator(3));
    }

    // ---------------------------------------------------------------- read aloud

    private void speak(String text) {
        String clean = text.replace("²", " squared").replace("≈", " about ").replace("·", " times ")
                .replace("[", " ").replace("]", " ").replace("--", " minus minus").replace("++", " plus plus");
        if (tts == null) {
            pendingSpeech = clean;
            tts = new TextToSpeech(this, status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (!ttsReady) {
                    toast("Text-to-speech isn't available");
                    return;
                }
                int lang = tts.setLanguage(Locale.getDefault());
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US);
                tts.setSpeechRate(1.02f);
                if (pendingSpeech != null) tts.speak(pendingSpeech, TextToSpeech.QUEUE_FLUSH, null, "lesson");
                pendingSpeech = null;
            });
            return;
        }
        if (ttsReady) tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "lesson");
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private View listenChip(final String text) {
        TextView t = new TextView(this);
        t.setText("🔊  Listen");
        t.setTextSize(13);
        t.setTypeface(getResources().getFont(R.font.hg_semibold));
        t.setTextColor(getColor(R.color.text));
        t.setPadding(dp(12), dp(7), dp(14), dp(7));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(100));
        g.setStroke(dp(1.5f), getColor(R.color.hairline));
        t.setBackground(g);
        t.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.press));
        t.setOnClickListener(v -> {
            Sfx.play(this, Sfx.TAP);
            speak(text);
        });
        return t;
    }

    // ---------------------------------------------------------------- steps

    private void render() {
        JSONObject step = queue.get(pos);
        checked = false;
        selected = -1;
        if (tts != null) tts.stop();
        body.removeAllViews();
        body.setGravity(Gravity.START);
        feedback.setVisibility(View.GONE);
        scroll.scrollTo(0, 0);
        setProgress();
        switch (step.optString("type")) {
            case "card":
                renderCard(step);
                break;
            case "trace":
                renderTrace(step);
                break;
            case "order":
                renderOrder(step);
                break;
            case "match":
                renderMatch(step);
                break;
            case "type":
                renderType(step);
                break;
            default:
                renderChoice(step, step.optString("type").equals("blank"));
        }
        body.setAlpha(0);
        body.setTranslationX(dp(32));
        body.animate().alpha(1).translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f));
    }

    private TextView text(String s, float size, int color, int font) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.2f);
        t.setTypeface(getResources().getFont(font));
        return t;
    }

    private void tag(String label) {
        TextView tag = text(lesson.unit.icon + "  " + label, 12, ink, R.font.hg_semibold);
        tag.setPadding(dp(10), dp(5), dp(10), dp(5));
        Ui.tint(tag, fill, 100, 0xFF);
        body.addView(tag, wrap(0));
    }

    private void prompt(JSONObject step) {
        body.addView(text(step.optString("prompt"), 21, getColor(R.color.text), R.font.hg_extrabold), wide(14));
        String code = step.optString("code", "");
        if (code.length() > 0) body.addView(Code.block(this, code), wide(16));
    }

    /** Children fade and rise in one after another. */
    private void stagger(List<? extends View> views, int startDelay) {
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            v.setAlpha(0);
            v.setTranslationY(dp(18));
            v.animate().alpha(1).translationY(0).setStartDelay(startDelay + 55L * i).setDuration(260).setInterpolator(new DecelerateInterpolator());
        }
    }

    private void renderCard(JSONObject step) {
        tag("New idea");
        String title = step.optString("title"), text = step.optString("body");
        body.addView(text(title, 26, getColor(R.color.text), R.font.hg_extrabold), wide(14));
        body.addView(text(text, 16.5f, getColor(R.color.subtext), R.font.hg_regular), wide(10));
        String code = step.optString("code", "");
        if (code.length() > 0) body.addView(Code.block(this, code), wide(18));
        body.addView(listenChip(title + ". " + text), wrap(18));
        setAction("Continue", true);
    }

    // ---- trace

    private void renderTrace(JSONObject step) {
        tag("Watch it run");
        body.addView(text(step.optString("title"), 22, getColor(R.color.text), R.font.hg_extrabold), wide(14));
        JSONArray frames = step.optJSONArray("frames");
        List<String> names = new ArrayList<>();
        for (int i = 0; frames != null && i < frames.length(); i++) {
            Iterator<String> keys = frames.optJSONObject(i).keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (!k.equals("array") && !k.equals("range") && !k.equals("note") && !names.contains(k)) names.add(k);
            }
        }
        traceValues = strings(step.optJSONArray("array"));
        trace = new TraceView(this, ink, names);
        body.addView(trace, wide(22));
        traceNote = text("", 16, getColor(R.color.text), R.font.hg_semibold);
        traceNote.setPadding(dp(16), dp(14), dp(16), dp(14));
        Ui.tint(traceNote, fill, 18, 0xFF);
        traceNote.setMinLines(2);
        body.addView(traceNote, wide(14));
        traceCounter = text("", 12, getColor(R.color.faint), R.font.hg_semibold);
        body.addView(traceCounter, wrap(8));
        frame = 0;
        showFrame(step, false);
    }

    private static List<String> strings(JSONArray arr) {
        List<String> out = new ArrayList<>();
        for (int i = 0; arr != null && i < arr.length(); i++) out.add(arr.optString(i));
        return out;
    }

    private void showFrame(JSONObject step, boolean animate) {
        JSONArray frames = step.optJSONArray("frames");
        JSONObject f = frames.optJSONObject(frame);
        if (f.has("array")) traceValues = strings(f.optJSONArray("array"));
        Map<String, Integer> pointers = new HashMap<>();
        Iterator<String> keys = f.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            if (!k.equals("array") && !k.equals("range") && !k.equals("note")) pointers.put(k, f.optInt(k));
        }
        JSONArray r = f.optJSONArray("range");
        int[] range = r == null ? null : new int[]{r.optInt(0), r.optInt(1)};
        trace.show(traceValues, pointers, range, animate);
        final String note = f.optString("note");
        if (animate) {
            traceNote.animate().alpha(0).setDuration(90).withEndAction(() -> {
                traceNote.setText(note);
                traceNote.animate().alpha(1).setDuration(160);
            });
            Sfx.play(this, Sfx.STEP, 1f + 0.06f * frame);
        } else {
            traceNote.setText(note);
        }
        boolean last = frame == frames.length() - 1;
        traceCounter.setText("Step " + (frame + 1) + " of " + frames.length());
        setAction(last ? "Continue" : "Next step  ▶", true);
    }

    // ---- choice and blank

    private void renderChoice(JSONObject step, boolean blank) {
        tag(blank ? "Fill the blank" : "Quick check");
        prompt(step);
        // Shuffled every time; the file always says which one is right.
        JSONArray raw = step.optJSONArray("options");
        int answer = step.optInt("answer");
        List<Integer> order = new ArrayList<>();
        for (int i = 0; raw != null && i < raw.length(); i++) order.add(i);
        Collections.shuffle(order);
        options.clear();
        optionViews.clear();
        for (int i = 0; i < order.size(); i++) {
            options.add(raw.optString(order.get(i)));
            if (order.get(i) == answer) correct = i;
        }
        boolean code = blank || looksLikeCode(options);
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            TextView o = text(options.get(i), 16, getColor(R.color.text), R.font.hg_semibold);
            if (code) o.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            o.setPadding(dp(18), dp(16), dp(18), dp(16));
            o.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.press));
            o.setOnClickListener(v -> {
                if (checked) return;
                selected = index;
                Sfx.play(this, Sfx.TAP);
                styleOptions();
                setAction("Check", true);
            });
            optionViews.add(o);
            body.addView(o, wide(i == 0 ? 20 : 10));
        }
        styleOptions();
        stagger(optionViews, 120);
        setAction("Check", false);
    }

    /** r-- and l++ read better in a code font. */
    private static boolean looksLikeCode(List<String> opts) {
        for (String o : opts) if (o.contains(" ") || o.length() > 8) return false;
        return true;
    }

    private GradientDrawable box(int fillColor, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(2), strokeColor);
        return g;
    }

    private void styleOptions() {
        int good = getColor(R.color.mint), bad = getColor(R.color.coral);
        for (int i = 0; i < optionViews.size(); i++) {
            TextView o = optionViews.get(i);
            if (checked && i == correct) o.setBackground(box(getColor(R.color.p_mint), good));
            else if (checked && i == selected) o.setBackground(box(getColor(R.color.p_peach), bad));
            else if (i == selected) o.setBackground(box(fill, ink));
            else o.setBackground(box(getColor(R.color.card), getColor(R.color.hairline)));
            if (checked) o.animate().alpha(i != correct && i != selected ? 0.45f : 1f).setStartDelay(0).setDuration(200);
        }
    }

    // ---- order

    private void renderOrder(JSONObject step) {
        tag("Build the code");
        body.addView(text(step.optString("prompt"), 21, getColor(R.color.text), R.font.hg_extrabold), wide(14));
        List<String> lines = lines(step);
        picked.clear();
        bank.clear();
        bank.addAll(lines);
        if (lines.size() > 1) while (bank.equals(lines)) Collections.shuffle(bank);

        answerArea = new LinearLayout(this);
        answerArea.setOrientation(LinearLayout.VERTICAL);
        answerArea.setPadding(dp(10), dp(10), dp(10), dp(10));
        answerArea.setMinimumHeight(dp(64));
        Ui.tint(answerArea, getColor(R.color.raised), 16, 0xFF);
        body.addView(answerArea, wide(18));
        bankArea = new FlowLayout(this);
        body.addView(bankArea, wide(16));
        drawOrder();
        List<View> chips = new ArrayList<>();
        for (int i = 0; i < bankArea.getChildCount(); i++) chips.add(bankArea.getChildAt(i));
        stagger(chips, 120);
        setAction("Check", false);
    }

    private static List<String> lines(JSONObject step) {
        List<String> out = new ArrayList<>();
        JSONArray arr = step.optJSONArray("lines");
        for (int i = 0; arr != null && i < arr.length(); i++) out.add(arr.optString(i).trim());
        return out;
    }

    private TextView codeChip(String line) {
        TextView t = new TextView(this);
        t.setText(Code.highlight(this, line));
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(13.5f);
        t.setTextColor(getColor(R.color.text));
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.press));
        return t;
    }

    private void drawOrder() {
        answerArea.removeAllViews();
        if (picked.isEmpty()) {
            TextView hint = text("Tap the lines in order", 14, getColor(R.color.faint), R.font.hg_regular);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, dp(12), 0, dp(12));
            answerArea.addView(hint, wide(0));
        }
        for (int i = 0; i < picked.size(); i++) {
            final int index = i;
            TextView t = codeChip(picked.get(i));
            t.setBackground(box(getColor(R.color.card), ink));
            t.setOnClickListener(v -> {
                if (checked) return;
                Sfx.play(this, Sfx.TAP, 0.9f);
                bank.add(picked.remove(index));
                drawOrder();
            });
            answerArea.addView(t, wide(i == 0 ? 0 : 6));
        }
        bankArea.removeAllViews();
        for (int i = 0; i < bank.size(); i++) {
            final int index = i;
            TextView t = codeChip(bank.get(i));
            t.setBackground(box(getColor(R.color.card), getColor(R.color.hairline)));
            t.setOnClickListener(v -> {
                if (checked) return;
                Sfx.play(this, Sfx.TAP, 1f + 0.05f * picked.size());
                picked.add(bank.remove(index));
                drawOrder();
            });
            bankArea.addView(t, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        setAction("Check", bank.isEmpty());
    }

    // ---- type the answer

    private void renderType(JSONObject step) {
        tag("Type the answer");
        prompt(step);
        input = new EditText(this, null, 0, R.style.Input);
        input.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        input.setTextSize(20);
        input.setHint("Your answer");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, id, e) -> {
            if (action.isEnabled()) onAction();
            return true;
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                if (!checked) setAction("Check", s.toString().trim().length() > 0);
            }
        });
        body.addView(input, wide(20));
        setAction("Check", false);
        input.postDelayed(() -> {
            input.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input, 0);
        }, 350);
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replaceAll("\\s*([,\\[\\]()])\\s*", "$1");
    }

    private boolean typedRight(JSONObject step) {
        String got = normalize(input.getText().toString());
        if (got.equals(normalize(step.optString("answer")))) return true;
        JSONArray accept = step.optJSONArray("accept");
        for (int i = 0; accept != null && i < accept.length(); i++) if (got.equals(normalize(accept.optString(i)))) return true;
        return false;
    }

    // ---- match the pairs

    private void renderMatch(JSONObject step) {
        tag("Match the pairs");
        body.addView(text(step.optString("prompt"), 21, getColor(R.color.text), R.font.hg_extrabold), wide(14));
        JSONArray pairs = step.optJSONArray("pairs");
        int n = pairs == null ? 0 : pairs.length();
        leftTiles = new TextView[n];
        rightTiles = new TextView[n];
        pickLeft = pickRight = -1;
        matched = matchMistakes = 0;
        matchBusy = false;
        List<Integer> leftOrder = new ArrayList<>(), rightOrder = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            leftOrder.add(i);
            rightOrder.add(i);
        }
        Collections.shuffle(leftOrder);
        Collections.shuffle(rightOrder);

        LinearLayout columns = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this), right = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        right.setOrientation(LinearLayout.VERTICAL);
        List<View> all = new ArrayList<>();
        for (int row = 0; row < n; row++) {
            int li = leftOrder.get(row), ri = rightOrder.get(row);
            leftTiles[li] = matchTile(pairs.optJSONArray(li).optString(0), li, true);
            rightTiles[ri] = matchTile(pairs.optJSONArray(ri).optString(1), ri, false);
            left.addView(leftTiles[li], tileParams(row));
            right.addView(rightTiles[ri], tileParams(row));
            all.add(leftTiles[li]);
            all.add(rightTiles[ri]);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = dp(10);
        columns.addView(left, lp);
        columns.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        body.addView(columns, wide(20));
        styleTiles();
        stagger(all, 100);
        setAction("Match them all", false);
    }

    private LinearLayout.LayoutParams tileParams(int row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        lp.topMargin = row == 0 ? 0 : dp(10);
        return lp;
    }

    private TextView matchTile(String label, final int pair, final boolean isLeft) {
        TextView t = text(label, 14.5f, getColor(R.color.text), R.font.hg_semibold);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(6), dp(8), dp(6));
        t.setMaxLines(3);
        // Code-looking labels (p[0], hi = mid, O(n)) in the code font.
        boolean code = label.contains("[") || label.contains(" = ") || label.contains("++") || label.matches("O\\(.*\\)");
        if (code) t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        t.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.press));
        t.setOnClickListener(v -> {
            if (matchBusy || checked || !v.isEnabled()) return;
            Sfx.play(this, Sfx.TAP);
            if (isLeft) pickLeft = pickLeft == pair ? -1 : pair;
            else pickRight = pickRight == pair ? -1 : pair;
            styleTiles();
            if (pickLeft >= 0 && pickRight >= 0) tryMatch();
        });
        return t;
    }

    private void styleTiles() {
        for (int i = 0; i < leftTiles.length; i++) {
            styleTile(leftTiles[i], i == pickLeft);
            styleTile(rightTiles[i], i == pickRight);
        }
    }

    private void styleTile(TextView t, boolean picked) {
        if (!t.isEnabled()) return;
        t.setBackground(picked ? box(fill, ink) : box(getColor(R.color.card), getColor(R.color.hairline)));
    }

    private void tryMatch() {
        final TextView l = leftTiles[pickLeft], r = rightTiles[pickRight];
        if (pickLeft == pickRight) {
            matched++;
            for (TextView t : new TextView[]{l, r}) {
                t.setEnabled(false);
                t.setBackground(box(getColor(R.color.p_mint), getColor(R.color.mint)));
                pop(t);
                t.postDelayed(() -> t.animate().alpha(0.4f).setStartDelay(0).setDuration(250), 350);
            }
            Sfx.play(this, Sfx.STEP, 1f + 0.12f * matched);
            pickLeft = pickRight = -1;
            if (matched == leftTiles.length) action.postDelayed(this::matchDone, 420);
            return;
        }
        matchMistakes++;
        matchBusy = true;
        Sfx.play(this, Sfx.WRONG);
        action.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        for (TextView t : new TextView[]{l, r}) {
            t.setBackground(box(getColor(R.color.p_peach), getColor(R.color.coral)));
            shake(t);
        }
        action.postDelayed(() -> {
            pickLeft = pickRight = -1;
            matchBusy = false;
            styleTiles();
        }, 520);
    }

    private void matchDone() {
        if (finished || checked) return;
        JSONObject step = queue.get(pos);
        checked = true;
        questions++;
        boolean right = matchMistakes == 0;
        // A messy match costs one mistake, however many wrong taps it took.
        if (!right) mistakes++;
        afterAnswer(right, step.optString("why"), null, null, false);
    }

    // ---------------------------------------------------------------- checking and moving on

    private void onAction() {
        if (finished) {
            finish();
            return;
        }
        JSONObject step = queue.get(pos);
        String type = step.optString("type");
        if (type.equals("trace") && !checked) {
            JSONArray frames = step.optJSONArray("frames");
            if (frame < frames.length() - 1) {
                frame++;
                showFrame(step, true);
                return;
            }
        }
        if (type.equals("card") || type.equals("trace") || checked) {
            Sfx.play(this, Sfx.TAP, 0.85f);
            next();
            return;
        }
        boolean right;
        String fix = null, answer = null;
        View target;
        if (type.equals("order")) {
            List<String> want = lines(step);
            right = picked.equals(want);
            if (!right) fix = String.join("\n", want);
            target = answerArea;
        } else if (type.equals("type")) {
            right = typedRight(step);
            if (!right) answer = step.optString("answer");
            input.setEnabled(false);
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.setBackground(box(getColor(right ? R.color.p_mint : R.color.p_peach), getColor(right ? R.color.mint : R.color.coral)));
            target = input;
        } else {
            if (selected < 0) return;
            right = selected == correct;
            target = optionViews.get(selected);
        }
        checked = true;
        if (type.equals("order")) colorAnswer(right);
        else if (!type.equals("type")) styleOptions();
        questions++;
        if (!right) {
            mistakes++;
            // Same question again at the end, freshly shuffled.
            queue.add(step);
        }
        if (right) pop(target);
        else shake(target);
        afterAnswer(right, step.optString("why"), fix, answer, !right);
        if (right) burstFrom(target, 26);
    }

    /** Shared by every question type: sound, combo, haptics, feedback panel. */
    private void afterAnswer(boolean right, String why, String fix, String answer, boolean comesBack) {
        if (right) {
            combo++;
            bestCombo = Math.max(bestCombo, combo);
            Sfx.play(this, Sfx.CORRECT);
        } else {
            combo = 0;
            Sfx.play(this, Sfx.WRONG);
        }
        showCombo();
        boolean milestone = right && (combo == 3 || combo == 5 || combo == 8 || combo >= 10 && combo % 5 == 0);
        if (milestone) {
            action.postDelayed(() -> Sfx.play(this, Sfx.COMBO), 260);
            comboView.post(() -> burstFrom(comboView, 30));
        }
        action.performHapticFeedback(right ? HapticFeedbackConstants.KEYBOARD_TAP : HapticFeedbackConstants.LONG_PRESS);
        showFeedback(right, why, fix, answer, comesBack);
        setAction("Continue", true);
    }

    /** setStartDelay(0) everywhere: a view's animator keeps the delay stagger() gave it. */
    private void pop(View v) {
        v.animate().scaleX(1.05f).scaleY(1.05f).setStartDelay(0).setDuration(110)
                .withEndAction(() -> v.animate().scaleX(1).scaleY(1).setStartDelay(0).setDuration(160));
    }

    private void shake(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "translationX", 0, dp(14), -dp(12), dp(9), -dp(6), dp(3), 0);
        a.setDuration(420);
        a.start();
    }

    private void burstFrom(View v, int count) {
        int[] at = new int[2], base = new int[2];
        v.getLocationInWindow(at);
        container.getLocationInWindow(base);
        confetti.burst(at[0] - base[0] + v.getWidth() / 2f, at[1] - base[1] + v.getHeight() / 2f, count);
    }

    private void colorAnswer(boolean right) {
        int stroke = getColor(right ? R.color.mint : R.color.coral);
        int bg = getColor(right ? R.color.p_mint : R.color.p_peach);
        for (int i = 0; i < answerArea.getChildCount(); i++) answerArea.getChildAt(i).setBackground(box(bg, stroke));
    }

    private static final String[] PRAISE = {"Nice!", "Correct!", "Great!", "You got it!", "Spot on!"};

    private void showFeedback(boolean right, String why, String fix, String answer, boolean comesBack) {
        feedback.removeAllViews();
        int color = getColor(right ? R.color.mint : R.color.coral);
        Ui.tint(feedback, getColor(right ? R.color.p_mint : R.color.p_peach), 20, 0xFF);
        String head = right ? (combo >= 3 ? "🔥 " + combo + " in a row!" : PRAISE[(questions + combo) % PRAISE.length]) : "Not quite";
        feedback.addView(text((right ? "✓  " : "✕  ") + head, 18, color, R.font.hg_extrabold));
        if (answer != null) feedback.addView(text("Answer: " + answer, 15, getColor(R.color.text), R.font.hg_semibold), wide(4));
        if (why.length() > 0) feedback.addView(text(why, 14.5f, getColor(R.color.text), R.font.hg_regular), wide(4));
        if (fix != null) feedback.addView(Code.block(this, fix), wide(10));
        if (comesBack) feedback.addView(text("It comes back at the end.", 12.5f, getColor(R.color.subtext), R.font.hg_regular), wide(6));
        feedback.setVisibility(View.VISIBLE);
        feedback.setAlpha(0);
        feedback.setTranslationY(dp(40));
        feedback.animate().alpha(1).translationY(0).setDuration(280).setInterpolator(new OvershootInterpolator(1.2f));
        if (fix != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void next() {
        JSONObject step = queue.get(pos);
        boolean wrongAttempt = checked && queue.lastIndexOf(step) > pos;
        if (!wrongAttempt) done++;
        pos++;
        if (pos >= queue.size()) showResult();
        else render();
    }

    // ---------------------------------------------------------------- the end

    private void showResult() {
        finished = true;
        if (tts != null) tts.stop();
        final int xp = LearnDb.get(this).complete(lesson.id, mistakes);
        int stars = LearnDb.stars(mistakes);
        Cloud.sync(this, null);
        setProgress();
        feedback.setVisibility(View.GONE);
        body.removeAllViews();
        body.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout starRow = new LinearLayout(this);
        starRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            final boolean earned = i < stars;
            final TextView s = text("★", i == 1 ? 72 : 56, earned ? getColor(R.color.amber) : getColor(R.color.hairline), R.font.hg_extrabold);
            s.setScaleX(0);
            s.setScaleY(0);
            if (i != 1) s.setTranslationY(dp(14));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.leftMargin = sp.rightMargin = dp(4);
            starRow.addView(s, sp);
            final int index = i;
            s.postDelayed(() -> {
                s.animate().scaleX(1).scaleY(1).rotation(earned ? 360 : 0).setDuration(520).setInterpolator(new OvershootInterpolator(2.2f));
                if (earned) {
                    Sfx.play(this, Sfx.STAR, 1f + 0.12f * index);
                    s.post(() -> burstFrom(s, 18));
                }
            }, 550 + 380L * i);
        }
        body.addView(starRow, wide(28));

        TextView title = text(mistakes == 0 ? "Perfect lesson!" : stars == 2 ? "Great work!" : "Lesson done", 30, getColor(R.color.text), R.font.hg_extrabold);
        title.setGravity(Gravity.CENTER);
        body.addView(title, wide(10));
        TextView sub = text(lesson.title, 15, getColor(R.color.subtext), R.font.hg_regular);
        sub.setGravity(Gravity.CENTER);
        body.addView(sub, wide(2));

        LinearLayout tiles = new LinearLayout(this);
        int accuracy = questions == 0 ? 100 : Math.round(100f * (questions - mistakes) / questions);
        final TextView xpValue = tileValue("+0", getColor(R.color.amber));
        tiles.addView(tile(xpValue, "XP", getColor(R.color.p_butter)), tileParams(true));
        tiles.addView(tile(tileValue(accuracy + "%", getColor(R.color.mint)), "Accuracy", getColor(R.color.p_mint)), tileParams(true));
        tiles.addView(tile(tileValue("🔥" + bestCombo, getColor(R.color.coral)), "Best combo", getColor(R.color.p_peach)), tileParams(false));
        body.addView(tiles, wide(30));
        List<View> rise = new ArrayList<>();
        for (int i = 0; i < tiles.getChildCount(); i++) rise.add(tiles.getChildAt(i));
        stagger(rise, 1500);

        ValueAnimator count = ValueAnimator.ofInt(0, xp);
        count.setStartDelay(1500);
        count.setDuration(800);
        count.setInterpolator(new DecelerateInterpolator());
        count.addUpdateListener(a -> xpValue.setText("+" + a.getAnimatedValue()));
        count.start();

        int today = LearnDb.get(this).todayXp();
        if (today >= LearnDb.DAILY_GOAL && today - xp < LearnDb.DAILY_GOAL) {
            TextView goal = text("🎯  Daily goal reached · " + LearnDb.get(this).streak() + " day streak", 15, ink, R.font.hg_semibold);
            goal.setGravity(Gravity.CENTER);
            goal.setPadding(dp(16), dp(10), dp(16), dp(10));
            Ui.tint(goal, fill, 100, 0xFF);
            body.addView(goal, wrap(20));
            goal.setAlpha(0);
            goal.animate().alpha(1).setStartDelay(2200).setDuration(300);
        }

        Sfx.play(this, Sfx.COMPLETE);
        action.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        container.post(() -> confetti.rain(mistakes == 0 ? 160 : 80));
        setAction("Continue", true);
    }

    private TextView tileValue(String s, int color) {
        TextView v = text(s, 24, color, R.font.hg_extrabold);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private LinearLayout.LayoutParams tileParams(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        if (gap) lp.rightMargin = dp(10);
        return lp;
    }

    private View tile(TextView value, String label, int bg) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(16), dp(8), dp(16));
        Ui.tint(t, bg, 20, 0xFF);
        t.addView(value);
        TextView l = text(label, 12, getColor(R.color.text), R.font.hg_regular);
        l.setGravity(Gravity.CENTER);
        l.setAlpha(0.7f);
        t.addView(l);
        return t;
    }
}
