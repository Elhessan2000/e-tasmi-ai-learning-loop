/* eslint-disable no-undef */
/**
 * Instructor Dashboard "Teaching Control Center" — vanilla JS.
 *
 * Modules:
 *   - Clock: greeting strip clock and clock-skew estimator (server time as anchor)
 *   - KPI: count-up animation on first paint
 *   - Countdown: countdown to next session, live state, overrun bar
 *   - Calendar: week / day / month, now-line, drawer
 *   - Heartbeat: 30-second poll to refresh nextSession + KPIs
 *
 * Bootstrap data is read from <script id="idash-bootstrap" type="application/json">.
 */
(function () {
  'use strict';

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        if (k === 'class') node.className = attrs[k];
        else if (k === 'text') node.textContent = attrs[k];
        else if (k === 'html') node.innerHTML = attrs[k];
        else if (k === 'style' && typeof attrs[k] === 'object') Object.assign(node.style, attrs[k]);
        else if (k.indexOf('data-') === 0 || k === 'role' || k === 'aria-label' || k === 'aria-selected' || k === 'tabindex') node.setAttribute(k, attrs[k]);
        else node[k] = attrs[k];
      });
    }
    (children || []).forEach(function (c) {
      if (c == null) return;
      if (typeof c === 'string') node.appendChild(document.createTextNode(c));
      else node.appendChild(c);
    });
    return node;
  }
  function pad2(n) { n = Number(n); return (n < 10 ? '0' : '') + n; }

  // -------------- Bootstrap --------------
  function readBootstrap() {
    var node = document.getElementById('idash-bootstrap');
    if (!node) return null;
    try { return JSON.parse(node.textContent || node.innerText || '{}'); }
    catch (e) { return null; }
  }

  // -------------- Server clock anchor --------------
  // Computes a function that returns "current server time in ms" using the
  // pageLoad anchor + server now we received. Protects against wrong client clocks.
  function makeServerClock(serverNowAtRender) {
    var clientAnchor = Date.now();
    return function nowMs() {
      return serverNowAtRender + (Date.now() - clientAnchor);
    };
  }

  // -------------- Clock module (greeting strip) --------------
  var Clock = {
    init: function (now) {
      this.node = $('[data-idash-clock]');
      if (!this.node) return;
      this.now = now;
      this.tick();
      this.timer = setInterval(this.tick.bind(this), 1000);
    },
    tick: function () {
      if (!this.node) return;
      var ts = new Date(this.now());
      var hh = pad2(ts.getHours());
      var mm = pad2(ts.getMinutes());
      var ss = pad2(ts.getSeconds());
      var hNode = this.node.querySelector('[data-idash-clock-h]');
      var mNode = this.node.querySelector('[data-idash-clock-m]');
      var sNode = this.node.querySelector('[data-idash-clock-s]');
      if (hNode && mNode) {
        hNode.textContent = hh;
        mNode.textContent = mm;
        if (sNode) sNode.textContent = ss;
        return;
      }
      this.node.innerHTML = '<strong>' + hh + ':' + mm + '</strong>:' + ss;
    }
  };

  // -------------- KPI count-up --------------
  var KPI = {
    init: function () {
      $$('.idash-kpi-card__value[data-target]').forEach(function (node) {
        var target = parseInt(node.getAttribute('data-target'), 10) || 0;
        var dur = 600;
        var start = performance.now();
        function step(t) {
          var p = Math.min(1, (t - start) / dur);
          var ease = 1 - Math.pow(1 - p, 3);
          node.textContent = Math.round(target * ease).toString();
          if (p < 1) requestAnimationFrame(step);
          else node.textContent = String(target);
        }
        node.textContent = '0';
        requestAnimationFrame(step);
      });
    },
    update: function (kpi) {
      function setNum(sel, value) {
        var node = $(sel);
        if (!node || value == null) return;
        node.setAttribute('data-target', String(value));
        node.textContent = String(value);
      }
      setNum('[data-kpi="todaySessions"]', kpi.todaySessions);
      setNum('[data-kpi="upcomingSessions"]', kpi.upcomingSessions);
      setNum('[data-kpi="pendingEvaluations"]', kpi.pendingEvaluations);
      setNum('[data-kpi="activeStudents"]', kpi.activeStudents);
    }
  };

  // -------------- Countdown --------------
  var Countdown = {
    init: function (root, now, session, ctx) {
      this.root = root;
      this.now = now;
      this.session = session;
      this.ctx = ctx;
      this.titleNode = $('[data-cd="title"]', root);
      this.subNode = $('[data-cd="sub"]', root);
      this.captionNode = $('[data-cd="caption"]', root);
      this.digitsNode = $('[data-cd="digits"]', root);
      this.unitNode = $('[data-cd="unit"]', root);
      this.pillNode = $('[data-cd="pill"]', root);
      this.pillDot = $('[data-cd="pill-dot"]', root);
      this.pillText = $('[data-cd="pill-text"]', root);
      this.ringFg = $('[data-cd="ring-fg"]', root);
      this.ringPct = $('[data-cd="ring-pct"]', root);
      this.footNode = $('[data-cd="foot"]', root);
      this.overrunBar = $('[data-cd="overrun-bar"]', root);

      this.RING_C = 2 * Math.PI * 60; // r=60 in SVG below
      if (this.ringFg) {
        this.ringFg.setAttribute('stroke-dasharray', String(this.RING_C));
        this.ringFg.setAttribute('stroke-dashoffset', String(this.RING_C));
      }

      if (!this.session) {
        this.renderEmpty();
        return;
      }
      this.renderStatic();
      this.tick();
      this.timer = setInterval(this.tick.bind(this), 1000);
    },
    setSession: function (session) {
      this.session = session;
      this._rollPending = false;
      if (!this.session) {
        this.renderEmpty();
        return;
      }
      this.renderStatic();
      this.tick();
    },
    renderEmpty: function () {
      this.root.classList.add('idash-countdown--empty');
      this.root.setAttribute('data-state', 'empty');
      if (this.titleNode) this.titleNode.textContent = t('instructor.dashboard.noUpcomingSessions');
      if (this.subNode) this.subNode.textContent = t('instructor.dashboard.noScheduledYet');
      if (this.captionNode) this.captionNode.textContent = '—';
      if (this.digitsNode) this.digitsNode.innerHTML = '<span class="idash-d-sep">—</span>';
      if (this.pillText) this.pillText.textContent = t('instructor.dashboard.noSession');
      if (this.pillNode) this.pillNode.classList.remove('idash-pill--live');
      if (this.footNode) {
        this.footNode.innerHTML = '';
        this.footNode.appendChild(el('a', {
          class: 'idash-btn idash-btn--primary',
          href: this.ctx + '/instructor/sessions',
          text: t('instructor.dashboard.scheduleSession')
        }));
      }
    },
    renderStatic: function () {
      this.root.classList.remove('idash-countdown--empty');
      var s = this.session;
      if (this.titleNode) this.titleNode.textContent = s.title || t('instructor.dashboard.untitledSession');

      var subBits = [];
      if (s.portion) subBits.push(s.portion);
      if (s.durationMinutes) subBits.push(t('common.minutesShort', { count: s.durationMinutes }));
      if (this.subNode) {
        this.subNode.textContent = subBits.length ? subBits.join('  ·  ') : '';
      }

      this.renderFoot();
    },
    renderFoot: function () {
      if (!this.footNode) return;
      this.footNode.innerHTML = '';
      var s = this.session;
      var ctx = this.ctx;
      var status = s.status;

      if (status === 'ONGOING') {
        var openLive = el('a', {
          class: 'idash-btn idash-btn--live',
          href: ctx + '/instructor/live-session?sessionId=' + s.id,
          target: '_blank',
          rel: 'noopener'
        }, [el('span', { html: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m23 7-7 5 7 5V7Z" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/><rect x="1" y="5" width="15" height="14" rx="2" stroke="currentColor" stroke-width="1.7"/></svg>' }), document.createTextNode(t('instructor.dashboard.openLiveSession'))]);
        this.footNode.appendChild(openLive);
        this.footNode.appendChild(el('a', { class: 'idash-btn idash-btn--ghost', href: ctx + '/instructor/sessions', text: t('common.manage') }));
      } else {
        // Far / near both show start (server-side will own the state transition)
        var form = el('form', { method: 'post', action: ctx + '/instructor/sessions' });
        form.appendChild(el('input', { type: 'hidden', name: 'action', value: 'start' }));
        form.appendChild(el('input', { type: 'hidden', name: 'sessionId', value: String(s.id) }));
        var startBtn = el('button', { class: 'idash-btn idash-btn--primary', type: 'submit' }, [
          document.createTextNode(t('instructor.dashboard.startSession'))
        ]);
        form.appendChild(startBtn);
        this.footNode.appendChild(form);
        this.footNode.appendChild(el('a', { class: 'idash-btn idash-btn--ghost', href: ctx + '/instructor/sessions', text: t('common.manage') }));
      }
    },
    tick: function () {
      if (!this.session) return;
      var s = this.session;
      var nowMs = this.now();
      var startMs = s.startEpochMs;
      var endMs = s.endEpochMs;

      // Auto-roll: if a SCHEDULED session has visibly slipped past its end-time
      // (instructor never started it), or an ONGOING session has been over for
      // more than ~30 min, ask the server for the next one immediately. This
      // covers the gap between heartbeat polls so the dashboard never lingers.
      if (endMs > 0) {
        var pastBy = nowMs - endMs;
        var rollAfter = (s.status === 'ONGOING') ? (30 * 60 * 1000) : 60 * 1000;
        if (pastBy > rollAfter && !this._rollPending) {
          this._rollPending = true;
          var self = this;
          if (Heartbeat && typeof Heartbeat.tick === 'function') {
            Heartbeat.tick();
          }
          // Allow another roll attempt after 20s if heartbeat hasn't replaced us yet.
          setTimeout(function () { self._rollPending = false; }, 20000);
        }
      }

      // State machine
      var state, captionLabel, deltaMs, pctRing;
      if (s.status === 'ONGOING') {
        state = 'live';
        deltaMs = Math.max(0, nowMs - (startMs > 0 ? startMs : nowMs));
        captionLabel = t('instructor.dashboard.liveFor');
        // Ring shows session progress against duration
        var dur = (endMs > 0 && startMs > 0) ? (endMs - startMs) : ((s.durationMinutes || 60) * 60000);
        pctRing = dur > 0 ? Math.min(1, deltaMs / dur) : 0;

        // Overrun shading
        var overrunRatio = dur > 0 ? deltaMs / dur : 0;
        if (overrunRatio >= 1) this.root.setAttribute('data-overrun', 'over');
        else if (overrunRatio >= 0.9) this.root.setAttribute('data-overrun', 'warn');
        else this.root.removeAttribute('data-overrun');
        if (this.overrunBar) this.overrunBar.style.width = Math.min(120, overrunRatio * 100) + '%';
      } else {
        // Pre-session
        if (startMs <= 0) {
          // No precise time; show "scheduled"
          state = 'far';
          deltaMs = 0;
          captionLabel = t('instructor.dashboard.scheduled');
          pctRing = 0;
        } else {
          deltaMs = startMs - nowMs;
          if (deltaMs <= 60 * 60 * 1000 && deltaMs > 0) state = 'near';
          else if (deltaMs <= 0) state = 'near';
          else state = 'far';
          captionLabel = deltaMs <= 0 ? t('instructor.dashboard.startingNow') : t('instructor.dashboard.startsIn');
          // Ring fills as we approach (over the last 24h window)
          var horizon = 24 * 60 * 60 * 1000;
          var elapsed = horizon - Math.max(0, Math.min(horizon, deltaMs));
          pctRing = horizon > 0 ? elapsed / horizon : 0;
        }
        this.root.removeAttribute('data-overrun');
      }

      this.root.setAttribute('data-state', state);

      if (this.captionNode) this.captionNode.textContent = captionLabel;

      // Pill
      if (this.pillNode && this.pillText) {
        if (state === 'live') {
          this.pillNode.classList.add('idash-pill--live');
          this.pillText.textContent = t('instructor.dashboard.liveNow');
        } else if (state === 'near') {
          this.pillNode.classList.remove('idash-pill--live');
          this.pillText.textContent = t('instructor.dashboard.startingSoon');
        } else {
          this.pillNode.classList.remove('idash-pill--live');
          this.pillText.textContent = t('instructor.dashboard.nextUp');
        }
      }

      // Digits
      this.renderDigits(Math.abs(deltaMs));

      // Ring
      if (this.ringFg) {
        var offset = this.RING_C * (1 - pctRing);
        this.ringFg.setAttribute('stroke-dashoffset', String(offset));
      }
      if (this.ringPct) {
        this.ringPct.textContent = Math.round(pctRing * 100) + '%';
      }
    },
    renderDigits: function (ms) {
      if (!this.digitsNode) return;
      var totalSec = Math.floor(ms / 1000);
      var h = Math.floor(totalSec / 3600);
      var m = Math.floor((totalSec % 3600) / 60);
      var s = totalSec % 60;
      this.digitsNode.innerHTML =
        '<span>' + pad2(h) + '<span class="idash-countdown__unit">h</span></span>' +
        '<span class="idash-d-sep">:</span>' +
        '<span>' + pad2(m) + '<span class="idash-countdown__unit">m</span></span>' +
        '<span class="idash-d-sep">:</span>' +
        '<span>' + pad2(s) + '<span class="idash-countdown__unit">s</span></span>';
    }
  };

  // -------------- Drawer --------------
  var Drawer = {
    init: function (ctx) {
      this.ctx = ctx;
      this.root = $('.idash-drawer');
      this.scrim = $('.idash-drawer__scrim');

      // PORTAL: detach drawer + scrim from any nested ancestor and re-mount under
      // <body>. This bypasses any ancestor with `transform`, `filter`,
      // `backdrop-filter`, `contain`, or `overflow:hidden` that would otherwise
      // confine our `position:fixed` overlay to a sub-rectangle of the viewport.
      if (this.root && this.root.parentNode !== document.body) {
        document.body.appendChild(this.root);
      }
      if (this.scrim && this.scrim.parentNode !== document.body) {
        document.body.appendChild(this.scrim);
      }

      this.titleNode = $('[data-drawer="title"]', this.root);
      this.body = $('[data-drawer="body"]', this.root);
      this.foot = $('[data-drawer="foot"]', this.root);
      this.statusPill = $('[data-drawer="status-pill"]', this.root);
      var self = this;
      if (this.scrim) this.scrim.addEventListener('click', function () { self.close(); });
      $$('[data-drawer-close]', this.root).forEach(function (n) {
        n.addEventListener('click', function () { self.close(); });
      });
      document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') self.close();
      });
    },
    open: function (session) {
      if (!this.root) return;
      var statusLabel = ({
        'SCHEDULED': t('instructor.dashboard.drawerUpcoming'),
        'ONGOING': t('instructor.dashboard.drawerLiveNow'),
        'COMPLETED': t('instructor.dashboard.drawerCompleted'),
        'CANCELLED': t('instructor.dashboard.drawerCancelled')
      })[session.status] || session.status;
      var statusKey = (session.status || 'SCHEDULED').toLowerCase();

      if (this.titleNode) this.titleNode.textContent = session.title || t('common.session');
      this.root.setAttribute('data-status', session.status || 'SCHEDULED');
      if (this.statusPill) {
        this.statusPill.textContent = statusLabel;
        this.statusPill.setAttribute('data-status', session.status || 'SCHEDULED');
      }

      // Build a compact two-column meta grid (Date/Time first, then Length/Capacity, etc.)
      var meta = el('div', { class: 'idash-drawer__meta' });
      meta.appendChild(this.metaCell('date', t('instructor.dashboard.drawerDate'), session.date || '—'));
      meta.appendChild(this.metaCell('time', t('instructor.dashboard.drawerTime'), session.time || '—'));
      if (session.durationMinutes) meta.appendChild(this.metaCell('len', t('instructor.dashboard.drawerLength'), t('common.minutesShort', { count: session.durationMinutes })));
      if (session.capacity != null) meta.appendChild(this.metaCell('seats', t('instructor.dashboard.drawerCapacity'), t('instructor.dashboard.drawerSeats', { count: session.capacity })));

      var portionRow = null;
      if (session.portion) {
        portionRow = el('div', { class: 'idash-drawer__row' }, [
          el('span', { html: this.iconSvg('book') }),
          el('div', {}, [
            el('span', { class: 'idash-drawer__row-label', text: t('instructor.dashboard.drawerPortion') }),
            el('span', { class: 'idash-drawer__row-value', text: session.portion })
          ])
        ]);
      }
      var levelRow = null;
      if (session.level) {
        levelRow = el('div', { class: 'idash-drawer__row' }, [
          el('span', { html: this.iconSvg('level') }),
          el('div', {}, [
            el('span', { class: 'idash-drawer__row-label', text: t('instructor.dashboard.drawerLevel') }),
            el('span', { class: 'idash-drawer__row-value', text: this.prettyLevel(session.level) })
          ])
        ]);
      }

      if (this.body) {
        this.body.innerHTML = '';
        this.body.appendChild(meta);
        if (portionRow) this.body.appendChild(portionRow);
        if (levelRow) this.body.appendChild(levelRow);
      }

      if (this.foot) {
        this.foot.innerHTML = '';
        if (session.status === 'ONGOING') {
          this.foot.appendChild(el('a', {
            class: 'idash-btn idash-btn--live',
            href: this.ctx + '/instructor/live-session?sessionId=' + session.id,
            target: '_blank', rel: 'noopener', text: t('instructor.dashboard.openLiveSession')
          }));
        } else if (session.status === 'SCHEDULED') {
          var form = el('form', { method: 'post', action: this.ctx + '/instructor/sessions', class: 'idash-drawer__form' });
          form.appendChild(el('input', { type: 'hidden', name: 'action', value: 'start' }));
          form.appendChild(el('input', { type: 'hidden', name: 'sessionId', value: String(session.id) }));
          form.appendChild(el('button', { class: 'idash-btn idash-btn--primary', type: 'submit', text: t('instructor.dashboard.startSession') }));
          this.foot.appendChild(form);
        }
        this.foot.appendChild(el('a', { class: 'idash-btn idash-btn--ghost', href: this.ctx + '/instructor/sessions', text: t('common.manage') }));
      }

      this.root.setAttribute('data-open', 'true');
      this.root.setAttribute('aria-hidden', 'false');
      if (this.scrim) this.scrim.setAttribute('data-open', 'true');
      document.body.style.overflow = 'hidden';
    },
    metaCell: function (icon, label, value) {
      return el('div', { class: 'idash-drawer__meta-cell' }, [
        el('span', { class: 'idash-drawer__meta-icon', html: this.iconSvg(icon) }),
        el('div', {}, [
          el('span', { class: 'idash-drawer__meta-label', text: label }),
          el('span', { class: 'idash-drawer__meta-value', text: String(value) })
        ])
      ]);
    },
    prettyLevel: function (raw) {
      if (!raw) return '';
      return String(raw).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, function (c) { return c.toUpperCase(); });
    },
    close: function () {
      if (!this.root) return;
      this.root.setAttribute('data-open', 'false');
      this.root.setAttribute('aria-hidden', 'true');
      if (this.scrim) this.scrim.setAttribute('data-open', 'false');
      document.body.style.overflow = '';
    },
    row: function (icon, label, value) {
      return el('div', { class: 'idash-drawer__row' }, [
        el('span', { html: this.iconSvg(icon) }),
        el('div', {}, [
          el('span', { class: 'idash-drawer__row-label', text: label }),
          el('span', { class: 'idash-drawer__row-value', text: String(value) })
        ])
      ]);
    },
    iconSvg: function (kind) {
      var icons = {
        date: '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>',
        time: '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/><path d="M12 7v5l4 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>',
        len: '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M12 8v8M9 12l3 3 3-3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/></svg>',
        book: '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
        level: '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M3 21h18M5 17h2v4H5zM10 13h2v8h-2zM15 9h2v12h-2zM20 5h2v16h-2z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
        seats: '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M16 13a4 4 0 1 0-8 0v3h8v-3Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><circle cx="12" cy="7" r="3" stroke="currentColor" stroke-width="1.6"/></svg>',
        status: '<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m9 12 2 2 4-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/></svg>'
      };
      return icons[kind] || icons.status;
    }
  };

  // -------------- Calendar --------------
  // Full 24h timeline. Hour-row height stays moderate so morning + evening are
  // both readable; the grid container itself scrolls vertically and we
  // auto-scroll to ~current hour on first paint so the user sees their day.
  var Calendar = {
    HOURS_START: 0,
    HOURS_END: 24, // exclusive
    HOUR_PX: 52,

    init: function (root, now, ctx, initialSessions) {
      this.root = root;
      this.now = now;
      this.ctx = ctx;
      this.sessions = initialSessions || [];
      this.view = 'week';
      this.anchor = startOfWeek(new Date(now()));
      this.gridNode = $('[data-cal="grid"]', root);
      this.rangeNode = $('[data-cal="range"]', root);
      this.didInitialScroll = false;
      var self = this;

      $$('[data-cal-view]', root).forEach(function (btn) {
        btn.addEventListener('click', function () { self.setView(btn.getAttribute('data-cal-view')); });
      });
      var prev = $('[data-cal-prev]', root);
      var next = $('[data-cal-next]', root);
      var today = $('[data-cal-today]', root);
      if (prev) prev.addEventListener('click', function () { self.shift(-1); });
      if (next) next.addEventListener('click', function () { self.shift(1); });
      if (today) today.addEventListener('click', function () { self.goToToday(); });

      this.render();
      this.scrollToNowOnce();
      this.startNowLineTimer();
    },

    setView: function (view) {
      if (view !== 'day' && view !== 'week' && view !== 'month') return;
      this.view = view;
      $$('[data-cal-view]', this.root).forEach(function (btn) {
        btn.setAttribute('aria-selected', btn.getAttribute('data-cal-view') === view ? 'true' : 'false');
      });
      // Switching view changes the visible date range (Day/Week/Month all differ).
      // Refetch so sessions outside the previously-loaded range still appear.
      this.fetchAndRender();
    },

    shift: function (dir) {
      if (this.view === 'day') {
        this.anchor.setDate(this.anchor.getDate() + dir);
      } else if (this.view === 'week') {
        this.anchor.setDate(this.anchor.getDate() + 7 * dir);
      } else {
        this.anchor.setMonth(this.anchor.getMonth() + dir);
        this.anchor.setDate(1);
      }
      this.fetchAndRender();
    },
    goToToday: function () {
      var today = new Date(this.now());
      if (this.view === 'week') this.anchor = startOfWeek(today);
      else if (this.view === 'day') this.anchor = atMidnight(today);
      else this.anchor = new Date(today.getFullYear(), today.getMonth(), 1);
      this.didInitialScroll = false;
      this.fetchAndRender();
    },

    fetchAndRender: function () {
      var range = this.computeRange();
      var url = this.ctx + '/instructor/dashboard/calendar.json'
        + '?from=' + isoDate(range.from)
        + '&to=' + isoDate(range.to);
      var self = this;
      fetch(url, { credentials: 'same-origin' })
        .then(function (r) { return r.json(); })
        .then(function (data) {
          self.sessions = (data && data.sessions) ? data.sessions : [];
          self.render();
          self.scrollToNowOnce();
        })
        .catch(function () { self.render(); });
    },

    computeRange: function () {
      var d = new Date(this.anchor);
      if (this.view === 'day') return { from: atMidnight(d), to: atMidnight(d) };
      if (this.view === 'week') {
        var s = startOfWeek(d);
        var e = new Date(s); e.setDate(s.getDate() + 6);
        return { from: s, to: e };
      }
      // month: include surrounding weeks
      var firstOfMonth = new Date(d.getFullYear(), d.getMonth(), 1);
      var lastOfMonth = new Date(d.getFullYear(), d.getMonth() + 1, 0);
      var s2 = startOfWeek(firstOfMonth);
      var e2 = new Date(startOfWeek(lastOfMonth)); e2.setDate(e2.getDate() + 6);
      return { from: s2, to: e2 };
    },

    render: function () {
      if (!this.gridNode) return;
      this.updateRangeLabel();

      // Reset class
      this.gridNode.className = 'idash-cal__grid';
      this.gridNode.innerHTML = '';

      if (this.view === 'month') {
        this.renderMonth();
      } else if (this.view === 'day') {
        this.gridNode.classList.add('idash-cal__grid--day');
        this.renderTimeGrid([atMidnight(this.anchor)]);
      } else {
        this.renderTimeGrid(this.weekDays());
      }
    },

    weekDays: function () {
      var s = startOfWeek(this.anchor);
      var arr = [];
      for (var i = 0; i < 7; i++) {
        var d = new Date(s); d.setDate(s.getDate() + i);
        arr.push(d);
      }
      return arr;
    },

    updateRangeLabel: function () {
      if (!this.rangeNode) return;
      var d = this.anchor;
      var monthName = d.toLocaleString(undefined, { month: 'long' });
      if (this.view === 'day') {
        this.rangeNode.textContent = d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
      } else if (this.view === 'week') {
        var s = startOfWeek(d);
        var e = new Date(s); e.setDate(s.getDate() + 6);
        var sM = s.toLocaleString(undefined, { month: 'short' });
        var eM = e.toLocaleString(undefined, { month: 'short' });
        var label = sM === eM
          ? (sM + ' ' + s.getDate() + '–' + e.getDate() + ', ' + e.getFullYear())
          : (sM + ' ' + s.getDate() + ' – ' + eM + ' ' + e.getDate() + ', ' + e.getFullYear());
        this.rangeNode.textContent = label;
      } else {
        this.rangeNode.textContent = monthName + ' ' + d.getFullYear();
      }
    },

    renderTimeGrid: function (days) {
      var grid = this.gridNode;
      var todayStr = isoDate(new Date(this.now()));

      // Header row
      grid.appendChild(el('div', { class: 'idash-cal__col-head idash-cal__col-head--gutter' }));
      days.forEach(function (d) {
        var isToday = isoDate(d) === todayStr;
        var head = el('div', { class: 'idash-cal__col-head' + (isToday ? ' idash-cal__col-head--today' : '') }, [
          el('span', { class: 'idash-cal__day-name', text: d.toLocaleDateString(undefined, { weekday: 'short' }) }),
          el('span', { class: 'idash-cal__day-num', text: String(d.getDate()) })
        ]);
        grid.appendChild(head);
      });

      // Hours gutter
      var hoursCol = el('div', { class: 'idash-cal__hours' });
      for (var h = this.HOURS_START; h < this.HOURS_END; h++) {
        hoursCol.appendChild(el('div', { class: 'idash-cal__hour-label', text: pad2(h) + ':00' }));
      }
      grid.appendChild(hoursCol);

      // Day columns + events
      var self = this;
      days.forEach(function (d) {
        var col = el('div', { class: 'idash-cal__col' });
        for (var h = self.HOURS_START; h < self.HOURS_END; h++) {
          col.appendChild(el('div', { class: 'idash-cal__hour-row' }));
        }
        // Place events for this day
        var dStr = isoDate(d);
        self.sessions.filter(function (s) { return s && s.date === dStr; }).forEach(function (s) {
          var evt = self.makeEventBlock(s);
          if (evt) col.appendChild(evt);
        });

        // Now line for today
        if (dStr === todayStr) {
          var nowLine = el('div', { class: 'idash-cal__now-line' });
          nowLine.setAttribute('data-now-line', '1');
          col.appendChild(nowLine);
        }
        grid.appendChild(col);
      });

      this.positionNowLine();
    },

    makeEventBlock: function (session) {
      if (!session || !session.time) return null;
      var parts = session.time.split(':');
      var hour = parseInt(parts[0], 10);
      var minute = parseInt(parts[1], 10) || 0;
      if (isNaN(hour)) return null;
      // Clamp into the visible 24h range; this is defensive for malformed times.
      if (hour < 0) { hour = 0; minute = 0; }
      if (hour >= 24) return null;
      var startSlot = (hour - this.HOURS_START) + minute / 60;
      var top = startSlot * this.HOUR_PX;
      var dur = session.durationMinutes || 60;
      // Height: actual duration in slots, clamped so a session can't overflow past 24:00.
      var totalSlots = this.HOURS_END - this.HOURS_START;
      var maxSlots = Math.max(0.25, totalSlots - startSlot);
      var slots = Math.min(maxSlots, dur / 60);
      var height = Math.max(28, slots * this.HOUR_PX - 2);
      var endLabel = (function () {
        var endMin = hour * 60 + minute + dur;
        // Wrap any minute overflow into 24h display.
        var eh = Math.floor(endMin / 60) % 24;
        var em = endMin % 60;
        return pad2(eh) + ':' + pad2(em);
      })();
      var node = el('div', {
        class: 'idash-evt',
        'data-status': session.status,
        role: 'button',
        tabindex: '0',
        style: { top: top + 'px', height: height + 'px' }
      }, [
        el('span', { class: 'idash-evt__title', text: session.title || t('common.session') }),
        el('span', { class: 'idash-evt__time', text: session.time + ' – ' + endLabel })
      ]);
      node.addEventListener('click', function () { Drawer.open(session); });
      node.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); Drawer.open(session); }
      });
      return node;
    },

    renderMonth: function () {
      var grid = this.gridNode;
      grid.className = 'idash-cal__month';
      var weekdayShort = [
        t('instructor.dashboard.weekdayMon'),
        t('instructor.dashboard.weekdayTue'),
        t('instructor.dashboard.weekdayWed'),
        t('instructor.dashboard.weekdayThu'),
        t('instructor.dashboard.weekdayFri'),
        t('instructor.dashboard.weekdaySat'),
        t('instructor.dashboard.weekdaySun')
      ];
      weekdayShort.forEach(function (w) {
        grid.appendChild(el('div', { class: 'idash-cal__month-head', text: w }));
      });

      var first = new Date(this.anchor.getFullYear(), this.anchor.getMonth(), 1);
      var start = startOfWeek(first);
      var todayStr = isoDate(new Date(this.now()));
      var monthIdx = this.anchor.getMonth();
      var self = this;

      for (var i = 0; i < 42; i++) {
        var d = new Date(start); d.setDate(start.getDate() + i);
        var inMonth = d.getMonth() === monthIdx;
        var cellCls = 'idash-cal__month-cell' + (inMonth ? '' : ' idash-cal__month-cell--other');
        if (isoDate(d) === todayStr) cellCls += ' idash-cal__month-cell--today';
        var cell = el('div', { class: cellCls });
        cell.appendChild(el('span', { class: 'idash-cal__month-day', text: String(d.getDate()) }));

        var dStr = isoDate(d);
        var dayEvents = self.sessions.filter(function (s) { return s && s.date === dStr; }).slice(0, 3);
        dayEvents.forEach(function (s) {
          var evt = el('div', {
            class: 'idash-evt idash-evt--month',
            'data-status': s.status,
            role: 'button', tabindex: '0'
          }, [el('span', { class: 'idash-evt__title', text: (s.time ? s.time + '  ' : '') + (s.title || t('common.session')) })]);
          evt.addEventListener('click', function () { Drawer.open(s); });
          cell.appendChild(evt);
        });
        var totalCount = self.sessions.filter(function (s) { return s && s.date === dStr; }).length;
        if (totalCount > 3) {
          cell.appendChild(el('div', { class: 'idash-evt--more', text: t('instructor.dashboard.moreEvents', { count: totalCount - 3 }) }));
        }
        grid.appendChild(cell);
      }
    },

    startNowLineTimer: function () {
      var self = this;
      this.positionNowLine();
      this.nowLineTimer = setInterval(function () { self.positionNowLine(); }, 60000);
    },
    positionNowLine: function () {
      var lines = $$('[data-now-line]', this.gridNode);
      if (!lines.length) return;
      var d = new Date(this.now());
      var hour = d.getHours();
      var minute = d.getMinutes();
      var top = ((hour - this.HOURS_START) + minute / 60) * this.HOUR_PX;
      lines.forEach(function (n) {
        n.style.display = '';
        n.style.top = top + 'px';
      });
    },

    // After the first time-grid render, scroll the calendar so the current hour
    // is roughly centered. With a 24-hour grid this is essential — without it
    // the user would land at midnight every page load.
    scrollToNowOnce: function () {
      if (this.didInitialScroll) return;
      if (!this.gridNode || this.view === 'month') return;
      var d = new Date(this.now());
      var hour = d.getHours();
      // Show ~1 hour of context above current time.
      var target = Math.max(0, (hour - 1)) * this.HOUR_PX;
      this.gridNode.scrollTop = target;
      this.didInitialScroll = true;
    }
  };

  // -------------- Heartbeat --------------
  var Heartbeat = {
    init: function (ctx, intervalMs) {
      this.ctx = ctx;
      this.intervalMs = intervalMs || 30000;
      this.timer = setInterval(this.tick.bind(this), this.intervalMs);
    },
    tick: function () {
      var url = this.ctx + '/instructor/dashboard/heartbeat.json';
      fetch(url, { credentials: 'same-origin' })
        .then(function (r) { return r.json(); })
        .then(function (data) {
          if (!data) return;
          if (data.kpi) KPI.update(data.kpi);
          if (data.nextSession !== undefined) {
            var prev = Countdown.session;
            var next = data.nextSession;
            var changed = !prev || !next
              || prev.id !== next.id
              || prev.status !== next.status;
            if (changed) {
              Countdown.setSession(next);
            } else {
              // refresh times if backend re-derived them
              prev.startEpochMs = next.startEpochMs;
              prev.endEpochMs = next.endEpochMs;
            }
          }
        })
        .catch(function () {/* silent */});
    }
  };

  // -------------- Date helpers --------------
  function startOfWeek(d) {
    var copy = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    var day = copy.getDay(); // 0=Sun
    var diff = (day === 0 ? -6 : 1 - day);
    copy.setDate(copy.getDate() + diff);
    return copy;
  }
  function atMidnight(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
  function isoDate(d) {
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate());
  }

  // -------------- Bootstrap & init --------------
  document.addEventListener('DOMContentLoaded', function () {
    var data = readBootstrap();
    if (!data) return;

    var ctx = data.contextPath || '';
    var serverNow = makeServerClock(data.serverNowEpochMs || Date.now());

    Clock.init(serverNow);
    KPI.init();

    var countdownRoot = $('.idash-countdown');
    if (countdownRoot) {
      Countdown.init(countdownRoot, serverNow, data.nextSession || null, ctx);
    }

    Drawer.init(ctx);

    var calRoot = $('.idash-cal');
    if (calRoot) {
      Calendar.init(calRoot, serverNow, ctx, data.weekSessions || []);
    }

    Heartbeat.init(ctx, 30000);

    document.addEventListener('etasmi:localechange', function () {
      if (countdownRoot) {
        if (Countdown.session) {
          Countdown.renderStatic();
          Countdown.tick();
        } else {
          Countdown.renderEmpty();
        }
      }
      if (calRoot) {
        Calendar.render();
      }
    });
  });
})();
