package io.phext.wavefront;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Share-sheet target: from the X app, share a post or profile to
 *  Golden Thread — the encounter is logged and the persona card shows. */
public class ShareActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Data data = Data.get(this);
        String text = getIntent() == null ? "" : String.valueOf(getIntent().getStringExtra(Intent.EXTRA_TEXT));
        String handle = extractHandle(text);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 40, 48, 40);
        TextView t = new TextView(this); t.setTextSize(15);
        if (handle == null) {
            t.setText("No @handle found in what you shared.");
        } else {
            Data.Caste caste = data.casteOf(handle);
            data.logEncounter(handle, caste.name().toLowerCase());
            String key = data.findKey(data.moots, handle);
            JSONObject m = key == null ? null : data.moots.optJSONObject(key);
            String keyF = data.findKey(data.follows, handle);
            JSONObject f = keyF == null ? null : data.follows.optJSONObject(keyF);
            StringBuilder sb = new StringBuilder();
            sb.append("@").append(handle).append(" — ").append(caste.name().toLowerCase()).append("\n");
            if (m != null) sb.append("Into (canon): ").append(data.interestsLine(m)).append("\n")
                    .append("Coordinate: ").append(m.optString("coord")).append("\n")
                    .append("Mentioned ").append(m.optInt("mentions")).append("× · retweeted ").append(m.optInt("rt")).append("×");
            else if (f != null) sb.append("Into (canon): ").append(data.interestsLine(f)).append("\n")
                    .append("You follow; no follow-back").append(f.optBoolean("aligned") ? " — but aligned ✦" : ".");
            else sb.append("Not in the canon — half-erased, or new since the close.");
            sb.append("\nEncounter logged ✦");
            t.setText(sb.toString());
            Button open = new Button(this); open.setText("Open profile ↗");
            final String h = handle;
            open.setOnClickListener(v -> {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/" + h)));
                finish();
            });
            root.addView(open);
        }
        root.addView(t, 0);
        Button done = new Button(this); done.setText("Done"); done.setOnClickListener(v -> finish());
        root.addView(done);
        setContentView(root);
    }
    static String extractHandle(String text) {
        if (text == null) return null;
        Matcher u = Pattern.compile("x\\.com/([A-Za-z0-9_]{1,15})").matcher(text);
        if (u.find() && !"wbic16".equalsIgnoreCase(u.group(1))) return u.group(1);
        Matcher m = Pattern.compile("@([A-Za-z0-9_]{1,15})").matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
