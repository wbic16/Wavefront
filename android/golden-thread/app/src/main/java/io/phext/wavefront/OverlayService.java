package io.phext.wavefront;

import android.app.Notification;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** The floating companion: the soul-print bubble hovers over the X app
 *  (and everything else); tapping it opens the wavefront panel —
 *  today's prune, the next due memory card, encounter logging. */
public class OverlayService extends Service {
    private WindowManager wm;
    private ImageView bubble;
    private LinearLayout panel;
    private WindowManager.LayoutParams bubbleParams, panelParams;
    private Data data;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        RadarService.overlayOn = true;
        data = Data.get(this);
        startForegroundWithNotification();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    private void startForegroundWithNotification() {
        String ch = "golden-thread";
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(ch, "Golden Thread", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, OverlayService.class); stop.setAction("STOP");
        PendingIntent pi = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, ch)
                .setContentTitle("Golden Thread")
                .setContentText("The wavefront rides with you. Tap the flame to open the panel.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(new Notification.Action.Builder(null, "Stop", pi).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    private int type() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                           : WindowManager.LayoutParams.TYPE_PHONE;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void showBubble() {
        bubble = new ImageView(this);
        bubble.setImageResource(R.mipmap.ic_launcher_round);
        bubbleParams = new WindowManager.LayoutParams(dp(58), dp(58), type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = dp(12); bubbleParams.y = dp(180);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int sx, sy; private float tx, ty; private boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = bubbleParams.x; sy = bubbleParams.y; tx = e.getRawX(); ty = e.getRawY(); moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - tx), dy = (int) (e.getRawY() - ty);
                        if (Math.abs(dx) + Math.abs(dy) > 12) moved = true;
                        bubbleParams.x = sx + dx; bubbleParams.y = sy + dy;
                        wm.updateViewLayout(bubble, bubbleParams); return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel();
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, bubbleParams);
    }

    private void togglePanel() { if (panel == null) showPanel(); else hidePanel(); }

    private TextView line(String text, float size, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(dp(4), dp(2), dp(4), dp(2));
        return t;
    }
    private Button btn(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(12);
        return b;
    }

    // ---- fireworks: gold bursts in a touch-through overlay window ----
    private static class FireworksView extends View {
        private final List<float[]> parts = new ArrayList<>();
        private final Paint paint = new Paint();
        private final Random rnd = new Random();
        private final int[] colors = {0xFFFFD968, 0xFFD4A017, 0xFFFFF3C4, 0xFFFFB347};
        FireworksView(android.content.Context c) { super(c); }
        void burst(float cx, float cy) {
            for (int i = 0; i < 46; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, sp = 3 + rnd.nextDouble() * 9;
                parts.add(new float[]{cx, cy, (float) (Math.cos(a) * sp), (float) (Math.sin(a) * sp) - 3f,
                        1f, colors[i % colors.length], 2 + rnd.nextFloat() * 3});
            }
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
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
    private void showFireworks() {
        final FireworksView fw = new FireworksView(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        try {
            wm.addView(fw, lp);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            fw.burst(w * 0.5f, h * 0.32f);
            fw.burst(w * 0.25f, h * 0.45f);
            fw.burst(w * 0.75f, h * 0.45f);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { wm.removeView(fw); } catch (Exception ignored) {}
            }, 1500);
        } catch (Exception ignored) {}
    }

    private void showPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xF214100A);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.addView(line("✦ Golden Thread", 16, 0xFFFFD968));

        // today's prune — first undecided suggestion
        String target = null;
        for (String h : data.todaysPrune()) if (data.decisionOf(h) == null) { target = h; break; }
        if (target != null) {
            final String h = target;
            JSONObject f = data.follows.optJSONObject(data.findKey(data.follows, h));
            panel.addView(line("Prune today: @" + h, 14, 0xFFF3E5C0));
            panel.addView(line("no follow-back · mentioned " + (f == null ? 0 : f.optInt("mentions")) + "× · "
                    + data.interestsLine(f == null ? new JSONObject() : f), 12, 0xFFB8A888));
            LinearLayout row = new LinearLayout(this);
            Button open = btn("Profile ↗"); open.setOnClickListener(v -> openProfile(h));
            Button un = btn("Un-followed ✓"); un.setOnClickListener(v -> { data.decide(h, "unfollowed"); showFireworks(); refreshPanel(); });
            Button keep = btn("Keep"); keep.setOnClickListener(v -> { data.decide(h, "keep"); refreshPanel(); });
            Button snooze = btn("Snooze"); snooze.setOnClickListener(v -> { data.decide(h, "snooze"); refreshPanel(); });
            row.addView(open); row.addView(un); row.addView(keep); row.addView(snooze);
            panel.addView(row);
        } else {
            panel.addView(line("Prune: today's ten are decided ✦", 13, 0xFFB8A888));
        }

        // next due memory card
        final String card = data.nextDueCard();
        if (card != null) {
            JSONObject m = data.moots.optJSONObject(data.findKey(data.moots, card));
            panel.addView(line("Recall: what is @" + card + " into?", 14, 0xFFF3E5C0));
            final TextView answer = line("tap reveal…", 12, 0xFFB8A888);
            panel.addView(answer);
            LinearLayout row = new LinearLayout(this);
            Button reveal = btn("Reveal");
            final String ints = data.interestsLine(m == null ? new JSONObject() : m);
            reveal.setOnClickListener(v -> answer.setText(ints));
            Button knew = btn("Knew it"); knew.setOnClickListener(v -> { data.grade(card, true); showFireworks(); refreshPanel(); });
            Button missed = btn("Missed"); missed.setOnClickListener(v -> { data.grade(card, false); refreshPanel(); });
            row.addView(reveal); row.addView(knew); row.addView(missed);
            panel.addView(row);
        } else {
            panel.addView(line("Recall: all caught up ✦", 13, 0xFFB8A888));
        }

        LinearLayout foot = new LinearLayout(this);
        Button test = btn("✦ Test fireworks"); test.setOnClickListener(v -> showFireworks());
        Button close = btn("Close panel"); close.setOnClickListener(v -> hidePanel());
        Button stop = btn("Stop the flame"); stop.setOnClickListener(v -> stopSelf());
        foot.addView(test); foot.addView(close); foot.addView(stop);
        panel.addView(foot);

        panelParams = new WindowManager.LayoutParams(dp(320), WindowManager.LayoutParams.WRAP_CONTENT, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = dp(12); panelParams.y = dp(180);
        wm.addView(panel, panelParams);
    }
    private void refreshPanel() { hidePanel(); showPanel(); }
    private void hidePanel() { if (panel != null) { wm.removeView(panel); panel = null; } }

    public void openProfile(String handle) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/" + handle));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override public void onDestroy() {
        RadarService.overlayOn = false;
        RadarService.overlayStopped();
        if (bubble != null) wm.removeView(bubble);
        hidePanel();
        super.onDestroy();
    }
}
