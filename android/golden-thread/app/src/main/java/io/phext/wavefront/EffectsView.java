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

/** The badge canvas (v1.4): no more post boxes. One small pill badge,
 *  anchored just below the X app's Grok button, showing the caste of
 *  the post in view: gold for a moot, green for an aligned follow,
 *  grey for the unaligned, faint for the chaff. Fireworks live here
 *  too, fired from the badge when a moot first scrolls into view. */
public class EffectsView extends View {
    private String handle = null;
    private Data.Caste caste = null;
    private Rect anchor = null; // the Grok button's bounds, when found
    private long fadeStart = 0;
    private final List<float[]> parts = new ArrayList<>();
    private final Paint paint = new Paint();
    private final Random rnd = new Random();
    private final int[] goldColors = {0xFFFFD968, 0xFFD4A017, 0xFFFFF3C4, 0xFFFFB347};

    public EffectsView(Context c) { super(c); setWillNotDraw(false); }

    public synchronized void setBadge(String h, Data.Caste c, Rect grokAnchor) {
        boolean changed = h == null ? handle != null : !h.equals(handle);
        handle = h; caste = c;
        if (grokAnchor != null) anchor = new Rect(grokAnchor);
        if (changed) fadeStart = System.currentTimeMillis();
        invalidate();
    }
    public synchronized void clearBadge() { handle = null; caste = null; invalidate(); }
    public synchronized float[] badgeCenter() {
        RectF r = badgeRect();
        return r == null ? null : new float[]{r.centerX(), r.centerY()};
    }
    public synchronized void burst(float cx, float cy) {
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 3 + rnd.nextDouble() * 9;
            parts.add(new float[]{cx, cy, (float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp) - 3f,
                    1f, goldColors[i % goldColors.length], 2 + rnd.nextFloat() * 3});
        }
        invalidate();
    }

    private RectF badgeRect() {
        if (handle == null) return null;
        int w = getWidth(), h = getHeight();
        if (w == 0) return null;
        paint.setTextSize(30f);
        String label = "✦ @" + handle;
        float tw = paint.measureText(label);
        float bw = tw + 56f, bh = 52f;
        float cx = anchor != null ? anchor.centerX() : w / 2f;
        float left = Math.max(8f, Math.min(cx - bw / 2f, w - bw - 8f));
        float top;
        if (anchor != null && anchor.bottom + 10 + bh <= h) top = anchor.bottom + 10;
        else top = h - bh - 150; // fallback: float above the bottom nav
        return new RectF(left, top, left + bw, top + bh);
    }

    private int casteColor() {
        if (caste == null) return Color.GRAY;
        switch (caste) {
            case MOOT: return 0xFFD4A017;
            case ALIGNED: return 0xFF7AC74F;
            case MUTED: return 0xFF9A9A9A;
            default: return 0xFF555555;
        }
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        RectF r = badgeRect();
        if (r != null) {
            float fade = Math.min(1f, (System.currentTimeMillis() - fadeStart) / 150f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF101010); paint.setAlpha((int) (215 * fade));
            canvas.drawRoundRect(r, 26f, 26f, paint);
            paint.setColor(casteColor()); paint.setAlpha((int) (255 * fade));
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
            canvas.drawRoundRect(r, 26f, 26f, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(r.left + 28f, r.centerY(), 10f, paint);
            paint.setColor(Color.WHITE); paint.setAlpha((int) (240 * fade));
            paint.setTextSize(30f);
            float ty = r.centerY() - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText("✦ @" + handle, r.left + 48f, ty, paint);
            if (fade < 1f) postInvalidateDelayed(16);
        }
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
