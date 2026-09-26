/**
 * e-Tasmi i18n engine — production localization runtime.
 * - Nested JSON keys: data-i18n="student.nav.dashboard"
 * - Multi-attribute: data-i18n-attr="placeholder|title|aria-label"
 * - Variables: data-i18n-vars='{"name":"Ahmad"}' or data-i18n-name="Ahmad"
 * - Interpolation: {{name}} and {name} in locale strings
 * - SEO locale URLs via buildLocalizedUrl on language switch
 */
(function (global) {
  'use strict';

  var STORAGE_KEY = 'etasmi.locale';
  var DEFAULT_LOCALE = 'en';
  var SUPPORTED = ['en', 'ar', 'ms'];
  /** Bump when locale JSON changes so browsers refetch /assets/locales/*.json */
  var LOCALE_BUILD_ID = '20260710-modal-labels';

  var LOCALE_META = {
    en: { dir: 'ltr', nativeLabel: 'English' },
    ar: { dir: 'rtl', nativeLabel: 'العربية' },
    ms: { dir: 'ltr', nativeLabel: 'Bahasa Melayu' }
  };

  var bundles = {};
  var currentLocale = DEFAULT_LOCALE;
  var contextPath = '';
  var assetVersion = '';
  var ready = false;

  function normalizeBrowserLocale(raw) {
    if (!raw) return null;
    var base = String(raw).toLowerCase().split('-')[0];
    return SUPPORTED.indexOf(base) !== -1 ? base : null;
  }

  function readStoredLocale() {
    try {
      var stored = localStorage.getItem(STORAGE_KEY);
      if (stored && SUPPORTED.indexOf(stored) !== -1) return stored;
    } catch (err) { /* private mode */ }
    return null;
  }

  function readServerLocale() {
    var html = document.documentElement;
    var fromDom = html.getAttribute('data-locale');
    if (fromDom && SUPPORTED.indexOf(fromDom) !== -1) return fromDom;
    var script = document.querySelector('script[data-etasmi-i18n-auto]');
    if (!script) return null;
    var fromScript = script.getAttribute('data-etasmi-locale');
    return fromScript && SUPPORTED.indexOf(fromScript) !== -1 ? fromScript : null;
  }

  function readCookieLocale() {
    var match = document.cookie.match(/(?:^|;\s*)etasmi\.locale=([^;]+)/);
    if (!match) return null;
    var value = decodeURIComponent(match[1]);
    return SUPPORTED.indexOf(value) !== -1 ? value : null;
  }

  function resolveInitialLocale() {
    return readServerLocale()
      || readStoredLocale()
      || readCookieLocale()
      || normalizeBrowserLocale(navigator.language || navigator.userLanguage)
      || DEFAULT_LOCALE;
  }

  function pathWithinContext(pathname) {
    var path = pathname || location.pathname;
    if (contextPath && path.indexOf(contextPath) === 0) {
      path = path.substring(contextPath.length);
    }
    return path || '/';
  }

  function stripLocalePrefix(path) {
    return path.replace(/^\/(en|ar|ms)(?=\/|$)/, '') || '/home';
  }

  function buildLocalizedUrl(nextLocale, pathname, search) {
    var safe = SUPPORTED.indexOf(nextLocale) !== -1 ? nextLocale : DEFAULT_LOCALE;
    var stripped = stripLocalePrefix(pathWithinContext(pathname));
    var url = (contextPath || '') + '/' + safe + (stripped.startsWith('/') ? stripped : '/' + stripped);
    if (search) url += search;
    else if (typeof location !== 'undefined' && location.search) url += location.search;
    return url;
  }

  function getNested(obj, path) {
    if (!obj || !path) return null;
    return String(path).split('.').reduce(function (acc, key) {
      if (acc == null || key === '') return acc;
      return acc[key] != null ? acc[key] : null;
    }, obj);
  }

  function interpolate(template, vars) {
    if (!vars || template == null) return String(template);
    var str = String(template);
    Object.keys(vars).forEach(function (key) {
      var value = String(vars[key]);
      str = str.replace(new RegExp('\\{\\{\\s*' + key + '\\s*\\}\\}', 'g'), value);
      str = str.replace(new RegExp('\\{\\s*' + key + '\\s*\\}', 'g'), value);
    });
    return str;
  }

  function resolveDocumentDir(locale, dirOverride) {
    if (document.body && document.body.getAttribute('data-layout-dir') === 'ltr') {
      return 'ltr';
    }
    var meta = LOCALE_META[locale] || LOCALE_META[DEFAULT_LOCALE];
    return dirOverride || meta.dir;
  }

  function applyDocumentLocale(locale, dirOverride) {
    var safe = SUPPORTED.indexOf(locale) !== -1 ? locale : DEFAULT_LOCALE;
    var dir = resolveDocumentDir(safe, dirOverride);
    var html = document.documentElement;

    html.setAttribute('lang', safe);
    html.setAttribute('dir', dir);
    html.setAttribute('data-locale', safe);
    html.setAttribute('data-dir', dir);

    if (document.body) {
      document.body.classList.toggle('etasmi-rtl', dir === 'rtl');
    }

    currentLocale = safe;

    try {
      localStorage.setItem(STORAGE_KEY, safe);
      document.cookie = STORAGE_KEY + '=' + encodeURIComponent(safe) + ';path=/;max-age=31536000;samesite=lax';
    } catch (err) { /* ignore */ }

    return safe;
  }

  function localeAssetUrl(locale) {
    var url = (contextPath || '') + '/assets/locales/' + locale + '.json';
    var version = LOCALE_BUILD_ID || assetVersion;
    return version ? url + '?v=' + encodeURIComponent(version) : url;
  }

  /** True when text looks like an unresolved dotted i18n key (e.g. instructor.payments.foo). */
  function looksLikeI18nKey(text) {
    if (!text) return false;
    return /^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$/i.test(String(text).trim());
  }

  function shouldKeepFallback(el, key, translated) {
    if (translated !== key) return false;
    var existing = (el.textContent || '').trim();
    if (!existing || existing === key) return false;
    return !looksLikeI18nKey(existing);
  }

  function loadBundle(locale) {
    if (bundles[locale]) return Promise.resolve(bundles[locale]);
    return fetch(localeAssetUrl(locale), { credentials: 'same-origin' })
      .then(function (response) {
        if (!response.ok) throw new Error('Failed to load locale: ' + locale);
        return response.json();
      })
      .then(function (json) {
        bundles[locale] = json;
        return json;
      });
  }

  function t(key, vars) {
    if (!key) return '';
    var value = getNested(bundles[currentLocale], key);
    if (value == null && currentLocale !== DEFAULT_LOCALE) {
      value = getNested(bundles[DEFAULT_LOCALE], key);
    }
    if (value == null) return key;
    return interpolate(String(value), vars);
  }

  function parseVarsAttribute(raw) {
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch (err) {
      return null;
    }
  }

  function collectVars(el) {
    var vars = {};

    if (el.hasAttribute('data-i18n-vars')) {
      var parsed = parseVarsAttribute(el.getAttribute('data-i18n-vars'));
      if (parsed) Object.keys(parsed).forEach(function (k) { vars[k] = parsed[k]; });
    }

    Array.prototype.slice.call(el.attributes).forEach(function (attr) {
      if (attr.name.indexOf('data-i18n-') === 0 && attr.name !== 'data-i18n' && attr.name !== 'data-i18n-attr'
          && attr.name !== 'data-i18n-vars' && attr.name !== 'data-i18n-html') {
        var varName = attr.name.substring('data-i18n-'.length);
        if (varName.indexOf('-') === -1) {
          vars[varName] = attr.value;
        }
      }
    });

    if (el.hasAttribute('data-i18n-count')) vars.count = el.getAttribute('data-i18n-count');
    if (el.hasAttribute('data-i18n-value')) vars.value = el.getAttribute('data-i18n-value');
    if (el.hasAttribute('data-i18n-name')) vars.name = el.getAttribute('data-i18n-name');
    if (el.hasAttribute('data-i18n-number')) vars.number = el.getAttribute('data-i18n-number');
    if (el.hasAttribute('data-i18n-juz')) vars.juz = el.getAttribute('data-i18n-juz');
    if (el.hasAttribute('data-i18n-surah')) vars.surah = el.getAttribute('data-i18n-surah');

    return Object.keys(vars).length ? vars : null;
  }

  function applyAttributes(el, key, vars) {
    var attrSpec = el.getAttribute('data-i18n-attr');
    if (!attrSpec) return false;

    attrSpec.split('|').map(function (s) { return s.trim(); }).filter(Boolean).forEach(function (attrName) {
      el.setAttribute(attrName, t(key, vars));
    });
    return true;
  }

  function applyTranslations(root) {
    root = root || document;

    root.querySelectorAll('[data-i18n]').forEach(function (el) {
      var key = el.getAttribute('data-i18n');
      if (!key) return;
      var vars = collectVars(el);

      if (applyAttributes(el, key, vars)) return;

      var attrOnly = el.getAttribute('data-i18n-attr');
      if (attrOnly) {
        applyAttributes(el, key, vars);
        return;
      }

      var text = t(key, vars);
      if (el.tagName === 'TITLE') {
        el.textContent = text;
        document.title = text;
      } else if (el.hasAttribute('data-i18n-html')) {
        el.innerHTML = text;
      } else if (!shouldKeepFallback(el, key, text)) {
        el.textContent = text;
      }
    });

    root.querySelectorAll('[data-i18n-html]').forEach(function (el) {
      if (el.hasAttribute('data-i18n')) return;
      el.innerHTML = t(el.getAttribute('data-i18n-html'), collectVars(el));
    });

    root.querySelectorAll('[data-i18n-placeholder]').forEach(function (el) {
      el.setAttribute('placeholder', t(el.getAttribute('data-i18n-placeholder'), collectVars(el)));
    });

    root.querySelectorAll('[data-i18n-title]').forEach(function (el) {
      el.setAttribute('title', t(el.getAttribute('data-i18n-title'), collectVars(el)));
    });

    root.querySelectorAll('[data-i18n-aria]').forEach(function (el) {
      el.setAttribute('aria-label', t(el.getAttribute('data-i18n-aria'), collectVars(el)));
    });

    var switcher = root.querySelector('[data-language-switcher]');
    if (switcher) switcher.setAttribute('aria-label', t('common.switchLanguage'));

    var select = root.querySelector('[data-language-switcher] select');
    if (select) select.value = currentLocale;

    updateThemeToggleLabels(root);
  }

  function updateThemeToggleLabels(root) {
    var toggles = root.querySelectorAll('[data-etasmi-theme-toggle]');
    if (!toggles.length) return;
    var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    var label = t(isDark ? 'theme.switchToLight' : 'theme.switchToDark');
    toggles.forEach(function (toggle) {
      toggle.setAttribute('aria-label', label);
      toggle.setAttribute('title', label);
    });
  }

  function bindLanguageSwitcher() {
    var select = document.querySelector('[data-language-switcher] select');
    if (!select || select.__etasmiI18nBound) return;
    select.__etasmiI18nBound = true;
    select.value = currentLocale;
    select.addEventListener('change', function () {
      var next = select.value;
      if (next === currentLocale) return;
      location.assign(buildLocalizedUrl(next));
    });
  }

  function ensureFallbackBundle() {
    if (currentLocale === DEFAULT_LOCALE || bundles[DEFAULT_LOCALE]) {
      return Promise.resolve();
    }
    return loadBundle(DEFAULT_LOCALE).catch(function () { return null; });
  }

  function dispatchLocaleChange() {
    document.dispatchEvent(new CustomEvent('etasmi:localechange', {
      detail: { locale: currentLocale, dir: LOCALE_META[currentLocale].dir, t: t }
    }));
  }

  function switchLocale(locale) {
    if (SUPPORTED.indexOf(locale) === -1) locale = DEFAULT_LOCALE;
    applyDocumentLocale(locale);
    return loadBundle(locale)
      .catch(function () {
        applyDocumentLocale(DEFAULT_LOCALE);
        return loadBundle(DEFAULT_LOCALE);
      })
      .then(ensureFallbackBundle)
      .then(function () {
        applyTranslations();
        dispatchLocaleChange();
      });
  }

  function init(options) {
    options = options || {};
    contextPath = options.contextPath || '';

    var script = document.querySelector('script[data-etasmi-i18n-auto]');
    if (!contextPath && script) {
      contextPath = script.getAttribute('data-etasmi-context') || '';
    }
    if (script) {
      var versionMatch = (script.getAttribute('src') || '').match(/[?&]v=([^&]+)/);
      if (versionMatch) assetVersion = decodeURIComponent(versionMatch[1]);
    }

    var serverDir = document.documentElement.getAttribute('data-dir')
      || document.documentElement.getAttribute('dir');
    var locale = options.locale || resolveInitialLocale();
    applyDocumentLocale(locale, serverDir);

    return loadBundle(locale)
      .catch(function () {
        applyDocumentLocale(DEFAULT_LOCALE);
        return loadBundle(DEFAULT_LOCALE);
      })
      .then(ensureFallbackBundle)
      .then(function () {
        ready = true;
        applyTranslations();
        bindLanguageSwitcher();
        document.addEventListener('etasmi:themechange', function () {
          updateThemeToggleLabels(document);
        });
        dispatchLocaleChange();
      });
  }

  global.EtasmiI18n = {
    init: init,
    switchLocale: switchLocale,
    apply: applyTranslations,
    t: t,
    isReady: function () { return ready; },
    getLocale: function () { return currentLocale; },
    getDirection: function () { return LOCALE_META[currentLocale].dir; },
    buildLocalizedUrl: function (locale) { return buildLocalizedUrl(locale); },
    SUPPORTED: SUPPORTED,
    LOCALE_META: LOCALE_META,
    DEFAULT_LOCALE: DEFAULT_LOCALE
  };

  function autoInit() {
    if (!document.querySelector('script[data-etasmi-i18n-auto]')) return;
    var run = function () {
      var script = document.querySelector('script[data-etasmi-i18n-auto]');
      EtasmiI18n.init({
        contextPath: script ? script.getAttribute('data-etasmi-context') || '' : ''
      });
    };
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', run);
    } else {
      run();
    }
  }

  autoInit();
})(window);
