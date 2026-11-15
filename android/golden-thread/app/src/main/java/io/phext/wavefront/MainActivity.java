package io.phext.wavefront;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

/** Home base: overlay permission + start/stop, handle lookup against
 *  the canon, and state export (same JSON as the Chrome extension). */
public class MainActivity extends Activity {
    private Data data;
    private TextView status, cardOut;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        data = Data.get(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 48, 40, 40);

        TextView title = new TextView(this);
        title.setText("✦ Golden Thread"); title.setTextSize(22); title.setTextColor(0xFFD4A017);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("The OG Wavefront rides over the X app: a floating flame, today's prune, and your memory of the moots — one canon, shared with the Win11 Chrome extension.");
        sub.setTextColor(0xFFF3E5C0); root.addView(sub);

        status = new TextView(this); status.setTextColor(0xFFB8A888); root.addView(status);

        Button grant = new Button(this); grant.setText("Grant overlay permission");
        grant.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        root.addView(grant);
        Button start = new Button(this); start.setText("Start the flame");
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                status.setText("Overlay permission not granted yet.");
                return;
            }
            startForegroundService(new Intent(this, OverlayService.class));
            status.setText("The flame is lit — switch to X and look for the soul-print bubble.");
        });
        root.addView(start);
        Button stop = new Button(this); stop.setText("Stop the flame");
        stop.setOnClickListener(v -> { stopService(new Intent(this, OverlayService.class)); status.setText("Stopped."); });
        root.addView(stop);

        TextView lookLabel = new TextView(this);
        lookLabel.setText("\nLook up a handle"); lookLabel.setTextColor(0xFFF3E5C0);
        root.addView(lookLabel);
        EditText field = new EditText(this); field.setHint("@handle"); field.setTextColor(0xFFF3E5C0);
        root.addView(field);
        cardOut = new TextView(this); cardOut.setTextColor(0xFFF3E5C0);
        Button look = new Button(this); look.setText("Show card");
        look.setOnClickListener(v -> showCard(field.getText().toString()));
        root.addView(look); root.addView(cardOut);

        Button radar = new Button(this); radar.setText("Enable Moot Radar (accessibility) — feed effects while you scroll X");
        radar.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(radar);
        Button export = new Button(this); export.setText("Share state (encounters · srs · prune)");
        export.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, data.exportState());
            startActivity(Intent.createChooser(i, "Golden Thread state"));
        });
        root.addView(export);

        ScrollView sv = new ScrollView(this); sv.addView(root);
        setContentView(sv);
        refreshStatus();
    }
    @Override protected void onResume() { super.onResume(); refreshStatus(); }
    private void refreshStatus() {
        boolean can = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        status.setText("Moot Radar: " + (RadarService.connected ? "on — feed effects live" : "off (enable in Accessibility)")
                + "\nLast radar scan: " + (RadarService.lastScanAt == 0 ? "none yet"
                        : (RadarService.lastNodes + " nodes, " + RadarService.lastHandles + " handles, " + RadarService.lastMarks + " marks"))
                + "\nOverlay permission: " + (can ? "granted" : "not granted")
                + " · moots in canon: " + data.moots.length()
                + " · prune pool: " + data.prune.length());
    }
    private void showCard(String raw) {
        String h = raw.replace("@", "").trim();
        if (h.isEmpty()) { cardOut.setText(""); return; }
        Data.Caste caste = data.casteOf(h);
        data.logEncounter(h, caste.name().toLowerCase());
        String key = data.findKey(data.moots, h);
        JSONObject m = key == null ? null : data.moots.optJSONObject(key);
        String keyF = data.findKey(data.follows, h);
        JSONObject f = keyF == null ? null : data.follows.optJSONObject(keyF);
        StringBuilder sb = new StringBuilder("@" + h + " — " + caste.name().toLowerCase() + "\n");
        if (m != null) sb.append("Into (canon): ").append(data.interestsLine(m)).append("\nCoordinate: ")
                .append(m.optString("coord")).append("\nMentioned ").append(m.optInt("mentions"))
                .append("× · retweeted ").append(m.optInt("rt")).append("× · since ").append(m.optString("first"));
        else if (f != null) sb.append("Into (canon): ").append(data.interestsLine(f))
                .append("\nYou follow; no follow-back").append(f.optBoolean("aligned") ? " — but aligned ✦" : ".");
        else sb.append("Not in the canon.");
        cardOut.setText(sb.toString());
    }
}
