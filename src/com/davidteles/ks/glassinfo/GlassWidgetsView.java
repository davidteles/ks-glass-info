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
        if (compact) { drawStrip(c, s, 0.30f, getHeight() * 0.10f); return; }
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
        if ("Columns".equals(s.str("dayLayout", "Corners"))) {
            // Same arrangement as beside Now Playing, with the user's look; the middle stays free for the photo.
            k = base;
            drawStrip(c, s, 0.27f, getResources().getDisplayMetrics().density * s.num("edgeMargin", 28, 0, 120));
            return;
        }
        final int[] calColors = calColors(s);
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
                    fill.setColor(calColors[Math.max(0, Math.min(3, e.optInt("c", 0)))]);
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

        List<Object[]> customs = new ArrayList<>();
        JSONArray xs = live.optJSONArray("xs");
        if (xs != null) for (int xi = 0; xi < xs.length(); xi++) {
            JSONObject x = xs.optJSONObject(xi);
            if (x == null) continue;
            final String customText = x.optString("t").trim();
            if (customText.isEmpty() || customText.equals("unknown") || customText.equals("unavailable")) continue;
            k = base;
            final float cs = (float) Math.max(10, Math.min(48, x.optDouble("size", 18)));
            final boolean glass = !"Text only".equals(x.optString("style", "Card"));
            font(cs, 500, INK);
            final List<String> lines = new ArrayList<>(java.util.Arrays.asList(customText.split("\\n")));
            float lw = 0; for (String l : lines) lw = Math.max(lw, text.measureText(l));
            final float maxW = w * 0.45f, lh = dp(cs * 1.35f), cp = glass ? pad : 0;
            final float cw = Math.min(lw, maxW);
            final float kk = base;
            Box b = new Box() { void draw(float l, float t) {
                k = kk;
                if (glass) card(l, t, l + w, t + h);
                font(cs, 500, INK);
                if (!glass) text.setShadowLayer(dp(6), 0, dp(1), Color.argb(170, 0, 0, 0));
                float y = t + cp + lh * 0.8f;
                for (String line : lines) { canvas.drawText(TextUtils.ellipsize(line, text, cw, TextUtils.TruncateAt.END).toString(), l + cp, y, text); y += lh; }
                text.clearShadowLayer();
            } };
            b.w = cw + cp * 2; b.h = lines.size() * lh + cp * 2 - lh * 0.2f;
            customs.add(new Object[] {b, x.optString("pos", "Bottom right")});
        }
        k = base;

        // ---- alerts + glance: chips (default) or one iPad-style info line ----
        List<String[]> chips = new ArrayList<>();
        for (String a : live.optString("info").split(" · ")) {
            String v = a.trim();
            if (v.isEmpty() || v.equals("unknown") || v.equals("unavailable")) continue;
            boolean crit = v.startsWith("!");
            chips.add(new String[] {crit ? "!" : "", crit ? v.substring(1).trim() : v});
        }
        JSONArray g = live.optJSONArray("g");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            JSONArray item = g.optJSONArray(i);
            if (item != null && item.length() >= 2) chips.add(new String[] {item.optString(0), item.optString(1), item.optString(2, "")});
        }
        k = base * s.num("chipsScale", 100, 50, 250) / 100f;
        alertTint = color(s, "alertColor", Color.rgb(70, 48, 12));
        criticalTint = color(s, "criticalColor", Color.rgb(120, 24, 24));
        infoAlign = s.str("infoAlign", "Right"); infoSize = s.num("infoSize", 19, 10, 40);
        boolean showRow = s.on("showChips") && !chips.isEmpty();
        boolean line = "Info line".equals(s.str("alertsStyle", "Chips"));
        boolean rowTop = "Top centre".equals(s.str("chipsPosition", "Bottom centre"));
        float chipH = line ? dp(30) : dp(44), rowGap = dp(8);
        float[] chipW = line ? null : chipWidths(chips, w - m * 2);
        List<int[]> chipLines = line ? null : chipRows(chipW, w - m * 2, dp(10));
        int nRows = line ? 1 : Math.max(1, Math.min(3, chipLines.size()));
        float rowH = nRows * chipH + (nRows - 1) * rowGap;
        float topStart = m + (showRow && rowTop ? rowH + dp(16) : 0);
        float bottomStart = h - m - (showRow && !rowTop ? rowH + dp(16) : 0);
        if (showRow) {
            float rowY = rowTop ? m : h - m - rowH;
            if (line) drawInfoLine(chips, w, m, rowY, chipH, rowTop);
            else for (int r = 0; r < nRows; r++) {
                int[] span = chipLines.get(r);
                float total = 0;
                for (int i = span[0]; i < span[1]; i++) total += chipW[i] + (i > span[0] ? dp(10) : 0);
                float x = (w - total) / 2, y = rowY + r * (chipH + rowGap);
                for (int i = span[0]; i < span[1]; i++) {
                    String[] ch2 = chips.get(i);
                    pill(x, y, chipW[i], chipH, ch2.length > 2 ? ch2[2] : "", ch2[0], ch2[1], ch2[0].isEmpty() || "!".equals(ch2[0]), false);
                    x += chipW[i] + dp(10);
                }
            }
        }

        // ---- place: each corner stacks its cards (top corners downwards, bottom corners upwards) ----
        float[] tops = {topStart, topStart}, bottoms = {bottomStart, bottomStart};
        k = base;
        List<Object[]> order = new ArrayList<>();
        order.add(new Object[] {clock, s.str("clockPosition", "Top left")});
        order.add(new Object[] {weather, s.str("weatherPosition", "Top right")});
        order.add(new Object[] {events, s.str("eventsPosition", "Bottom left")});
        order.addAll(customs);
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

    private static int[] calColors(Snapshot s) {
        int[] out = {Color.rgb(143, 201, 138), Color.rgb(224, 178, 94), Color.rgb(127, 183, 255), Color.rgb(232, 140, 180)};
        Object calRows = s.opt.get("calendars");
        if (calRows instanceof List) {
            List<?> cl = (List<?>) calRows;
            for (int ci = 0; ci < cl.size() && ci < 4; ci++) {
                Object row = cl.get(ci);
                Object cv = row instanceof java.util.Map ? ((java.util.Map<?, ?>) row).get("color") : null;
                if (cv instanceof String && ((String) cv).matches("#[0-9a-fA-F]{6}")) out[ci] = Color.parseColor((String) cv);
            }
        }
        return out;
    }

    private int alertTint = Color.rgb(70, 48, 12);
    private int criticalTint = Color.rgb(120, 24, 24);
    private String infoAlign = "Right";
    private float infoSize = 19;

    /** Width of each chip, never wider than the row. */
    private float[] chipWidths(List<String[]> chips, float avail) {
        float[] widths = new float[chips.size()];
        for (int i = 0; i < chips.size(); i++) {
            String[] ch2 = chips.get(i);
            font(12, 400, MUTED); float a = ch2[0].isEmpty() || "!".equals(ch2[0]) ? 0 : text.measureText(ch2[0]);
            font(15, 500, INK); float b = text.measureText(ch2[1]);
            boolean icon = ch2.length > 2 && !ch2[2].isEmpty() && theme.iconPath(ch2[2]) != null;
            widths[i] = Math.min(avail, Math.max(a, b) + dp(32) + (icon ? dp(26) : 0));
        }
        return widths;
    }

    /** Splits the chips into rows that fit, each row [from, to). Every row is drawn centred. */
    private static List<int[]> chipRows(float[] widths, float avail, float gap) {
        List<int[]> rows = new ArrayList<>();
        int start = 0; float used = 0;
        for (int i = 0; i < widths.length; i++) {
            float need = widths[i] + (i > start ? gap : 0);
            if (i > start && used + need > avail) { rows.add(new int[] {start, i}); start = i; used = widths[i]; }
            else used += need;
        }
        if (start < widths.length) rows.add(new int[] {start, widths.length});
        return rows;
    }

    /** One rounded chip: amber when it is an alert. Two lines (small label over the value) unless oneLine or no label. */
    private void pill(float l, float t, float pw, float ph, String icon, String label, String value, boolean alert, boolean oneLine) {
        boolean critical = "!".equals(label);
        if (critical) label = "";
        int tint = critical ? criticalTint : alertTint;
        rect.set(l, t, l + pw, t + ph);
        fill.setColor(alert ? Color.argb(Math.max(Color.alpha(cardFill), critical ? 150 : 60), Color.red(tint), Color.green(tint), Color.blue(tint)) : cardFill);
        canvas.drawRoundRect(rect, ph / 2, ph / 2, fill);
        canvas.drawRoundRect(rect, ph / 2, ph / 2, stroke);
        float tx = l + dp(16);
        if (icon != null && !icon.isEmpty() && theme.iconPath(icon) != null) {
            int keep = iconColor; iconColor = INK;
            drawIcon(icon, l + dp(14), t + (ph - dp(20)) / 2, dp(20));
            iconColor = keep; tx += dp(26);
        }
        float avail = Math.max(dp(20), l + pw - dp(14) - tx);
        if (label.isEmpty()) {
            font(15, 500, INK);
            canvas.drawText(TextUtils.ellipsize(value, text, avail, TextUtils.TruncateAt.END).toString(), tx, t + ph / 2 + dp(5.5f), text);
        } else if (oneLine) {
            font(13, 400, MUTED); String lab = label + "  "; float lw = text.measureText(lab);
            canvas.drawText(lab, tx, t + ph / 2 + dp(5), text);
            font(15, 500, INK);
            canvas.drawText(TextUtils.ellipsize(value, text, Math.max(dp(10), avail - lw), TextUtils.TruncateAt.END).toString(), tx + lw, t + ph / 2 + dp(5.5f), text);
        } else {
            font(12, 400, MUTED); canvas.drawText(label, tx, t + ph / 2 - dp(4), text);
            font(15, 500, INK); canvas.drawText(TextUtils.ellipsize(value, text, avail, TextUtils.TruncateAt.END).toString(), tx, t + ph / 2 + dp(13), text);
        }
    }

    /** iPad style: everything on one line of text, right-aligned, with a soft shadow instead of cards. */
    private void drawInfoLine(List<String[]> chips, float w, float m, float y, float lineH, boolean top) {
        List<String> parts = new ArrayList<>();
        for (String[] ch2 : chips) parts.add(ch2[0].isEmpty() || "!".equals(ch2[0]) ? ch2[1] : ch2[0] + " " + ch2[1]);
        font(infoSize, 500, INK);
        text.setShadowLayer(dp(6), 0, dp(1), Color.argb(170, 0, 0, 0));
        String all = TextUtils.ellipsize(TextUtils.join("  ·  ", parts), text, w - m * 2, TextUtils.TruncateAt.END).toString();
        float tw = text.measureText(all);
        float x = "Left".equals(infoAlign) ? m : "Centre".equals(infoAlign) ? (w - tw) / 2 : w - m - tw;
        canvas.drawText(all, x, y + lineH * 0.75f, text);
        text.clearShadowLayer();
    }

    /**
     * Full-screen Now Playing. The cover, title and controls use the middle; each side gets a column:
     * left = big clock + date, then every alert (amber); right = weather + inside, then your chips
     * (alarm, car, power...) and the next events while there is room.
     */
    private void drawStrip(Canvas c, Snapshot s, float sideFrac, float top) {
        JSONObject live = s.live == null ? new JSONObject() : s.live;
        alertTint = color(s, "alertColor", Color.rgb(70, 48, 12));
        criticalTint = color(s, "criticalColor", Color.rgb(120, 24, 24));
        float w = getWidth(), h = getHeight(), pad = dp(22), gap = dp(10);
        float side = w * sideFrac;
        float colW = Math.min(side - dp(28), dp(360));
        float leftL = (side - colW) / 2 + dp(6), rightL = w - side + (side - colW) / 2 - dp(6);
        float bottomLimit = h - dp(20);

        // Left: clock + date, then the alerts.
        Date now = new Date();
        String time = new SimpleDateFormat(s.h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now);
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now);
        if (!date.isEmpty()) date = Character.toUpperCase(date.charAt(0)) + date.substring(1);
        float size = Math.min(84, colW / dp(1) * 0.30f);
        font(size, 300, INK); float tw = text.measureText(time); float th = dp(size);
        font(17, 400, s.accent); String d2 = TextUtils.ellipsize(date, text, colW - pad * 2, TextUtils.TruncateAt.END).toString(); float dw = text.measureText(d2);
        float ch = pad * 2 + th + dp(28);
        card(leftL, top, leftL + colW, top + ch);
        font(size, 300, INK); c.drawText(time, leftL + (colW - tw) / 2, top + pad + th * 0.82f, text);
        font(17, 400, s.accent); c.drawText(d2, leftL + (colW - dw) / 2, top + pad + th + dp(22), text);

        List<String> alerts = new ArrayList<>();
        for (String a : live.optString("info").split(" · ")) {
            String v = a.trim();
            if (!v.isEmpty() && !v.equals("unknown") && !v.equals("unavailable")) alerts.add(v);
        }
        float pillH = dp(40), y = top + ch + dp(14);
        for (int i = 0; i < alerts.size(); i++) {
            if (y + pillH > bottomLimit) break;
            boolean last = y + pillH * 2 + gap > bottomLimit && i < alerts.size() - 1;
            String a = alerts.get(i);
            boolean crit = a.startsWith("!");
            if (crit) a = a.substring(1).trim();
            String v = last ? a + "  +" + (alerts.size() - i - 1) : a;
            pill(leftL, y, colW, pillH, "", crit ? "!" : "", v, true, true);
            y += pillH + gap;
            if (last) break;
        }

        // Right: weather + inside, then the chips, then the next events.
        float ry = top;
        Object temp = live.opt("temp");
        String inside = live.optString("inside");
        if (temp instanceof Number) {
            String tt = Math.round(((Number) temp).doubleValue()) + live.optString("unit", "°");
            String cond = condition(live.optString("cond"));
            String in = inside.isEmpty() || inside.contains("unknown") || inside.contains("unavailable") ? "" : inside + " inside";
            // In a narrow column (portrait, after rotating) the icon and the 54 sp temperature don't fit:
            // StripSizes drops the icon and shrinks the text so "15°C" stays inside the card.
            font(54, 300, INK);
            float[] ws = StripSizes.weather(colW / dp(1), pad / dp(1), text.measureText(tt) / dp(1));
            float icon = dp(ws[0]);
            float wh = pad * 2 + dp(54) + dp(52);
            card(rightL, ry, rightL + colW, ry + wh);
            if (icon > 0) drawIcon(weatherIcon(live.optString("cond")), rightL + pad, ry + pad + dp(10), icon);
            float x = rightL + pad + icon + dp(ws[1]), avail = rightL + colW - pad - x;
            font(ws[2], 300, INK); c.drawText(TextUtils.ellipsize(tt, text, avail, TextUtils.TruncateAt.END).toString(), x, ry + pad + dp(46), text);
            font(17, 400, MUTED); c.drawText(TextUtils.ellipsize(cond, text, avail, TextUtils.TruncateAt.END).toString(), x, ry + pad + dp(74), text);
            if (!in.isEmpty()) c.drawText(TextUtils.ellipsize(in, text, avail, TextUtils.TruncateAt.END).toString(), x, ry + pad + dp(96), text);
            ry += wh + dp(14);
        }
        JSONArray g = live.optJSONArray("g");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            JSONArray item = g.optJSONArray(i);
            if (item == null || item.length() < 2) continue;
            String value = item.optString(1);
            if (!inside.isEmpty() && inside.equals(value)) continue;          // already in the weather card
            if (value.isEmpty() || value.equals("unknown") || value.equals("unavailable")) continue;
            if (ry + pillH > bottomLimit) break;
            pill(rightL, ry, colW, pillH, item.optString(2, ""), item.optString(0), value, false, true);
            ry += pillH + gap;
        }
        JSONArray ev = s.events;
        if (ev != null && ev.length() > 0) {
            int[] cols = calColors(s);
            float row = dp(46), et = ry + dp(4);
            int n = Math.min(ev.length(), (int) Math.floor((bottomLimit - et - pad * 2 + dp(8)) / row));
            n = Math.min(n, 3);
            if (n > 0) {
                card(rightL, et, rightL + colW, et + pad * 2 + n * row - dp(8));
                for (int i = 0; i < n; i++) {
                    JSONObject e = ev.optJSONObject(i);
                    if (e == null) continue;
                    float ey = et + pad + i * row;
                    fill.setColor(cols[Math.max(0, Math.min(3, e.optInt("c", 0)))]);
                    rect.set(rightL + pad, ey + dp(2), rightL + pad + dp(4), ey + row - dp(12));
                    c.drawRoundRect(rect, dp(2), dp(2), fill);
                    float tx = rightL + pad + dp(14), avail = colW - pad * 2 - dp(14);
                    font(13, 400, MUTED); c.drawText(when(e.optString("s"), s.h24), tx, ey + dp(13), text);
                    font(16, 400, INK);
                    c.drawText(TextUtils.ellipsize(e.optString("m"), text, avail, TextUtils.TruncateAt.END).toString(), tx, ey + dp(33), text);
                }
            }
        }
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
