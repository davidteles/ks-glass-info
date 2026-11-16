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
        final JSONObject live; final JSONArray events; final int accent; final boolean h24; final java.util.Map<String, Object> opt;
        Snapshot(JSONObject live, JSONArray events, int accent, boolean h24) { this(live, events, accent, h24, new java.util.HashMap<String, Object>()); }
        Snapshot(JSONObject live, JSONArray events, int accent, boolean h24, java.util.Map<String, Object> opt) {
            this.live = live; this.events = events; this.accent = accent; this.h24 = h24; this.opt = opt;
        }
        String str(String k, String d) { Object v = opt.get(k); return v instanceof String && !((String) v).isEmpty() ? (String) v : d; }
        boolean on(String k) { return !Boolean.FALSE.equals(opt.get(k)); }
        float num(String k, float d, float lo, float hi) { Object v = opt.get(k); float f = v instanceof Number ? ((Number) v).floatValue() : d; return Math.max(lo, Math.min(hi, f)); }
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

    private float k = 1f;                 // user size setting, 1 = default
    private float dp(float v) { return v * k * getResources().getDisplayMetrics().density; }
    private int cardFill = Color.argb(118, 10, 12, 16);

    private void font(float sp, int weight, int color) {
        android.graphics.Typeface tf;
        if ("Kiosk Satellite".equals(family)) tf = theme.typeface(weight);
        else {
            String name = "Serif".equals(family) ? "serif" : "Monospace".equals(family) ? "monospace"
                : "Condensed".equals(family) ? "sans-serif-condensed" : "sans-serif";
            android.graphics.Typeface base = android.graphics.Typeface.create(name, android.graphics.Typeface.NORMAL);
            tf = android.os.Build.VERSION.SDK_INT >= 28 ? android.graphics.Typeface.create(base, weight, false)
                : android.graphics.Typeface.create(base, weight >= 600 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        text.setTypeface(tf);
        text.setTextSize(dp(sp));
        text.setColor(color);
    }

    private void card(float l, float t, float r, float b) {
        rect.set(l, t, r, b);
        float radius = dp(radiusDp);
        fill.setColor(cardFill);
        canvas.drawRoundRect(rect, radius, radius, fill);
        canvas.drawRoundRect(rect, radius, radius, stroke);
    }

    /** One measured widget; draw(left, top) paints it. */
    private abstract static class Box { float w, h; abstract void draw(float l, float t); }

    private Canvas canvas;
    private int INK = Color.rgb(243, 238, 230);
    private int MUTED = Color.argb(160, 243, 238, 230);
    private String family = "Kiosk Satellite";
    private float radiusDp = 22;
    private int iconColor = Color.rgb(214, 221, 232);
    private static int color(Snapshot s, String key, int fallback) {
        try { String v = s.str(key, ""); return v.matches("#[0-9a-fA-F]{6}") ? Color.parseColor(v) : fallback; } catch (RuntimeException e) { return fallback; }
    }

    @Override protected void onDraw(Canvas c) {
        Snapshot s = source.snapshot();
        if (s == null) return;
        canvas = c;
        k = 1f;
        cardFill = Color.argb(118, 10, 12, 16);
        INK = Color.rgb(243, 238, 230); MUTED = Color.argb(160, 243, 238, 230);
        family = "Kiosk Satellite"; radiusDp = 22; iconColor = Color.rgb(214, 221, 232);
        stroke.setColor(Color.argb(46, 255, 255, 255));
        if (compact) { drawStrip(c, s); return; }
        final float base = s.num("scale", 100, 60, 180) / 100f;
        k = base;
        int tint = color(s, "glassColor", Color.rgb(10, 12, 16));
        cardFill = Color.argb(Math.round(s.num("cardOpacity", 46, 0, 90) * 2.55f), Color.red(tint), Color.green(tint), Color.blue(tint));
        stroke.setColor(Color.argb(Math.round(s.num("borderOpacity", 18, 0, 100) * 2.55f), 255, 255, 255));
        int ink = color(s, "textColor", Color.rgb(243, 238, 230));
        INK = ink; MUTED = Color.argb(160, Color.red(ink), Color.green(ink), Color.blue(ink));
        family = s.str("fontFamily", "Kiosk Satellite");
        radiusDp = s.num("cornerRadius", 22, 0, 40);
        iconColor = color(s, "weatherIconColor", Color.rgb(214, 221, 232));
        final int cal1 = color(s, "calendar1Color", Color.rgb(143, 201, 138)), cal2 = color(s, "calendar2Color", Color.rgb(224, 178, 94));
        final int clockWeight = "Bold".equals(s.str("clockWeight", "Light")) ? 600 : "Regular".equals(s.str("clockWeight", "Light")) ? 400 : 300;
        final float m = getResources().getDisplayMetrics().density * s.num("edgeMargin", 28, 0, 120), pad = dp(20);
        final float w = getWidth(), h = getHeight();
        final JSONObject live = s.live == null ? new JSONObject() : s.live;
        Date now = new Date();

        // ---- measure ----
        final boolean secs = Boolean.TRUE.equals(s.opt.get("showSeconds"));
        final String time = new SimpleDateFormat(s.h24 ? (secs ? "HH:mm:ss" : "HH:mm") : (secs ? "h:mm:ss" : "h:mm"), Locale.getDefault()).format(now);
        String d0;
        try { d0 = s.on("showDate") ? new SimpleDateFormat(s.str("dateFormat", "EEEE, d MMMM"), Locale.getDefault()).format(now) : ""; }
        catch (IllegalArgumentException badPattern) { d0 = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now); }
        final String date = d0.isEmpty() ? d0 : Character.toUpperCase(d0.charAt(0)) + d0.substring(1);
        Box clock = null;
        if (s.on("showClock")) {
            final float kk = base * s.num("clockScale", 100, 50, 250) / 100f; k = kk;
            font(66, clockWeight, INK); final float tw = text.measureText(time); final float th = dp(66);
            font(17, 400, s.accent); float dw = text.measureText(date);
            clock = new Box() { void draw(float l, float t) {
                k = kk;
                card(l, t, l + w, t + h);
                font(66, clockWeight, INK); canvas.drawText(time, l + pad, t + pad + th * 0.82f, text);
                if (!date.isEmpty()) { font(17, 400, s.accent); canvas.drawText(date, l + pad, t + pad + th + dp(20), text); }
            } };
            clock.w = Math.max(tw, dw) + pad * 2; clock.h = pad * 2 + th + (date.isEmpty() ? dp(4) : dp(26));
        }
        Box weather = null;
        Object temp = live.opt("temp");
        if (s.on("showWeather") && temp instanceof Number) {
            final float kk = base * s.num("weatherScale", 100, 50, 250) / 100f; k = kk;
            final String t = Math.round(((Number) temp).doubleValue()) + live.optString("unit", "°");
            final String cond = s.on("showCondition") ? condition(live.optString("cond")) : "";
            String inside = live.optString("inside");
            final String in = !s.on("showInside") || inside.isEmpty() || inside.contains("unknown") || inside.contains("unavailable") ? "" : inside + " inside";
            final boolean showIcon = s.on("showWeatherIcon");
            font(42, 300, INK); float t1 = text.measureText(t);
            font(15, 400, MUTED); float t2 = Math.max(text.measureText(cond), text.measureText(in));
            final float icon = showIcon ? dp(48) : 0, iconGap = showIcon ? dp(14) : 0;
            weather = new Box() { void draw(float l, float top) {
                k = kk;
                card(l, top, l + w, top + h);
                if (showIcon) drawIcon(weatherIcon(live.optString("cond")), l + pad, top + pad + dp(6), icon);
                float x = l + pad + icon + iconGap;
                font(42, 300, INK); canvas.drawText(t, x, top + pad + dp(36), text);
                font(15, 400, MUTED);
                float ly = top + pad + dp(62);
                if (!cond.isEmpty()) { canvas.drawText(cond, x, ly, text); ly += dp(20); }
                if (!in.isEmpty()) canvas.drawText(in, x, ly, text);
            } };
            int lines = (cond.isEmpty() ? 0 : 1) + (in.isEmpty() ? 0 : 1);
            weather.w = pad * 2 + icon + iconGap + Math.max(t1, t2); weather.h = pad * 2 + dp(42) + (lines == 0 ? dp(4) : dp(4 + 20 * lines));
        }
        Box events = null;
        final JSONArray ev = s.events;
        if (s.on("showEvents") && ev != null && ev.length() > 0) {
            final float kk = base * s.num("eventsScale", 100, 50, 250) / 100f; k = kk;
            final int n = Math.min(ev.length(), (int) s.num("maxEvents", 4, 1, 6));
            final float row = dp(48);
            events = new Box() { void draw(float l, float top) {
                k = kk;
                card(l, top, l + w, top + h);
                for (int i = 0; i < n; i++) {
                    JSONObject e = ev.optJSONObject(i);
                    if (e == null) continue;
                    float y = top + pad + i * row;
                    fill.setColor(e.optInt("c", 1) == 2 ? cal2 : cal1);
                    rect.set(l + pad, y + dp(2), l + pad + dp(4), y + row - dp(12));
                    canvas.drawRoundRect(rect, dp(2), dp(2), fill);
                    float tx = l + pad + dp(14), avail = w - pad * 2 - dp(14);
                    font(13, 400, MUTED); canvas.drawText(when(e.optString("s"), s.h24), tx, y + dp(13), text);
                    font(17, 400, INK);
                    canvas.drawText(TextUtils.ellipsize(e.optString("m"), text, avail, TextUtils.TruncateAt.END).toString(), tx, y + dp(34), text);
                }
            } };
            events.w = Math.min(dp(380), w * s.num("eventsWidth", 36, 20, 60) / 100f); events.h = pad * 2 + n * row - dp(8);
        }

        Box custom = null;
        final String customText = live.optString("x").trim();
        if (!customText.isEmpty() && !customText.equals("unknown") && !customText.equals("unavailable")) {
            final float kk = base * s.num("customScale", 100, 50, 250) / 100f; k = kk;
            final float cs = s.num("customSize", 18, 10, 48);
            final boolean glass = !"Text only".equals(s.str("customStyle", "Card"));
            font(cs, 500, INK);
            final List<String> lines = new ArrayList<>(java.util.Arrays.asList(customText.split("\n")));
            float lw = 0; for (String l : lines) lw = Math.max(lw, text.measureText(l));
            final float maxW = w * 0.45f, lh = dp(cs * 1.35f), cp = glass ? pad : 0;
            final float cw = Math.min(lw, maxW);
            custom = new Box() { void draw(float l, float t) {
                k = kk;
                if (glass) card(l, t, l + w, t + h);
                font(cs, 500, INK);
                if (!glass) text.setShadowLayer(dp(6), 0, dp(1), Color.argb(170, 0, 0, 0));
                float y = t + cp + lh * 0.8f;
                for (String line : lines) { canvas.drawText(TextUtils.ellipsize(line, text, cw, TextUtils.TruncateAt.END).toString(), l + cp, y, text); y += lh; }
                text.clearShadowLayer();
            } };
            custom.w = cw + cp * 2; custom.h = lines.size() * lh + cp * 2 - lh * 0.2f;
        }
        k = base;

        // ---- alerts + glance: chips (default) or one iPad-style info line ----
        List<String[]> chips = new ArrayList<>();
        for (String a : live.optString("info").split(" · ")) {
            String v = a.trim();
            if (!v.isEmpty() && !v.equals("unknown") && !v.equals("unavailable")) chips.add(new String[] {"", v});
        }
        JSONArray g = live.optJSONArray("g");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            JSONArray item = g.optJSONArray(i);
            if (item != null && item.length() >= 2) chips.add(new String[] {item.optString(0), item.optString(1), item.optString(2, "")});
        }
        k = base * s.num("chipsScale", 100, 50, 250) / 100f;
        alertTint = color(s, "alertColor", Color.rgb(70, 48, 12));
        infoAlign = s.str("infoAlign", "Right"); infoSize = s.num("infoSize", 19, 10, 40);
        boolean showRow = s.on("showChips") && !chips.isEmpty();
        boolean line = "Info line".equals(s.str("alertsStyle", "Chips"));
        boolean rowTop = "Top centre".equals(s.str("chipsPosition", "Bottom centre"));
        float rowH = line ? dp(30) : dp(44);
        float topStart = m + (showRow && rowTop ? rowH + dp(16) : 0);
        float bottomStart = h - m - (showRow && !rowTop ? rowH + dp(16) : 0);
        if (showRow) {
            float rowY = rowTop ? m : h - m - rowH;
            if (line) drawInfoLine(chips, w, m, rowY, rowH, rowTop);
            else drawChips(chips, w, m, rowY, rowH);
        }

        // ---- place: each corner stacks its cards (top corners downwards, bottom corners upwards) ----
        float[] tops = {topStart, topStart}, bottoms = {bottomStart, bottomStart};
        k = base;
        Object[][] order = {{clock, s.str("clockPosition", "Top left")}, {weather, s.str("weatherPosition", "Top right")},
                            {events, s.str("eventsPosition", "Bottom left")}, {custom, s.str("customPosition", "Bottom right")}};
        for (Object[] o : order) {
            Box b = (Box) o[0];
            if (b == null) continue;
            String pos = (String) o[1];
            boolean right = pos.endsWith("right"), bottom = pos.startsWith("Bottom");
            int side = right ? 1 : 0;
            float l = right ? w - m - b.w : m;
            float t;
            if (bottom) { t = bottoms[side] - b.h; bottoms[side] = t - dp(14); }
            else { t = tops[side]; tops[side] = t + b.h + dp(14); }
            b.draw(l, t);
        }
    }

    private int alertTint = Color.rgb(70, 48, 12);
    private String infoAlign = "Right";
    private float infoSize = 19;

    private void drawChips(List<String[]> chips, float w, float m, float chipTop, float chipH) {
        float gap = dp(10), total = 0;
        float[] widths = new float[chips.size()];
        for (int i = 0; i < chips.size(); i++) {
            String[] ch2 = chips.get(i);
            font(12, 400, MUTED); float a = ch2[0].isEmpty() ? 0 : text.measureText(ch2[0]);
            font(15, 500, INK); float b = text.measureText(ch2[1]);
            boolean icon = ch2.length > 2 && !ch2[2].isEmpty() && theme.iconPath(ch2[2]) != null;
            widths[i] = Math.max(a, b) + dp(32) + (icon ? dp(26) : 0);
            total += widths[i] + (i > 0 ? gap : 0);
        }
        float x0 = Math.max(m, (w - total) / 2);
        for (int i = 0; i < chips.size(); i++) {
            String[] ch2 = chips.get(i);
            if (x0 + widths[i] > w - m) break;
            rect.set(x0, chipTop, x0 + widths[i], chipTop + chipH);
            boolean alert = ch2[0].isEmpty();
            fill.setColor(alert ? Color.argb(Math.max(Color.alpha(cardFill), 60), Color.red(alertTint), Color.green(alertTint), Color.blue(alertTint)) : cardFill);
            canvas.drawRoundRect(rect, chipH / 2, chipH / 2, fill);
            canvas.drawRoundRect(rect, chipH / 2, chipH / 2, stroke);
            float tx = x0 + dp(16);
            if (ch2.length > 2 && !ch2[2].isEmpty() && theme.iconPath(ch2[2]) != null) {
                int keep = iconColor; iconColor = INK;
                drawIcon(ch2[2], x0 + dp(14), chipTop + (chipH - dp(20)) / 2, dp(20));
                iconColor = keep; tx += dp(26);
            }
            if (alert) {
                font(15, 500, INK); canvas.drawText(ch2[1], tx, chipTop + dp(28), text);
            } else {
                font(12, 400, MUTED); canvas.drawText(ch2[0], tx, chipTop + dp(18), text);
                font(15, 500, INK); canvas.drawText(ch2[1], tx, chipTop + dp(35), text);
            }
            x0 += widths[i] + gap;
        }
    }

    /** iPad style: everything on one line of text, right-aligned, with a soft shadow instead of cards. */
    private void drawInfoLine(List<String[]> chips, float w, float m, float y, float lineH, boolean top) {
        List<String> parts = new ArrayList<>();
        for (String[] ch2 : chips) parts.add(ch2[0].isEmpty() ? ch2[1] : ch2[0] + " " + ch2[1]);
        font(infoSize, 500, INK);
        text.setShadowLayer(dp(6), 0, dp(1), Color.argb(170, 0, 0, 0));
        String all = TextUtils.ellipsize(TextUtils.join("  ·  ", parts), text, w - m * 2, TextUtils.TruncateAt.END).toString();
        float tw = text.measureText(all);
        float x = "Left".equals(infoAlign) ? m : "Centre".equals(infoAlign) ? (w - tw) / 2 : w - m - tw;
        canvas.drawText(all, x, y + lineH * 0.75f, text);
        text.clearShadowLayer();
    }

    /**
     * Full-screen Now Playing: a big clock + date in the empty area left of the cover, weather + inside on the right,
     * and only the alerts in a slim pill at the top. The cover and title use the middle third of the screen.
     */
    private void drawStrip(Canvas c, Snapshot s) {
        JSONObject live = s.live == null ? new JSONObject() : s.live;
        float w = getWidth(), h = getHeight(), pad = dp(22);
        float side = w * 0.30f;            // each side area
        float cy = h * 0.32f;              // level with the cover

        // Left: clock + date.
        Date now = new Date();
        String time = new SimpleDateFormat(s.h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now);
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        if (!date.isEmpty()) date = Character.toUpperCase(date.charAt(0)) + date.substring(1);
        float size = Math.min(84, side / dp(1) * 0.30f);
        font(size, 300, INK); float tw = text.measureText(time); float th = dp(size);
        font(17, 400, s.accent); String d2 = TextUtils.ellipsize(date, text, side - pad * 2 - dp(16), TextUtils.TruncateAt.END).toString(); float dw = text.measureText(d2);
        float cw = Math.min(side - dp(16), Math.max(tw, dw) + pad * 2), ch = pad * 2 + th + dp(28);
        float l = (side - cw) / 2 + dp(8), t = cy - ch / 2;
        card(l, t, l + cw, t + ch);
        font(size, 300, INK); c.drawText(time, l + (cw - tw) / 2, t + pad + th * 0.82f, text);
        font(17, 400, s.accent); c.drawText(d2, l + (cw - dw) / 2, t + pad + th + dp(22), text);

        // Right: weather + inside.
        Object temp = live.opt("temp");
        if (temp instanceof Number) {
            String tt = Math.round(((Number) temp).doubleValue()) + live.optString("unit", "°");
            String cond = condition(live.optString("cond"));
            String inside = live.optString("inside");
            String in = inside.isEmpty() || inside.contains("unknown") || inside.contains("unavailable") ? "" : inside + " inside";
            float icon = dp(64);
            font(54, 300, INK); float t1 = text.measureText(tt);
            font(17, 400, MUTED); float t2 = Math.max(text.measureText(cond), text.measureText(in));
            float ww = Math.min(side - dp(16), pad * 2 + icon + dp(16) + Math.max(t1, t2)), wh = pad * 2 + dp(54) + dp(52);
            float rl = w - side + (side - ww) / 2 - dp(8), rt = cy - wh / 2;
            card(rl, rt, rl + ww, rt + wh);
            drawIcon(weatherIcon(live.optString("cond")), rl + pad, rt + pad + dp(10), icon);
            float x = rl + pad + icon + dp(16);
            font(54, 300, INK); c.drawText(tt, x, rt + pad + dp(46), text);
            font(17, 400, MUTED); c.drawText(cond, x, rt + pad + dp(74), text);
            if (!in.isEmpty()) c.drawText(in, x, rt + pad + dp(96), text);
        }

        // Top: alerts only.
        List<String> parts = new ArrayList<>();
        for (String a : live.optString("info").split(" · ")) {
            String v = a.trim();
            if (!v.isEmpty() && !v.equals("unknown") && !v.equals("unavailable")) parts.add(v);
        }
        if (parts.isEmpty()) return;
        String line = TextUtils.join("   ·   ", parts);
        font(15, 400, INK);
        String shown = TextUtils.ellipsize(line, text, w * 0.5f, TextUtils.TruncateAt.END).toString();
        float sw = text.measureText(shown), sh = dp(36), top = dp(10);
        float sl = (w - sw) / 2 - dp(18);
        rect.set(sl, top, sl + sw + dp(36), top + sh);
        c.drawRoundRect(rect, sh / 2, sh / 2, fill);
        c.drawRoundRect(rect, sh / 2, sh / 2, stroke);
        c.drawText(shown, sl + dp(18), top + dp(23), text);
    }

    private void drawIcon(String name, float x, float y, float size) {
        Path path = theme.iconPath(name);
        if (path == null) return;
        Matrix mx = new Matrix();
        mx.setScale(size / 24f, size / 24f);
        mx.postTranslate(x, y);
        path.transform(mx);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(iconColor);
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
