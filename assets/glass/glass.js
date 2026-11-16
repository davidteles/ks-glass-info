'use strict';
(function () {
  var opts = {};
  try { opts = JSON.parse(decodeURIComponent(location.hash.slice(1) || '%7B%7D')); } catch (e) { opts = {}; }

  // Preview only: opening the file directly shows sample data, never on the tablet.
  if (!opts.data && (location.protocol === 'file:' || location.protocol === 'data:' || location.protocol === 'about:' || /[?&]preview/.test(location.search))) {
    var d = new Date(), iso = function (days, h) { var x = new Date(d); x.setDate(x.getDate() + days); x.setHours(h || 0, 0, 0, 0); return x.toISOString(); };
    opts = { variant: 'black', accent: '#FFB547', h24: true, data: JSON.stringify({
      live: { temp: 17, unit: '°C', cond: 'partlycloudy', inside: '21–23°', info: '🔌 Car charging 14% → 80% · 🧺 Laundry waiting' },
      events: [
        { s: iso(0).slice(0, 10), e: iso(1).slice(0, 10), m: 'Family birthday', c: 1 },
        { s: iso(1, 18), e: iso(1, 19), m: 'Swimming class', c: 1 },
        { s: iso(3).slice(0, 10), e: iso(4).slice(0, 10), m: 'Public holiday', c: 2 },
        { s: iso(3, 20), e: iso(3, 22), m: 'Dinner with friends', c: 1 }
      ] }) };
  }

  var data = {};
  try { data = JSON.parse(opts.data || '{}'); } catch (e) { data = {}; }
  var live = data.live || {}, events = data.events || [];
  if (opts.variant === 'midnight') document.body.classList.add('midnight');
  if (/^#[0-9a-f]{6}$/i.test(opts.accent || '')) document.documentElement.style.setProperty('--accent', opts.accent);
  var lang = navigator.language || 'en';
  var $ = function (id) { return document.getElementById(id); };

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function tick() {
    var now = new Date(), h = now.getHours();
    if (opts.h24 === false) h = h % 12 || 12;
    $('time').innerHTML = (opts.h24 === false ? h : pad(h)) + '<span class="colon">:</span>' + pad(now.getMinutes());
    var ds = now.toLocaleDateString(lang, { weekday: 'long', day: 'numeric', month: 'long' }); $('date').textContent = ds.charAt(0).toUpperCase() + ds.slice(1);
    setTimeout(tick, 1000 - now.getMilliseconds() + 5);
  }

  var ICONS = {
    sun: '<circle cx="32" cy="32" r="11" fill="#FFC75A"/><g stroke="#FFC75A" stroke-width="3" stroke-linecap="round"><path d="M32 8v6M32 50v6M8 32h6M50 32h6M15 15l4 4M45 45l4 4M15 49l4-4M45 19l4-4"/></g>',
    moon: '<path d="M40 10a22 22 0 1 0 14 34A18 18 0 0 1 40 10z" fill="#D9DEE8"/>',
    cloud: '<path d="M20 48h26a10 10 0 0 0 0-20 14 14 0 0 0-27-3A11 11 0 0 0 20 48z" fill="#C9D1DD"/>',
    partly: '<circle cx="24" cy="24" r="9" fill="#FFC75A"/><path d="M24 50h22a9 9 0 0 0 0-18 12 12 0 0 0-23-2.5A9.5 9.5 0 0 0 24 50z" fill="#C9D1DD"/>',
    rain: '<path d="M20 40h26a10 10 0 0 0 0-20 14 14 0 0 0-27-3A11 11 0 0 0 20 40z" fill="#AEB8C7"/><g stroke="#7FB7FF" stroke-width="3" stroke-linecap="round"><path d="M24 46l-3 7M34 46l-3 7M44 46l-3 7"/></g>',
    storm: '<path d="M20 40h26a10 10 0 0 0 0-20 14 14 0 0 0-27-3A11 11 0 0 0 20 40z" fill="#9AA4B3"/><path d="M33 42l-6 10h6l-3 8 9-12h-6l3-6z" fill="#FFC75A"/>',
    fog: '<g stroke="#C9D1DD" stroke-width="3" stroke-linecap="round"><path d="M14 26h36M10 34h44M16 42h32"/></g>'
  };
  function icon(cond) {
    var night = new Date().getHours() >= 19 || new Date().getHours() < 7;
    var c = String(cond || '');
    var k = /sunny|clear/.test(c) ? (night || c === 'clear-night' ? 'moon' : 'sun')
      : /partly/.test(c) ? (night ? 'cloud' : 'partly')
      : /lightning|hail/.test(c) ? 'storm'
      : /rain|pour|snow/.test(c) ? 'rain'
      : /fog/.test(c) ? 'fog' : 'cloud';
    return '<svg viewBox="0 0 64 64" aria-hidden="true">' + ICONS[k] + '</svg>';
  }
  var COND = { 'clear-night': 'clear', partlycloudy: 'partly cloudy', lightning: 'thunder', 'lightning-rainy': 'thunderstorm', pouring: 'heavy rain', 'snowy-rainy': 'sleet', windy: 'windy', 'windy-variant': 'windy' };

  function weather() {
    if (live.temp === null || live.temp === undefined || live.temp === '') { $('weather').style.display = 'none'; return; }
    $('wx-icon').innerHTML = icon(live.cond);
    $('wx-temp').textContent = Math.round(Number(live.temp)) + (live.unit || '°');
    $('wx-cond').textContent = COND[live.cond] || String(live.cond || '').replace(/-/g, ' ');
    var inside = String(live.inside || '');
    $('wx-inside').textContent = inside && !/unknown|unavailable/.test(inside) ? inside + ' inside' : '';
  }

  function when(e) {
    var allDay = /^\d{4}-\d{2}-\d{2}$/.test(e.s);
    var start = allDay ? new Date(e.s + 'T00:00:00') : new Date(e.s);
    var today = new Date(); today.setHours(0, 0, 0, 0);
    var day = new Date(start); day.setHours(0, 0, 0, 0);
    var diff = Math.round((day - today) / 86400000);
    var dayText = diff <= 0 ? 'Today' : diff === 1 ? 'Tomorrow' : start.toLocaleDateString(lang, { weekday: 'short', day: 'numeric' });
    var timeText = allDay ? 'All day' : start.toLocaleTimeString(lang, { hour: '2-digit', minute: '2-digit', hour12: opts.h24 === false });
    return dayText + ' · ' + timeText;
  }
  function eventsList() {
    var ul = $('events');
    ul.textContent = '';
    if (!events.length) { var li = document.createElement('li'); li.className = 'empty'; li.textContent = 'Nothing planned'; ul.appendChild(li); return; }
    events.forEach(function (e) {
      var li = document.createElement('li');
      li.className = e.c === 2 ? 'c2' : 'c1';
      var bar = document.createElement('span'); bar.className = 'bar';
      var txt = document.createElement('div'); txt.className = 'txt';
      var w = document.createElement('div'); w.className = 'when'; w.textContent = when(e);
      var m = document.createElement('div'); m.className = 'what'; m.textContent = e.m || '';
      txt.appendChild(w); txt.appendChild(m); li.appendChild(bar); li.appendChild(txt); ul.appendChild(li);
    });
  }
  function chips() {
    var box = $('chips'); box.textContent = '';
    String(live.info || '').split(' · ').map(function (s) { return s.trim(); }).filter(function (s) { return s && !/^(unknown|unavailable)$/.test(s); }).slice(0, 6).forEach(function (s) {
      var c = document.createElement('span'); c.className = 'chip'; c.textContent = s; box.appendChild(c);
    });
  }

  tick(); weather(); eventsList(); chips();
})();
