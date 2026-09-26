/**
 * e-Tasmi Quran Assistant — premium AI companion UI
 */
(function () {
  'use strict';

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  function modeSpec(id, icon) {
    return {
      id: id,
      icon: icon,
      label: t('js.assistant.modes.' + id + '.label'),
      hint: t('js.assistant.modes.' + id + '.hint'),
      prompt: t('js.assistant.modes.' + id + '.prompt'),
      typing: t('js.assistant.modes.' + id + '.typing')
    };
  }

  function buildModes() {
    return [
      modeSpec('tafsir', '📖'),
      modeSpec('tajweed', '🎙'),
      modeSpec('meaning', '🕌'),
      modeSpec('memorization', '📚'),
      modeSpec('word', '✨'),
      modeSpec('asbab', '🕋'),
      modeSpec('tips', '💡')
    ];
  }

  var MODES = buildModes();

  function typingDefault() { return t('js.assistant.thinking'); }

  function buildQuickByMode() {
    return {
      tafsir: [t('js.assistant.chips.explainDeeper'), t('js.assistant.chips.keyLessons'), t('js.assistant.chips.simplerMeaning'), t('js.assistant.chips.relatedAyahs')],
      tajweed: [t('js.assistant.chips.commonMistakes'), t('js.assistant.chips.practiceSteps'), t('js.assistant.chips.makhrajTips'), t('js.assistant.chips.slowRecitation')],
      meaning: [t('js.assistant.chips.deeperMeaning'), t('js.assistant.chips.wordByWord'), t('js.assistant.chips.surahContext'), t('js.assistant.chips.simplify')],
      memorization: [t('js.assistant.chips.memorizationPlan'), t('js.assistant.chips.similarAyahTraps'), t('js.assistant.chips.revisionSchedule'), t('js.assistant.chips.tajweedWhileMemorizing')],
      word: [t('js.assistant.chips.rootMorphology'), t('js.assistant.chips.otherQuranicUses'), t('js.assistant.chips.simplerGloss')],
      asbab: [t('js.assistant.chips.historicalContext'), t('js.assistant.chips.lessonsToday'), t('js.assistant.chips.scholarlyNote')],
      tips: [t('js.assistant.chips.dailyRoutine'), t('js.assistant.chips.reflectionMethod'), t('js.assistant.chips.connectSalah')],
      general: [t('js.assistant.chips.explainDeeper'), t('js.assistant.chips.showTajweed'), t('js.assistant.chips.memorizationTips'), t('js.assistant.chips.simplerMeaning')]
    };
  }

  function buildPills() {
    return [
      { id: 'tafsir', icon: '📖', label: t('js.assistant.pills.tafsir') },
      { id: 'tajweed', icon: '🎙', label: t('js.assistant.pills.tajweed') },
      { id: 'meaning', icon: '🕌', label: t('js.assistant.pills.meaning') },
      { id: 'tips', icon: '💡', label: t('js.assistant.pills.tips') }
    ];
  }

  var root, fab, backdrop, panel, closeBtn, minimizeBtn, homeEl, chatEl, messagesEl, quickEl, footEl;
  var formEl, inputEl, sendBtn, suggestionsEl, contextBanner, greetingEl, statusTextEl, welcomeAvatarEl;
  var headerAvatarEl, fabAvatarEl;
  var apiChat, studentProfile;
  var isOpen = false;
  var isMinimized = false;
  var currentMode = null;
  var currentTypingLabel = typingDefault();
  var history = [];
  var quranContext = null;
  var busy = false;

  function init() {
    root = document.querySelector('[data-eqa-root]');
    if (!root) return;

    apiChat = root.getAttribute('data-api-chat') || '';
    studentProfile = {
      photo: root.getAttribute('data-student-photo') || '',
      name: root.getAttribute('data-student-name') || 'You',
      initials: root.getAttribute('data-student-initials') || 'S'
    };

    fab = root.querySelector('[data-eqa-fab]');
    backdrop = root.querySelector('[data-eqa-backdrop]');
    panel = root.querySelector('[data-eqa-panel]');
    closeBtn = root.querySelector('[data-eqa-close]');
    minimizeBtn = root.querySelector('[data-eqa-minimize]');
    homeEl = root.querySelector('[data-eqa-home]');
    chatEl = root.querySelector('[data-eqa-chat]');
    messagesEl = root.querySelector('[data-eqa-messages]');
    quickEl = root.querySelector('[data-eqa-quick]');
    footEl = root.querySelector('[data-eqa-foot]');
    formEl = root.querySelector('[data-eqa-form]');
    inputEl = root.querySelector('[data-eqa-input]');
    sendBtn = root.querySelector('[data-eqa-send]');
    suggestionsEl = root.querySelector('[data-eqa-suggestions]');
    contextBanner = root.querySelector('[data-eqa-context-banner]');
    greetingEl = root.querySelector('[data-eqa-greeting-text]');
    statusTextEl = root.querySelector('[data-eqa-status-text]');
    welcomeAvatarEl = root.querySelector('[data-eqa-welcome-avatar]');
    headerAvatarEl = root.querySelector('[data-eqa-header-avatar]');
    fabAvatarEl = root.querySelector('[data-eqa-fab-avatar]');

    root.removeAttribute('hidden');
    root.setAttribute('aria-hidden', 'false');

    mountAiAvatar(welcomeAvatarEl);
    if (headerAvatarEl) headerAvatarEl.innerHTML = robotHtml();
    if (fabAvatarEl) fabAvatarEl.innerHTML = robotHtml();
    renderPrompts();
    bindEvents();
    setupDragging();
    refreshContextBanner();
    setupGreetingBubble();
    exposeBridge();
    refreshWelcomeGreeting();
    document.addEventListener('etasmi:localechange', refreshWelcomeGreeting);

    document.addEventListener('etasmi-quran-context', function (ev) {
      quranContext = ev && ev.detail ? ev.detail : null;
      refreshContextBanner();
    });
    if (window.__etasmiQuranContext) {
      quranContext = window.__etasmiQuranContext;
      refreshContextBanner();
    }
  }

  // Islamic child character avatar (kufi cap + thobe). Pure inline SVG so it
  // scales crisply and the eyes/float can be animated via CSS.
  function robotHtml() {
    return '' +
      '<svg class="eqa-ava" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false">' +
        '<defs>' +
          '<linearGradient id="eqaSkin" x1="0" y1="0" x2="0" y2="1">' +
            '<stop offset="0" stop-color="#fadcbf"/><stop offset="1" stop-color="#eebd96"/>' +
          '</linearGradient>' +
          '<linearGradient id="eqaCap" x1="0" y1="0" x2="0" y2="1">' +
            '<stop offset="0" stop-color="#ffffff"/><stop offset="1" stop-color="#e7eef0"/>' +
          '</linearGradient>' +
        '</defs>' +
        // thobe / shoulders
        '<path class="eqa-ava__thobe" d="M12 62c1.5-8.5 8.5-13 20-13s18.5 4.5 20 13z" fill="#0f766e"/>' +
        '<path d="M27.5 49l4.5 6 4.5-6z" fill="#ffffff" opacity=".92"/>' +
        // ears
        '<circle cx="15" cy="33" r="3.6" fill="url(#eqaSkin)"/>' +
        '<circle cx="49" cy="33" r="3.6" fill="url(#eqaSkin)"/>' +
        // face
        '<circle cx="32" cy="32" r="16" fill="url(#eqaSkin)"/>' +
        // kufi / taqiyah cap
        '<path class="eqa-ava__cap" d="M15 25c0-9.4 7.4-16 17-16s17 6.6 17 16z" fill="url(#eqaCap)"/>' +
        '<path d="M15 25h34" stroke="#0f766e" stroke-width="2.6" stroke-linecap="round"/>' +
        '<path d="M22 18.5q10-5 20 0" stroke="#cfdbdb" stroke-width="1.4" fill="none" opacity=".8"/>' +
        '<circle cx="32" cy="10.5" r="1.7" fill="#0f766e"/>' +
        // eyebrows
        '<path d="M23.5 28q2.5-1.6 5 0" stroke="#7a4a2b" stroke-width="1.4" fill="none" stroke-linecap="round"/>' +
        '<path d="M35.5 28q2.5-1.6 5 0" stroke="#7a4a2b" stroke-width="1.4" fill="none" stroke-linecap="round"/>' +
        // eyes (blink animated)
        '<g fill="#26313f">' +
          '<ellipse class="eqa-ava__eye" cx="26" cy="33" rx="2.3" ry="3.1"/>' +
          '<ellipse class="eqa-ava__eye" cx="38" cy="33" rx="2.3" ry="3.1"/>' +
        '</g>' +
        // cheeks
        '<circle cx="22" cy="38.5" r="2.5" fill="#f59ca0" opacity=".5"/>' +
        '<circle cx="42" cy="38.5" r="2.5" fill="#f59ca0" opacity=".5"/>' +
        // smile
        '<path class="eqa-ava__smile" d="M27 40q5 4 10 0" stroke="#a85d38" stroke-width="2" fill="none" stroke-linecap="round"/>' +
      '</svg>';
  }

  function mountAiAvatar(el) {
    if (!el) return;
    el.className = 'eqa-avatar eqa-avatar--ai';
    el.innerHTML = robotHtml();
  }

  function userAvatarHtml() {
    var img = studentProfile.photo
      ? '<img src="' + escapeAttr(studentProfile.photo) + '" alt="" width="36" height="36" loading="lazy" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\'" />'
      : '';
    return img + '<span class="eqa-avatar__fallback" style="' + (studentProfile.photo ? 'display:none' : '') + '">' +
      escapeHtml(studentProfile.initials) + '</span>';
  }

  function bindEvents() {
    fab.addEventListener('click', function () {
      if (isMinimized) {
        isMinimized = false;
        root.classList.remove('is-minimized');
      }
      openPanel();
    });
    backdrop.addEventListener('click', closePanel);
    closeBtn.addEventListener('click', closePanel);
    minimizeBtn.addEventListener('click', minimizePanel);

    document.addEventListener('keydown', function (ev) {
      if (ev.key === 'Escape' && isOpen && !isMinimized) closePanel();
    });

    formEl.addEventListener('submit', function (ev) {
      ev.preventDefault();
      submitMessage();
    });

    inputEl.addEventListener('keydown', function (ev) {
      if (ev.key === 'Enter' && !ev.shiftKey) {
        ev.preventDefault();
        submitMessage();
      }
    });

    inputEl.addEventListener('input', autoResizeInput);
  }

  function autoResizeInput() {
    inputEl.style.height = 'auto';
    inputEl.style.height = Math.min(inputEl.scrollHeight, 88) + 'px';
  }

  /* ------------------------------------------------------------------
     Lightweight, dependency-free drag-and-drop for the FAB and panel.
     Supports mouse + touch, clamps to the viewport, and distinguishes a
     click (open) from a drag (reposition) using a distance threshold.
     ------------------------------------------------------------------ */
  var DRAG_MARGIN = 8;
  var DRAG_THRESHOLD = 6;

  function setupDragging() {
    var handles = root.querySelectorAll('[data-eqa-drag-handle]');
    for (var i = 0; i < handles.length; i++) {
      var handle = handles[i];
      var target = handle.getAttribute('data-eqa-drag-handle') === 'panel' ? panel : fab;
      if (target) makeDraggable(handle, target);
    }
    window.addEventListener('resize', function () {
      reclamp(fab);
      reclamp(panel);
    });
  }

  function makeDraggable(handle, target) {
    var startX = 0, startY = 0, baseLeft = 0, baseTop = 0;
    var pointerDown = false, dragging = false;

    function point(e) {
      if (e.touches && e.touches.length) return { x: e.touches[0].clientX, y: e.touches[0].clientY };
      if (e.changedTouches && e.changedTouches.length) return { x: e.changedTouches[0].clientX, y: e.changedTouches[0].clientY };
      return { x: e.clientX, y: e.clientY };
    }

    function onDown(e) {
      if (e.type === 'mousedown' && e.button !== 0) return;
      if (e.target.closest && e.target.closest('.eqa-panel__controls, [data-eqa-no-drag]')) return;
      // Keep the press scoped to the widget so it can't start a background
      // text-selection or bubble to document-level handlers.
      e.stopPropagation();
      var p = point(e);
      var rect = target.getBoundingClientRect();
      startX = p.x; startY = p.y;
      baseLeft = rect.left; baseTop = rect.top;
      pointerDown = true; dragging = false;
      document.addEventListener('mousemove', onMove, { passive: false });
      document.addEventListener('mouseup', onUp);
      document.addEventListener('touchmove', onMove, { passive: false });
      document.addEventListener('touchend', onUp);
      document.addEventListener('touchcancel', onUp);
    }

    function onMove(e) {
      if (!pointerDown) return;
      var p = point(e);
      var dx = p.x - startX, dy = p.y - startY;
      if (!dragging) {
        if (Math.sqrt(dx * dx + dy * dy) < DRAG_THRESHOLD) return;
        dragging = true;
        target.classList.add('is-dragging');
        root.classList.add('eqa-dragging');
      }
      // Block page scroll (touch) and text-selection (mouse) while dragging,
      // and stop the move from reaching the underlying dashboard.
      if (e.cancelable) e.preventDefault();
      e.stopPropagation();
      var w = target.offsetWidth, h = target.offsetHeight;
      var nx = clampNum(baseLeft + dx, DRAG_MARGIN, window.innerWidth - w - DRAG_MARGIN);
      var ny = clampNum(baseTop + dy, DRAG_MARGIN, window.innerHeight - h - DRAG_MARGIN);
      applyPos(target, nx, ny);
    }

    function onUp() {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.removeEventListener('touchmove', onMove);
      document.removeEventListener('touchend', onUp);
      document.removeEventListener('touchcancel', onUp);
      pointerDown = false;
      target.classList.remove('is-dragging');
      root.classList.remove('eqa-dragging');
      if (dragging) {
        dragging = false;
        suppressNextClick(handle);
      }
    }

    handle.addEventListener('mousedown', onDown);
    handle.addEventListener('touchstart', onDown, { passive: true });
  }

  function applyPos(el, left, top) {
    el.style.left = left + 'px';
    el.style.top = top + 'px';
    el.style.right = 'auto';
    el.style.bottom = 'auto';
    el.setAttribute('data-eqa-moved', '1');
  }

  function reclamp(el) {
    if (!el || el.getAttribute('data-eqa-moved') !== '1' || el.hidden) return;
    var w = el.offsetWidth, h = el.offsetHeight;
    var rect = el.getBoundingClientRect();
    applyPos(el,
      clampNum(rect.left, DRAG_MARGIN, window.innerWidth - w - DRAG_MARGIN),
      clampNum(rect.top, DRAG_MARGIN, window.innerHeight - h - DRAG_MARGIN));
  }

  function clampNum(v, min, max) {
    if (max < min) max = min;
    return Math.max(min, Math.min(max, v));
  }

  // Cancel the synthetic click that follows a mouse drag so the FAB doesn't
  // toggle open after the user merely repositioned it.
  function suppressNextClick(el) {
    var handler = function (ev) {
      ev.stopPropagation();
      ev.preventDefault();
      el.removeEventListener('click', handler, true);
    };
    el.addEventListener('click', handler, true);
    setTimeout(function () { el.removeEventListener('click', handler, true); }, 400);
  }

  function studentFirstName() {
    var n = (studentProfile && studentProfile.name ? studentProfile.name : '').trim();
    if (!n) return '';
    var lower = n.toLowerCase();
    if (lower === 'you' || lower === 'student') return '';
    return n.split(/\s+/)[0];
  }

  function compactGreetingFallback(firstName) {
    return firstName
      ? ('Assalamu Alaikum, ' + firstName + ' \uD83D\uDC4B')
      : 'Assalamu Alaikum \uD83D\uDC4B';
  }

  function isRawAssistantKey(value) {
    return !value || String(value).indexOf('quranAssistant.') === 0;
  }

  function welcomeGreeting() {
    var fn = studentFirstName();
    if (window.EtasmiI18n && EtasmiI18n.isReady && !EtasmiI18n.isReady()) {
      return compactGreetingFallback(fn);
    }
    var value = fn
      ? t('quranAssistant.greeting', { name: fn })
      : t('quranAssistant.greetingPlain');
    if (isRawAssistantKey(value)) {
      return compactGreetingFallback(fn);
    }
    return value;
  }

  function refreshWelcomeGreeting() {
    if (!greetingEl) return;
    greetingEl.textContent = welcomeGreeting();
  }

  function playGreetingOnOpen() {
    if (!greetingEl) return;
    var full = welcomeGreeting();
    greetingEl.textContent = '';
    var i = 0;
    clearInterval(root._eqaGreetingTimer);
    root._eqaGreetingTimer = setInterval(function () {
      greetingEl.textContent = full.slice(0, i);
      i++;
      if (i > full.length) clearInterval(root._eqaGreetingTimer);
    }, 14);
  }

  function renderPrompts() {
    if (!suggestionsEl) return;
    suggestionsEl.innerHTML = '';
    buildPills().forEach(function (pill) {
      var mode = findMode(pill.id);
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'eqa-pill';
      btn.setAttribute('role', 'listitem');
      btn.innerHTML =
        '<span class="eqa-pill__icon" aria-hidden="true">' + pill.icon + '</span>' +
        '<span class="eqa-pill__label">' + escapeHtml(pill.label) + '</span>';
      btn.addEventListener('click', function () { usePill(mode, pill.label); });
      suggestionsEl.appendChild(btn);
    });
  }

  // Clicking a pill activates its mode and drops the topic into the input,
  // ready for the student to refine and send (input-first behaviour).
  function usePill(mode, label) {
    var text = label + ': ';
    if (currentMode === mode.id) {
      fillInput(text);
      return;
    }
    homeEl.classList.add('is-leaving');
    setStatus(t('js.assistant.onlineMode', { mode: mode.label }));
    setTimeout(function () {
      startMode(mode);
      homeEl.classList.remove('is-leaving');
      fillInput(text);
    }, 280);
  }

  function fillInput(text) {
    inputEl.value = text;
    autoResizeInput();
    inputEl.focus();
    var len = inputEl.value.length;
    try { inputEl.setSelectionRange(len, len); } catch (e) {}
  }

  function transitionToMode(mode, prefill) {
    homeEl.classList.add('is-leaving');
    setStatus(t('js.assistant.onlineMode', { mode: mode.label }));
    setTimeout(function () {
      startMode(mode, prefill);
      homeEl.classList.remove('is-leaving');
    }, 280);
  }

  function refreshContextBanner() {
    if (!contextBanner) return;
    var ctx = quranContext || window.__etasmiQuranContext;
    if (!ctx || !ctx.surahId) {
      contextBanner.hidden = true;
      contextBanner.innerHTML = '';
      return;
    }
    contextBanner.hidden = false;
    var surahLabel = ctx.surahName
      ? t('js.assistant.surahLabel', { name: ctx.surahName })
      : t('js.assistant.surahNumber', { id: ctx.surahId });
    var ayahPart = ctx.verseKey ? t('js.assistant.ayahLabel', { number: ctx.verseKey.split(':')[1] }) : '';
    contextBanner.innerHTML =
      '<strong>' + escapeHtml(t('js.assistant.readingNow')) + '</strong>' +
      escapeHtml(surahLabel + ayahPart) +
      '<div class="eqa-context-actions">' +
      '<button type="button" data-eqa-ctx-surah>' + escapeHtml(t('js.assistant.explainSurah')) + '</button>' +
      (ctx.verseKey ? '<button type="button" data-eqa-ctx-ayah>' + escapeHtml(t('js.assistant.explainAyah')) + '</button>' : '') +
      '</div>';

    contextBanner.querySelector('[data-eqa-ctx-surah]').addEventListener('click', function () {
      transitionToMode(findMode('meaning'), t('js.assistant.explainSurahPrompt', { name: ctx.surahName || ctx.surahId }));
    });
    if (ctx.verseKey) {
      contextBanner.querySelector('[data-eqa-ctx-ayah]').addEventListener('click', function () {
        transitionToMode(findMode('meaning'), t('js.assistant.explainAyahPrompt', { key: ctx.verseKey }));
      });
    }
  }

  function findMode(id) {
    MODES = buildModes();
    for (var i = 0; i < MODES.length; i++) {
      if (MODES[i].id === id) return MODES[i];
    }
    return MODES[0];
  }

  function setStatus(text) {
    if (!statusTextEl) return;
    statusTextEl.innerHTML = '<span class="eqa-panel__status-pulse" aria-hidden="true"></span> ' + escapeHtml(text);
  }

  function openPanel() {
    isOpen = true;
    isMinimized = false;
    root.classList.add('is-open');
    root.classList.remove('is-minimized');
    panel.hidden = false;
    backdrop.hidden = false;
    refreshContextBanner();
    if (!currentMode) {
      showHome();
      playGreetingOnOpen();
      setStatus(t('js.assistant.onlineReady'));
    } else {
      setStatus(t('js.assistant.onlineMode', { mode: findMode(currentMode).label || 'Chat' }));
    }
    requestAnimationFrame(function () {
      if (inputEl) inputEl.focus();
    });
  }

  function closePanel() {
    exitVoiceMode();
    isOpen = false;
    isMinimized = false;
    root.classList.remove('is-open', 'is-minimized', 'is-voice');
    panel.hidden = true;
    backdrop.hidden = true;
  }

  function minimizePanel() {
    exitVoiceMode();
    isMinimized = true;
    root.classList.add('is-minimized');
  }

  // Fully tear down the optional voice overlay (stops mic + hides it) so it
  // never lingers behind the chat or re-appears automatically on reopen.
  function exitVoiceMode() {
    if (window.EtasmiQuranVoice && typeof window.EtasmiQuranVoice.exit === 'function') {
      try { window.EtasmiQuranVoice.exit(); } catch (e) {}
    }
  }

  function showHome() {
    homeEl.hidden = false;
    chatEl.hidden = true;
    quickEl.hidden = true;
    currentMode = null;
  }

  function showChat() {
    homeEl.hidden = true;
    chatEl.hidden = false;
  }

  function startMode(mode, prefill) {
    currentMode = mode.id;
    currentTypingLabel = mode.typing || typingDefault();
    history = [];
    messagesEl.innerHTML = '';
    showChat();
    addMessage('assistant', mode.prompt, null, { isSystem: true });
    inputEl.placeholder = mode.id === 'word'
      ? t('js.assistant.wordPlaceholder')
      : t('js.assistant.referencePlaceholder');
    var quick = buildQuickByMode();
    renderQuickChips(quick[mode.id] || quick.general, true);

    if (prefill) {
      submitMessage(prefill);
    } else {
      inputEl.value = '';
      autoResizeInput();
      inputEl.focus();
    }
  }

  function nowTime() {
    var d = new Date();
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  function addMessage(role, text, html, opts) {
    opts = opts || {};
    var isUser = role === 'user';
    var isAssistant = role === 'assistant' || (role === 'system' && opts.isSystem);
    var wrap = document.createElement('div');
    wrap.className = 'eqa-msg eqa-msg--' + (isUser ? 'user' : (opts.isSystem ? 'system' : 'assistant'));

    var avatar = document.createElement('div');
    avatar.className = 'eqa-avatar eqa-avatar--' + (isUser ? 'user' : 'ai');
    avatar.setAttribute('aria-hidden', 'true');
    if (isUser) {
      avatar.innerHTML = userAvatarHtml();
    } else {
      avatar.innerHTML = robotHtml();
    }

    var content = document.createElement('div');
    content.className = 'eqa-msg__content';

    if (!opts.isSystem) {
      var meta = document.createElement('div');
      meta.className = 'eqa-msg__meta';
      meta.innerHTML =
        '<span class="eqa-msg__name">' + escapeHtml(isUser ? studentProfile.name : t('js.assistant.quranAi')) + '</span>' +
        '<span class="eqa-msg__time">' + nowTime() + '</span>';
      content.appendChild(meta);
    }

    var bubble = document.createElement('div');
    bubble.className = 'eqa-msg__bubble';
    if (html) bubble.innerHTML = html;
    else bubble.textContent = text;
    content.appendChild(bubble);

    wrap.appendChild(avatar);
    wrap.appendChild(content);
    messagesEl.appendChild(wrap);
    scrollMessages();
    return wrap;
  }

  function scrollMessages() {
    requestAnimationFrame(function () {
      messagesEl.scrollTop = messagesEl.scrollHeight;
      var body = root.querySelector('[data-eqa-body]');
      if (body) body.scrollTop = body.scrollHeight;
    });
  }

  function showTyping() {
    var wrap = document.createElement('div');
    wrap.className = 'eqa-msg eqa-msg--assistant eqa-msg--typing';
    wrap.setAttribute('data-eqa-typing', '1');

    var avatar = document.createElement('div');
    avatar.className = 'eqa-avatar eqa-avatar--ai';
    avatar.innerHTML = robotHtml();

    var content = document.createElement('div');
    content.className = 'eqa-msg__content';
    content.innerHTML =
      '<div class="eqa-msg__meta">' +
      '<span class="eqa-msg__name">' + escapeHtml(t('js.assistant.quranAi')) + '</span>' +
      '</div>' +
      '<div class="eqa-msg__bubble">' +
      '<span class="eqa-typing-status">' + escapeHtml(currentTypingLabel) + '</span>' +
      '<span class="eqa-typing" aria-label="' + escapeAttr(t('js.assistant.assistantThinking')) + '">' +
      '<span></span><span></span><span></span></span></div>';

    wrap.appendChild(avatar);
    wrap.appendChild(content);
    messagesEl.appendChild(wrap);
    setStatus(currentTypingLabel);
    scrollMessages();
    return wrap;
  }

  function removeTyping(node) {
    if (node && node.parentNode) node.parentNode.removeChild(node);
    if (currentMode) {
      setStatus(t('js.assistant.onlineMode', { mode: findMode(currentMode).label || 'Chat' }));
    }
  }

  function submitMessage(optionalText) {
    if (busy) return;
    var text = (optionalText != null ? optionalText : inputEl.value).trim();
    if (!text) return;

    if (!currentMode) transitionToMode(findMode('tips'));

    addMessage('user', text);
    inputEl.value = '';
    autoResizeInput();

    history.push({ role: 'user', content: text });

    var typingNode = showTyping();
    busy = true;
    sendBtn.disabled = true;

    fetch(apiChat, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        'Accept': 'application/json',
        'X-Requested-With': 'XMLHttpRequest'
      },
      body: JSON.stringify({
        mode: currentMode || 'general',
        message: text,
        history: history.slice(0, -1),
        context: buildContextPayload()
      })
    })
      .then(function (res) { return res.json().then(function (data) { return { res: res, data: data }; }); })
      .then(function (out) {
        removeTyping(typingNode);
        if (!out.res.ok || !out.data || !out.data.ok) {
          var msg = (out.data && out.data.message) ? out.data.message : t('js.assistant.somethingWrong');
          addMessage('assistant', msg, null, { isSystem: true });
          return;
        }
        addMessage('assistant', '', formatReply(out.data.reply || ''));
        history.push({ role: 'assistant', content: out.data.reply || '' });
        var quickAfter = buildQuickByMode();
        renderQuickChips(quickAfter[currentMode] || quickAfter.general, false);
        inputEl.focus();
      })
      .catch(function () {
        removeTyping(typingNode);
        addMessage('assistant', t('js.assistant.networkError'), null, { isSystem: true });
      })
      .finally(function () {
        busy = false;
        sendBtn.disabled = false;
      });
  }

  function buildContextPayload() {
    var ctx = quranContext || window.__etasmiQuranContext;
    if (!ctx || !ctx.surahId) return null;
    return {
      surahId: ctx.surahId,
      surahName: ctx.surahName || null,
      surahNameArabic: ctx.surahNameArabic || null,
      verseKey: ctx.verseKey || null,
      ayahNumber: ctx.ayahNumber || 0
    };
  }

  function renderQuickChips(chips, hideUntilReply) {
    quickEl.innerHTML = '';
    if (hideUntilReply) {
      quickEl.hidden = true;
      return;
    }
    quickEl.hidden = false;
    var all = chips.slice();
    var newTopicLabel = t('js.assistant.newTopic');
    all.push(newTopicLabel);
    all.forEach(function (label) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'eqa-chip' + (label === newTopicLabel ? ' eqa-chip--ghost' : '');
      btn.textContent = label;
      btn.addEventListener('click', function () {
        if (label === newTopicLabel) {
          currentMode = null;
          history = [];
          showHome();
          playGreetingOnOpen();
          setStatus(t('js.assistant.onlineReady'));
          return;
        }
        submitMessage(mapChipToPrompt(label));
      });
      quickEl.appendChild(btn);
    });
  }

  function mapChipToPrompt(label) {
    var ctx = quranContext || window.__etasmiQuranContext;
    var ref = ctx && ctx.verseKey ? ctx.verseKey : t('js.assistant.chipPrompts.ayahDiscussed');
    var quick = buildQuickByMode();
    var chipKeys = {
      [quick.tafsir[0]]: 'js.assistant.chipPrompts.explainDeeper',
      [quick.general[3]]: 'js.assistant.chipPrompts.simplerMeaning',
      [quick.general[1]]: 'js.assistant.chipPrompts.showTajweed',
      [quick.general[2]]: 'js.assistant.chipPrompts.memorizationTips',
      [quick.tafsir[1]]: 'js.assistant.chipPrompts.keyLessons',
      [quick.tafsir[3]]: 'js.assistant.chipPrompts.relatedAyahs',
      [quick.meaning[1]]: 'js.assistant.chipPrompts.wordByWord',
      [quick.meaning[0]]: 'js.assistant.chipPrompts.deeperMeaning'
    };
    if (chipKeys[label]) {
      return t(chipKeys[label], { ref: ref });
    }
    return t('js.assistant.chipPrompts.forRef', { label: label, ref: ref });
  }

  function formatReply(md) {
    if (!md) return '';
    var lines = String(md).split('\n');
    var html = [];
    var inList = false;

    function closeList() {
      if (inList) { html.push('</ul>'); inList = false; }
    }

    lines.forEach(function (line) {
      var trimmed = line.trim();
      if (!trimmed) { closeList(); return; }
      if (/^###?\s+/.test(trimmed)) {
        closeList();
        html.push('<h3>' + inlineFormat(trimmed.replace(/^###?\s+/, '')) + '</h3>');
        return;
      }
      if (/^[-*]\s+/.test(trimmed)) {
        if (!inList) { html.push('<ul>'); inList = true; }
        html.push('<li>' + inlineFormat(trimmed.replace(/^[-*]\s+/, '')) + '</li>');
        return;
      }
      closeList();
      if (isMostlyArabic(trimmed)) {
        html.push('<p class="eqa-arabic" dir="rtl" lang="ar">' + inlineFormat(trimmed) + '</p>');
      } else {
        html.push('<p>' + inlineFormat(trimmed) + '</p>');
      }
    });
    closeList();
    return '<div class="eqa-reply">' + html.join('') + '</div>';
  }

  function isMostlyArabic(s) {
    var ar = 0, total = 0;
    for (var i = 0; i < s.length; i++) {
      if (s.charCodeAt(i) >= 0x0600 && s.charCodeAt(i) <= 0x06FF) ar++;
      if (!/\s/.test(s.charAt(i))) total++;
    }
    return total > 0 && ar / total > 0.45;
  }

  function inlineFormat(s) {
    return escapeHtml(s)
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>');
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function escapeAttr(s) {
    return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
  }

  function onLocaleChange() {
    MODES = buildModes();
    renderPrompts();
    refreshContextBanner();
    if (!currentMode && isOpen) {
      playGreetingOnOpen();
      setStatus(t('js.assistant.onlineReady'));
    } else if (currentMode) {
      setStatus(t('js.assistant.onlineMode', { mode: findMode(currentMode).label || 'Chat' }));
      var quick = buildQuickByMode();
      renderQuickChips(quick[currentMode] || quick.general, busy);
    }
  }

  document.addEventListener('etasmi:localechange', onLocaleChange);

  /* ------------------------------------------------------------------
     Auto welcome greeting bubble (dashboard only, time-of-day aware).
     Friendly, professional, non-intrusive; auto-dismisses after a few
     seconds or when the student opens the assistant.
     ------------------------------------------------------------------ */
  function timeOfDayKey() {
    var h = new Date().getHours();
    if (h < 12) return 'morning';
    if (h < 18) return 'afternoon';
    return 'evening';
  }

  function setupGreetingBubble() {
    var bubble = root.querySelector('[data-eqa-greet-bubble]');
    if (!bubble) return;

    var textEl = bubble.querySelector('[data-eqa-greet-text]');
    var avatarEl = bubble.querySelector('[data-eqa-greet-avatar]');
    var closeBtn = bubble.querySelector('[data-eqa-greet-close]');
    var openBtn = bubble.querySelector('[data-eqa-greet-open]');
    if (avatarEl) avatarEl.innerHTML = robotHtml();

    var hideTimer = null;
    function fillGreeting() {
      if (!textEl) return;
      textEl.textContent = welcomeGreeting();
    }
    function dismiss() {
      clearTimeout(hideTimer);
      bubble.classList.remove('is-visible');
      setTimeout(function () { bubble.hidden = true; }, 320);
    }

    if (closeBtn) closeBtn.addEventListener('click', dismiss);
    if (openBtn) openBtn.addEventListener('click', function () {
      dismiss();
      if (isMinimized) { isMinimized = false; root.classList.remove('is-minimized'); }
      openPanel();
    });
    // Opening the panel by any means should retire the greeting.
    if (fab) fab.addEventListener('click', dismiss);

    // Only greet on the dashboard, once per browser session, and never while open.
    var onDashboard = document.body && document.body.classList.contains('student-dashboard-page');
    var alreadyGreeted = false;
    try { alreadyGreeted = sessionStorage.getItem('etasmi.eqa.greeted') === '1'; } catch (e) {}
    if (!onDashboard || alreadyGreeted) return;

    fillGreeting();
    setTimeout(function () {
      if (isOpen) return;
      bubble.hidden = false;
      requestAnimationFrame(function () { bubble.classList.add('is-visible'); });
      try { sessionStorage.setItem('etasmi.eqa.greeted', '1'); } catch (e) {}
      hideTimer = setTimeout(dismiss, 6000);
    }, 1400);

    document.addEventListener('etasmi:localechange', function () {
      if (!bubble.hidden) fillGreeting();
    });
  }

  /* ------------------------------------------------------------------
     Bridge consumed by quran-voice.js so the voice layer can reuse the
     shared conversation history, mode, context and rendering helpers.
     ------------------------------------------------------------------ */
  function exposeBridge() {
    window.EtasmiQuranAssistant = {
      root: root,
      apiVoiceTurn: root.getAttribute('data-api-voice-turn') || '',
      apiVoiceSpeak: root.getAttribute('data-api-voice-speak') || '',
      t: t,
      formatReply: formatReply,
      setStatus: setStatus,
      buildContextPayload: buildContextPayload,
      getMode: function () { return currentMode || 'general'; },
      getHistory: function () { return history; },
      pushHistory: function (role, content) { history.push({ role: role, content: content }); },
      addMessage: function (role, text, html, opts) { return addMessage(role, text, html, opts); },
      formatAndAddAssistant: function (reply) {
        showChat();
        return addMessage('assistant', '', formatReply(reply || ''));
      },
      addUser: function (text) {
        showChat();
        return addMessage('user', text);
      },
      revealChat: function () { showChat(); },
      isOpen: function () { return isOpen; },
      openPanel: openPanel,
      robotHtml: robotHtml
    };
    document.dispatchEvent(new CustomEvent('etasmi-quran-assistant-ready'));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
}());
