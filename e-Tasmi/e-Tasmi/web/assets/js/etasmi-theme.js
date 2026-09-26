/*
 * e-Tasmi — Theme toggle script.
 *
 * Responsibilities:
 *   • Read the preferred theme from localStorage.
 *   • Fall back to the operating-system / browser preference
 *     when the user has not chosen one yet.
 *   • Apply the active theme to <html data-theme="…"> and sync
 *     Bootstrap's data-bs-theme so LearnHub/Bootstrap components
 *     follow the same mode.
 *   • Wire every element marked with [data-etasmi-theme-toggle]
 *     to toggle between light and dark and persist the choice.
 *   • Expose window.etasmiTheme.{get,set,toggle} for any JSP
 *     that needs to drive the theme programmatically.
 *   • Dispatch etasmi:themechange so dynamic widgets can react.
 *
 * The FOUC-prevention snippet in etasmi_theme_init.jspf runs
 * BEFORE CSS, so first paint is already in the correct mode.
 * This file handles interactions and keeps Bootstrap in sync.
 */

(function () {
  'use strict';

  var STORAGE_KEY = 'etasmi-theme';
  var THEME_LIGHT = 'light';
  var THEME_DARK = 'dark';

  function safeStorageGet() {
    try {
      return window.localStorage.getItem(STORAGE_KEY);
    } catch (err) {
      return null;
    }
  }

  function safeStorageSet(value) {
    try {
      window.localStorage.setItem(STORAGE_KEY, value);
    } catch (err) {
      /* ignore — private mode / disabled storage */
    }
  }

  function prefersDark() {
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function resolveInitialTheme() {
    var stored = safeStorageGet();
    if (stored === THEME_DARK || stored === THEME_LIGHT) {
      return stored;
    }
    return prefersDark() ? THEME_DARK : THEME_LIGHT;
  }

  function syncColorSchemeMeta(theme) {
    var meta = document.querySelector('meta[name="color-scheme"]');
    if (meta) {
      meta.setAttribute('content', 'light dark');
    }
  }

  function applyTheme(theme) {
    var root = document.documentElement;
    var normalised = theme === THEME_DARK ? THEME_DARK : THEME_LIGHT;

    root.setAttribute('data-theme', normalised);
    root.setAttribute('data-bs-theme', normalised);
    syncColorSchemeMeta(normalised);
    updateToggleButtons(normalised);
    dispatchThemeEvent(normalised);
  }

  function dispatchThemeEvent(theme) {
    try {
      document.dispatchEvent(new CustomEvent('etasmi:themechange', {
        detail: { theme: theme }
      }));
    } catch (err) {
      /* older browsers — fine to ignore */
    }
  }

  function updateToggleButtons(theme) {
    var toggles = document.querySelectorAll('[data-etasmi-theme-toggle]');
    for (var i = 0; i < toggles.length; i++) {
      var btn = toggles[i];
      var isDark = theme === THEME_DARK;
      btn.setAttribute('aria-pressed', String(isDark));
      var nextLabel = isDark ? 'Switch to light mode' : 'Switch to dark mode';
      btn.setAttribute('aria-label', nextLabel);
      btn.setAttribute('title', nextLabel);
    }
  }

  function getTheme() {
    return document.documentElement.getAttribute('data-theme') === THEME_DARK
      ? THEME_DARK
      : THEME_LIGHT;
  }

  function setTheme(theme) {
    var normalised = theme === THEME_DARK ? THEME_DARK : THEME_LIGHT;
    safeStorageSet(normalised);
    applyTheme(normalised);
  }

  function toggleTheme() {
    setTheme(getTheme() === THEME_DARK ? THEME_LIGHT : THEME_DARK);
  }

  function wireToggleButtons() {
    document.addEventListener('click', function (event) {
      var target = event.target;
      if (!target || !target.closest) return;
      var btn = target.closest('[data-etasmi-theme-toggle]');
      if (!btn) return;
      event.preventDefault();
      toggleTheme();
    }, false);

    updateToggleButtons(getTheme());
  }

  function listenToSystemChanges() {
    if (!window.matchMedia) return;
    var mql = window.matchMedia('(prefers-color-scheme: dark)');
    var handler = function (ev) {
      // Only auto-follow the OS when the user has never picked a theme.
      if (safeStorageGet() === null) {
        applyTheme(ev.matches ? THEME_DARK : THEME_LIGHT);
      }
    };
    if (typeof mql.addEventListener === 'function') {
      mql.addEventListener('change', handler);
    } else if (typeof mql.addListener === 'function') {
      mql.addListener(handler);
    }
  }

  function boot() {
    applyTheme(resolveInitialTheme());
    wireToggleButtons();
    listenToSystemChanges();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

  window.etasmiTheme = {
    get: getTheme,
    set: setTheme,
    toggle: toggleTheme,
    STORAGE_KEY: STORAGE_KEY
  };
})();
