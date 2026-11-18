package com.kun.cfdaily.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import com.kun.cfdaily.R;
import com.kun.cfdaily.data.Mode;

import java.util.Locale;

public class Ui {

    /** Set by the activity: which palette the fixed colours below should come from. */
    public static boolean dark;

    public static int ratingColor(int r) {
        if (!dark) {
            if (r < 1200) return 0xFF6B7080;
            if (r < 1400) return 0xFF1B8F3F;
            if (r < 1600) return 0xFF0B8F88;
            if (r < 1900) return 0xFF3D5AFE;
            if (r < 2100) return 0xFFA21CAF;
            if (r < 2400) return 0xFFC25E00;
            return 0xFFD32F2F;
        }
        if (r <= 0) return 0xFF9AA0AE;
        if (r < 1200) return 0xFF9AA0AE;
        if (r < 1400) return 0xFF4CC764;
        if (r < 1600) return 0xFF2DD4C8;
        if (r < 1900) return 0xFF7C96FF;
        if (r < 2100) return 0xFFD873E6;
        if (r < 2400) return 0xFFFFA940;
        return 0xFFFF6A62;
    }

    /** A problem's level as text: the rating on Codeforces, Easy / Med / Hard on LeetCode. */
    public static String levelText(int rating) {
        if (Mode.lc()) return rating == 1 ? "Easy" : rating == 2 ? "Med" : rating == 3 ? "Hard" : "–";
        return rating > 0 ? String.valueOf(rating) : "–";
    }

    public static int levelColor(int rating) {
        if (!Mode.lc()) return ratingColor(rating);
        if (rating == 1) return dark ? 0xFF5EE6B0 : 0xFF0B8A70;
        if (rating == 2) return dark ? 0xFFFFC66B : 0xFFB45F06;
        if (rating == 3) return dark ? 0xFFFF7A70 : 0xFFD93A3A;
        return ratingColor(0);
    }

    /** Topic name for display: "dfs and similar" becomes "DFS and Similar", "dp" becomes "DP". */
    public static String tag(String raw) {
        StringBuilder out = new StringBuilder();
        String[] words = raw.replace("*", "").split(" ");
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (w.length() == 0) continue;
            String low = w.toLowerCase(Locale.US);
            if (out.length() > 0) out.append(' ');
            if (low.equals("dp") || low.equals("dsu") || low.equals("fft") || low.equals("dfs") || low.equals("2-sat")) out.append(low.toUpperCase(Locale.US));
            else if (i > 0 && (low.equals("and") || low.equals("of") || low.equals("the"))) out.append(low);
            else out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Rounded fill in the given colour at the given opacity (0-255). */
    public static void tint(View v, int color, float radiusDp, int alpha) {
        GradientDrawable g = new GradientDrawable();
        g.setColor((color & 0x00FFFFFF) | (alpha << 24));
        g.setCornerRadius(dp(v.getContext(), radiusDp));
        v.setBackground(g);
    }

    /** Coloured label on a soft fill of the same colour. */
    public static void badge(TextView tv, String text, int color) {
        tv.setText(text);
        tv.setTextColor(color);
        tint(tv, color, 8, dark ? 0x30 : 0x22);
    }

    /** 19197 becomes 19.2K. */
    public static String compact(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 100000) return String.format(Locale.US, "%.1fK", n / 1000.0);
        return (n / 1000) + "K";
    }

    public static String divLabel(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.contains("biweekly")) return "Biweekly";
        if (n.contains("weekly")) return "Weekly";
        if (n.contains("div. 1 + div. 2") || n.contains("global round")) return "Div 1+2";
        if (n.contains("educational")) return "Edu";
        if (n.contains("div. 4")) return "Div 4";
        if (n.contains("div. 3")) return "Div 3";
        if (n.contains("div. 2")) return "Div 2";
        if (n.contains("div. 1")) return "Div 1";
        return "Other";
    }

    public static int divColor(String label) {
        if (label.equals("Weekly")) return dark ? 0xFF7CC4FF : 0xFF0A74C2;
        if (label.equals("Biweekly")) return dark ? 0xFFB49BFF : 0xFF6D4DF2;
        if (!dark) {
            if (label.equals("Div 1")) return 0xFFD93A3A;
            if (label.equals("Div 1+2")) return 0xFFD6339F;
            if (label.equals("Div 2")) return 0xFF0A74C2;
            if (label.equals("Div 3")) return 0xFF0B8A70;
            if (label.equals("Div 4")) return 0xFFB45F06;
            if (label.equals("Edu")) return 0xFF6D4DF2;
            return 0xFF6B7080;
        }
        if (label.equals("Div 1")) return 0xFFFF7A70;
        if (label.equals("Div 1+2")) return 0xFFFF8FC7;
        if (label.equals("Div 2")) return 0xFF7CC4FF;
        if (label.equals("Div 3")) return 0xFF5EE6B0;
        if (label.equals("Div 4")) return 0xFFFFC66B;
        if (label.equals("Edu")) return 0xFFB49BFF;
        return 0xFF9AA0AE;
    }

    private static final String[] UNIT_COLORS = {"mint", "sky", "lilac", "peach", "butter", "rose"};
    private static final int[] UNIT_FILLS = {R.color.p_mint, R.color.p_sky, R.color.p_lilac, R.color.p_peach, R.color.p_butter, R.color.p_rose};
    private static final int[] UNIT_INKS = {R.color.mint, R.color.blue, R.color.violet, R.color.coral, R.color.amber, R.color.pink};

    private static int unitIndex(String color) {
        for (int i = 0; i < UNIT_COLORS.length; i++) if (UNIT_COLORS[i].equals(color)) return i;
        return 0;
    }

    /** Pastel background for a lesson unit's colour name (content/learn: mint, sky, lilac, peach, butter, rose). */
    public static int unitFill(Context c, String color) {
        return c.getColor(UNIT_FILLS[unitIndex(color)]);
    }

    /** Strong colour for the same unit: icons, bars, accents. */
    public static int unitInk(Context c, String color) {
        return c.getColor(UNIT_INKS[unitIndex(color)]);
    }

    public static void open(Context c, String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            if (c instanceof MainActivity) ((MainActivity) c).snack("No browser found");
        }
    }

    public static String span(long ms) {
        long min = Math.max(0, ms / 60000);
        long d = min / 1440;
        long h = (min % 1440) / 60;
        long m = min % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
