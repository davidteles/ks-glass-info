// SPDX-License-Identifier: Apache-2.0
package com.davidteles.ks.glassinfo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import me.jxl.kiosk.plugins.KsTheme;
import org.json.JSONArray;
import org.json.JSONObject;

/** Our own glass widgets over any screensaver: clock, weather, next events and a chip row. Draws only; no touches. */
final class GlassWidgetsView extends View {
    interface Source { Snapshot snapshot(); }

    /** Immutable data for one frame. */
    static final class Snapshot {
        final JSONObject live; final JSONArray events; final int accent; final boolean h24;
        Snapshot(JSONObject live, JSONArray events, int accent, boolean h24) { this.live = live; this.events = events; this.accent = accent; this.h24 = h24; }
    }

    private final KsTheme theme;
    private final Source source;
    private final boolean compact;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            long now = System.currentTimeMillis();
            postDelayed(this, 1000 - now % 1000 + 10);
        }
    };

    GlassWidgetsView(Context context, KsTheme theme, Source source) { this(context, theme, source, false); }

    /** compact = one slim strip at the top, for full-screen Now Playing. */
    GlassWidgetsView(Context context, KsTheme theme, Source source, boolean compact) {
        super(context);
        this.theme = theme;
        this.source = source;
        this.compact = compact;
        fill.setColor(Color.argb(118, 10, 12, 16));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(46, 255, 255, 255));
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private void font(float sp, int weight, int color) {
        text.setTypeface(theme.typeface(weight));
        text.setTextSize(dp(sp));
        text.setColor(color);
    }

    private void card(float l, float t, float r, float b) {
        rect.set(l, t, r, b);
        float radius = dp(22);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, stroke);
    }

    private Canvas canvas;
    private static final int INK = Color.rgb(243, 238, 230);
    private static final int MUTED = Color.argb(160, 243, 238, 230);

    @Override protected void onDraw(Canvas c) {
        Snapshot s = source.snapshot();
        if (s == null) return;
        canvas = c;
        if (compact) { drawStrip(c, s); return; }
        float m = dp(28), pad = dp(20);
        float w = getWidth(), h = getHeight();
        Date now = new Date();

        // Clock + date, top left.
        String time = new SimpleDateFormat(s.h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now);
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        if (!date.isEmpty()) date = Character.toUpperCase(date.charAt(0)) + date.substring(1);
        font(66, 300, INK); float tw = text.measureText(time); float th = dp(66);
        font(17, 400, s.accent); float dw = text.measureText(date);
        float cw = Math.max(tw, dw) + pad * 2, ch = pad * 2 + th + dp(26);
        card(m, m, m + cw, m + ch);
        font(66, 300, INK); c.drawText(time, m + pad, m + pad + th * 0.82f, text);
        font(17, 400, s.accent); c.drawText(date, m + pad, m + pad + th + dp(20), text);

        // Weather + inside, top right.
        JSONObject live = s.live == null ? new JSONObject() : s.live;
        Object temp = live.opt("temp");
        if (temp instanceof Number) {
            String t = Math.round(((Number) temp).doubleValue()) + live.optString("unit", "°");
            String cond = condition(live.optString("cond"));
            String inside = live.optString("inside");
            String in = inside.isEmpty() || inside.contains("unknown") || inside.contains("unavailable") ? "" : inside + " inside";
            font(42, 300, INK); float t1 = text.measureText(t);
            font(15, 400, MUTED); float t2 = Math.max(text.measureText(cond), text.measureText(in));
            float icon = dp(48);
            float ww = pad * 2 + icon + dp(14) + Math.max(t1, t2), wh = pad * 2 + dp(42) + dp(44);
            float l = w - m - ww;
            card(l, m, w - m, m + wh);
            drawIcon(weatherIcon(live.optString("cond")), l + pad, m + pad + dp(6), icon);
            float x = l + pad + icon + dp(14);
            font(42, 300, INK); c.drawText(t, x, m + pad + dp(36), text);
            font(15, 400, MUTED); c.drawText(cond, x, m + pad + dp(62), text);
            if (!in.isEmpty()) c.drawText(in, x, m + pad + dp(82), text);
        }

        // Chips, bottom centre: alerts first, then the glance items.
        List<String[]> chips = new ArrayList<>();
        for (String a : live.optString("info").split(" · ")) {
            String v = a.trim();
            if (!v.isEmpty() && !v.equals("unknown") && !v.equals("unavailable")) chips.add(new String[] {"", v});
        }
        JSONArray g = live.optJSONArray("g");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            JSONArray item = g.optJSONArray(i);
            if (item != null && item.length() >= 2) chips.add(new String[] {item.optString(0), item.optString(1)});
        }
        float chipH = dp(44), gap = dp(10), total = 0;
        float[] widths = new float[chips.size()];
        for (int i = 0; i < chips.size(); i++) {
            String[] ch2 = chips.get(i);
            font(12, 400, MUTED); float a = ch2[0].isEmpty() ? 0 : text.measureText(ch2[0]);
            font(15, 500, INK); float b = text.measureText(ch2[1]);
            widths[i] = Math.max(a, b) + dp(32);
            total += widths[i] + (i > 0 ? gap : 0);
        }
        float chipTop = h - m - chipH;
        float x0 = Math.max(m, (w - total) / 2);
        for (int i = 0; i < chips.size(); i++) {
            String[] ch2 = chips.get(i);
            if (x0 + widths[i] > w - m) break;
            rect.set(x0, chipTop, x0 + widths[i], chipTop + chipH);
            boolean alert = ch2[0].isEmpty();
            fill.setColor(alert ? Color.argb(120, 70, 48, 12) : Color.argb(118, 10, 12, 16));
            c.drawRoundRect(rect, chipH / 2, chipH / 2, fill);
            c.drawRoundRect(rect, chipH / 2, chipH / 2, stroke);
            fill.setColor(Color.argb(118, 10, 12, 16));
            if (alert) {
                font(15, 500, INK); c.drawText(ch2[1], x0 + dp(16), chipTop + dp(28), text);
            } else {
                font(12, 400, MUTED); c.drawText(ch2[0], x0 + dp(16), chipTop + dp(18), text);
                font(15, 500, INK); c.drawText(ch2[1], x0 + dp(16), chipTop + dp(35), text);
            }
            x0 += widths[i] + gap;
        }

        // Next events, bottom left above the chips.
        JSONArray ev = s.events;
        if (ev != null && ev.length() > 0) {
            int n = Math.min(ev.length(), 4);
            float row = dp(48), ew = Math.min(dp(380), w * 0.36f);
            float eh = pad * 2 + n * row - dp(8);
            float top = chipTop - dp(16) - eh;
            card(m, top, m + ew, top + eh);
            for (int i = 0; i < n; i++) {
                JSONObject e = ev.optJSONObject(i);
                if (e == null) continue;
                float y = top + pad + i * row;
                fill.setColor(e.optInt("c", 1) == 2 ? Color.rgb(224, 178, 94) : Color.rgb(143, 201, 138));
                rect.set(m + pad, y + dp(2), m + pad + dp(4), y + row - dp(12));
                c.drawRoundRect(rect, dp(2), dp(2), fill);
                fill.setColor(Color.argb(118, 10, 12, 16));
                float tx = m + pad + dp(14), avail = ew - pad * 2 - dp(14);
                font(13, 400, MUTED); c.drawText(when(e.optString("s"), s.h24), tx, y + dp(13), text);
                font(17, 400, INK);
                c.drawText(TextUtils.ellipsize(e.optString("m"), text, avail, TextUtils.TruncateAt.END).toString(), tx, y + dp(34), text);
            }
        }
    }

    /** Time · weather · inside · alerts in one glass pill, centred at the top. */
    private void drawStrip(Canvas c, Snapshot s) {
        JSONObject live = s.live == null ? new JSONObject() : s.live;
        List<String> parts = new ArrayList<>();
        parts.add(new SimpleDateFormat(s.h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date()));
        Object temp = live.opt("temp");
        boolean weather = temp instanceof Number;
        if (weather) parts.add(Math.round(((Number) temp).doubleValue()) + live.optString("unit", "°") + " " + condition(live.optString("cond")).toLowerCase(Locale.getDefault()));
        String inside = live.optString("inside");
        if (!inside.isEmpty() && !inside.contains("unknown") && !inside.contains("unavailable")) parts.add(inside + " inside");
        for (String a : live.optString("info").split(" · ")) {
            String v = a.trim();
            if (!v.isEmpty() && !v.equals("unknown") && !v.equals("unavailable")) parts.add(v);
        }
        String line = TextUtils.join("   ·   ", parts);
        font(16, 400, INK);
        float maxW = getWidth() - dp(56) * 2;
        String shown = TextUtils.ellipsize(line, text, maxW - dp(40), TextUtils.TruncateAt.END).toString();
        float tw = text.measureText(shown), h = dp(44), top = dp(20);
        float l = (getWidth() - tw) / 2 - dp(20);
        rect.set(l, top, l + tw + dp(40), top + h);
        c.drawRoundRect(rect, h / 2, h / 2, fill);
        c.drawRoundRect(rect, h / 2, h / 2, stroke);
        c.drawText(shown, l + dp(20), top + dp(28), text);
    }

    private void drawIcon(String name, float x, float y, float size) {
        Path path = theme.iconPath(name);
        if (path == null) return;
        Matrix mx = new Matrix();
        mx.setScale(size / 24f, size / 24f);
        mx.postTranslate(x, y);
        path.transform(mx);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(214, 221, 232));
        canvas.drawPath(path, p);
    }

    static String weatherIcon(String cond) {
        switch (cond) {
            case "sunny": return "mdi:weather-sunny";
            case "clear-night": return "mdi:weather-night";
            case "partlycloudy": return "mdi:weather-partly-cloudy";
            case "rainy": return "mdi:weather-rainy";
            case "pouring": return "mdi:weather-pouring";
            case "lightning": case "lightning-rainy": return "mdi:weather-lightning";
            case "fog": return "mdi:weather-fog";
            case "snowy": case "snowy-rainy": return "mdi:weather-snowy";
            case "windy": case "windy-variant": return "mdi:weather-windy";
            case "hail": return "mdi:weather-hail";
            default: return "mdi:weather-cloudy";
        }
    }

    static String condition(String cond) {
        switch (cond) {
            case "partlycloudy": return "Partly cloudy";
            case "clear-night": return "Clear";
            case "lightning-rainy": return "Thunderstorm";
            case "snowy-rainy": return "Sleet";
            case "windy-variant": return "Windy";
            default: return cond.isEmpty() ? "" : Character.toUpperCase(cond.charAt(0)) + cond.substring(1).replace('-', ' ');
        }
    }

    static String when(String start, boolean h24) {
        try {
            boolean allDay = start.length() == 10;
            Date d = allDay ? new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(start)
                    : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(start);
            Calendar today = Calendar.getInstance();
            Calendar day = Calendar.getInstance();
            day.setTime(d);
            int diff = (int) Math.round((strip(day) - strip(today)) / 86400000.0);
            String dayText = diff <= 0 ? "Today" : diff == 1 ? "Tomorrow" : new SimpleDateFormat("EEE d", Locale.getDefault()).format(d);
            String timeText = allDay ? "All day" : new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(d);
            return dayText + " · " + timeText;
        } catch (Exception error) {
            return start;
        }
    }

    private static long strip(Calendar c) {
        Calendar x = (Calendar) c.clone();
        x.set(Calendar.HOUR_OF_DAY, 0); x.set(Calendar.MINUTE, 0); x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }
}
