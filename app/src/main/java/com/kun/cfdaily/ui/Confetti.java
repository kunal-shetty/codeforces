package com.kun.cfdaily.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Full-screen overlay that throws paper confetti. Touches pass through. */
public class Confetti extends View {

    private static class Bit {
        float x, y, vx, vy, rot, vr, w, h, life, wobble;
        int color;
        boolean round;
    }

    private static final int[] COLORS = {0xFF5EDDA6, 0xFF7CB2FF, 0xFFA995FF, 0xFFFF8A73, 0xFFF5C451, 0xFFFF8DBB, 0xFF2DD4C8};
    private final List<Bit> bits = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float density;
    private long last;

    public Confetti(Context c) {
        super(c);
        density = getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
    }

    /** A pop from one point, flying up and out. */
    public void burst(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI * 1.1;
            float speed = (380 + random.nextFloat() * 520) * density;
            bits.add(bit(x, y, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed));
        }
        start();
    }

    /** Confetti falling from across the top of the screen. */
    public void rain(int count) {
        for (int i = 0; i < count; i++) {
            Bit b = bit(random.nextFloat() * getWidth(), -random.nextFloat() * getHeight() * 0.5f,
                    (random.nextFloat() - 0.5f) * 160 * density, (120 + random.nextFloat() * 200) * density);
            b.life = 3.2f + random.nextFloat();
            bits.add(b);
        }
        start();
    }

    private Bit bit(float x, float y, float vx, float vy) {
        Bit b = new Bit();
        b.x = x;
        b.y = y;
        b.vx = vx;
        b.vy = vy;
        b.rot = random.nextFloat() * 360;
        b.vr = (random.nextFloat() - 0.5f) * 720;
        b.w = (5 + random.nextFloat() * 5) * density;
        b.h = b.w * (0.4f + random.nextFloat() * 0.5f);
        b.round = random.nextInt(4) == 0;
        b.color = COLORS[random.nextInt(COLORS.length)];
        b.life = 2.2f + random.nextFloat();
        b.wobble = random.nextFloat() * 6.28f;
        return b;
    }

    private void start() {
        last = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float gravity = 900 * density;
        for (int i = bits.size() - 1; i >= 0; i--) {
            Bit b = bits.get(i);
            b.vy += gravity * dt;
            b.vx *= 1 - 1.6f * dt;
            b.vy = Math.min(b.vy, 420 * density);
            b.wobble += dt * 7;
            b.x += (b.vx + (float) Math.sin(b.wobble) * 40 * density) * dt;
            b.y += b.vy * dt;
            b.rot += b.vr * dt;
            b.life -= dt;
            if (b.life <= 0 || b.y > getHeight() + 40 * density) {
                bits.remove(i);
                continue;
            }
            paint.setColor(b.color);
            paint.setAlpha((int) (255 * Math.min(1, b.life / 0.6f)));
            canvas.save();
            canvas.translate(b.x, b.y);
            canvas.rotate(b.rot);
            // Squash on one axis so the paper looks like it's flipping.
            canvas.scale(1, (float) Math.abs(Math.cos(b.wobble)) * 0.8f + 0.2f);
            if (b.round) canvas.drawCircle(0, 0, b.w / 2, paint);
            else canvas.drawRect(-b.w / 2, -b.h / 2, b.w / 2, b.h / 2, paint);
            canvas.restore();
        }
        if (!bits.isEmpty()) postInvalidateOnAnimation();
    }
}
