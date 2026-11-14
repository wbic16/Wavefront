package io.phext.wavefront;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The canon (assets/wavefront.json) + local state, in the same JSON
 *  shapes the Chrome extension uses, so logs and decisions move
 *  between Will's surfaces by export/import. */
public class Data {
    public JSONObject moots, follows;
    public JSONArray prune;
    private final SharedPreferences prefs;

    private static Data instance;
    public static synchronized Data get(Context c) {
        if (instance == null) instance = new Data(c.getApplicationContext());
        return instance;
    }

    private Data(Context c) {
        prefs = c.getSharedPreferences("golden-thread", Context.MODE_PRIVATE);
        try {
            InputStream in = c.getAssets().open("wavefront.json");
            byte[] buf = new byte[in.available()];
            //noinspection ResultOfMethodCallIgnored
            in.read(buf); in.close();
            JSONObject root = new JSONObject(new String(buf, StandardCharsets.UTF_8));
            moots = root.getJSONObject("moots");
            follows = root.getJSONObject("follows");
            prune = root.getJSONArray("prune");
        } catch (Exception e) {
            moots = new JSONObject(); follows = new JSONObject(); prune = new JSONArray();
        }
    }

    public enum Caste { MOOT, ALIGNED, MUTED, CHAFF, SELF }
    public Caste casteOf(String handle) {
        if (handle == null) return Caste.CHAFF;
        String h = handle.replace("@", "").trim();
        if (h.equalsIgnoreCase("wbic16")) return Caste.SELF;
        if (findKey(moots, h) != null) return Caste.MOOT;
        String fk = findKey(follows, h);
        if (fk != null) {
            try { return follows.getJSONObject(fk).optBoolean("aligned") ? Caste.ALIGNED : Caste.MUTED; }
            catch (Exception e) { return Caste.MUTED; }
        }
        return Caste.CHAFF;
    }
    public String findKey(JSONObject o, String handle) {
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) { String k = it.next(); if (k.equalsIgnoreCase(handle)) return k; }
        return null;
    }
    public String interestsLine(JSONObject entry) {
        JSONArray a = entry.optJSONArray("interests");
        if (a == null || a.length() == 0) return "quiet in the canon";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) parts.add(a.optString(i));
        return String.join(" · ", parts);
    }

    // ---- prune state (same shape as the extension) ----
    public JSONObject pruneState() {
        try { return new JSONObject(prefs.getString("prune", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    public void savePruneState(JSONObject s) { prefs.edit().putString("prune", s.toString()).apply(); }
    public String todayStr() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    /** Today's 10, rolled when the local day changes. */
    public List<String> todaysPrune() {
        JSONObject s = pruneState();
        try {
            if (!todayStr().equals(s.optString("day"))) {
                JSONObject dec = s.optJSONObject("decisions");
                if (dec == null) dec = new JSONObject();
                JSONArray today = new JSONArray();
                long now = System.currentTimeMillis();
                for (int i = 0; i < prune.length() && today.length() < 10; i++) {
                    String h = prune.getString(i);
                    JSONObject d = dec.optJSONObject(h);
                    if (d != null && ("unfollowed".equals(d.optString("a")) || "keep".equals(d.optString("a"))
                            || ("snooze".equals(d.optString("a")) && d.optLong("until") > now))) continue;
                    today.put(h);
                }
                s.put("day", todayStr()); s.put("today", today); s.put("decisions", dec);
                savePruneState(s);
            }
            List<String> out = new ArrayList<>();
            JSONArray t = s.optJSONArray("today");
            if (t != null) for (int i = 0; i < t.length(); i++) out.add(t.getString(i));
            return out;
        } catch (Exception e) { return new ArrayList<>(); }
    }
    public void decide(String handle, String action) {
        JSONObject s = pruneState();
        try {
            JSONObject dec = s.optJSONObject("decisions");
            if (dec == null) dec = new JSONObject();
            JSONObject d = new JSONObject();
            d.put("a", action); d.put("day", todayStr()); d.put("ts", System.currentTimeMillis());
            d.put("until", "snooze".equals(action) ? System.currentTimeMillis() + 30L * 86400000L : 0);
            dec.put(handle, d); s.put("decisions", dec); savePruneState(s);
        } catch (Exception ignored) {}
    }
    public String decisionOf(String handle) {
        JSONObject dec = pruneState().optJSONObject("decisions");
        if (dec == null) return null;
        JSONObject d = dec.optJSONObject(handle);
        return d == null ? null : d.optString("a", null);
    }

    // ---- spaced repetition (same intervals as the extension) ----
    public JSONObject srs() {
        try { return new JSONObject(prefs.getString("srs", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    public List<String> deckHandles() {
        List<String> out = new ArrayList<>();
        java.util.Iterator<String> it = moots.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONArray a = moots.optJSONObject(k) == null ? null : moots.optJSONObject(k).optJSONArray("interests");
            if (a != null && a.length() > 0) out.add(k);
        }
        return out;
    }
    public String nextDueCard() {
        JSONObject s = srs(); long now = System.currentTimeMillis();
        for (String h : deckHandles()) {
            JSONObject c = s.optJSONObject(h);
            if (c == null || c.optLong("due") <= now) return h;
        }
        return null;
    }
    public void grade(String handle, boolean correct) {
        JSONObject s = srs();
        try {
            JSONObject c = s.optJSONObject(handle);
            if (c == null) c = new JSONObject();
            long interval = c.optLong("interval");
            if (correct) { interval = interval == 0 ? 1 : (long) Math.ceil(interval * 2.5); c.put("reps", c.optInt("reps") + 1); }
            else { interval = 1; c.put("reps", 0); }
            c.put("interval", interval);
            c.put("due", System.currentTimeMillis() + interval * 86400000L);
            s.put(handle, c);
            prefs.edit().putString("srs", s.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ---- encounters ----
    public void logEncounter(String handle, String caste) {
        try {
            JSONArray log = new JSONArray(prefs.getString("encounters", "[]"));
            JSONObject e = new JSONObject();
            e.put("handle", handle); e.put("ts", System.currentTimeMillis()); e.put("cls", caste); e.put("surface", "android");
            log.put(e);
            while (log.length() > 500) log.remove(0);
            prefs.edit().putString("encounters", log.toString()).apply();
        } catch (Exception ignored) {}
    }
    public String exportState() {
        JSONObject o = new JSONObject();
        try {
            o.put("encounters", new JSONArray(prefs.getString("encounters", "[]")));
            o.put("srs", srs());
            o.put("prune", pruneState());
        } catch (Exception ignored) {}
        return o.toString();
    }
}
