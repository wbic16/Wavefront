package io.phext.wavefront;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Moot Radar (v1.4 badge design): an accessibility service scoped
 *  to the X app only. It reads the handles already on screen, finds the
 *  post at the centre of the view, and shows its caste as one small
 *  badge anchored just below the Grok button. No post boxes — the
 *  boxes never aligned. On-device only; nothing is transmitted. */
public class RadarService extends AccessibilityService {
    public static volatile boolean connected = false;
    public static volatile boolean overlayOn = false;
    public static volatile int lastNodes = 0, lastHandles = 0, lastMarks = 0;
    public static volatile long lastScanAt = 0;
    private static volatile RadarService instance = null;
    public static void overlayStopped() { if (instance != null) instance.clearAll(); }

    private static final Pattern HANDLE = Pattern.compile("@([A-Za-z0-9_]{1,15})");
    private WindowManager wm;
    private EffectsView fx;
    private Data data;
    private long lastRun = 0;
    private int budget, scanNodes, scanHandles;
    private boolean badgeShown = false;
    private final Set<String> celebrated = new HashSet<>();
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    private static class Post {
        String handle; Data.Caste caste; Rect rect;
        Post(String h, Data.Caste c, Rect r) { handle = h; caste = c; rect = r; }
    }
    private final List<Post> posts = new ArrayList<>();
    private final List<Rect> grokButtons = new ArrayList<>();

    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (!overlayOn || !xInForeground()) clearAll();
            ui.postDelayed(this, 800);
        }
    };

    private boolean xInForeground() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            return root != null && "com.twitter.android".contentEquals(root.getPackageName());
        } catch (Exception e) { return false; }
    }
    private void clearAll() {
        if (fx != null && badgeShown) { fx.clearBadges(); badgeShown = false; }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        instance = this;
        data = Data.get(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        fx = new EffectsView(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try { wm.addView(fx, lp); } catch (Exception ignored) {}
        ui.postDelayed(() -> {
            if (fx != null) {
                int w = getResources().getDisplayMetrics().widthPixels;
                int h = getResources().getDisplayMetrics().heightPixels;
                fx.burst(w * 0.5f, h * 0.35f);
            }
        }, 700);
        ui.postDelayed(heartbeat, 900);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.twitter.android".contentEquals(event.getPackageName())) return;
        if (!overlayOn) { clearAll(); return; }
        scan(false);
    }

    private void scan(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastRun < 90) return;
        lastRun = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !"com.twitter.android".contentEquals(root.getPackageName())) { clearAll(); return; }
        budget = 900; scanNodes = 0; scanHandles = 0;
        posts.clear(); grokButtons.clear();
        walk(root);
        lastNodes = scanNodes; lastHandles = scanHandles; lastScanAt = now;
        if (fx == null) return;
        List<EffectsView.Badge> badges = new ArrayList<>();
        for (Post p : posts) {
            Rect anchor = null;
            for (Rect g : grokButtons) {
                if (g.centerX() >= p.rect.left && g.centerX() <= p.rect.right
                        && g.centerY() >= p.rect.top && g.centerY() <= p.rect.bottom) { anchor = g; break; }
            }
            if (anchor == null) anchor = new Rect(p.rect.right - 90, p.rect.top + 6, p.rect.right - 10, p.rect.top + 46);
            badges.add(new EffectsView.Badge(p.handle, p.caste, anchor));
            if ((p.caste == Data.Caste.MOOT || p.caste == Data.Caste.ALIGNED)
                    && celebrated.add(p.handle.toLowerCase())) {
                data.logEncounter(p.handle, p.caste.name().toLowerCase());
                if (p.caste == Data.Caste.MOOT) fx.burstBelow(anchor, p.handle);
            }
        }
        lastMarks = badges.size();
        if (badges.isEmpty()) { clearAll(); return; }
        fx.setBadges(badges);
        badgeShown = true;
    }

    private void walk(AccessibilityNodeInfo node) {
        if (node == null || budget-- <= 0) return;
        scanNodes++;
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().contains("Grok")) {
            Rect r = new Rect(); node.getBoundsInScreen(r);
            if (r.width() > 0) grokButtons.add(r);
        }
        CharSequence text = node.getText();
        if (text == null) text = desc;
        if (text != null && text.length() > 1 && text.length() < 220) {
            Matcher m = HANDLE.matcher(text);
            if (m.find()) {
                scanHandles++;
                String handle = m.group(1);
                Data.Caste caste = data.casteOf(handle);
                if (caste != Data.Caste.SELF) {
                    Rect post = postBounds(node);
                    if (post != null) posts.add(new Post(handle, caste, post));
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i));
    }

    /** The post cell is the first near-full-width container above the handle. */
    private Rect postBounds(AccessibilityNodeInfo node) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        Rect best = null;
        AccessibilityNodeInfo cur = node;
        for (int depth = 0; depth < 7 && cur != null; depth++) {
            Rect r = new Rect();
            cur.getBoundsInScreen(r);
            if (r.width() >= screenW * 0.88 && r.height() >= 100 && r.height() <= screenH * 0.55) return r;
            if (r.width() >= screenW * 0.55 && r.height() >= 110 && r.height() <= screenH * 0.62) best = r;
            cur = cur.getParent();
        }
        if (best == null) {
            Rect r = new Rect();
            node.getBoundsInScreen(r);
            if (r.height() < 60) r.bottom += 160;
            best = r;
        }
        return best;
    }

    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        connected = false;
        instance = null;
        ui.removeCallbacksAndMessages(null);
        if (fx != null && wm != null) { try { wm.removeView(fx); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
