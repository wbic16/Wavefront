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
    public static volatile String lastDebug = "(no scan yet)";
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
    private final List<Rect> avatarCandidates = new ArrayList<>();

    private StringBuilder dbg = null;
    private final Runnable settle = () -> scan(true);
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
        if (fx != null && badgeShown) { fx.clearHalos(); badgeShown = false; }
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
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            int dy = event.getScrollDeltaY();
            if (dy != 0 && fx != null) fx.shiftHalos(-dy);
            ui.removeCallbacks(settle);
            ui.postDelayed(settle, 160);
            return;
        }
        scan(false);
    }

    private void scan(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastRun < 90) return;
        lastRun = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !"com.twitter.android".contentEquals(root.getPackageName())) { clearAll(); return; }
        budget = 900; scanNodes = 0; scanHandles = 0;
        dbg = new StringBuilder();
        dbg.append("scan ").append(new java.util.Date(now)).append(" screen ")
           .append(getResources().getDisplayMetrics().widthPixels).append("x")
           .append(getResources().getDisplayMetrics().heightPixels)
           .append(" density ").append(getResources().getDisplayMetrics().density).append("\n");
        posts.clear(); avatarCandidates.clear();
        walk(root);
        lastNodes = scanNodes; lastHandles = scanHandles; lastScanAt = now;
        if (fx == null) return;
        List<EffectsView.Halo> halos = new ArrayList<>();
        for (Post p : posts) {
            Rect pfp = null; long bestScore = Long.MAX_VALUE;
            for (Rect a : avatarCandidates) {
                if (a.centerX() < p.rect.left || a.centerX() > p.rect.right
                        || a.centerY() < p.rect.top || a.centerY() > p.rect.bottom) continue;
                long score = (long) (a.left - p.rect.left) * 4 + (a.top - p.rect.top);
                if (score < bestScore) { bestScore = score; pfp = a; }
            }
            if (dbg != null && halos.size() < 14) dbg.append("post @").append(p.handle)
                .append(" cell ").append(p.rect.toShortString())
                .append(" pfp ").append(pfp == null ? "NONE" : pfp.toShortString()).append("\n");
            if (pfp == null) continue;
            halos.add(new EffectsView.Halo(p.caste, pfp));
            if ((p.caste == Data.Caste.MOOT || p.caste == Data.Caste.ALIGNED)
                    && celebrated.add(p.handle.toLowerCase())) {
                data.logEncounter(p.handle, p.caste.name().toLowerCase());
                if (p.caste == Data.Caste.MOOT) fx.burst(pfp.centerX(), pfp.centerY());
            }
        }
        lastMarks = halos.size();
        if (dbg != null) {
            dbg.append("candidates: ");
            int shown = 0;
            for (Rect a : avatarCandidates) { if (shown++ < 18) dbg.append(a.toShortString()).append(" "); }
            dbg.append("\nnodes ").append(scanNodes).append(" handles ").append(scanHandles)
               .append(" posts ").append(posts.size()).append(" halos ").append(halos.size());
            lastDebug = dbg.toString();
        }
        if (halos.isEmpty()) { clearAll(); return; }
        fx.setHalos(halos);
        badgeShown = true;
    }

    private void walk(AccessibilityNodeInfo node) {
        if (node == null || budget-- <= 0) return;
        scanNodes++;
        CharSequence desc = node.getContentDescription();
        // profile pictures are squarish image-sized nodes; collect them
        // all and let each post claim the one nearest its top-left
        {
            Rect r = new Rect(); node.getBoundsInScreen(r);
            int w = r.width(), h = r.height();
            if (w >= 60 && w <= 210 && h >= 60 && h <= 210
                    && Math.abs(w - h) <= Math.max(w, h) * 0.3) {
                boolean dup = false;
                for (Rect a : avatarCandidates) if (Math.abs(a.left - r.left) < 6 && Math.abs(a.top - r.top) < 6) { dup = true; break; }
                if (!dup) avatarCandidates.add(r);
            }
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
