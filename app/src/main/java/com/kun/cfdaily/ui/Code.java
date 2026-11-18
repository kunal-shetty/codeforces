package com.kun.cfdaily.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import com.kun.cfdaily.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Small C++ highlighter and code block for lessons. */
public class Code {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "if", "else", "for", "while", "do", "return", "break", "continue", "auto", "const", "using", "namespace",
            "true", "false", "struct", "class", "new", "delete", "sizeof"));
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
            "int", "long", "short", "char", "bool", "double", "float", "void", "string", "vector", "map", "set",
            "unordered_map", "unordered_set", "pair", "queue", "stack", "deque", "priority_queue", "size_t"));

    public static CharSequence highlight(Context c, String code) {
        SpannableStringBuilder s = new SpannableStringBuilder(code);
        int keyword = c.getColor(R.color.violet), type = c.getColor(R.color.blue), number = c.getColor(R.color.amber);
        int comment = c.getColor(R.color.faint), blank = c.getColor(R.color.coral);
        int i = 0, n = code.length();
        while (i < n) {
            char ch = code.charAt(i);
            if (ch == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                int end = code.indexOf('\n', i);
                if (end < 0) end = n;
                color(s, i, end, comment);
                i = end;
            } else if (ch == '_' && code.startsWith("___", i)) {
                s.setSpan(new BackgroundColorSpan((blank & 0x00FFFFFF) | 0x40000000), i, i + 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                color(s, i, i + 3, blank);
                i += 3;
            } else if (Character.isDigit(ch)) {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '.')) end++;
                color(s, i, end, number);
                i = end;
            } else if (Character.isLetter(ch) || ch == '_') {
                int end = i;
                while (end < n && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '_')) end++;
                String word = code.substring(i, end);
                if (KEYWORDS.contains(word)) {
                    color(s, i, end, keyword);
                    s.setSpan(new StyleSpan(Typeface.BOLD), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (TYPES.contains(word)) {
                    color(s, i, end, type);
                }
                i = end;
            } else {
                i++;
            }
        }
        return s;
    }

    private static void color(SpannableStringBuilder s, int start, int end, int color) {
        s.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Highlighted, horizontally scrollable code on a raised panel. */
    public static HorizontalScrollView block(Context c, String code) {
        TextView tv = new TextView(c);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(13.5f);
        tv.setTextColor(c.getColor(R.color.text));
        tv.setLineSpacing(0, 1.18f);
        tv.setText(highlight(c, code));
        int pad = Ui.dp(c, 14);
        tv.setPadding(pad, pad, pad, pad);
        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        Ui.tint(scroll, c.getColor(R.color.raised), 16, 0xFF);
        scroll.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }
}
