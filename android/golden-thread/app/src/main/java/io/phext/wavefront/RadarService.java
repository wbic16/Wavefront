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

/** The Moot Radar: an accessibility service scoped to the X app only.
 *  It reads the handles already on screen, classifies them against the
 *  canon, and draws caste effects at their posts' screen positions.
 *  On-device only; nothing is transmitted anywhere. */
public class RadarService extends AccessibilityService {
    public static volatile boolean connected = false;
    private static final Pattern HANDLE = Pattern.compile("@([A-Za-z0-9_]{1,15})");
    private WindowManager wm;
    private EffectsView fx;
    private Data data;
    private long lastRun = 0;
    private final Set<String> celebrated = new HashSet<>();
    private int budget;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
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
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.twitter.android".contentEquals(event.getPackageName())) return;
        long now = System.currentTimeMillis();
        if (now - lastRun < 250) return;
        lastRun = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        budget = 700;
        List<EffectsView.Mark> marks = new ArrayList<>();
        Set<String> seenRects = new HashSet<>();
        walk(root, marks, seenRects);
        if (fx != null) fx.setMarks(marks);
    }

    private void walk(AccessibilityNodeInfo node, List<EffectsView.Mark> marks, Set<String> seenRects) {
        if (node == null || budget-- <= 0) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 1 && text.length() < 80) {
            Matcher m = HANDLE.matcher(text);
            if (m.find()) {
                String handle = m.group(1);
                Data.Caste caste = data.casteOf(handle);
                if (caste != Data.Caste.SELF) {
                    Rect post = postBounds(node);
                    if (post != null) {
                        String key = post.left + ":" + post.top + ":" + post.bottom;
                        if (seenRects.add(key)) marks.add(new EffectsView.Mark(post, caste));
                        if ((caste == Data.Caste.MOOT || caste == Data.Caste.ALIGNED)
                                && celebrated.add(handle.toLowerCase())) {
                            data.logEncounter(handle, caste.name().toLowerCase());
                            if (fx != null) fx.burst(post.centerX(), post.top + 40);
                        }
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), marks, seenRects);
    }

    /** Climb from the handle node to the enclosing post-sized container. */
    private Rect postBounds(AccessibilityNodeInfo node) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        Rect best = null;
        AccessibilityNodeInfo cur = node;
        for (int depth = 0; depth < 7 && cur != null; depth++) {
            Rect r = new Rect();
            cur.getBoundsInScreen(r);
            if (r.width() >= screenW * 0.55 && r.height() >= 110 && r.height() <= screenH * 0.62) best = r;
            if (r.width() >= screenW * 0.92 && best != null) break;
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
        if (fx != null && wm != null) { try { wm.removeView(fx); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
