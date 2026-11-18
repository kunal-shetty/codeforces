package com.kun.cfdaily.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Lesson sound effects, synthesised in code (no audio files to license or ship).
 * Each sound is rendered once to a small WAV in the cache and played through SoundPool for low latency.
 */
public class Sfx {

    public static final int TAP = 0, CORRECT = 1, WRONG = 2, COMBO = 3, COMPLETE = 4, STAR = 5, STEP = 6;
    private static final int RATE = 44100;
    private static final int VERSION = 1;

    private static SoundPool pool;
    private static final int[] ids = new int[7];
    private static final boolean[] loaded = new boolean[7];

    public static boolean enabled(Context c) {
        return c.getSharedPreferences("ui", 0).getBoolean("sound", true);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("ui", 0).edit().putBoolean("sound", on).apply();
    }

    /** Renders the sounds off the main thread the first time; cheap afterwards. */
    public static synchronized void init(Context c) {
        if (pool != null) return;
        final Context app = c.getApplicationContext();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
            for (int i = 0; i < ids.length; i++) if (ids[i] == id && status == 0) loaded[i] = true;
        });
        new Thread(() -> {
            for (int i = 0; i < ids.length; i++) {
                try {
                    File f = new File(app.getCacheDir(), "sfx" + VERSION + "_" + i + ".wav");
                    if (!f.exists()) writeWav(f, render(i));
                    final int index = i;
                    final String path = f.getAbsolutePath();
                    synchronized (Sfx.class) {
                        if (pool != null) ids[index] = pool.load(path, 1);
                    }
                } catch (IOException ignored) {
                    // No sound is better than a crash.
                }
            }
        }).start();
    }

    public static void play(Context c, int sound) {
        play(c, sound, 1f);
    }

    /** rate shifts the pitch: 1 is normal, 1.12 is about two semitones up. */
    public static synchronized void play(Context c, int sound, float rate) {
        if (pool == null || !loaded[sound] || !enabled(c)) return;
        float volume = sound == TAP || sound == STEP ? 0.45f : 0.8f;
        pool.play(ids[sound], volume, volume, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    public static synchronized void release() {
        if (pool != null) pool.release();
        pool = null;
        for (int i = 0; i < loaded.length; i++) loaded[i] = false;
    }

    // ---------------------------------------------------------------- synthesis

    private static float[] buffer(int ms) {
        return new float[RATE * ms / 1000];
    }

    /** A soft bell: sine with two overtones and an exponential fade. */
    private static void bell(float[] out, int startMs, double freq, int ms, float gain, double decay) {
        int start = RATE * startMs / 1000, n = RATE * ms / 1000, attack = RATE / 200;
        for (int i = 0; i < n && start + i < out.length; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-decay * t) * Math.min(1, i / (double) attack);
            double s = Math.sin(2 * Math.PI * freq * t) + 0.35 * Math.sin(4 * Math.PI * freq * t) + 0.12 * Math.sin(6 * Math.PI * freq * t);
            out[start + i] += (float) (gain * env * s);
        }
    }

    /** Buzzy low tone for a wrong answer: a soft-clipped triangle with a slight pitch drop. */
    private static void buzz(float[] out, int startMs, double freq, int ms, float gain) {
        int start = RATE * startMs / 1000, n = RATE * ms / 1000;
        double phase = 0;
        for (int i = 0; i < n && start + i < out.length; i++) {
            double t = i / (double) RATE;
            double f = freq * (1 - 0.12 * t / (ms / 1000.0));
            phase += f / RATE;
            double tri = 4 * Math.abs(phase - Math.floor(phase + 0.5)) - 1;
            double env = Math.min(1, i / (RATE * 0.004)) * Math.exp(-6 * t);
            out[start + i] += (float) (gain * env * Math.tanh(2.2 * tri));
        }
    }

    private static float[] render(int sound) {
        float[] b;
        switch (sound) {
            case TAP:
                b = buffer(60);
                bell(b, 0, 1760, 60, 0.35f, 70);
                break;
            case STEP:
                b = buffer(90);
                bell(b, 0, 1175, 90, 0.3f, 45);
                bell(b, 25, 1568, 65, 0.22f, 50);
                break;
            case CORRECT:
                b = buffer(520);
                bell(b, 0, 880, 200, 0.45f, 14);
                bell(b, 85, 1318.5, 435, 0.5f, 7);
                break;
            case WRONG:
                b = buffer(380);
                buzz(b, 0, 196, 170, 0.32f);
                buzz(b, 150, 147, 230, 0.32f);
                break;
            case COMBO:
                b = buffer(600);
                double[] up = {1046.5, 1318.5, 1568, 2093};
                for (int i = 0; i < up.length; i++) bell(b, i * 60, up[i], 600 - i * 60, 0.35f, 9);
                break;
            case COMPLETE:
                b = buffer(1500);
                double[] chord = {523.25, 659.25, 783.99, 1046.5};
                for (int i = 0; i < chord.length; i++) bell(b, i * 110, chord[i], 1500 - i * 110, 0.38f, i == 3 ? 2.6 : 4.5);
                bell(b, 440, 1568, 1000, 0.18f, 3.5);
                bell(b, 440, 2093, 1000, 0.12f, 3.5);
                break;
            default: // STAR
                b = buffer(450);
                bell(b, 0, 1318.5, 450, 0.4f, 8);
                bell(b, 40, 1975.5, 410, 0.25f, 9);
                break;
        }
        normalize(b);
        return b;
    }

    private static void normalize(float[] b) {
        float peak = 0;
        for (float v : b) peak = Math.max(peak, Math.abs(v));
        if (peak > 0.95f) for (int i = 0; i < b.length; i++) b[i] *= 0.95f / peak;
        // Short fade-out so nothing clicks at the end.
        int fade = Math.min(b.length, RATE / 100);
        for (int i = 0; i < fade; i++) b[b.length - 1 - i] *= i / (float) fade;
    }

    private static void writeWav(File f, float[] samples) throws IOException {
        int dataBytes = samples.length * 2;
        ByteBuffer bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        bb.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        bb.put("data".getBytes()).putInt(dataBytes);
        for (float s : samples) bb.putShort((short) Math.round(Math.max(-1, Math.min(1, s)) * 32767));
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bb.array());
        }
        if (!tmp.renameTo(f)) throw new IOException("rename failed");
    }
}
