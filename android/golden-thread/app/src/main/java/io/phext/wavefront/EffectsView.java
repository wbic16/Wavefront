package io.phext.wavefront;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Halo canvas (v1.6): no boxes, no badges. Each post author's
 *  profile picture gets a glowing ring in their caste colour —
 *  gold for moots, green for aligned follows, grey for the
 *  unaligned, faint for the chaff. Fireworks burst from a moot's
 *  halo on first sight. */
public class EffectsView extends View {
    public static class Halo {
        public final Data.Caste caste; public final Rect pfp;
        Halo(Data.Caste c, Rect r) { caste = c; pfp = r; }
    }
    private final List<Halo> halos = new ArrayList<>();
    private long fadeStart = 0;
    private final List<float[]> parts = new ArrayList<>();
    private final Paint paint = new Paint();
    private final Random rnd = new Random();
    private final int[] goldColors = {0xFFFFD968, 0xFFD4A017, 0xFFFFF3C4, 0xFFFFB347};

    public EffectsView(Context c) { super(c); setWillNotDraw(false); }

    public synchronized void setHalos(List<Halo> hs) {
        halos.clear(); halos.addAll(hs);
        fadeStart = System.currentTimeMillis();
        invalidate();
    }
    public synchronized void clearHalos() { halos.clear(); invalidate(); }
    public synchronized void burst(float cx, float cy) {
        for (int i = 0; i < 36; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 3 + rnd.nextDouble() * 8;
            parts.add(new float[]{cx, cy, (float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp) - 3f,
                    1f, goldColors[i % goldColors.length], 2 + rnd.nextFloat() * 3});
        }
        invalidate();
    }

    private int casteColor(Data.Caste c) {
        switch (c) {
            case MOOT: return 0xFFD4A017;
            case ALIGNED: return 0xFF7AC74F;
            case MUTED: return 0xFF8A8A8A;
            default: return 0xFF4A4A4A;
        }
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        float fade = halos.isEmpty() ? 1f : Math.min(1f, (System.currentTimeMillis() - fadeStart) / 150f);
        paint.setStyle(Paint.Style.STROKE);
        for (Halo h : halos) {
            float cx = h.pfp.centerX(), cy = h.pfp.centerY();
            float radius = Math.max(h.pfp.width(), h.pfp.height()) / 2f + 9f;
            int col = casteColor(h.caste);
            boolean bright = h.caste == Data.Caste.MOOT || h.caste == Data.Caste.ALIGNED;
            paint.setColor(col);
            paint.setAlpha((int) ((bright ? 70 : 25) * fade));
            paint.setStrokeWidth(20f);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setAlpha((int) ((bright ? 255 : 150) * fade));
            paint.setStrokeWidth(bright ? 6f : 3.5f);
            canvas.drawCircle(cx, cy, radius, paint);
            if (h.caste == Data.Caste.MOOT) {
                paint.setColor(0xFFFFF3C4);
                paint.setAlpha((int) (210 * fade));
                paint.setStrokeWidth(2f);
                canvas.drawCircle(cx, cy, radius, paint);
            }
        }
        if (!halos.isEmpty() && fade < 1f) postInvalidateDelayed(16);
        paint.setStyle(Paint.Style.FILL);
        parts.removeIf(pt -> pt[4] <= 0);
        for (float[] pt : parts) {
            pt[0] += pt[2]; pt[1] += pt[3]; pt[3] += 0.22f; pt[2] *= 0.985f; pt[3] *= 0.985f; pt[4] -= 0.018f;
            paint.setColor((int) pt[5]);
            paint.setAlpha(Math.max(0, Math.min(255, (int) (pt[4] * 255))));
            canvas.drawCircle(pt[0], pt[1], pt[6], paint);
        }
        if (!parts.isEmpty()) postInvalidateDelayed(16);
    }
}
