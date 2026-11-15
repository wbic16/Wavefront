package io.phext.wavefront;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Per-post badge canvas (v1.5): one small pill per post, anchored
 *  just below that post's own Grok button — a real element with real
 *  bounds, so the badge sits exactly where it belongs. Pill = caste
 *  dot + handle. Fireworks fire from a moot's badge on first sight. */
public class EffectsView extends View {
    public static class Badge {
        public final String handle; public final Data.Caste caste; public final Rect anchor;
        Badge(String h, Data.Caste c, Rect a) { handle = h; caste = c; anchor = a; }
    }
    private final List<Badge> badges = new ArrayList<>();
    private long fadeStart = 0;
    private final List<float[]> parts = new ArrayList<>();
    private final Paint paint = new Paint();
    private final Random rnd = new Random();
    private final int[] goldColors = {0xFFFFD968, 0xFFD4A017, 0xFFFFF3C4, 0xFFFFB347};

    public EffectsView(Context c) { super(c); setWillNotDraw(false); }

    public synchronized void setBadges(List<Badge> b) {
        badges.clear(); badges.addAll(b);
        fadeStart = System.currentTimeMillis();
        invalidate();
    }
    public synchronized void clearBadges() { badges.clear(); invalidate(); }

    private RectF pillRect(Badge b) {
        paint.setTextSize(26f);
        float tw = paint.measureText("✦ @" + b.handle);
        float bw = tw + 50f, bh = 44f;
        float right = b.anchor.right;
        float left = Math.max(8f, right - bw);
        float top = b.anchor.bottom + 6;
        return new RectF(left, top, left + bw, top + bh);
    }
    public synchronized void burstBelow(Rect anchor, String handle) {
        RectF r = pillRect(new Badge(handle, Data.Caste.MOOT, anchor));
        burst(r.centerX(), r.centerY());
    }
    public synchronized void burst(float cx, float cy) {
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 3 + rnd.nextDouble() * 9;
            parts.add(new float[]{cx, cy, (float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp) - 3f,
                    1f, goldColors[i % goldColors.length], 2 + rnd.nextFloat() * 3});
        }
        invalidate();
    }

    private int casteColor(Data.Caste c) {
        switch (c) {
            case MOOT: return 0xFFD4A017;
            case ALIGNED: return 0xFF7AC74F;
            case MUTED: return 0xFF9A9A9A;
            default: return 0xFF555555;
        }
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        float fade = badges.isEmpty() ? 1f : Math.min(1f, (System.currentTimeMillis() - fadeStart) / 150f);
        for (Badge b : badges) {
            RectF r = pillRect(b);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF101010); paint.setAlpha((int) (215 * fade));
            canvas.drawRoundRect(r, 22f, 22f, paint);
            paint.setColor(casteColor(b.caste)); paint.setAlpha((int) (255 * fade));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
            canvas.drawRoundRect(r, 22f, 22f, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(r.left + 24f, r.centerY(), 9f, paint);
            paint.setColor(Color.WHITE); paint.setAlpha((int) (240 * fade));
            paint.setTextSize(26f);
            float ty = r.centerY() - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText("✦ @" + b.handle, r.left + 42f, ty, paint);
        }
        if (!badges.isEmpty() && fade < 1f) postInvalidateDelayed(16);
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
