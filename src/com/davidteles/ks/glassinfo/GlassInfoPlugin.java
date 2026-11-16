// SPDX-License-Identifier: Apache-2.0
package com.davidteles.ks.glassinfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.graphics.Color;
import android.view.View;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Glass Info: one HTML screensaver renderer ("glass") fed by Home Assistant.
 * Live values come from a render_template subscription; events from calendar.get_events every 10 minutes.
 * The clock runs inside the page, so options are only republished when the data changes.
 */
public final class GlassInfoPlugin implements KioskPlugin {
    private static final String KEY = "glass";
    private static final String SUB = "glass-live";
    private static final String ENTITY = "[a-z_]+\\.[a-z0-9_]+";

    private PluginHost host;
    private Map<String, Object> settings = new HashMap<>();
    private ScheduledExecutorService timer;
    private JSONObject live = new JSONObject();
    private JSONArray events = new JSONArray();
    private String lastPublished = "";
    private boolean subscribed;
    private static final String OVERLAY = "widgets";
    private static final String MODE = "plugin:glass-info:glass";
    private volatile GlassWidgetsView.Snapshot snapshot;
    private volatile GlassWidgetsView widgetsView;
    private volatile GlassWidgetsView stripView;
    private boolean screensaverActive, nowPlayingFull, overlayShown, stripShown;
    private static final String STRIP = "nowplaying";

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        this.settings = settings == null ? new HashMap<>() : new HashMap<>(settings);
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "glass-info");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::fetchEvents, 0, 10, TimeUnit.MINUTES);
        subscribe();
        try { host.subscribe("screensaver.state"); } catch (RuntimeException ignored) { }
        try { host.subscribe("screensaver.appearance"); } catch (RuntimeException ignored) { }
        publish(true);
        // The screensaver or Now Playing may already be on when the plugin starts or restarts after an update.
        try {
            host.executeCommand("getScreensaverAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
                if (ok && data instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) data;
                    synchronized (GlassInfoPlugin.this) {
                        nowPlayingFull = Boolean.TRUE.equals(m.get("nowPlaying")) && !Boolean.TRUE.equals(m.get("nowPlayingShared"));
                    }
                }
            });
        } catch (RuntimeException ignored) { }
        timer.schedule(this::updateOverlay, 2, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        this.settings = settings == null ? new HashMap<>() : new HashMap<>(settings);
        if (host == null) return;
        subscribe();
        if (timer != null) timer.execute(this::fetchEvents);
        publish(true);
        if (timer != null) timer.schedule(this::updateOverlay, 1, TimeUnit.SECONDS);
    }

    @Override
    public void execute(String command, Map<String, Object> arguments) { }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("ks.screensaver.state".equals(event)) {
            screensaverActive = payload != null && Boolean.TRUE.equals(payload.get("active"));
            if (screensaverActive && timer != null) {
                // The renderer may need a moment to be on screen; the tint fails harmlessly for other modes.
                timer.schedule(this::hideWidgets, 800, TimeUnit.MILLISECONDS);
                timer.schedule(this::hideWidgets, 3, TimeUnit.SECONDS);
                timer.execute(this::updateOverlay);
            } else {
                hideOverlay();
            }
            return;
        }
        if ("ks.screensaver.appearance".equals(event)) {
            nowPlayingFull = payload != null && Boolean.TRUE.equals(payload.get("nowPlaying")) && !Boolean.TRUE.equals(payload.get("nowPlayingShared"));
            if (timer != null) timer.execute(this::updateOverlay);
            return;
        }
        if (!("ks.ha.subscription." + SUB).equals(event) || payload == null) return;
        Object type = payload.get("type");
        if ("resubscribed".equals(type) && timer != null) timer.execute(this::fetchEvents);
        if (!"event".equals(type)) return;
        Object ev = payload.get("event");
        Object result = ev instanceof Map ? ((Map<?, ?>) ev).get("result") : null;
        try {
            if (result instanceof Map) live = new JSONObject((Map<?, ?>) result);
            else if (result instanceof String) live = new JSONObject((String) result);
            else return;
        } catch (Exception error) {
            host.log("Glass Info: bad template result " + error.getMessage());
            return;
        }
        publish(false);
    }

    @Override
    public synchronized void stop() {
        if (timer != null) timer.shutdownNow();
        timer = null;
        if (host != null) {
            hideOverlay();
            hideStrip();
            try { host.unsubscribe("screensaver.state"); } catch (RuntimeException ignored) { }
            try { host.unsubscribe("screensaver.appearance"); } catch (RuntimeException ignored) { }
            try { host.haUnsubscribe(SUB); } catch (RuntimeException ignored) { }
            try { host.removeScreensaver(KEY); } catch (RuntimeException ignored) { }
        }
        subscribed = false;
        host = null;
    }

    /** Draws the stock widgets and At a Glance in black over Glass Info only. KS drops the tint when it leaves. */
    private void hideWidgets() {
        PluginHost h;
        synchronized (this) {
            h = host;
            if (h == null || Boolean.FALSE.equals(settings.get("hideWidgets"))) return;
        }
        try {
            h.executeCommand("setScreensaverTint", Collections.singletonMap("color", "#000000"), (ok, data, error) -> { });
        } catch (RuntimeException ignored) { }
    }

    // ---- Day widgets (native overlay over other screensavers) ----

    /** Shows the widgets while a screensaver other than Glass Info is on screen, without full-screen Now Playing. */
    private void updateOverlay() {
        PluginHost h;
        synchronized (this) {
            h = host;
            if (h == null) return;
            if (nowPlayingFull) {
                hideOverlay();
                if (!Boolean.FALSE.equals(settings.get("nowPlayingStrip"))) showStrip();
                return;
            }
            hideStrip();
            if (Boolean.FALSE.equals(settings.get("dayWidgets"))) { hideOverlay(); return; }
        }
        try {
            h.executeCommand("getScreensaverMode", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
                Object mode = ok && data instanceof Map ? ((Map<?, ?>) data).get("mode") : null;
                Object active = ok && data instanceof Map ? ((Map<?, ?>) data).get("active") : null;
                synchronized (GlassInfoPlugin.this) {
                    if (active instanceof Boolean) screensaverActive = (Boolean) active;
                    if (MODE.equals(mode) || !screensaverActive) hideOverlay(); else showOverlay();
                }
            });
        } catch (RuntimeException error) {
            synchronized (this) { showOverlay(); }
        }
    }

    private void showOverlay() {
        if (host == null || overlayShown) return;
        OverlaySpec spec = OverlaySpec.fullScreen().layer(OverlaySpec.LAYER_TOP).touchable(false);
        try { spec = spec.enter(OverlaySpec.FADE).exit(OverlaySpec.FADE); } catch (LinkageError ignored) { }
        try {
            host.showOverlay(OVERLAY, spec, new OverlayFactory() {
                @Override public View create(android.content.Context context, KsTheme theme) {
                    GlassWidgetsView view = new GlassWidgetsView(context, theme, () -> snapshot);
                    widgetsView = view;
                    return view;
                }
                @Override public void onDestroy(View view) {
                    if (widgetsView == view) widgetsView = null;
                }
            });
            overlayShown = true;
        } catch (RuntimeException | LinkageError error) {
            host.log("Glass Info: overlay unavailable " + error.getMessage());
        }
    }

    private void showStrip() {
        if (host == null || stripShown) return;
        OverlaySpec spec = OverlaySpec.fullScreen().layer(OverlaySpec.LAYER_TOP).touchable(false);
        try { spec = spec.enter(OverlaySpec.FADE).exit(OverlaySpec.FADE); } catch (LinkageError ignored) { }
        try {
            host.showOverlay(STRIP, spec, new OverlayFactory() {
                @Override public View create(android.content.Context context, KsTheme theme) {
                    GlassWidgetsView view = new GlassWidgetsView(context, theme, () -> snapshot, true);
                    stripView = view;
                    return view;
                }
                @Override public void onDestroy(View view) {
                    if (stripView == view) stripView = null;
                }
            });
            stripShown = true;
        } catch (RuntimeException | LinkageError error) {
            host.log("Glass Info: strip unavailable " + error.getMessage());
        }
    }

    private void hideStrip() {
        if (host == null || !stripShown) return;
        try { host.hideOverlay(STRIP); } catch (RuntimeException ignored) { }
        stripShown = false;
    }

    private void hideOverlay() {
        if (host == null || !overlayShown) return;
        try { host.hideOverlay(OVERLAY); } catch (RuntimeException ignored) { }
        overlayShown = false;
    }

    // ---- Home Assistant ----

    private String entity(String key) {
        Object value = settings.get(key);
        return value instanceof String && ((String) value).matches(ENTITY) ? (String) value : "";
    }

    /** One template returns every live value as JSON, so one subscription covers them all. */
    private String template() {
        String w = entity("weatherEntity"), in = entity("insideEntity"), info = entity("infoEntity");
        StringBuilder t = new StringBuilder("{{ {");
        t.append("'temp': ").append(w.isEmpty() ? "none" : "state_attr('" + w + "','temperature')").append(", ");
        t.append("'unit': ").append(w.isEmpty() ? "''" : "state_attr('" + w + "','temperature_unit')").append(", ");
        t.append("'cond': ").append(w.isEmpty() ? "''" : "states('" + w + "')").append(", ");
        t.append("'inside': ").append(in.isEmpty() ? "''" : "states('" + in + "')").append(", ");
        t.append("'info': ").append(info.isEmpty() ? "''" : "states('" + info + "')").append(", ");
        t.append("'g': [");
        boolean first = true;
        for (int i = 1; i <= 4; i++) {
            String e = entity("glance" + i);
            if (e.isEmpty()) continue;
            Object raw = settings.get("glance" + i + "Name");
            String name = raw instanceof String ? ((String) raw).replaceAll("[^\\p{L}\\p{N} ._-]", "") : "";
            if (!first) t.append(", ");
            first = false;
            t.append("['").append(name).append("', ((states('").append(e).append("') | float(0) | round(0) | int) ~ (' ' ~ state_attr('")
             .append(e).append("','unit_of_measurement') if state_attr('").append(e).append("','unit_of_measurement') else '')) if states('")
             .append(e).append("') | is_number else state_translated('").append(e).append("')]");
        }
        t.append("]");
        t.append("} | to_json }}");
        return t.toString();
    }

    private void subscribe() {
        if (subscribed) {
            try { host.haUnsubscribe(SUB); } catch (RuntimeException ignored) { }
            subscribed = false;
        }
        Map<String, Object> message = new HashMap<>();
        message.put("type", "render_template");
        message.put("template", template());
        try {
            host.haSubscribe(SUB, message, (ok, data, error) -> {
                if (!ok) host.status("Home Assistant: " + error, true);
            });
            subscribed = true;
            host.status("Choose Glass Info under Screensaver > Screensaver mode.", false);
        } catch (RuntimeException error) {
            host.status("Home Assistant access is unavailable: " + error.getMessage(), true);
        }
    }

    private int number(String key, int fallback, int min, int max) {
        Object value = settings.get(key);
        int n = value instanceof Number ? ((Number) value).intValue() : fallback;
        return Math.max(min, Math.min(max, n));
    }

    private void fetchEvents() {
        PluginHost h;
        List<String> calendars = new ArrayList<>();
        synchronized (this) {
            h = host;
            for (String key : new String[] {"calendar1", "calendar2"}) if (!entity(key).isEmpty()) calendars.add(entity(key));
        }
        if (h == null) return;
        if (calendars.isEmpty()) {
            synchronized (this) { events = new JSONArray(); publish(false); }
            return;
        }
        Map<String, Object> duration = new HashMap<>();
        duration.put("days", number("days", 3, 1, 7));
        Map<String, Object> data = new HashMap<>();
        data.put("duration", duration);
        Map<String, Object> target = new HashMap<>();
        target.put("entity_id", calendars);
        Map<String, Object> message = new HashMap<>();
        message.put("type", "call_service");
        message.put("domain", "calendar");
        message.put("service", "get_events");
        message.put("service_data", data);
        message.put("target", target);
        message.put("return_response", true);
        try {
            h.haCall(message, (ok, result, error) -> {
                if (!ok) { h.log("Glass Info: events failed " + error); return; }
                synchronized (GlassInfoPlugin.this) {
                    events = collect(result);
                    publish(false);
                }
            });
        } catch (RuntimeException error) {
            h.log("Glass Info: events unavailable " + error.getMessage());
        }
    }

    /** Flattens {response: {calendar.x: {events: [...]}}} into a short, sorted list. */
    private JSONArray collect(Object result) {
        List<JSONObject> all = new ArrayList<>();
        try {
            JSONObject root = result instanceof Map ? new JSONObject((Map<?, ?>) result) : new JSONObject(String.valueOf(result));
            JSONObject response = root.optJSONObject("response");
            if (response == null) return new JSONArray();
            for (java.util.Iterator<String> it = response.keys(); it.hasNext(); ) {
                String cal = it.next();
                JSONObject entry = response.optJSONObject(cal);
                JSONArray list = entry == null ? null : entry.optJSONArray("events");
                if (list == null) continue;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject e = list.optJSONObject(i);
                    if (e == null) continue;
                    JSONObject out = new JSONObject();
                    out.put("s", e.optString("start"));
                    out.put("e", e.optString("end"));
                    out.put("m", e.optString("summary"));
                    out.put("c", cal.equals(entity("calendar1")) ? 1 : 2);
                    all.add(out);
                }
            }
        } catch (Exception error) {
            if (host != null) host.log("Glass Info: could not read events " + error.getMessage());
        }
        Collections.sort(all, (a, b) -> a.optString("s").compareTo(b.optString("s")));
        JSONArray out = new JSONArray();
        int max = number("maxEvents", 4, 1, 6);
        for (int i = 0; i < all.size() && i < max; i++) out.put(all.get(i));
        return out;
    }

    // ---- Renderer ----

    private void publish(boolean force) {
        if (host == null) return;
        Map<String, Object> options = new LinkedHashMap<>();
        Object variant = settings.get("variant");
        options.put("variant", "Midnight".equals(variant) ? "midnight" : "black");
        Object accent = settings.get("accent");
        options.put("accent", accent instanceof String && ((String) accent).matches("#[0-9a-fA-F]{6}") ? accent : "#FFB547");
        options.put("h24", !Boolean.FALSE.equals(settings.get("clock24")));
        JSONObject data = new JSONObject();
        try {
            data.put("live", live);
            data.put("events", events);
        } catch (Exception ignored) { }
        options.put("data", data.toString());
        int accentColor;
        try { accentColor = Color.parseColor((String) options.get("accent")); } catch (RuntimeException error) { accentColor = 0xFFFFB547; }
        snapshot = new GlassWidgetsView.Snapshot(live, events, accentColor, Boolean.TRUE.equals(options.get("h24")), new HashMap<>(settings));
        GlassWidgetsView v = widgetsView;
        if (v != null) v.postInvalidate();
        GlassWidgetsView sv = stripView;
        if (sv != null) sv.postInvalidate();
        String fingerprint = options.toString();
        if (!force && fingerprint.equals(lastPublished)) return;
        lastPublished = fingerprint;
        try {
            host.publishScreensaverAsset(KEY, "Glass Info", "glass/index.html", options);
        } catch (RuntimeException error) {
            host.status("Could not publish the screensaver: " + error.getMessage(), true);
        }
    }
}
