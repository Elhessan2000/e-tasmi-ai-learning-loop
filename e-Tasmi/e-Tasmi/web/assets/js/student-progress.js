(function () {
  'use strict';

  var root = document.querySelector('[data-sp]');
  if (!root) return;

  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function currentLocale() {
    var lang = document.documentElement.getAttribute('lang') || 'en';
    return lang === 'ar' ? 'ar-u-nu-latn' : lang;
  }

  function formatDates() {
    if (!window.Intl || !Intl.DateTimeFormat) return;
    var locale = currentLocale();
    var thisYear = new Date().getFullYear();
    root.querySelectorAll('time[data-sp-date][datetime]').forEach(function (el) {
      var raw = el.getAttribute('datetime');
      if (!raw) return;
      var date = new Date(raw);
      if (isNaN(date.getTime())) return;
      var opts = { month: 'short', day: 'numeric' };
      if (el.getAttribute('data-sp-date') === 'full' || date.getFullYear() !== thisYear) {
        opts.year = 'numeric';
      }
      try {
        el.textContent = new Intl.DateTimeFormat(locale, opts).format(date);
        el.setAttribute('title', new Intl.DateTimeFormat(locale, { dateStyle: 'long' }).format(date));
      } catch (err) { /* keep server label */ }
    });
  }

  function animateCount(el) {
    var target = parseInt(el.getAttribute('data-sp-count'), 10);
    if (isNaN(target) || target <= 0) return;
    var start = null;
    var duration = 900;
    el.textContent = '0';
    function frame(ts) {
      if (start === null) start = ts;
      var t = Math.min(1, (ts - start) / duration);
      var eased = 1 - Math.pow(1 - t, 3);
      el.textContent = String(Math.round(target * eased));
      if (t < 1) window.requestAnimationFrame(frame);
    }
    window.requestAnimationFrame(frame);
  }

  function initCounters() {
    if (reduceMotion || !window.requestAnimationFrame) return;
    var counters = Array.prototype.slice.call(root.querySelectorAll('[data-sp-count]'));
    if (!('IntersectionObserver' in window)) {
      counters.forEach(animateCount);
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        io.unobserve(entry.target);
        animateCount(entry.target);
      });
    }, { threshold: 0.35 });
    counters.forEach(function (el) { io.observe(el); });
  }

  function initShowMore() {
    var button = root.querySelector('[data-sp-more-btn]');
    if (!button) return;
    var label = root.querySelector('[data-sp-shown-label]');
    var step = parseInt(button.getAttribute('data-step'), 10) || 8;
    button.addEventListener('click', function () {
      var hidden = Array.prototype.slice.call(root.querySelectorAll('tr[data-sp-more][hidden]'));
      var batch = hidden.slice(0, step);
      batch.forEach(function (row, index) {
        row.hidden = false;
        row.style.setProperty('--r', String(index));
        row.classList.add('sp-row--revealed');
      });
      if (label) {
        var shown = root.querySelectorAll('.sp-table tbody tr:not([hidden])').length;
        label.setAttribute('data-i18n-shown', String(shown));
        if (window.EtasmiI18n && window.EtasmiI18n.isReady()) {
          window.EtasmiI18n.apply(label.parentNode);
        } else {
          label.textContent = 'Showing ' + shown + ' of ' + label.getAttribute('data-i18n-total');
        }
      }
      if (batch.length) {
        var firstLink = batch[0].querySelector('a');
        if (firstLink) firstLink.focus({ preventScroll: true });
      }
      if (hidden.length <= step) {
        button.hidden = true;
      }
    });
  }

  formatDates();
  initCounters();
  initShowMore();
  document.addEventListener('etasmi:localechange', formatDates);
})();
