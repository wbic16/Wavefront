package io.phext.wavefront;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** One full-screen transparent canvas for the Moot Radar: gold frames
 *  for moots, RGB frames for aligned follows, translucent veils that
 *  mute the unaligned and half-erase the chaff, and fireworks when a
 *  moot scrolls into view. Drawn from a model the RadarService updates. */
public class EffectsView extends View {
    public static class Mark {
        public final Rect rect; public final Data.Caste caste;
        Mark(Rect r, Data.Caste c) { rect = r; caste = c; }
    }
    private final List<Mark> marks = new ArrayList<>();
    private final List<float[]> parts = new ArrayList<>();
    private final Paint paint = new Paint();
    private final Random rnd = new Random();
    private final int[] goldColors = {0xFFFFD968, 0xFFD4A017, 0xFFFFF3C4, 0xFFFFB347};

    public EffectsView(Context c) { super(c); setWillNotDraw(false); }

    private int yShift = 0;
    private long fadeStart = 0;
    public synchronized void setMarks(List<Mark> m) {
        marks.clear(); marks.addAll(m);
        yShift = 0;
        fadeStart = System.currentTimeMillis();
        invalidate();
    }
    public synchronized void shift(int dy) { yShift += dy; invalidate(); }
    public synchronized void burst(float cx, float cy) {
        for (int i = 0; i < 40; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = 3 + rnd.nextDouble() * 9;
            parts.add(new float[]{cx, cy, (float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp) - 3f,
                    1f, goldColors[i % goldColors.length], 2 + rnd.nextFloat() * 3});
        }
        invalidate();
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        float fade = marks.isEmpty() ? 1f : Math.min(1f, (System.currentTimeMillis() - fadeStart) / 150f);
        for (Mark m0 : marks) {
            Mark m = new Mark(new Rect(m0.rect.left, m0.rect.top + yShift, m0.rect.right, m0.rect.bottom + yShift), m0.caste);
            switch (m.caste) {
                case MOOT:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFFD4A017); paint.setAlpha((int) (80 * fade)); paint.setStrokeWidth(34f);
                    canvas.drawRect(m.rect, paint);
                    paint.setAlpha((int) (255 * fade)); paint.setStrokeWidth(10f);
                    canvas.drawRect(m.rect, paint);
                    paint.setColor(0xFFFFF3C4); paint.setAlpha((int) (210 * fade)); paint.setStrokeWidth(3f);
                    canvas.drawRect(m.rect, paint);
                    break;
                case ALIGNED:
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFF7AC74F); paint.setAlpha((int) (60 * fade)); paint.setStrokeWidth(26f);
                    canvas.drawRect(m.rect, paint);
                    paint.setAlpha((int) (240 * fade)); paint.setStrokeWidth(8f);
                    canvas.drawRect(m.rect, paint);
                    break;
                case MUTED:
                    paint.setStyle(Paint.Style.FILL); paint.setColor(Color.GRAY); paint.setAlpha((int) (66 * fade));
                    canvas.drawRect(m.rect, paint);
                    break;
                case CHAFF:
                    paint.setStyle(Paint.Style.FILL); paint.setColor(0xFF0A0A0A); paint.setAlpha((int) (104 * fade));
                    canvas.drawRect(m.rect, paint);
                    break;
                default: break;
            }
        }
        paint.setStyle(Paint.Style.FILL);
        parts.removeIf(pt -> pt[4] <= 0);
        for (float[] pt : parts) {
            pt[0] += pt[2]; pt[1] += pt[3]; pt[3] += 0.22f; pt[2] *= 0.985f; pt[3] *= 0.985f; pt[4] -= 0.018f;
            paint.setColor((int) pt[5]);
            paint.setAlpha(Math.max(0, Math.min(255, (int) (pt[4] * 255))));
            canvas.drawCircle(pt[0], pt[1], pt[6], paint);
        }
        if (!parts.isEmpty() || fade < 1f) postInvalidateDelayed(16);
    }
}
