/*
 * eTasmi Quran Library — unified reading experience.
 *
 * Architecture
 * ------------
 *   Api          : thin fetch wrappers around the existing student JSON endpoints (chapters,
 *                  verses, audio + verse_timings, reciters, search, translation/tafsir resources).
 *   Store        : localStorage-backed prefs, recent surahs, recent reciters, bookmarks.
 *   Router       : minimal hash router (#/, #/surah/:n, #/reciters, #/my-quran).
 *   AudioController: drives <audio>, exposes timeupdate-based "current ayah" highlighting using
 *                  the verse_timings returned by Quran Foundation.
 *   Views        : HomePanel, ReaderView, RecitersView, MyQuranView render into .qlx-content inside the shell.
 *   App          : boot-up; loads chapters once, mounts top bar / dock / toast, dispatches routes.
 *
 * Everything is server-proxied via /student/api/quran-library/* — no QF secrets on the client.
 */
(function () {
  'use strict';

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  /** Wait until locale bundles are loaded before rendering dynamic labels. */
  function whenI18nReady(fn) {
    if (window.EtasmiI18n && EtasmiI18n.isReady && EtasmiI18n.isReady()) {
      fn();
      return;
    }
    var settled = false;
    function run() {
      if (settled) return;
      if (window.EtasmiI18n && EtasmiI18n.isReady && EtasmiI18n.isReady()) {
        settled = true;
        fn();
      }
    }
    document.addEventListener('etasmi:localechange', run);
    var attempts = 0;
    var timer = setInterval(function () {
      attempts += 1;
      run();
      if (settled || attempts > 80) {
        clearInterval(timer);
        if (!settled) fn();
      }
    }, 50);
  }

  var QL = 'student.quranLibrary';

  /* ============================================================================
   * 1. Tiny DOM helpers
   * ============================================================================ */

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function elt(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function (k) {
        var v = attrs[k];
        if (v == null) return;
        if (k === 'class' || k === 'className') node.className = v;
        else if (k === 'text') node.textContent = String(v);
        else if (k === 'html') node.innerHTML = v;
        else if (k === 'dataset') Object.keys(v).forEach(function (dk) { node.dataset[dk] = v[dk]; });
        else if (k === 'style' && typeof v === 'object') Object.keys(v).forEach(function (sk) { node.style[sk] = v[sk]; });
        else if (k.indexOf('on') === 0 && typeof v === 'function') node.addEventListener(k.slice(2), v);
        else node.setAttribute(k, v);
      });
    }
    if (children) {
      (Array.isArray(children) ? children : [children]).forEach(function (c) {
        if (c == null) return;
        node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
      });
    }
    return node;
  }
  function clear(node) { while (node && node.firstChild) node.removeChild(node.firstChild); }

  /** Publish reading context for e-Tasmi Quran Assistant (student dashboard FAB). */
  function publishQuranAssistantContext(ctx) {
    window.__etasmiQuranContext = ctx || null;
    try {
      document.dispatchEvent(new CustomEvent('etasmi-quran-context', { detail: ctx || null }));
    } catch (e) { /* ignore */ }
  }

  function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function formatTime(sec) {
    if (sec == null || isNaN(sec) || sec < 0) return '0:00';
    var s = Math.floor(sec);
    var m = Math.floor(s / 60);
    var r = s % 60;
    return m + ':' + (r < 10 ? '0' : '') + r;
  }

  function arabicNumeral(n) {
    var ar = '٠١٢٣٤٥٦٧٨٩';
    return String(n).replace(/[0-9]/g, function (d) { return ar[Number(d)]; });
  }

  /** Works with raw relay or normalized student JSON. */
  function extractRecitersArray(data) {
    if (!data || typeof data !== 'object') return [];
    if (Array.isArray(data.reciters)) return data.reciters;
    if (Array.isArray(data.chapter_reciters)) return data.chapter_reciters;
    if (data.data && Array.isArray(data.data.reciters)) return data.data.reciters;
    if (data.data && Array.isArray(data.data.chapter_reciters)) return data.data.chapter_reciters;
    return [];
  }

  /** First letter from any script (Arabic, Latin, etc.); defaults to '?'. */
  function avatarInitial(name) {
    var s = String(name == null ? '' : name).trim();
    if (!s) return '?';
    var ch = s.charAt(0);
    return /[A-Za-z\u00C0-\u024F]/.test(ch) ? ch.toUpperCase() : ch;
  }

  /** Bulletproof CSS-only avatar: gradient background + initial letter inside a <div>. */
  function buildAvatarDiv(name, faceClass) {
    return elt('div', {
      class: 'qlx-avatar ' + (faceClass || ''),
      'aria-hidden': 'true',
      text: avatarInitial(name)
    });
  }

  /**
   * Quran CDN serves reciter portraits as relative paths (e.g. {@code images/reciters/6/…}).
   * Public hostnames are unreliable (most are NXDOMAIN today). Order matters:
   * {@code static.qurancdn.com} is the only base currently serving image bytes;
   * the others are kept ONLY as defensive fallbacks for the rare day a CDN flips
   * — they will silently error out otherwise. Prefer same-origin proxy first
   * ({@code /student/api/quran-library/reciter-portrait}), then these direct CDN
   * guesses in the img onerror chain.
   */
  var QURANCDN_RECITER_MEDIA_BASES = [
    'https://static.qurancdn.com/',
    'https://assets.quran.com/',
    'https://cdn.quran.com/',
    'https://images.qurancdn.com/',
    'https://api.qurancdn.com/'
  ];

  /**
   * Chapter-reciter IDs from Quran Foundation’s {@code chapter_reciters} catalogue (1–10 style
   * rows in our UI map to bios order) do not match {@code reciter_id} on Quran CDN’s audio API.
   * Map QF chapter-reciter id → QDC {@code reciter_id} for portrait lookup.
   */
  var QF_CHAPTER_RECITER_ID_TO_QDC = {
    1: 1, 2: 1, 3: 2, 4: 3, 5: 4, 6: 5, 7: 6, 8: 7, 9: 7, 10: 8,
    11: 11
  };

  /**
   * When Quran CDN returns an empty profile_picture, you can supply portraits yourself:
   *   • Host files under web/assets/img/quran-reciters/ and reference them as
   *     '/assets/img/quran-reciters/yourfile.jpg' (context path is added automatically).
   *   • Or use any absolute https:// URL you have the right to use.
   * Keys are Quran Foundation chapter-reciter id numbers from your catalogue.
   */
  var RECITER_PORTRAIT_FALLBACK_BY_QF_ID = {
    /* Example: 12: '/assets/img/quran-reciters/reciter-12.jpg' */
  };

  /**
   * Same as RECITER_PORTRAIT_FALLBACK_BY_QF_ID but keyed by normalized English name
   * (see normalizeReciterNameForMatch), e.g. 'yasser ad dussary'.
   */
  var RECITER_PORTRAIT_FALLBACK_BY_NORM_NAME = {
    /* Example: 'yasser ad dussary': '/assets/img/quran-reciters/yasser-ad-dussary.jpg' */
  };

  /** Merged from reciter-portrait-fallbacks.js (Cloudinary / curated URLs). */
  (function mergeExternalPortraitFallbacks() {
    var ext = typeof window !== 'undefined' && window.QLX_RECITER_PORTRAIT_FALLBACKS;
    if (!ext) return;
    if (ext.byQfId) {
      Object.keys(ext.byQfId).forEach(function (k) {
        RECITER_PORTRAIT_FALLBACK_BY_QF_ID[k] = ext.byQfId[k];
      });
    }
    if (ext.byNormName) {
      Object.keys(ext.byNormName).forEach(function (k) {
        RECITER_PORTRAIT_FALLBACK_BY_NORM_NAME[k] = ext.byNormName[k];
      });
    }
  })();

  /**
   * Curated portrait when API / QDC catalogue omit profile_picture.
   * API images always win — this runs only when pickReciterRawImage is empty.
   */
  function lookupManualPortraitUrl(r) {
    if (!r) return '';
    var fid = Number(r.id);
    if (Object.prototype.hasOwnProperty.call(RECITER_PORTRAIT_FALLBACK_BY_QF_ID, fid)) {
      return resolveReciterPortraitFallbackUrl(RECITER_PORTRAIT_FALLBACK_BY_QF_ID[fid]);
    }
    var labels = [];
    if (r.translated_name && r.translated_name.name) labels.push(r.translated_name.name);
    if (r.name) labels.push(r.name);
    var seen = {};
    for (var i = 0; i < labels.length; i++) {
      var nk = normalizeReciterNameForMatch(labels[i]);
      if (!nk || seen[nk]) continue;
      seen[nk] = true;
      if (Object.prototype.hasOwnProperty.call(RECITER_PORTRAIT_FALLBACK_BY_NORM_NAME, nk)) {
        return resolveReciterPortraitFallbackUrl(RECITER_PORTRAIT_FALLBACK_BY_NORM_NAME[nk]);
      }
    }
    return '';
  }

  /**
   * Normalizes reciter display names for matching QF catalogue rows to Quran CDN names
   * (hyphens, apostrophes, and dialect variants differ).
   */
  function normalizeReciterNameForMatch(s) {
    if (s == null) return '';
    return String(s)
      .toLowerCase()
      .replace(/[`'\u2018\u2019\u02bb\u0060]/g, '')
      .replace(/[^\w\s-]+/g, ' ')
      .replace(/[\s_-]+/g, ' ')
      .trim();
  }

  /**
   * When {@code reciter_id} mapping misses (QF ids vary by environment), resolve portrait by
   * exact normalized name, then by “all significant name tokens appear in CDN label” fuzzy match.
   */
  function portraitFromNameCatalog(r, picByNormName) {
    if (!r || !picByNormName || !Object.keys(picByNormName).length) return '';
    var labels = [];
    if (r.translated_name && r.translated_name.name) labels.push(r.translated_name.name);
    if (r.name) labels.push(r.name);
    var seen = {};
    for (var i = 0; i < labels.length; i++) {
      var rk = normalizeReciterNameForMatch(labels[i]);
      if (!rk || seen[rk]) continue;
      seen[rk] = true;
      if (picByNormName[rk]) return picByNormName[rk];
    }
    for (var j = 0; j < labels.length; j++) {
      var rj = normalizeReciterNameForMatch(labels[j]);
      if (!rj) continue;
      var tokens = rj.split(' ').filter(function (t) { return t.length > 2; });
      if (tokens.length < 2) continue;
      var hit = '';
      Object.keys(picByNormName).forEach(function (k) {
        if (hit) return;
        var ok = tokens.every(function (t) { return k.indexOf(t) >= 0; });
        if (ok) hit = picByNormName[k];
      });
      if (hit) return hit;
    }
    return '';
  }

  /** Cached successful Quran CDN reciters payload (profile paths). */
  var __qdcRecitersCatalogPayload = null;
  /** In-flight fetch; cleared after settle so failures can retry on the next visit. */
  var __qdcRecitersCatalogInflight = null;

  function qlxContextPath() {
    var n = document.querySelector('[data-qlx-root]');
    return (n && n.dataset && n.dataset.ctx) ? String(n.dataset.ctx) : '';
  }

  /** Resolves app-relative paths ({@code /assets/...}) with the JSP context path; leaves https URLs unchanged. */
  function resolveReciterPortraitFallbackUrl(v) {
    if (v == null || typeof v !== 'string') return '';
    var s = v.trim();
    if (!s) return '';
    if (/^https?:\/\//i.test(s)) return s;
    if (s.charAt(0) !== '/') s = '/' + s;
    var ctx = qlxContextPath().replace(/\/$/, '');
    return ctx ? (ctx + s) : s;
  }

  /**
   * Collects any raw portrait string the upstream JSON might use (QF, QDC-shaped, etc.).
   */
  function pickReciterRawImage(r) {
    if (!r || typeof r !== 'object') return '';
    var candidates = [
      r.profile_picture, r.profilePicture,
      r.image, r.image_url, r.imageUrl,
      r.avatar_url, r.avatarUrl,
      r.photo_url, r.portrait_url,
      r.cover_image, r.coverImage,
      r.avatar && (typeof r.avatar === 'string' ? r.avatar : (r.avatar.url || r.avatar.src || '')),
      r.media && (r.media.avatar_url || r.media.profile_picture)
    ];
    for (var i = 0; i < candidates.length; i++) {
      var v = candidates[i];
      if (typeof v === 'string' && v.trim()) return v.trim();
    }
    return '';
  }

  /**
   * Same-origin proxy first (stable in production); then public CDN bases as img-retry fallbacks.
   */
  function portraitUrlCandidates(raw) {
    var s = typeof raw === 'string' ? raw.trim() : '';
    if (!s) return [];
    if (/^https?:\/\//i.test(s)) return [s];
    if (s.indexOf('//') === 0) return ['https:' + s];
    var ctx = qlxContextPath().replace(/\/$/, '');
    if (s.charAt(0) === '/') {
      var local = [];
      if (ctx !== '') local.push(ctx + s);
      local.push(s);
      return local;
    }
    var path = s.replace(/^\/+/, '');
    var urls = [];
    if (ctx !== '') {
      urls.push(ctx + '/student/api/quran-library/reciter-portrait?rel=' + encodeURIComponent(path));
    }
    QURANCDN_RECITER_MEDIA_BASES.forEach(function (base) { urls.push(base + path); });
    return urls;
  }

  function qdcReciterIdForChapterReciter(r) {
    var id = Number(r && r.id);
    if (Object.prototype.hasOwnProperty.call(QF_CHAPTER_RECITER_ID_TO_QDC, id)) {
      return QF_CHAPTER_RECITER_ID_TO_QDC[id];
    }
    return id;
  }

  function fetchQdcRecitersCatalogOnce() {
    if (__qdcRecitersCatalogPayload) return Promise.resolve(__qdcRecitersCatalogPayload);
    if (__qdcRecitersCatalogInflight) return __qdcRecitersCatalogInflight;
    __qdcRecitersCatalogInflight = fetch(
      'https://api.qurancdn.com/api/qdc/audio/reciters?fields=profile_picture&per_page=100',
      { credentials: 'omit', mode: 'cors', headers: { Accept: 'application/json' } }
    )
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        __qdcRecitersCatalogInflight = null;
        if (data && Array.isArray(data.reciters)) {
          __qdcRecitersCatalogPayload = data;
        }
        return __qdcRecitersCatalogPayload;
      })
      .catch(function () {
        __qdcRecitersCatalogInflight = null;
        return null;
      });
    return __qdcRecitersCatalogInflight;
  }

  /**
   * Quran Foundation’s chapter_reciter list often has no image fields. Merge in
   * {@code profile_picture} from Quran CDN’s public audio reciters API (same global celebrities;
   * not the same numeric id space — use {@link #QF_CHAPTER_RECITER_ID_TO_QDC}).
   */
  function mergeQdcProfilePictures(reciters) {
    if (!reciters || !reciters.length) return Promise.resolve(reciters);
    return fetchQdcRecitersCatalogOnce().then(function (data) {
      if (!data || !Array.isArray(data.reciters)) return reciters;
      var picByQdcId = {};
      var picByNormName = {};
      data.reciters.forEach(function (q) {
        var pic = q.profile_picture;
        if (pic == null || !String(pic).trim()) return;
        pic = String(pic).trim();
        var rid = q.reciter_id;
        if (rid != null && !picByQdcId[rid]) picByQdcId[rid] = pic;
        var nms = [];
        if (q.translated_name && q.translated_name.name) nms.push(q.translated_name.name);
        if (q.name) nms.push(q.name);
        nms.forEach(function (nm) {
          var k = normalizeReciterNameForMatch(nm);
          if (k && !picByNormName[k]) picByNormName[k] = pic;
        });
      });
      return reciters.map(function (r) {
        if (pickReciterRawImage(r)) return r;
        var qdcId = qdcReciterIdForChapterReciter(r);
        var pic = picByQdcId[qdcId];
        if (!pic) pic = portraitFromNameCatalog(r, picByNormName);
        if (!pic) {
          var fid = Number(r && r.id);
          if (Object.prototype.hasOwnProperty.call(RECITER_PORTRAIT_FALLBACK_BY_QF_ID, fid)) {
            pic = resolveReciterPortraitFallbackUrl(RECITER_PORTRAIT_FALLBACK_BY_QF_ID[fid]);
          }
        }
        if (!pic) {
          var labs = [(r.translated_name && r.translated_name.name), r.name];
          for (var li = 0; li < labs.length; li++) {
            var nk = normalizeReciterNameForMatch(labs[li]);
            if (nk && Object.prototype.hasOwnProperty.call(RECITER_PORTRAIT_FALLBACK_BY_NORM_NAME, nk)) {
              pic = resolveReciterPortraitFallbackUrl(RECITER_PORTRAIT_FALLBACK_BY_NORM_NAME[nk]);
              break;
            }
          }
        }
        if (!pic) return r;
        return Object.assign({}, r, { profile_picture: pic });
      });
    });
  }

  /** Render a reciter face: try portrait URL(s), then gradient + initial on hard failure.
   *
   * IMPORTANT: keep both absolute (http(s)://...) AND same-origin (/path...) URLs.
   * portraitUrlCandidates() is documented to put the same-origin reciter-portrait
   * proxy first because it is the most reliable upstream — filtering it out (the
   * old `^https?://` regex did exactly that) made every reciter whose API portrait
   * is a relative path fall through to public CDN hosts only, and most of those
   * hosts no longer resolve. The proxy URL is a same-origin path beginning with
   * '/', so we accept it here.
   */
  function buildReciterFace(reciter, faceClass) {
    var cands = (reciter && reciter._portraitCandidates) || portraitUrlCandidates(reciter && reciter.imageUrl);
    cands = (cands || []).filter(function (u) {
      return typeof u === 'string' && u && (/^https?:\/\//i.test(u) || u.charAt(0) === '/');
    });
    var manual = lookupManualPortraitUrl(reciter);
    if (manual && cands.indexOf(manual) < 0) cands.push(manual);
    if (!cands.length) {
      return buildAvatarDiv(reciter && reciter.name, faceClass);
    }
    var img = document.createElement('img');
    img.className = faceClass;
    img.alt = '';
    img.setAttribute('loading', 'lazy');
    img.decoding = 'async';
    var idx = 0;
    img.addEventListener('error', function onPortraitErr() {
      idx++;
      if (idx < cands.length) img.src = cands[idx];
      else {
        img.removeEventListener('error', onPortraitErr);
        var fallback = buildAvatarDiv(reciter && reciter.name, faceClass);
        if (img.parentNode) img.parentNode.replaceChild(fallback, img);
      }
    });
    img.src = cands[0];
    return img;
  }

  /** Curated bio for the major QF chapter-reciter IDs (used on Reciter Profile). */
  var RECITER_BIOS = {
    1: { country: 'Egypt', years: '1927–1988', desc: 'Sheikh \u02BBAbd al-Basit \u02BBAbd as-Samad — among the most renowned reciters of the 20th century, beloved for his clear murattal recitation.' },
    2: { country: 'Egypt', years: '1927–1988', desc: 'Sheikh \u02BBAbd al-Basit\u2019s celebrated mujawwad recitation, distinguished by its extended ornamentation and deep maqam.' },
    3: { country: 'Saudi Arabia', years: 'b. 1962', desc: 'Sheikh \u02BBAbd ar-Rahman as-Sudais, Imam at the Grand Mosque of Mecca, known for a calm and contemplative recitation style.' },
    4: { country: 'Saudi Arabia', years: 'b. 1973', desc: 'Sheikh Abu Bakr al-Shatri, a respected Murattal reciter and judge in Saudi Arabia.' },
    5: { country: 'Saudi Arabia', desc: 'Sheikh Hani ar-Rifai — measured, contemplative Murattal recitation widely used in Tarawih broadcasts.' },
    6: { country: 'Egypt', years: '1917–1980', desc: 'Sheikh Mahmoud Khalil al-Hussary, master of tajweed; a primary reference voice for Quran teaching worldwide.' },
    7: { country: 'Kuwait', years: 'b. 1976', desc: 'Sheikh Mishary Rashid Alafasy, Imam of the Grand Mosque of Kuwait, world-renowned for his melodic and emotive recitation.' },
    8: { country: 'Egypt', years: '1920–1969', desc: 'Sheikh Mohamed Siddiq al-Minshawi — iconic voice of Egyptian Quran broadcasts. This entry is his Murattal recitation.' },
    9: { country: 'Egypt', years: '1920–1969', desc: 'Sheikh Mohamed Siddiq al-Minshawi — Mujawwad style, marked by powerful tajweed and emotional weight.' },
    10: { country: 'Saudi Arabia', desc: 'Sheikh Sa\u02BBud ash-Shuraym, Imam at the Grand Mosque of Mecca; recognised for solemn, reverent recitation.' }
  };

  function normalizeReciter(r) {
    if (!r || r.id == null) return null;
    var styleName = '';
    if (r.style && typeof r.style === 'object') styleName = r.style.name || '';
    else if (typeof r.style === 'string') styleName = r.style;
    var raw = pickReciterRawImage(r);
    if (!raw) raw = lookupManualPortraitUrl(r);
    var candidates = portraitUrlCandidates(raw);
    var name = (r.translated_name && r.translated_name.name) || r.name || t(QL + '.reciter', { id: r.id });
    return {
      id: r.id,
      name: name,
      style: styleName,
      imageUrl: candidates[0] || '',
      _portraitCandidates: candidates.length ? candidates : undefined,
      bio: RECITER_BIOS[Number(r.id)] || null
    };
  }

  /** Picks Mishary Rashid Alafasy from a normalized reciter list (or Al-Minshawi as a fallback). */
  function pickAlafasy(list) {
    if (!Array.isArray(list) || !list.length) return null;
    function byRe(re) {
      return list.find(function (r) { return re.test(String(r.name || '').toLowerCase()); }) || null;
    }
    return byRe(/(alafasy|al[\s-]?afasy|mishary)/) ||
           byRe(/(minshawi|menshawi|al[\s-]?minshawi)/) ||
           list.find(function (r) { return Number(r.id) === 7; }) ||
           list[0];
  }

  function preferredReciterId() {
    var p = Store.getPrefs();
    if (p.reciterId && Number(p.reciterId) > 0) return Number(p.reciterId);
    var picked = pickAlafasy(window.__qlxReciters || []);
    return picked && picked.id != null ? Number(picked.id) : 7;
  }

  function audioErrorHuman(err, code) {
    var c = code || err.code || '';
    if (c === 'upstream_not_found')
      return t(QL + '.audioNotFound');
    if (c === 'upstream_forbidden')
      return t(QL + '.audioForbidden');
    return err.message || t(QL + '.audioLoadFailed');
  }

  /* ============================================================================
   * 2. Toast
   * ============================================================================ */

  var Toast = (function () {
    var node = null;
    var hideTimer = null;
    function bind(n) { node = n; }
    function show(msg, kind) {
      if (!node) return;
      node.textContent = msg;
      node.classList.add('is-show');
      if (kind === 'error') node.style.background = '#b53939';
      else if (kind === 'success') node.style.background = '#0a7d6b';
      else node.style.background = '';
      clearTimeout(hideTimer);
      hideTimer = setTimeout(function () { node.classList.remove('is-show'); }, 2200);
    }
    return { bind: bind, show: show };
  }());

  /* ============================================================================
   * 2b. SurahPicker — centred modal with filter + scrollable list
   *     Replaces the old sidebar; opened from topbar / hero / reader.
   * ============================================================================ */

  var SurahPicker = (function () {
    var overlay = null, dialog = null, listEl = null, filterEl = null, current = null;

    function build(rootEl) {
      if (overlay) return;
      overlay = elt('div', {
        class: 'qlx-modal',
        hidden: true,
        role: 'dialog',
        'aria-modal': 'true',
        'aria-label': t(QL + '.selectSurah')
      });
      dialog = elt('div', { class: 'qlx-modal__dialog' });
      var head = elt('div', { class: 'qlx-modal__head' }, [
        elt('h3', { text: t(QL + '.selectSurah') }),
        elt('button', {
          class: 'qlx-modal__close',
          type: 'button',
          'aria-label': t('common.close'),
          html: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M6 18 18 6"/></svg>',
          onclick: close
        })
      ]);
      filterEl = elt('input', {
        class: 'qlx-modal__filter',
        type: 'search',
        placeholder: t(QL + '.searchSurahPlaceholder'),
        autocomplete: 'off'
      });
      listEl = elt('div', { class: 'qlx-modal__list', role: 'listbox', 'aria-label': t(QL + '.surahs') });
      dialog.appendChild(head);
      dialog.appendChild(filterEl);
      dialog.appendChild(listEl);
      overlay.appendChild(dialog);
      overlay.addEventListener('click', function (ev) {
        if (ev.target === overlay) close();
      });
      document.addEventListener('keydown', function (ev) {
        if (!overlay.hidden && ev.key === 'Escape') close();
      });
      filterEl.addEventListener('input', renderList);
      rootEl.appendChild(overlay);
    }

    function renderList() {
      clear(listEl);
      var v = (filterEl.value || '').trim().toLowerCase();
      var items = (window.__qlxChapters || []).filter(function (c) {
        if (!v) return true;
        var hay = (c.id + ' ' + (c.nameSimple || '') + ' ' + (c.translatedName || '') + ' ' + (c.nameArabic || '')).toLowerCase();
        return hay.indexOf(v) >= 0;
      });
      if (!items.length) {
        listEl.appendChild(elt('div', { class: 'qlx-modal__empty', text: t(QL + '.noMatches') }));
        return;
      }
      items.forEach(function (c) {
        listEl.appendChild(elt('button', {
          class: 'qlx-modal__row',
          type: 'button',
          role: 'option',
          onclick: function () { pick(c); }
        }, [
          elt('span', { class: 'qlx-modal__num', text: String(c.id) }),
          elt('span', { class: 'qlx-modal__txt' }, [
            elt('span', { class: 'qlx-modal__en', text: c.nameSimple || t(QL + '.chapter', { number: c.id }) }),
            elt('small', { text: c.translatedName || '' })
          ]),
          elt('span', { class: 'qlx-modal__ar', text: c.nameArabic || '', dir: 'rtl' })
        ]));
      });
    }

    function open(onPick) {
      if (!overlay) return;
      current = onPick || null;
      overlay.hidden = false;
      filterEl.value = '';
      renderList();
      setTimeout(function () { try { filterEl.focus(); } catch (e) { /* ignore */ } }, 30);
    }

    function close() {
      if (!overlay) return;
      overlay.hidden = true;
      current = null;
    }

    function pick(c) {
      var cb = current;
      close();
      if (cb) { try { cb(c); } catch (e) { /* ignore */ } }
      else { Router.go('#/surah/' + c.id); }
    }

    return { build: build, open: open, close: close };
  }());

  /* ============================================================================
   * 3. Api — thin wrappers around the existing servlets.
   *    Defensive against {ok:false} payloads, returns either the parsed body or throws.
   * ============================================================================ */

  function makeApi(root) {
    var ds = root.dataset;
    function buildQs(params) {
      var out = [];
      Object.keys(params || {}).forEach(function (k) {
        var v = params[k];
        if (v === undefined || v === null || v === '') return;
        out.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
      });
      return out.length ? '?' + out.join('&') : '';
    }
    function get(url, params) {
      return fetch(url + buildQs(params), {
        credentials: 'same-origin',
        headers: { 'Accept': 'application/json' }
      }).then(function (r) {
        return r.text().then(function (txt) {
          var data;
          try { data = txt ? JSON.parse(txt) : {}; } catch (e) { data = { ok: false, error: 'parse_error' }; }
          if (!r.ok || data.ok === false) {
            var hint = data.message || data.detail || '';
            var err = new Error(hint || data.error || ('HTTP ' + r.status));
            err.code = data.error;
            err.status = r.status;
            err.payload = data;
            throw err;
          }
          return data;
        });
      });
    }
    function postJson(url, body) {
      return fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Accept': 'application/json', 'Content-Type': 'application/json;charset=UTF-8' },
        body: JSON.stringify(body || {})
      }).then(function (r) {
        return r.text().then(function (txt) {
          var data;
          try { data = txt ? JSON.parse(txt) : {}; } catch (e) { data = { ok: false, error: 'parse_error' }; }
          if (!r.ok || data.ok === false) {
            var hint = data.message || data.detail || '';
            var err = new Error(hint || data.error || ('HTTP ' + r.status));
            err.code = data.error;
            err.status = r.status;
            err.payload = data;
            throw err;
          }
          return data;
        });
      });
    }
    return {
      chapters: function () { return get(ds.apiChapters); },
      verses: function (chapter, opts) {
        opts = opts || {};
        return get(ds.apiVerses, {
          chapter: chapter,
          merged: opts.merged === false ? '0' : '1',
          translations: opts.translations || ''
        });
      },
      verseByKey: function (verseKey, tafsirsCsv, translationsCsv) {
        return get(ds.apiVerseByKey, {
          verse_key: verseKey,
          tafsirs: tafsirsCsv,
          translations: translationsCsv || ''
        });
      },
      reciters: function () {
        return get(ds.apiReciters).then(function (data) {
          var arr = extractRecitersArray(data);
          return mergeQdcProfilePictures(arr).then(function (merged) {
            if (Array.isArray(data.reciters)) data.reciters = merged;
            else if (Array.isArray(data.chapter_reciters)) data.chapter_reciters = merged;
            else if (data.data && Array.isArray(data.data.reciters)) data.data.reciters = merged;
            else data.reciters = merged;
            return data;
          });
        });
      },
      audio: function (chapter, reciterId) {
        return get(ds.apiAudio, { chapter: chapter, reciter_id: reciterId });
      },
      translations: function (lang) { return get(ds.apiTranslations, { language: lang || ds.defaultLanguage }); },
      tafsirs: function (lang) { return get(ds.apiTafsirs, { language: lang || ds.defaultLanguage }); },
      search: function (q) { return get(ds.apiSearch, { q: q }); },
      /**
       * AI translation fallback. Accepts [{verseKey, arabic}, ...] and resolves to
       * { '<verseKey>': '<english>' } using OpenAI server-side. Server caches by verseKey.
       */
      aiTranslate: function (verses) {
        if (!ds.apiTranslateAi) return Promise.resolve({ translations: {} });
        return postJson(ds.apiTranslateAi, { verses: verses || [] });
      }
    };
  }

  /* ============================================================================
   * 4. Store — localStorage prefs, recents, bookmarks.
   *    Best-effort; silently degrades when storage is denied (private mode).
   * ============================================================================ */

  var Store = (function () {
    var KEY = 'etasmiQlState/v1';
    var FALLBACK = {
      prefs: {
        showTranslation: true,
        compact: false,
        arabicScale: 1.0,
        translationId: '131',
        tafsirId: '169',
        reciterId: 0,
        language: 'en'
      },
      recentSurahs: [],
      recentReciters: [],
      bookmarks: [],
      continue: null
    };
    var cache = null;
    function load() {
      if (cache) return cache;
      try {
        var raw = window.localStorage.getItem(KEY);
        cache = raw ? Object.assign({}, FALLBACK, JSON.parse(raw)) : Object.assign({}, FALLBACK);
        cache.prefs = Object.assign({}, FALLBACK.prefs, cache.prefs || {});
      } catch (e) { cache = Object.assign({}, FALLBACK); }
      return cache;
    }
    function persist() {
      try { window.localStorage.setItem(KEY, JSON.stringify(cache)); } catch (e) { /* ignore */ }
    }
    function getPrefs() { return Object.assign({}, load().prefs); }
    function setPrefs(patch) {
      load(); cache.prefs = Object.assign({}, cache.prefs, patch); persist();
    }
    function recordSurah(chapter) {
      load();
      var id = String(chapter.id);
      cache.recentSurahs = [chapter].concat(cache.recentSurahs.filter(function (c) { return String(c.id) !== id; })).slice(0, 8);
      persist();
    }
    function recordReciter(reciter) {
      load();
      var id = String(reciter.id);
      cache.recentReciters = [reciter].concat(cache.recentReciters.filter(function (r) { return String(r.id) !== id; })).slice(0, 6);
      persist();
    }
    function recentSurahs() { return load().recentSurahs.slice(); }
    function recentReciters() { return load().recentReciters.slice(); }

    function setContinue(cont) { load(); cache.continue = cont; persist(); }
    function getContinue() { return load().continue; }

    function toggleBookmark(verseKey, meta) {
      load();
      var idx = cache.bookmarks.findIndex(function (b) { return b.verseKey === verseKey; });
      if (idx >= 0) { cache.bookmarks.splice(idx, 1); persist(); return false; }
      cache.bookmarks.unshift(Object.assign({ verseKey: verseKey, ts: Date.now() }, meta || {}));
      cache.bookmarks = cache.bookmarks.slice(0, 50);
      persist();
      return true;
    }
    function isBookmarked(verseKey) {
      return load().bookmarks.some(function (b) { return b.verseKey === verseKey; });
    }
    function bookmarks() { return load().bookmarks.slice(); }

    return {
      getPrefs: getPrefs, setPrefs: setPrefs,
      recordSurah: recordSurah, recordReciter: recordReciter,
      recentSurahs: recentSurahs, recentReciters: recentReciters,
      setContinue: setContinue, getContinue: getContinue,
      toggleBookmark: toggleBookmark, isBookmarked: isBookmarked, bookmarks: bookmarks
    };
  }());

  /* ============================================================================
   * 5. Router — minimal hash routing.
   *    #/                 -> home
   *    #/surah/:id        -> reader (?reciter=<id>?ayah=<n>)
   *    #/reciters         -> reciters index
   *    #/reciters/:id     -> reciter detail (chapter chooser)
   *    #/my-quran         -> personal dashboard
   * ============================================================================ */

  var Router = (function () {
    var routes = [];
    function on(pattern, handler) { routes.push({ pattern: pattern, handler: handler }); }
    function go(hash) {
      if (window.location.hash !== hash) window.location.hash = hash;
      else handle();
    }
    function parseQuery(q) {
      var out = {};
      if (!q) return out;
      q.replace(/^\?/, '').split('&').forEach(function (kv) {
        if (!kv) return;
        var p = kv.split('=');
        out[decodeURIComponent(p[0])] = decodeURIComponent(p[1] || '');
      });
      return out;
    }
    function handle() {
      var raw = window.location.hash.replace(/^#/, '') || '/';
      var qIdx = raw.indexOf('?');
      var path = qIdx >= 0 ? raw.slice(0, qIdx) : raw;
      var query = qIdx >= 0 ? parseQuery(raw.slice(qIdx)) : {};
      for (var i = 0; i < routes.length; i++) {
        var m = path.match(routes[i].pattern);
        if (m) { routes[i].handler(m, query); return; }
      }
      go('#/');
    }
    function start() {
      window.addEventListener('hashchange', handle);
      handle();
    }
    return { on: on, go: go, start: start, refresh: handle };
  }());

  /* ============================================================================
   * 6. AudioController — single global <audio> + verse-timing sync.
   *    Each ReaderView observes "verse-changed" to highlight ayahs and scroll.
   * ============================================================================ */

  function makeAudioController(dock) {
    var audio = new Audio();
    audio.preload = 'metadata';
    var session = null;
    /* { chapterId, chapterName, reciter, audioUrl, durationSec, verseTimings: [{verseKey,from,to}] } */
    var listeners = [];
    var currentVerseKey = null;

    function on(fn) { listeners.push(fn); return function () { listeners = listeners.filter(function (l) { return l !== fn; }); }; }
    function emit(name, data) { listeners.forEach(function (l) { try { l(name, data); } catch (e) { /* ignore */ } }); }

    function findVerseAt(ms) {
      if (!session || !session.verseTimings || !session.verseTimings.length) return null;
      var arr = session.verseTimings;
      for (var i = 0; i < arr.length; i++) {
        if (ms >= arr[i].from && ms <= arr[i].to) return arr[i].verseKey;
      }
      return null;
    }

    audio.addEventListener('timeupdate', function () {
      var ms = audio.currentTime * 1000;
      var key = findVerseAt(ms);
      if (key !== currentVerseKey) {
        currentVerseKey = key;
        emit('verse-changed', { verseKey: key });
      }
      emit('progress', { current: audio.currentTime, duration: audio.duration });
    });
    audio.addEventListener('play', function () { emit('play'); dock.update(); });
    audio.addEventListener('pause', function () { emit('pause'); dock.update(); });
    audio.addEventListener('ended', function () { emit('ended'); dock.update(); });
    audio.addEventListener('loadedmetadata', function () { emit('loaded'); dock.update(); });
    audio.addEventListener('error', function () { emit('error'); });

    function load(s) {
      session = s;
      currentVerseKey = null;
      audio.src = s.audioUrl;
      audio.load();
      dock.show(s);
    }
    function play() { var p = audio.play(); if (p && p.catch) p.catch(function () { /* user gesture required */ }); }
    function pause() { audio.pause(); }
    function toggle() { if (audio.paused) play(); else pause(); }
    function seekToVerse(verseKey) {
      if (!session || !session.verseTimings) return false;
      var hit = session.verseTimings.find(function (v) { return v.verseKey === verseKey; });
      if (!hit) return false;
      audio.currentTime = (hit.from / 1000) + 0.01;
      play();
      return true;
    }
    function seekRatio(ratio) {
      if (!isFinite(audio.duration) || audio.duration <= 0) return;
      audio.currentTime = Math.max(0, Math.min(audio.duration, ratio * audio.duration));
    }
    function nextVerse() {
      if (!session || !session.verseTimings) return false;
      var ms = audio.currentTime * 1000;
      var arr = session.verseTimings;
      for (var i = 0; i < arr.length; i++) {
        if (arr[i].from > ms) { audio.currentTime = arr[i].from / 1000 + 0.01; play(); return true; }
      }
      return false;
    }
    function prevVerse() {
      if (!session || !session.verseTimings) return false;
      var ms = audio.currentTime * 1000;
      var arr = session.verseTimings;
      var target = arr[0];
      for (var i = 0; i < arr.length; i++) {
        if (arr[i].to < ms - 1500) target = arr[i];
      }
      if (target) { audio.currentTime = target.from / 1000 + 0.01; play(); return true; }
      return false;
    }
    function close() {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
      session = null;
      currentVerseKey = null;
      dock.hide();
      emit('closed');
    }

    return {
      audio: audio,
      on: on,
      load: load,
      play: play, pause: pause, toggle: toggle,
      seekToVerse: seekToVerse, seekRatio: seekRatio,
      nextVerse: nextVerse, prevVerse: prevVerse,
      close: close,
      isPlaying: function () { return !audio.paused && !audio.ended; },
      session: function () { return session; },
      currentVerseKey: function () { return currentVerseKey; },
      currentTime: function () { return audio.currentTime; },
      duration: function () { return audio.duration; }
    };
  }

  /* ============================================================================
   * 7. Audio Dock (bottom sticky player)
   * ============================================================================ */

  function makeDock(rootEl) {
    var node = $('[data-qlx-dock]', rootEl);
    var audioCtl = null;
    var fillEl, curEl, durEl, titleEl, subEl, playBtn, avatarEl;

    function build() {
      node.innerHTML = '';
      var info = elt('div', { class: 'qlx-dock__info' }, [
        avatarEl = elt('span', { class: 'qlx-dock__avatar', text: '♪' }),
        elt('div', null, [
          titleEl = elt('p', { class: 'qlx-dock__title', text: '' }),
          subEl = elt('p', { class: 'qlx-dock__sub', text: '' })
        ])
      ]);
      var center = elt('div', { class: 'qlx-dock__center' }, [
        elt('div', { class: 'qlx-dock__buttons' }, [
          elt('button', { class: 'qlx-dock__btn', title: t(QL + '.prevAyah'), onclick: function () { audioCtl.prevVerse(); } }, '◀◀'),
          playBtn = elt('button', { class: 'qlx-dock__btn qlx-dock__play', title: t(QL + '.playPauseRecitation'),
            onclick: function () { audioCtl.toggle(); } }, '▶'),
          elt('button', { class: 'qlx-dock__btn', title: t(QL + '.nextAyah'), onclick: function () { audioCtl.nextVerse(); } }, '▶▶')
        ]),
        elt('div', { class: 'qlx-dock__progress' }, [
          curEl = elt('span', { text: '0:00' }),
          (function () {
            var bar = elt('div', { class: 'qlx-dock__bar', onclick: function (ev) {
              var rect = bar.getBoundingClientRect();
              audioCtl.seekRatio((ev.clientX - rect.left) / rect.width);
            } }, [fillEl = elt('div', { class: 'qlx-dock__bar-fill' })]);
            return bar;
          }()),
          durEl = elt('span', { text: '0:00' })
        ])
      ]);
      var right = elt('div', { class: 'qlx-dock__right' }, [
        elt('button', { class: 'qlx-dock__close', title: t(QL + '.stopClose'), onclick: function () { audioCtl.close(); } }, '✕')
      ]);
      node.appendChild(info);
      node.appendChild(center);
      node.appendChild(right);
    }

    function bind(controller) { audioCtl = controller; build(); }
    function show(s) {
      node.hidden = false;
      document.body.classList.add('qlx-dock-open');
      titleEl.textContent = s.chapterName ? s.chapterName : t(QL + '.chapter', { number: s.chapterId });
      subEl.textContent = s.reciter ? t(QL + '.reciterLabel', { name: s.reciter.name }) : t(QL + '.recitation');
      avatarEl.textContent = s.reciter && s.reciter.name ? s.reciter.name.charAt(0).toUpperCase() : '♪';
    }
    function hide() {
      node.hidden = true;
      document.body.classList.remove('qlx-dock-open');
    }
    function update() {
      if (!audioCtl) return;
      playBtn.textContent = audioCtl.isPlaying() ? '⏸' : '▶';
      var cur = audioCtl.currentTime() || 0;
      var dur = audioCtl.duration() || 0;
      if (curEl) curEl.textContent = formatTime(cur);
      if (durEl) durEl.textContent = formatTime(dur);
      if (fillEl) fillEl.style.width = (dur ? (cur / dur) * 100 : 0) + '%';
    }
    return { bind: bind, show: show, hide: hide, update: update };
  }

  /* ============================================================================
   * 8. Top bar — branding, navigation, search.
   * ============================================================================ */

  function buildTopbar(rootEl, ctx) {
    var node = $('[data-qlx-topbar]', rootEl);
    node.innerHTML = '';

    function topbarCopy() {
      return {
        library: {
          title: t(QL + '.title'),
          subtitle: t(QL + '.topbarSubtitle')
        },
        reciters: {
          title: t(QL + '.recitersTitle'),
          subtitle: t(QL + '.recitersSubtitle')
        },
        myQuran: {
          title: t(QL + '.myQuranTitle'),
          subtitle: t(QL + '.myQuranSubtitle')
        }
      };
    }

    function navBtn(label, hash) {
      return elt('button', {
        type: 'button',
        text: label,
        dataset: { hash: hash },
        onclick: function () { Router.go(hash); }
      });
    }
    var nav = elt('div', { class: 'qlx-topbar__nav', role: 'tablist' }, [
      navBtn(t(QL + '.navLibrary'), '#/'),
      navBtn(t(QL + '.navReciters'), '#/reciters'),
      navBtn(t(QL + '.navMyQuran'), '#/my-quran')
    ]);

    var copy = topbarCopy();
    var titleText = document.createTextNode(copy.library.title);
    var titleSmall = elt('small', { text: copy.library.subtitle });
    var titleDiv = elt('div', { class: 'qlx-topbar__title' }, [titleText, titleSmall]);

    var brand = elt('a', { class: 'qlx-topbar__brand', href: ctx + '/student/dashboard' }, [
      elt('span', { class: 'qlx-topbar__brand-mark', html: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 5a2 2 0 0 1 2-2h6v18H6a2 2 0 0 1-2-2V5Z"/><path d="M20 5a2 2 0 0 0-2-2h-6v18h6a2 2 0 0 0 2-2V5Z"/></svg>' }),
      titleDiv
    ]);

    var pickBtn = elt('button', {
      class: 'qlx-topbar__pick',
      type: 'button',
      title: t(QL + '.chooseSurah'),
      html: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h10"/></svg><span>' + escapeHtml(t(QL + '.selectSurah')) + '</span>',
      onclick: function () { SurahPicker.open(); }
    });

    node.appendChild(brand);
    node.appendChild(nav);
    node.appendChild(elt('div', { class: 'qlx-topbar__spacer' }));
    node.appendChild(pickBtn);

    function setContext(mode) {
      var allCopy = topbarCopy();
      var copy = allCopy[mode] || allCopy.library;
      titleText.textContent = copy.title;
      titleSmall.textContent = copy.subtitle;
    }

    function highlight(hash) {
      $$('.qlx-topbar__nav button', node).forEach(function (b) {
        var on = (b.dataset.hash === hash) || (hash.indexOf('/surah') === 0 && b.dataset.hash === '#/');
        b.classList.toggle('is-active', on);
      });
    }

    function relabel(activeHash, activeMode) {
      var labels = [t(QL + '.navLibrary'), t(QL + '.navReciters'), t(QL + '.navMyQuran')];
      $$('.qlx-topbar__nav button', node).forEach(function (b, i) {
        if (labels[i]) b.textContent = labels[i];
      });
      pickBtn.title = t(QL + '.chooseSurah');
      var pickSpan = pickBtn.querySelector('span');
      if (pickSpan) pickSpan.textContent = t(QL + '.selectSurah');
      if (activeMode) setContext(activeMode);
      if (activeHash) highlight(activeHash);
    }

    return { highlight: highlight, setContext: setContext, relabel: relabel };
  }

  /* ============================================================================
   * 9. Library shell + home panel (full-width, no sidebar)
   * ============================================================================ */

  function mountLibraryShell(stage) {
    clear(stage);
    var contentCol = elt('div', { class: 'qlx-content qlx-content--full' });
    stage.appendChild(contentCol);
    return {
      content: contentCol,
      setActiveSidbar: function () { /* sidebar removed; kept for router compatibility */ }
    };
  }

  function HomePanel(contentEl) {
    clear(contentEl);

    var cont = Store.getContinue();
    if (cont) {
      contentEl.appendChild(elt('section', { class: 'qlx-home-continue', 'aria-label': t(QL + '.continueReading') }, [
        elt('div', { class: 'qlx-home-continue__copy' }, [
          elt('p', { class: 'qlx-home-continue__label', text: t(QL + '.continueReading') }),
          elt('h3', { class: 'qlx-home-continue__name', text: cont.chapterName || t(QL + '.chapter', { number: cont.chapterId }) })
        ]),
        elt('a', {
          class: 'qlx-home-continue__btn',
          href: '#/surah/' + cont.chapterId,
          text: t(QL + '.resume')
        })
      ]));
    }

    var recents = Store.recentSurahs();
    if (recents.length) {
      contentEl.appendChild(elt('h2', { class: 'qlx-home-section-title', text: t(QL + '.recentlyOpened') }));
      var rgrid = elt('div', { class: 'qlx-home-recents' });
      recents.forEach(function (c) {
        rgrid.appendChild(elt('a', {
          class: 'qlx-recent-card',
          href: '#/surah/' + c.id
        }, [
          elt('span', { class: 'qlx-recent-card__num', text: String(c.id) }),
          elt('div', { class: 'qlx-recent-card__txt' }, [
            elt('p', { class: 'qlx-recent-card__name', text: c.nameSimple }),
            elt('p', { class: 'qlx-recent-card__meta', text: (c.translatedName || '') + ' · ' + t(QL + '.ayahsCount', { count: c.versesCount || 0 }) })
          ]),
          elt('span', { class: 'qlx-recent-card__ar', text: c.nameArabic || '', dir: 'rtl' })
        ]));
      });
      contentEl.appendChild(rgrid);
    }

    contentEl.appendChild(elt('div', { class: 'qlx-home-pillars' }, [
      pillar(t(QL + '.pillarMushafTitle'), t(QL + '.pillarMushafDesc'), '<path d="M4 4h16v16H4z M4 12h16"/>'),
      pillar(t(QL + '.pillarTranslationTitle'), t(QL + '.pillarTranslationDesc'), '<path d="M4 7h16M4 12h16M4 17h10"/>'),
      pillar(t(QL + '.pillarRecitationTitle'), t(QL + '.pillarRecitationDesc'), '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>')
    ]));

    function pillar(title, desc, svgPaths) {
      return elt('div', { class: 'qlx-pillar' }, [
        elt('span', {
          class: 'qlx-pillar__icon',
          html: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + svgPaths + '</svg>'
        }),
        elt('h3', { class: 'qlx-pillar__title', text: title }),
        elt('p', { class: 'qlx-pillar__desc', text: desc })
      ]);
    }
  }

  /* ============================================================================
   * 10. ReaderView — mushaf stream + translation blocks + audio
   * ============================================================================ */

  function ReaderView(contentEl, api, chapters, chapterId, query, audioCtl) {
    clear(contentEl);
    var prefs = Store.getPrefs();
    var chapter = chapters.find(function (c) { return String(c.id) === String(chapterId); });
    if (!chapter) {
      contentEl.appendChild(elt('div', { class: 'qlx-error' }, [
        document.createTextNode(t(QL + '.surahNotFound', { id: chapterId })),
        elt('button', { type: 'button', onclick: function () { Router.go('#/'); }, text: t('common.back') })
      ]));
      return;
    }

    Store.recordSurah({
      id: chapter.id,
      nameSimple: chapter.nameSimple,
      nameArabic: chapter.nameArabic,
      translatedName: chapter.translatedName,
      versesCount: chapter.versesCount
    });
    Store.setContinue({ chapterId: chapter.id, chapterName: chapter.nameSimple });

    var initialAyah = query && query.ayah ? parseInt(query.ayah, 10) : 0;
    function buildAssistantContext(verseKey) {
      var vk = verseKey || (initialAyah > 0 ? chapter.id + ':' + initialAyah : null);
      var ayNum = 0;
      if (vk) {
        var p = vk.split(':');
        if (p.length > 1) ayNum = parseInt(p[1], 10) || 0;
      }
      return {
        page: 'quran-library',
        surahId: Number(chapter.id),
        surahName: chapter.nameSimple,
        surahNameArabic: chapter.nameArabic || null,
        verseKey: vk,
        ayahNumber: ayNum
      };
    }
    publishQuranAssistantContext(buildAssistantContext(null));

    var listenBtn = elt('button', {
      class: 'qlx-toggle qlx-toggle--play',
      type: 'button',
      title: t(QL + '.playPauseRecitation'),
      html: '<svg class="qlx-toggle__icon" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg> ' + t(QL + '.listen'),
      onclick: function () { onListenClick(); }
    });
    var translateBtn = elt('button', {
      class: 'qlx-toggle' + (prefs.showTranslation ? ' is-on' : ''),
      type: 'button',
      title: t(QL + '.showHideTranslation'),
      html: '<svg class="qlx-toggle__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 8l4 4-4 4M11 19h8M14 4h7M14 9h7"/></svg> ' + t(QL + '.translate'),
      'aria-pressed': prefs.showTranslation ? 'true' : 'false',
      onclick: function () {
        var on = !translateBtn.classList.contains('is-on');
        translateBtn.classList.toggle('is-on', on);
        translateBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
        Store.setPrefs({ showTranslation: on });
        // Re-render so the layout switches between continuous mushaf
        // (off) and inline ayah-by-ayah pairs (on). Audio session is
        // preserved because we keep using the same audio controller and
        // re-attach the verse highlight via syncAudioUiWithExistingSession.
        if (loadedVerses && loadedVerses.length) renderVerses(loadedVerses);
        else showLayoutForTranslationMode(on);
        if (on) ensureAiTranslationsForMissing();
      }
    });

    var head = elt('div', { class: 'qlx-reader__head' }, [
      elt('button', {
        class: 'qlx-back',
        type: 'button',
        html: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg> <span>' + escapeHtml(t(QL + '.navLibrary')) + '</span>',
        onclick: function () { Router.go('#/'); }
      }),
      elt('div', { class: 'qlx-read-title' }, [
        elt('strong', { text: chapter.id + '. ' + chapter.nameSimple }),
        chapter.translatedName
          ? elt('span', { class: 'qlx-read-sub', text: ' — ' + chapter.translatedName })
          : null
      ].filter(Boolean)),
      elt('div', { class: 'qlx-reader__head-spacer' }),
      listenBtn,
      translateBtn
    ]);

    var mushaf = elt('article', { class: 'qlx-mushaf qlx-mushaf-body' });
    mushaf.appendChild(elt('header', { class: 'qlx-mushaf__header' }, [
      elt('span', { class: 'qlx-mushaf__crest', text: String(chapter.id) }),
      elt('h1', { class: 'qlx-mushaf__name', text: chapter.nameSimple }),
      elt('p', { class: 'qlx-mushaf__meaning', text: chapter.translatedName || '' }),
      elt('div', { class: 'qlx-mushaf__meta' }, [
        elt('span', { text: (chapter.revelationPlace || '').toUpperCase() }),
        elt('i'),
        elt('span', { text: t(QL + '.ayahsCount', { count: chapter.versesCount || 0 }) }),
        elt('i'),
        elt('span', { text: chapter.nameArabic || '', dir: 'rtl' })
      ])
    ]));
    /*
     * Decorative Bismillah above the surah body.
     *  - Chapter 1 (Al-Fatihah): the Bismillah IS ayah 1, so suppress the
     *    decorative copy to avoid a duplicate above the verse list.
     *  - Chapter 9 (At-Tawbah): traditionally has no Bismillah header.
     *  - All other chapters: render the decorative Bismillah as usual.
     */
    var chId = Number(chapter.id);
    if (chId !== 1 && chId !== 9) {
      mushaf.appendChild(elt('div', { class: 'qlx-mushaf__bismillah', text: 'بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ' }));
    }

    /*
     * Two render targets — only one is visible at a time:
     *   arabWrap   : continuous mushaf stream (translation OFF)
     *   pairedWrap : ayah-by-ayah cards, Arabic above its English (translation ON)
     * Both register the Arabic span in `verseSlotByKey` so audio highlight + scroll
     * sync are preserved across toggles without losing the playback session.
     */
    var arabWrap = elt('div', {
      class: 'qlx-mushaf-stream' + (prefs.showTranslation ? ' qlx-is-hidden' : ''),
      dir: 'rtl',
      lang: 'ar',
      hidden: prefs.showTranslation
    });
    var pairedWrap = elt('div', {
      class: 'qlx-mushaf-paired' + (prefs.showTranslation ? '' : ' qlx-is-hidden'),
      hidden: !prefs.showTranslation
    });
    var body = elt('div', { class: 'qlx-mushaf-col' });
    body.appendChild(arabWrap);
    body.appendChild(pairedWrap);
    var loadBox = elt('div', { class: 'qlx-skeleton-grid' });
    for (var si = 0; si < 3; si++) loadBox.appendChild(elt('div', { class: 'qlx-skeleton' }));
    (prefs.showTranslation ? pairedWrap : arabWrap).appendChild(loadBox);

    mushaf.appendChild(body);
    contentEl.appendChild(head);
    contentEl.appendChild(mushaf);

    function applyArabicScale() {
      var fs = 2.15 * (Store.getPrefs().arabicScale || 1);
      arabWrap.style.fontSize = fs.toFixed(2) + 'rem';
      arabWrap.style.lineHeight = '2.55';
      pairedWrap.style.setProperty('--qlx-paired-arabic-size', fs.toFixed(2) + 'rem');
    }

    var verseSlotByKey = {};

    function syncAudioUiWithExistingSession() {
      var existing = audioCtl.session();
      if (!existing || Number(existing.chapterId) !== Number(chapter.id)) return;
      var kk = audioCtl.currentVerseKey();
      if (kk && verseSlotByKey[kk]) {
        var sl = verseSlotByKey[kk];
        if (sl.ayah) sl.ayah.classList.add('is-playing');
        if (sl.pair) sl.pair.classList.add('qlx-ayah-pair--playing');
      }
      listenBtn.innerHTML = audioCtl.isPlaying()
        ? '<svg class="qlx-toggle__icon" viewBox="0 0 24 24" fill="currentColor"><path d="M6 5h4v14H6zM14 5h4v14h-4z"/></svg> ' + t(QL + '.pause')
        : '<svg class="qlx-toggle__icon" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg> ' + t(QL + '.listen');
      listenBtn.classList.toggle('is-on', audioCtl.isPlaying());
    }

    var loadedVerses = [];

    function makeVerseTools(v, verseKey) {
      var tools = elt('div', { class: 'qlx-mushaf-tools' });
      tools.appendChild(elt('button', {
        type: 'button',
        text: t(QL + '.playAyah'),
        onclick: function () { playFromVerse(verseKey); }
      }));
      var bm = Store.isBookmarked(verseKey);
      var bmBtn = elt('button', {
        type: 'button',
        text: bm ? t(QL + '.saved') : t(QL + '.bookmark'),
        onclick: function () {
          var nowOn = Store.toggleBookmark(verseKey, {
            chapterId: chapter.id,
            chapterName: chapter.nameSimple,
            verseNumber: v.verseNumber,
            snippet: v.translationText || ''
          });
          bmBtn.textContent = nowOn ? t(QL + '.saved') : t(QL + '.bookmark');
          Toast.show(nowOn ? t(QL + '.bookmarkSaved') : t(QL + '.bookmarkRemoved'));
        }
      });
      tools.appendChild(bmBtn);
      return tools;
    }

    function showLayoutForTranslationMode(translationOn) {
      arabWrap.hidden = !!translationOn;
      arabWrap.classList.toggle('qlx-is-hidden', !!translationOn);
      pairedWrap.hidden = !translationOn;
      pairedWrap.classList.toggle('qlx-is-hidden', !translationOn);
    }

    /*
     * Two presentation modes share the same data:
     *   Translation OFF → continuous mushaf flow inside `arabWrap`.
     *   Translation ON  → ayah-by-ayah pair cards inside `pairedWrap`
     *                     (Arabic on top, English directly below).
     * In both modes each Arabic span is the same element shape
     * (`.qlx-mushaf-ayah` + `data-verse-key`) so the audio sync code
     * and the bookmark/play-ayah tools just keep working.
     */
    function renderVerses(verses) {
      loadedVerses = verses.slice();
      verseSlotByKey = {};
      clear(arabWrap);
      clear(pairedWrap);

      var translationOn = !!Store.getPrefs().showTranslation;
      showLayoutForTranslationMode(translationOn);

      verses.forEach(function (v) {
        var verseKey = v.verseKey || (chapter.id + ':' + v.verseNumber);
        var ar = v.textArabic || '';
        var ay = elt('span', {
          class: 'qlx-mushaf-ayah',
          'data-verse-key': verseKey,
          id: 'qlx-v-' + verseKey.replace(':', '_')
        });
        ay.appendChild(document.createTextNode(ar + '\u00a0'));
        ay.appendChild(elt('span', {
          class: 'qlx-end-ayah',
          text: '\uFD3F' + arabicNumeral(v.verseNumber) + '\uFD3E'
        }));

        if (translationOn) {
          var trText = v.translationText || '';
          var trPara = elt('p', { class: 'qlx-mushaf-tr__text', text: trText || t(QL + '.translationLoading') });
          if (!trText) trPara.classList.add('qlx-mushaf-tr__text--pending');

          var arBlock = elt('div', { class: 'qlx-ayah-pair__arabic', dir: 'rtl', lang: 'ar' });
          arBlock.appendChild(ay);

          var trBlock = elt('div', { class: 'qlx-ayah-pair__translation qlx-mushaf-tr', 'data-verse-key': verseKey }, [
            elt('span', { class: 'qlx-mushaf-tr__num', text: chapter.id + ':' + v.verseNumber }),
            trPara
          ]);
          trBlock.appendChild(makeVerseTools(v, verseKey));

          var pair = elt('article', { class: 'qlx-ayah-pair', 'data-verse-key': verseKey });
          pair.appendChild(arBlock);
          pair.appendChild(trBlock);
          pairedWrap.appendChild(pair);

          verseSlotByKey[verseKey] = { ayah: ay, tr: trBlock, pair: pair };
        } else {
          arabWrap.appendChild(ay);
          arabWrap.appendChild(document.createTextNode(' '));
          verseSlotByKey[verseKey] = { ayah: ay, tr: null, pair: null };
        }
      });

      applyArabicScale();
      syncAudioUiWithExistingSession();
      if (query && query.ayah) {
        var k = chapter.id + ':' + query.ayah;
        var slot = verseSlotByKey[k];
        if (slot && slot.ayah) setTimeout(function () { slot.ayah.scrollIntoView({ behavior: 'smooth', block: 'center' }); }, 80);
      }
    }

    function loadVerses(force) {
      var translation = Store.getPrefs().translationId || '131';
      api.verses(chapter.id, { merged: true, translations: translation })
        .then(function (data) {
          renderVerses(data.verses || []);
          if (Store.getPrefs().showTranslation) ensureAiTranslationsForMissing();
        })
        .catch(function (err) {
          clear(arabWrap);
          clear(pairedWrap);
          // Always show the error in the visible container so the user sees it
          // regardless of the current Translate toggle state.
          showLayoutForTranslationMode(false);
          var detail = (err.payload && (err.payload.message || err.payload.detail)) || err.message || '';
          arabWrap.appendChild(elt('div', { class: 'qlx-error' }, [
            document.createTextNode(detail || t(QL + '.couldNotLoadAyahs')),
            elt('button', { type: 'button', text: t('common.retry'), onclick: function () { loadVerses(true); } })
          ]));
        });
    }
    loadVerses(false);

    /**
     * Two-tier translation policy:
     *   1. Quran Foundation already populates `translationText` whenever it is available.
     *   2. For ayahs whose QF translation came back empty (missing entitlement, restricted
     *      resource, etc.) we fall back to OpenAI via /translate-ai. Results are also cached
     *      server-side so subsequent loads/toggles are instant.
     * Either way, the user sees a real English translation under every ayah.
     */
    var aiInflight = false;
    function ensureAiTranslationsForMissing() {
      if (aiInflight) return;
      var missing = (loadedVerses || []).filter(function (v) {
        return !v.translationText && (v.textArabic || '').trim().length > 0;
      });
      if (!missing.length) return;
      aiInflight = true;
      var payload = missing.map(function (v) {
        return { verseKey: v.verseKey || (chapter.id + ':' + v.verseNumber), arabic: v.textArabic };
      });
      api.aiTranslate(payload).then(function (resp) {
        var map = (resp && resp.translations) || {};
        Object.keys(map).forEach(function (key) {
          var slot = verseSlotByKey[key];
          if (!slot || !slot.tr) return;
          var p = slot.tr.querySelector('.qlx-mushaf-tr__text');
          if (!p) return;
          p.textContent = map[key];
          p.classList.remove('qlx-mushaf-tr__text--pending');
          slot.tr.classList.add('qlx-mushaf-tr--ai');
          // Mirror onto the source verse so a re-render keeps the AI text.
          var src = (loadedVerses || []).find(function (vv) {
            return (vv.verseKey || (chapter.id + ':' + vv.verseNumber)) === key;
          });
          if (src) src.translationText = map[key];
        });
        // Any verse still pending → mark gracefully (won't disappear, won't be empty).
        Object.keys(verseSlotByKey).forEach(function (key) {
          var slot = verseSlotByKey[key];
          if (!slot || !slot.tr) return;
          var p = slot.tr.querySelector('.qlx-mushaf-tr__text--pending');
          if (p) p.textContent = t(QL + '.translationUnavailable');
        });
      }).catch(function (err) {
        Object.keys(verseSlotByKey).forEach(function (key) {
          var slot = verseSlotByKey[key];
          if (!slot || !slot.tr) return;
          var p = slot.tr.querySelector('.qlx-mushaf-tr__text--pending');
          if (p) p.textContent = t(QL + '.translationUnavailableCode', { code: (err && err.code) ? err.code : 'offline' });
        });
      }).then(function () { aiInflight = false; });
    }

    if (!window.__qlxReciters || !window.__qlxReciters.length) {
      api.reciters().then(function (data) {
        window.__qlxReciters = extractRecitersArray(data).map(normalizeReciter).filter(Boolean);
      }).catch(function () { window.__qlxReciters = window.__qlxReciters || []; });
    }

    var unsubscribeAudio = audioCtl.on(function (name, payload) {
      if (name === 'verse-changed') {
        Object.keys(verseSlotByKey).forEach(function (k) {
          var sl = verseSlotByKey[k];
          if (sl && sl.ayah) sl.ayah.classList.remove('is-playing');
          if (sl && sl.pair) sl.pair.classList.remove('qlx-ayah-pair--playing');
        });
        if (payload && payload.verseKey) {
          publishQuranAssistantContext(buildAssistantContext(payload.verseKey));
          if (verseSlotByKey[payload.verseKey]) {
            var slot = verseSlotByKey[payload.verseKey];
            if (slot.ayah) slot.ayah.classList.add('is-playing');
            if (slot.pair) slot.pair.classList.add('qlx-ayah-pair--playing');
            var anchor = slot.pair || slot.ayah;
            var rect = anchor.getBoundingClientRect();
            if (rect.top < 120 || rect.bottom > window.innerHeight - 140) {
              anchor.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
          }
        }
      } else if (name === 'play' || name === 'pause' || name === 'loaded' || name === 'ended') {
        listenBtn.innerHTML = audioCtl.isPlaying()
          ? '<svg class="qlx-toggle__icon" viewBox="0 0 24 24" fill="currentColor"><path d="M6 5h4v14H6zM14 5h4v14h-4z"/></svg> ' + t(QL + '.pause')
          : '<svg class="qlx-toggle__icon" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg> ' + t(QL + '.listen');
        listenBtn.classList.toggle('is-on', audioCtl.isPlaying());
      }
    });

    function loadAudioForChapter(autoplay) {
      var reciterId = (query && query.reciter) ? Number(query.reciter) : preferredReciterId();
      var reciterMeta = null;
      try {
        reciterMeta = (window.__qlxReciters || []).find(function (r) { return String(r.id) === String(reciterId); }) || null;
      } catch (e) { /* ignore */ }
      Toast.show(t(QL + '.loadingRecitation'));
      return api.audio(chapter.id, reciterId).then(function (data) {
        audioCtl.load({
          chapterId: chapter.id,
          chapterName: chapter.nameSimple,
          reciter: reciterMeta || { id: reciterId, name: t(QL + '.reciter', { id: reciterId }) },
          audioUrl: data.audioUrl,
          durationSec: data.durationSec,
          verseTimings: data.verseTimings || []
        });
        if (autoplay !== false) audioCtl.play();
        Store.recordReciter(reciterMeta || { id: reciterId, name: t(QL + '.reciter', { id: reciterId }) });
      }).catch(function (err) {
        Toast.show(audioErrorHuman(err, err.code), 'error');
      });
    }

    function onListenClick() {
      var s = audioCtl.session();
      if (s && Number(s.chapterId) === Number(chapter.id)) {
        audioCtl.toggle();
      } else {
        loadAudioForChapter(true);
      }
    }

    function playFromVerse(verseKey) {
      var s = audioCtl.session();
      if (s && Number(s.chapterId) === Number(chapter.id) && audioCtl.seekToVerse(verseKey)) return;
      loadAudioForChapter(false).then(function () {
        var tries = 0;
        var tick = setInterval(function () {
          if (audioCtl.seekToVerse(verseKey) || ++tries > 25) clearInterval(tick);
        }, 180);
      });
    }

    contentEl.__qlxCleanup = function () {
      try { unsubscribeAudio(); } catch (e) { /* ignore */ }
      publishQuranAssistantContext(null);
    };
  }

  /* ============================================================================
   * 11. RecitersView — searchable, filterable directory of reciters.
   *     Top: rich header + "Continue listening" rail of recent reciters.
   *     Toolbar: search input + style filter chips + sort dropdown + count chip.
   *     Grid: animated cards with hover "Play Al-Fatihah" overlay and a live
   *     equalizer badge when that reciter is currently in the audio dock.
   * ============================================================================ */

  /** True if the global audio session is playing a track by this reciter. */
  function isAudioSessionByReciter(reciter) {
    var ctl = window.__qlxAudio;
    var s = ctl && ctl.session && ctl.session();
    if (!s || !s.reciter || !reciter) return false;
    return String(s.reciter.id) === String(reciter.id);
  }

  function RecitersView(contentEl, api) {
    clear(contentEl);
    contentEl.classList.add('qlx-content--reciters');

    var page = elt('div', { class: 'qlx-rcx-page' });
    var countEl = elt('p', { class: 'qlx-rcx-count', text: t(QL + '.loadingReciters') });

    var searchInput = elt('input', {
      class: 'qlx-rcx-search__input',
      type: 'search',
      placeholder: t(QL + '.searchReciter'),
      autocomplete: 'off',
      'aria-label': t(QL + '.searchRecitersAria')
    });
    var clearBtn = elt('button', {
      class: 'qlx-rcx-search__clear',
      type: 'button',
      'aria-label': t(QL + '.clearSearch'),
      html: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 6l12 12M6 18 18 6"/></svg>'
    });
    clearBtn.hidden = true;
    var searchWrap = elt('div', { class: 'qlx-rcx-search' }, [
      elt('span', { class: 'qlx-rcx-search__icon', 'aria-hidden': 'true', html: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>' }),
      searchInput,
      clearBtn
    ]);

    var STYLE_FILTERS = [
      { label: t(QL + '.styleAll'),      val: 'all' },
      { label: t(QL + '.styleMurattal'), val: 'murattal' },
      { label: t(QL + '.styleMujawwad'), val: 'mujawwad' }
    ];
    var styleChips = elt('div', { class: 'qlx-rcx-filters', role: 'group', 'aria-label': t(QL + '.filterStyleAria') },
      STYLE_FILTERS.map(function (f, i) {
        return elt('button', {
          class: 'qlx-rcx-chip' + (i === 0 ? ' is-active' : ''),
          type: 'button',
          'data-val': f.val,
          text: f.label
        });
      })
    );

    var body = elt('div', { class: 'qlx-rcx-body' });
    body.appendChild(elt('div', { class: 'qlx-rcx-toolbar' }, [
      searchWrap,
      elt('div', { class: 'qlx-rcx-toolbar__right' }, [countEl])
    ]));
    body.appendChild(elt('div', { class: 'qlx-rcx-controls' }, [styleChips]));

    /* ----- 11.3 Grid ----------------------------------------------------- */
    var grid = elt('div', { class: 'qlx-rcx-grid' });
    body.appendChild(grid);
    page.appendChild(body);
    contentEl.appendChild(page);
    for (var si = 0; si < 12; si++) grid.appendChild(skeletonCard());

    function skeletonCard() {
      return elt('article', { class: 'qlx-rcx-skel', 'aria-hidden': 'true' }, [
        elt('div', { class: 'qlx-rcx-skel__media' }),
        elt('div', { class: 'qlx-rcx-skel__body' }, [
          elt('div', { class: 'qlx-rcx-skel__line qlx-rcx-skel__line--w70' }),
          elt('div', { class: 'qlx-rcx-skel__line qlx-rcx-skel__line--w45' })
        ])
      ]);
    }

    var state = { all: [], filtered: [], q: '', style: 'all' };

    function hasPortrait(r) {
      return !!(r && r.imageUrl);
    }

    function renderGrid() {
      clear(grid);
      var list = state.filtered;
      if (!list.length) {
        grid.appendChild(elt('div', { class: 'qlx-rcx-empty' }, [
          elt('div', { class: 'qlx-rcx-empty__icon', 'aria-hidden': 'true', html: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" width="36" height="36"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/></svg>' }),
          elt('p', { class: 'qlx-rcx-empty__title', text: state.q ? t(QL + '.noRecitersMatch', { query: state.q }) : t(QL + '.noRecitersAvailable') }),
          elt('p', { class: 'qlx-rcx-empty__hint', text: state.q ? t(QL + '.tryDifferentSearch') : t(QL + '.tryAgainShortly') }),
          state.q ? elt('button', {
            class: 'qlx-rcx-empty__reset', type: 'button', text: t(QL + '.resetFilters'),
            onclick: function () { resetFilters(); }
          }) : null
        ].filter(Boolean)));
        return;
      }
      list.forEach(function (r) {
        grid.appendChild(reciterCard(r));
      });
    }

    function applyFilters() {
      var q = (state.q || '').trim().toLowerCase();
      var styleVal = state.style;
      var list = state.all.slice();
      if (q) {
        list = list.filter(function (r) {
          var hay = ((r.name || '') + ' ' + (r.style || '') + ' ' + ((r.bio && r.bio.country) || '')).toLowerCase();
          return hay.indexOf(q) >= 0;
        });
      }
      if (styleVal && styleVal !== 'all') {
        list = list.filter(function (r) {
          return String(r.style || '').toLowerCase().indexOf(styleVal) >= 0;
        });
      }
      /* Stable secondary sort: reciters WITH a portrait first, others trailing. */
      list.sort(function (a, b) {
        var ha = hasPortrait(a) ? 0 : 1;
        var hb = hasPortrait(b) ? 0 : 1;
        return ha - hb;
      });
      state.filtered = list;
      renderGrid();
      if (!state.all.length) {
        countEl.textContent = t(QL + '.noRecitersAvailable');
      } else if (list.length === state.all.length) {
        countEl.textContent = state.all.length + ' ' + (state.all.length === 1 ? t(QL + '.reciterSingular') : t(QL + '.recitersPlural'));
      } else {
        countEl.textContent = t(QL + '.showingReciters', { shown: list.length, total: state.all.length });
      }
    }

    function resetFilters() {
      state.q = ''; state.style = 'all';
      searchInput.value = '';
      clearBtn.hidden = true;
      $$('button.qlx-rcx-chip', styleChips).forEach(function (b) {
        b.classList.toggle('is-active', b.dataset.val === 'all');
      });
      applyFilters();
    }

    searchInput.addEventListener('input', function () {
      state.q = searchInput.value;
      clearBtn.hidden = !state.q;
      applyFilters();
    });
    clearBtn.addEventListener('click', function () {
      searchInput.value = '';
      state.q = '';
      clearBtn.hidden = true;
      searchInput.focus();
      applyFilters();
    });
    styleChips.addEventListener('click', function (ev) {
      var btn = ev.target && ev.target.closest && ev.target.closest('button.qlx-rcx-chip');
      if (!btn) return;
      $$('button.qlx-rcx-chip', styleChips).forEach(function (b) { b.classList.remove('is-active'); });
      btn.classList.add('is-active');
      state.style = btn.dataset.val || 'all';
      applyFilters();
    });
    function reciterInitials(name) {
      var s = String(name || '').trim();
      if (!s) return '';
      var parts = s.replace(/[`'\u2018\u2019\u02bb]/g, '').split(/\s+/).filter(Boolean);
      if (!parts.length) return '';
      if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
      return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    function reciterCard(r) {
      var name = r.name || t(QL + '.reciter', { id: r.id });
      var styleTxt = typeof r.style === 'string' ? r.style : '';
      var withPortrait = hasPortrait(r);

      var media;
      if (withPortrait) {
        media = elt('div', { class: 'qlx-rcx-card__media' }, [
          buildReciterFace(r, 'qlx-rcx-card__img'),
          elt('span', {
            class: 'qlx-rcx-card__play',
            'aria-hidden': 'true',
            html: '<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>'
          })
        ]);
      } else {
        media = elt('div', { class: 'qlx-rcx-card__media qlx-rcx-card__media--placeholder' }, [
          elt('span', { class: 'qlx-rcx-card__placeholder-mark', 'aria-hidden': 'true', text: reciterInitials(name) || '\u272A' }),
          elt('span', {
            class: 'qlx-rcx-card__play qlx-rcx-card__play--soft',
            'aria-hidden': 'true',
            html: '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>'
          })
        ]);
      }

      if (isAudioSessionByReciter(r)) {
        media.appendChild(elt('span', {
          class: 'qlx-rcx-card__live',
          title: t(QL + '.nowPlaying'),
          'aria-label': t(QL + '.currentlyPlaying'),
          html: '<span></span><span></span><span></span>'
        }));
      }

      return elt('a', {
        class: 'qlx-rcx-card' + (withPortrait ? '' : ' qlx-rcx-card--placeholder'),
        href: '#/reciter/' + r.id
      }, [
        media,
        elt('div', { class: 'qlx-rcx-card__body' }, [
          elt('h3', { class: 'qlx-rcx-card__name', text: name }),
          elt('p', { class: 'qlx-rcx-card__style', text: styleTxt || t(QL + '.styleMurattal') })
        ])
      ]);
    }

    api.reciters().then(function (data) {
      var arr = extractRecitersArray(data).map(normalizeReciter).filter(Boolean);
      window.__qlxReciters = arr;
      state.all = arr;
      if (!arr.length) {
        countEl.textContent = '0 ' + t(QL + '.recitersPlural');
        clear(grid);
        grid.appendChild(elt('div', { class: 'qlx-rcx-empty' }, [
          elt('div', { class: 'qlx-rcx-empty__icon', 'aria-hidden': 'true', html: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" width="36" height="36"><circle cx="12" cy="12" r="9"/><path d="M9 9l6 6M15 9l-6 6"/></svg>' }),
          elt('p', { class: 'qlx-rcx-empty__title', text: t(QL + '.noRecitersAvailable') }),
          elt('p', { class: 'qlx-rcx-empty__hint', text: ((data || {}).error === 'upstream_forbidden')
            ? t(QL + '.reciterCatalogueForbidden')
            : t(QL + '.tryAgainShortly') })
        ]));
        return;
      }
      applyFilters();
    }).catch(function (err) {
      countEl.textContent = '';
      clear(grid);
      grid.appendChild(elt('div', { class: 'qlx-rcx-empty qlx-rcx-empty--error' }, [
        elt('div', { class: 'qlx-rcx-empty__icon', 'aria-hidden': 'true', html: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" width="36" height="36"><circle cx="12" cy="12" r="10"/><path d="M12 8v5M12 16h.01"/></svg>' }),
        elt('p', { class: 'qlx-rcx-empty__title', text: t(QL + '.couldNotLoadReciters') }),
        elt('p', { class: 'qlx-rcx-empty__hint', text: err.message || t(QL + '.reciterCatalogueFailed') }),
        elt('button', {
          class: 'qlx-rcx-empty__reset', type: 'button', text: t('common.tryAgain'),
          onclick: function () { RecitersView(contentEl, api); }
        })
      ]));
    });
  }

  /* ============================================================================
   * 11b. ReciterProfileView — image, name, surah list with Listen + Download.
   * ============================================================================ */

  function ReciterProfileView(contentEl, api, chapters, reciterId) {
    clear(contentEl);
    var loadingBox = elt('div', { class: 'qlx-skeleton-grid' });
    for (var i = 0; i < 8; i++) loadingBox.appendChild(elt('div', { class: 'qlx-skeleton' }));
    contentEl.appendChild(loadingBox);

    function ensureReciters() {
      if (window.__qlxReciters && window.__qlxReciters.length) {
        return Promise.resolve(window.__qlxReciters);
      }
      return api.reciters().then(function (data) {
        var arr = extractRecitersArray(data).map(normalizeReciter).filter(Boolean);
        window.__qlxReciters = arr;
        return arr;
      });
    }

    ensureReciters().then(function (list) {
      var rec = (list || []).find(function (r) { return String(r.id) === String(reciterId); });
      clear(contentEl);
      if (!rec) {
        contentEl.appendChild(elt('div', { class: 'qlx-rcx-empty qlx-rcx-empty--error' }, [
          elt('div', { class: 'qlx-rcx-empty__icon', 'aria-hidden': 'true', html: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" width="36" height="36"><circle cx="12" cy="12" r="10"/><path d="M12 8v5M12 16h.01"/></svg>' }),
          elt('p', { class: 'qlx-rcx-empty__title', text: t(QL + '.reciterNotInCatalogue', { id: reciterId }) }),
          elt('p', { class: 'qlx-rcx-empty__hint', text: t(QL + '.browseDirectory') }),
          elt('button', { class: 'qlx-rcx-empty__reset', type: 'button', text: t(QL + '.allReciters'), onclick: function () { Router.go('#/reciters'); } })
        ]));
        return;
      }
      Store.recordReciter({ id: rec.id, name: rec.name, style: rec.style, imageUrl: rec.imageUrl });

      var bio = rec.bio || null;
      var face = buildReciterFace(rec, 'qlx-profile__photo qlx-rcx-prof__photo');
      var page = elt('div', { class: 'qlx-rcx-prof-page' });

      page.appendChild(elt('button', {
        class: 'qlx-back qlx-rcx-prof__back',
        type: 'button',
        html: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg> <span>' + escapeHtml(t(QL + '.allReciters')) + '</span>',
        onclick: function () { Router.go('#/reciters'); }
      }));

      /* ----- Hero with primary actions ---------------------------------- */
      var ICONS = {
        pin:  '<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 22s7-7.58 7-13a7 7 0 1 0-14 0c0 5.42 7 13 7 13z"/><circle cx="12" cy="9" r="2.5"/></svg>',
        cal:  '<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></svg>',
        mic:  '<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v4M8 22h8"/></svg>',
        book: '<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 4h12a4 4 0 0 1 4 4v12H8a4 4 0 0 1-4-4z"/><path d="M4 4v12"/></svg>'
      };
      var pillSpecs = [];
      if (rec.style) pillSpecs.push({ icon: 'mic', text: rec.style });
      if (bio && bio.country) pillSpecs.push({ icon: 'pin', text: bio.country });
      if (bio && bio.years) pillSpecs.push({ icon: 'cal', text: bio.years });
      pillSpecs.push({ icon: 'book', text: t(QL + '.surahsCount', { count: chapters.length || 0 }) });

      var liveDot = isAudioSessionByReciter(rec)
        ? elt('span', { class: 'qlx-rcx-prof__live', html: '<span></span><span></span><span></span><em>' + escapeHtml(t(QL + '.nowPlaying')) + '</em>' })
        : null;

      var bodyChildren = [
        elt('p', { class: 'qlx-rcx-prof__eyebrow', text: t(QL + '.brandEyebrow') }),
        elt('h1', { class: 'qlx-profile__name qlx-rcx-prof__name', text: rec.name || t(QL + '.reciter', { id: rec.id }) }),
        elt('div', { class: 'qlx-profile__pills qlx-rcx-prof__pills' },
          pillSpecs.map(function (p) {
            return elt('span', { class: 'qlx-profile__pill qlx-rcx-prof__pill', html: ICONS[p.icon] + '<span>' + escapeHtml(p.text) + '</span>' });
          })
        ),
        bio && bio.desc ? elt('p', { class: 'qlx-profile__bio qlx-rcx-prof__bio', text: bio.desc }) : null
      ].filter(Boolean);

      page.appendChild(elt('section', { class: 'qlx-profile__hero qlx-rcx-prof' }, [
        elt('div', { class: 'qlx-rcx-prof__art', 'aria-hidden': 'true' }, [
          elt('span', { class: 'qlx-rcx-prof__geom' })
        ]),
        elt('div', { class: 'qlx-rcx-prof__veil', 'aria-hidden': 'true' }),
        elt('div', { class: 'qlx-rcx-prof__inner' }, [
          elt('div', { class: 'qlx-rcx-prof__photo-wrap' }, [face, liveDot].filter(Boolean)),
          elt('div', { class: 'qlx-profile__meta qlx-rcx-prof__body' }, bodyChildren)
        ])
      ]));

      /* ----- Surah list with search + filter ---------------------------- */
      var listHead = elt('div', { class: 'qlx-rcx-list__head' }, [
        elt('div', { class: 'qlx-rcx-list__title-wrap' }, [
          elt('h2', { class: 'qlx-home-section-title qlx-rcx-list__title', text: t(QL + '.surahs') }),
          elt('span', { class: 'qlx-rcx-list__count', id: 'qlx-rcx-list-count' })
        ]),
        (function () {
          var input = elt('input', {
            class: 'qlx-rcx-list__search',
            type: 'search',
            placeholder: t(QL + '.searchSurahList'),
            autocomplete: 'off',
            'aria-label': t(QL + '.filterSurahsAria')
          });
          input.addEventListener('input', function () { renderSurahs(input.value); });
          return elt('div', { class: 'qlx-rcx-list__search-wrap' }, [
            elt('span', { class: 'qlx-rcx-list__search-icon', 'aria-hidden': 'true', html: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>' }),
            input
          ]);
        }())
      ]);
      page.appendChild(listHead);

      var surahList = elt('div', { class: 'qlx-profile__surahs qlx-rcx-list__grid' });
      page.appendChild(surahList);
      contentEl.appendChild(page);

      function isCurrentlyPlayingChapter(chapterId) {
        var ctl = window.__qlxAudio;
        var s = ctl && ctl.session && ctl.session();
        if (!s) return false;
        return Number(s.chapterId) === Number(chapterId) && s.reciter && String(s.reciter.id) === String(rec.id);
      }

      function renderSurahs(filter) {
        clear(surahList);
        var q = String(filter || '').trim().toLowerCase();
        var items = chapters.filter(function (c) {
          if (!q) return true;
          var hay = (c.id + ' ' + (c.nameSimple || '') + ' ' + (c.translatedName || '') + ' ' + (c.nameArabic || '')).toLowerCase();
          return hay.indexOf(q) >= 0;
        });
        var counter = document.getElementById('qlx-rcx-list-count');
        if (counter) {
          counter.textContent = (items.length === chapters.length)
            ? t(QL + '.availableCount', { count: chapters.length })
            : t(QL + '.ofCount', { shown: items.length, total: chapters.length });
        }
        if (!items.length) {
          surahList.appendChild(elt('div', { class: 'qlx-rcx-empty qlx-rcx-empty--inline' }, [
            elt('p', { class: 'qlx-rcx-empty__title', text: t(QL + '.noSurahMatch', { query: q }) }),
            elt('p', { class: 'qlx-rcx-empty__hint', text: t(QL + '.tryDifferentSurah') })
          ]));
          return;
        }
        items.forEach(function (c) {
          var playing = isCurrentlyPlayingChapter(c.id);
          var listenLink = elt('a', {
            class: 'qlx-action qlx-action--listen',
            href: '#/surah/' + c.id + '?reciter=' + encodeURIComponent(String(rec.id)),
            html: '<svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg> ' + (playing ? t(QL + '.playing') : t(QL + '.listen'))
          });
          var dlBtn = elt('button', {
            class: 'qlx-action qlx-action--dl',
            type: 'button',
            html: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12M7 10l5 5 5-5M5 21h14"/></svg> ' + t(QL + '.download'),
            onclick: function () { downloadChapter(c, rec, dlBtn); }
          });
          var nameChildren = [document.createTextNode(c.nameSimple)];
          if (playing) {
            nameChildren.push(elt('span', { class: 'qlx-rcx-row__live', 'aria-label': t(QL + '.nowPlaying'), html: '<span></span><span></span><span></span>' }));
          }
          var row = elt('div', {
            class: 'qlx-profile-surah qlx-rcx-row' + (playing ? ' is-playing' : '')
          }, [
            elt('span', { class: 'qlx-profile-surah__num qlx-rcx-row__num', text: String(c.id) }),
            elt('div', { class: 'qlx-profile-surah__txt' }, [
              elt('p', { class: 'qlx-profile-surah__name qlx-rcx-row__name' }, nameChildren),
              elt('p', { class: 'qlx-profile-surah__meta', text: (c.translatedName || '') + ' · ' + t(QL + '.ayahsCount', { count: c.versesCount || 0 }) })
            ]),
            elt('span', { class: 'qlx-profile-surah__ar', text: c.nameArabic || '', dir: 'rtl' }),
            elt('div', { class: 'qlx-profile-surah__actions' }, [listenLink, dlBtn])
          ]);
          surahList.appendChild(row);
        });
      }
      renderSurahs('');
    }).catch(function (err) {
      clear(contentEl);
      contentEl.appendChild(elt('div', { class: 'qlx-rcx-empty qlx-rcx-empty--error' }, [
        elt('div', { class: 'qlx-rcx-empty__icon', 'aria-hidden': 'true', html: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" width="36" height="36"><circle cx="12" cy="12" r="10"/><path d="M12 8v5M12 16h.01"/></svg>' }),
        elt('p', { class: 'qlx-rcx-empty__title', text: t(QL + '.couldNotLoadProfile') }),
        elt('p', { class: 'qlx-rcx-empty__hint', text: err.message || t(QL + '.tryAgainMoment') }),
        elt('button', { class: 'qlx-rcx-empty__reset', type: 'button', text: t('common.retry'), onclick: function () { Router.go('#/reciter/' + reciterId); } })
      ]));
    });

    function safeFile(s) { return String(s || '').replace(/[^\w\-]+/g, '_'); }
    function pad3(n) { var s = String(n); while (s.length < 3) s = '0' + s; return s; }

    function downloadChapter(chapter, reciter, btn) {
      var prevHtml = btn.innerHTML;
      btn.disabled = true;
      btn.innerHTML = t(QL + '.preparing');
      api.audio(chapter.id, reciter.id).then(function (data) {
        var url = data.audioUrl;
        if (!url) throw new Error('Audio URL missing');
        var fileName = pad3(chapter.id) + '_' + safeFile(chapter.nameSimple || ('Chapter_' + chapter.id))
          + '_' + safeFile(reciter.name || ('Reciter_' + reciter.id)) + '.mp3';
        return fetch(url, { mode: 'cors' }).then(function (r) {
          if (!r.ok) throw new Error('HTTP ' + r.status);
          return r.blob();
        }).then(function (blob) {
          var a = document.createElement('a');
          var blobUrl = URL.createObjectURL(blob);
          a.href = blobUrl;
          a.download = fileName;
          document.body.appendChild(a);
          a.click();
          setTimeout(function () { URL.revokeObjectURL(blobUrl); a.remove(); }, 200);
          Toast.show(t(QL + '.downloadStarted'), 'success');
        }).catch(function () {
          var a = document.createElement('a');
          a.href = url;
          a.target = '_blank';
          a.rel = 'noopener';
          a.download = fileName;
          document.body.appendChild(a);
          a.click();
          a.remove();
          Toast.show(t(QL + '.openedInNewTab'));
        });
      }).catch(function (err) {
        Toast.show(audioErrorHuman(err, err.code), 'error');
      }).then(function () {
        btn.disabled = false;
        btn.innerHTML = prevHtml;
      });
    }
  }

  /* ============================================================================
   * 12. MyQuranView — recent surahs, recent reciters, bookmarks.
   * ============================================================================ */

  function MyQuranView(contentEl, api) {
    clear(contentEl);
    var grid = elt('div', { class: 'qlx-my-grid' });
    contentEl.appendChild(grid);

    /* Continue reading */
    var cont = Store.getContinue();
    grid.appendChild((function () {
      var card = elt('div', { class: 'qlx-my-card' });
      card.appendChild(elt('h3', { text: t(QL + '.continueReading') }));
      card.appendChild(elt('p', { text: t(QL + '.continueReadingHint') }));
      if (cont) {
        var ul = elt('ul');
        ul.appendChild(elt('li', { onclick: function () { Router.go('#/surah/' + cont.chapterId); } }, [
          elt('span', { text: cont.chapterName || t(QL + '.chapter', { number: cont.chapterId }) }),
          elt('small', { text: t('common.openArrow') })
        ]));
        card.appendChild(ul);
      } else {
        card.appendChild(elt('div', { class: 'qlx-my-empty', text: t(QL + '.noSurahOpened') }));
      }
      return card;
    }()));

    /* Recent surahs */
    grid.appendChild((function () {
      var card = elt('div', { class: 'qlx-my-card' });
      card.appendChild(elt('h3', { text: t(QL + '.recentSurahs') }));
      var recent = Store.recentSurahs();
      if (!recent.length) { card.appendChild(elt('div', { class: 'qlx-my-empty', text: t('common.nothingYet') })); return card; }
      var ul = elt('ul');
      recent.forEach(function (c) {
        ul.appendChild(elt('li', { onclick: function () { Router.go('#/surah/' + c.id); } }, [
          elt('span', { text: c.id + '. ' + c.nameSimple }),
          elt('small', { text: c.translatedName || '' })
        ]));
      });
      card.appendChild(ul);
      return card;
    }()));

    /* Recent reciters */
    grid.appendChild((function () {
      var card = elt('div', { class: 'qlx-my-card' });
      card.appendChild(elt('h3', { text: t(QL + '.recentReciters') }));
      var recent = Store.recentReciters();
      if (!recent.length) { card.appendChild(elt('div', { class: 'qlx-my-empty', text: t('common.nothingYet') })); return card; }
      var ul = elt('ul');
      recent.forEach(function (r) {
        ul.appendChild(elt('li', {
          onclick: function () { Router.go('#/reciter/' + r.id); }
        }, [
          elt('span', { text: r.name }),
          elt('small', { text: t('common.profileArrow') })
        ]));
      });
      card.appendChild(ul);
      return card;
    }()));

    /* Bookmarks */
    grid.appendChild((function () {
      var card = elt('div', { class: 'qlx-my-card' });
      card.appendChild(elt('h3', { text: t(QL + '.bookmarks') }));
      var marks = Store.bookmarks();
      if (!marks.length) { card.appendChild(elt('div', { class: 'qlx-my-empty', text: t(QL + '.bookmarkHint') })); return card; }
      var ul = elt('ul');
      marks.forEach(function (b) {
        ul.appendChild(elt('li', {
          onclick: function () { Router.go('#/surah/' + b.chapterId + '?ayah=' + (b.verseNumber || 1)); }
        }, [
          elt('span', { text: (b.chapterName || t(QL + '.chapter', { number: b.chapterId })) + ' · ' + b.verseKey }),
          elt('small', { text: t('common.openArrow') })
        ]));
      });
      card.appendChild(ul);
      return card;
    }()));
  }

  /* ============================================================================
   * 13. App boot
   * ============================================================================ */

  var qlxTopbarState = { api: null, hash: '#/', mode: 'library' };

  function init() {
    whenI18nReady(bootQuranLibrary);
  }

  function bootQuranLibrary() {
    var root = $('[data-qlx-root]');
    if (!root) return;
    var ctx = root.dataset.ctx || '';
    var api = makeApi(root);
    var stage = $('[data-qlx-stage]', root);
    var dock = makeDock(root);
    var audioCtl = makeAudioController(dock);
    dock.bind(audioCtl);
    /* Expose for non-Reader views (RecitersView / ReciterProfileView) so they can
       render a "currently playing" indicator without holding a direct reference. */
    window.__qlxAudio = audioCtl;
    Toast.bind($('[data-qlx-toast]', root));

    var topbar = buildTopbar(root, ctx);
    qlxTopbarState.api = topbar;

    /* Periodic dock progress refresh while playing — keeps the bar smooth */
    setInterval(function () { if (audioCtl.isPlaying()) dock.update(); }, 250);

    var layout = null;

    /* Cleanup hook for views (audio listener detach, picker timers, etc.) */
    function disposePrev() {
      var pane = layout && layout.content ? layout.content : stage;
      if (pane.__qlxCleanup) { try { pane.__qlxCleanup(); } catch (e) { /* ignore */ } }
      pane.__qlxCleanup = null;
      pane.classList.remove('qlx-content--reciters');
      clear(pane);
    }

    function loading() {
      clear(stage);
      var grid = elt('div', { class: 'qlx-skeleton-grid' });
      for (var i = 0; i < 8; i++) grid.appendChild(elt('div', { class: 'qlx-skeleton' }));
      stage.appendChild(grid);
    }
    function fatal(msg) {
      clear(stage);
      stage.appendChild(elt('div', { class: 'qlx-error' }, [
        document.createTextNode(msg || t(QL + '.fatalLoad')),
        elt('button', { onclick: function () { window.location.reload(); }, text: t('common.reload') })
      ]));
    }

    /* Bootstrap chapters once; cache on window for cross-view reuse */
    loading();
    api.chapters().then(function (data) {
      window.__qlxChapters = data.chapters || [];
      clear(stage);
      layout = mountLibraryShell(stage);
      window.__qlxLayout = layout;
      SurahPicker.build(root);
      preloadReciters();
      registerRoutes();
      Router.start();
    }).catch(function (err) {
      fatal(err.message || t(QL + '.chaptersUnavailable'));
    });

    function preloadReciters() {
      api.reciters().then(function (data) {
        window.__qlxReciters = extractRecitersArray(data).map(normalizeReciter).filter(Boolean);
      }).catch(function () { window.__qlxReciters = window.__qlxReciters || []; });
    }

    function registerRoutes() {
      function pane() { return layout.content; }
      function chapters() { return window.__qlxChapters || []; }
      Router.on(/^\/$/, function () {
        disposePrev();
        publishQuranAssistantContext(null);
        qlxTopbarState.hash = '#/';
        qlxTopbarState.mode = 'library';
        topbar.highlight('#/');
        topbar.setContext('library');
        HomePanel(pane());
      });
      Router.on(/^\/surah\/(\d+)$/, function (m, q) {
        disposePrev();
        qlxTopbarState.hash = '#/';
        qlxTopbarState.mode = 'library';
        topbar.highlight('#/');
        topbar.setContext('library');
        ReaderView(pane(), api, chapters(), m[1], q || {}, audioCtl);
      });
      Router.on(/^\/reciter\/(\d+)$/, function (m) {
        disposePrev();
        qlxTopbarState.hash = '#/reciters';
        qlxTopbarState.mode = 'reciters';
        topbar.highlight('#/reciters');
        topbar.setContext('reciters');
        ReciterProfileView(pane(), api, chapters(), m[1]);
      });
      Router.on(/^\/reciters$/, function () {
        disposePrev();
        qlxTopbarState.hash = '#/reciters';
        qlxTopbarState.mode = 'reciters';
        topbar.highlight('#/reciters');
        topbar.setContext('reciters');
        RecitersView(pane(), api);
      });
      Router.on(/^\/my-quran$/, function () {
        disposePrev();
        qlxTopbarState.hash = '#/my-quran';
        qlxTopbarState.mode = 'myQuran';
        topbar.highlight('#/my-quran');
        topbar.setContext('myQuran');
        MyQuranView(pane(), api);
      });
    }
  }

  document.addEventListener('etasmi:localechange', function () {
    if (qlxTopbarState.api && typeof qlxTopbarState.api.relabel === 'function') {
      qlxTopbarState.api.relabel(qlxTopbarState.hash, qlxTopbarState.mode);
    }
    if (typeof Router.refresh === 'function') {
      Router.refresh();
    }
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
}());
