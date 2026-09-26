/* =====================================================================
 * e-Tasmi · Recitation Studio (Session-first)
 *
 * Flow:
 *   1) Student picks an enrolled session from a searchable selector.
 *   2) A summary card + two action cards (Record / Upload) are revealed.
 *   3) The recording or uploaded file is submitted, linked to that
 *      session's enrollmentId.
 *   4) Recent submissions open a detailed evaluation report.
 *
 * Backend contract (StudentRecitationServlet) is unchanged:
 *   POST {endpoint} (multipart, ajax=1)  → { ok, redirect } | { ok:false, error }
 *     fields: enrollmentId, audio (file), duration?
 * ===================================================================== */
(function () {
  'use strict';

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  var root = document.querySelector('[data-rec-root]');
  if (!root) return;
  var ENDPOINT = root.getAttribute('data-rec-endpoint');
  var $ = function (sel, ctx) { return (ctx || document).querySelector(sel); };

  /* refs: selector + summary */
  var select = $('#recSelect');
  var selectTrigger = $('[data-rec-select-trigger]');
  var selectPanel = $('#recSelectPanel');
  var selectLabel = $('#recSelectLabel');
  var summary = $('#recSummary');
  var infoInstructor = $('#recInfoInstructor');
  var infoSchedule = $('#recInfoSchedule');
  var infoMode = $('#recInfoMode');
  var infoPortion = $('#recInfoPortion');
  var step2 = $('#recStep2');

  /* refs: upload */
  var uploadModal = $('#recUploadModal');
  var uploadFor = $('#recUploadFor');
  var fileEl = $('#recFile');
  var fileHint = $('#recFileHint');
  var dropzone = $('#recDropzone');
  var uploadSubmitBtn = $('#recUploadSubmitBtn');
  var uploadStatus = $('#recUploadStatus');

  /* refs: studio */
  var studio = $('#recStudio');
  var studioFor = $('#recStudioFor');
  var orb = $('#recOrb');
  var canvas = $('#recWave');
  var timerEl = $('#recTimer');
  var elapsedEl = $('#recElapsed');
  var pauseBtn = $('#recPauseBtn');
  var pauseLabel = $('#recPauseLabel');
  var pauseGlyph = $('#recPauseGlyph');
  var review = $('#recReview');
  var playback = $('#recPlayback');
  var reviewMeta = $('#recReviewMeta');
  var submitBtn = $('#recSubmitBtn');
  var submitStatus = $('#recSubmitStatus');

  /* refs: feedback */
  var fbModal = $('#recFeedbackModal');

  var ICON_PAUSE = '<svg viewBox="0 0 24 24" fill="none"><rect x="7" y="5" width="3.5" height="14" rx="1" fill="currentColor"/><rect x="13.5" y="5" width="3.5" height="14" rx="1" fill="currentColor"/></svg>';
  var ICON_PLAY = '<svg viewBox="0 0 24 24" fill="none"><path d="M8 5.5v13l11-6.5-11-6.5Z" fill="currentColor"/></svg>';

  /* state */
  var sel = { enrollmentId: '', title: '' };
  var rec = {
    stream: null, recorder: null, chunks: [], blob: null,
    mimeType: 'audio/webm', elapsed: 0, tick: null, paused: false,
    audioCtx: null, analyser: null, raf: null
  };

  /* ---------- helpers ---------- */
  function fmt(t) { t = Math.max(0, Math.floor(t)); var m = Math.floor(t / 60), s = t % 60; return m + ':' + (s < 10 ? '0' + s : s); }
  function supportsRecording() { return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && typeof MediaRecorder !== 'undefined'); }
  function pickMimeType() {
    if (typeof MediaRecorder === 'undefined' || !MediaRecorder.isTypeSupported) return '';
    var prefs = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];
    for (var i = 0; i < prefs.length; i++) { if (MediaRecorder.isTypeSupported(prefs[i])) return prefs[i]; }
    return '';
  }
  function post(form) {
    return fetch(ENDPOINT, { method: 'POST', body: form, credentials: 'same-origin' })
      .then(function (res) { return res.json().catch(function () { return { ok: false, error: t('errors.unexpectedResponse') }; }); });
  }
  function setCell(el, value) {
    if (!el) return;
    el.textContent = (value && String(value).trim()) ? value : '-';
  }
  function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  function lockScroll(on) { document.body.style.overflow = on ? 'hidden' : ''; }

  /* ===================================================================
   * Step 1: session selector (simple dropdown — no search)
   * ================================================================= */
  var options = Array.prototype.slice.call(document.querySelectorAll('[data-rec-session]'));

  function isOpen() { return selectPanel && !selectPanel.hidden; }
  function openPanel() {
    if (!selectPanel) return;
    selectPanel.hidden = false;
    if (selectTrigger) selectTrigger.setAttribute('aria-expanded', 'true');
    if (select) select.classList.add('is-open');
  }
  function closePanel() {
    if (!selectPanel) return;
    selectPanel.hidden = true;
    if (selectTrigger) selectTrigger.setAttribute('aria-expanded', 'false');
    if (select) select.classList.remove('is-open');
  }

  if (selectTrigger) selectTrigger.addEventListener('click', function (e) {
    e.stopPropagation();
    if (isOpen()) closePanel(); else openPanel();
  });
  document.addEventListener('click', function (e) {
    if (isOpen() && select && !select.contains(e.target)) closePanel();
  });

  options.forEach(function (o) { o.addEventListener('click', function () { selectSession(o); }); });

  function selectSession(option) {
    options.forEach(function (o) { o.classList.remove('is-selected'); o.setAttribute('aria-selected', 'false'); });
    option.classList.add('is-selected');
    option.setAttribute('aria-selected', 'true');

    sel.enrollmentId = option.getAttribute('data-enrollment-id') || '';
    sel.title = option.getAttribute('data-title') || t('common.session');

    if (selectLabel) { selectLabel.textContent = sel.title; selectLabel.classList.add('is-chosen'); }
    setCell(infoInstructor, option.getAttribute('data-instructor'));
    setCell(infoSchedule, option.getAttribute('data-schedule'));
    setCell(infoMode, option.getAttribute('data-mode'));
    setCell(infoPortion, option.getAttribute('data-portion'));
    if (summary) summary.hidden = false;

    closePanel();
    if (step2) {
      step2.hidden = false;
      step2.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }

  function requireSession() {
    if (!sel.enrollmentId) { alert(t('student.recitations.selectSessionFirst')); if (selectTrigger) selectTrigger.focus(); return false; }
    return true;
  }

  /* ===================================================================
   * Upload flow
   * ================================================================= */
  document.querySelectorAll('[data-rec-upload]').forEach(function (el) {
    el.addEventListener('click', function () {
      if (!requireSession()) return;
      if (uploadFor) uploadFor.textContent = t('student.recitations.forSession', { name: sel.title });
      if (uploadStatus) { uploadStatus.textContent = ''; uploadStatus.classList.remove('is-error'); }
      if (uploadSubmitBtn) { uploadSubmitBtn.disabled = true; uploadSubmitBtn.textContent = t('student.recitations.submitRecitation'); }
      if (fileEl) fileEl.value = '';
      if (fileHint) fileHint.textContent = t('student.recitations.fileFormatsHint');
      uploadModal.hidden = false;
      lockScroll(true);
    });
  });
  document.querySelectorAll('[data-rec-modal-dismiss]').forEach(function (el) {
    el.addEventListener('click', function () { uploadModal.hidden = true; lockScroll(false); });
  });
  if (fileEl) fileEl.addEventListener('change', function () {
    var has = fileEl.files && fileEl.files.length > 0;
    uploadSubmitBtn.disabled = !has;
    if (has && fileHint) {
      var f = fileEl.files[0];
      fileHint.textContent = f.name + '  -  ' + (f.size / (1024 * 1024)).toFixed(2) + ' MB';
    }
  });
  if (dropzone && fileEl) {
    ['dragenter', 'dragover'].forEach(function (ev) {
      dropzone.addEventListener(ev, function (e) { e.preventDefault(); dropzone.classList.add('is-drag'); });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      dropzone.addEventListener(ev, function (e) { e.preventDefault(); dropzone.classList.remove('is-drag'); });
    });
    dropzone.addEventListener('drop', function (e) {
      var files = e.dataTransfer && e.dataTransfer.files;
      if (!files || !files.length) return;
      try { var dt = new DataTransfer(); dt.items.add(files[0]); fileEl.files = dt.files; fileEl.dispatchEvent(new Event('change', { bubbles: true })); } catch (err) {}
    });
  }
  if (uploadSubmitBtn) uploadSubmitBtn.addEventListener('click', function () {
    if (!fileEl || !fileEl.files || !fileEl.files.length || !requireSession()) return;
    uploadSubmitBtn.disabled = true;
    uploadSubmitBtn.textContent = t('student.recitations.uploading');
    if (uploadStatus) uploadStatus.classList.remove('is-error');

    var form = new FormData();
    form.append('ajax', '1');
    form.append('enrollmentId', sel.enrollmentId);
    form.append('audio', fileEl.files[0]);

    post(form).then(function (data) {
      if (data && data.ok) { window.location.href = data.redirect || (ENDPOINT + '?submitted=1'); }
      else {
        uploadSubmitBtn.disabled = false; uploadSubmitBtn.textContent = t('student.recitations.submitRecitation');
        if (uploadStatus) { uploadStatus.classList.add('is-error'); uploadStatus.textContent = (data && data.error) || t('student.recitations.uploadFailed'); }
      }
    }).catch(function () {
      uploadSubmitBtn.disabled = false; uploadSubmitBtn.textContent = t('student.recitations.submitRecitation');
      if (uploadStatus) { uploadStatus.classList.add('is-error'); uploadStatus.textContent = t('errors.networkError'); }
    });
  });

  /* ===================================================================
   * Recording flow
   * ================================================================= */
  document.querySelectorAll('[data-rec-start]').forEach(function (el) {
    el.addEventListener('click', function () {
      if (!requireSession()) return;
      if (!supportsRecording()) {
        alert(t('student.recitations.recordingNotSupported'));
        return;
      }
      el.disabled = true;
      navigator.mediaDevices.getUserMedia({ audio: true })
        .then(function (stream) { rec.stream = stream; el.disabled = false; launchStudio(); })
        .catch(function (err) {
          el.disabled = false;
          alert(err && err.name === 'NotAllowedError'
            ? t('student.recitations.micBlocked')
            : t('student.recitations.recordingFailed'));
        });
    });
  });

  function launchStudio() {
    rec.chunks = []; rec.blob = null; rec.elapsed = 0; rec.paused = false;
    rec.mimeType = pickMimeType();
    if (studioFor) studioFor.textContent = t('student.recitations.recordingFor', { name: sel.title });
    timerEl.textContent = '0:00'; elapsedEl.textContent = '0:00';
    setPausedUi(false);
    studio.hidden = false; review.hidden = true;
    lockScroll(true);

    try {
      rec.recorder = rec.mimeType ? new MediaRecorder(rec.stream, { mimeType: rec.mimeType }) : new MediaRecorder(rec.stream);
    } catch (e) { rec.recorder = new MediaRecorder(rec.stream); }
    rec.recorder.ondataavailable = function (e) { if (e.data && e.data.size > 0) rec.chunks.push(e.data); };
    rec.recorder.onstop = handleStop;
    rec.recorder.start(250);
    startTimer();
    startVisualizer();
  }

  function startTimer() {
    stopTimer();
    rec.tick = setInterval(function () {
      if (rec.paused) return;
      rec.elapsed += 1;
      timerEl.textContent = fmt(rec.elapsed);
      elapsedEl.textContent = fmt(rec.elapsed);
    }, 1000);
  }
  function stopTimer() { if (rec.tick) { clearInterval(rec.tick); rec.tick = null; } }

  function startVisualizer() {
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      rec.audioCtx = new AC();
      var src = rec.audioCtx.createMediaStreamSource(rec.stream);
      rec.analyser = rec.audioCtx.createAnalyser();
      rec.analyser.fftSize = 256;
      src.connect(rec.analyser);
    } catch (e) { rec.analyser = null; }
    drawWave();
  }
  function drawWave() {
    var ctx = canvas.getContext('2d');
    var len = rec.analyser ? rec.analyser.frequencyBinCount : 64;
    var data = new Uint8Array(len);
    function frame() {
      rec.raf = requestAnimationFrame(frame);
      var w = canvas.width, h = canvas.height;
      ctx.clearRect(0, 0, w, h);
      if (rec.analyser && !rec.paused) rec.analyser.getByteFrequencyData(data);
      var bars = 48, gap = 3, barW = (w - gap * (bars - 1)) / bars, mid = h / 2, amp = 0;
      for (var i = 0; i < bars; i++) {
        var idx = Math.floor(i / bars * len);
        var v = rec.paused ? 6 : (data[idx] || 0);
        amp += v;
        var bh = Math.max(4, (v / 255) * (h * 0.92));
        var x = i * (barW + gap);
        var grad = ctx.createLinearGradient(0, mid - bh / 2, 0, mid + bh / 2);
        grad.addColorStop(0, '#2dd4bf'); grad.addColorStop(1, '#0f766e');
        ctx.fillStyle = grad;
        roundRect(ctx, x, mid - bh / 2, barW, bh, Math.min(barW / 2, 3));
        ctx.fill();
      }
      var level = rec.paused ? 0 : Math.min(1, (amp / bars) / 120);
      if (orb) orb.style.transform = 'scale(' + (1 + level * 0.12).toFixed(3) + ')';
    }
    frame();
  }
  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath(); ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
  }
  function stopVisualizer() {
    if (rec.raf) { cancelAnimationFrame(rec.raf); rec.raf = null; }
    if (rec.audioCtx) { try { rec.audioCtx.close(); } catch (e) {} rec.audioCtx = null; }
    rec.analyser = null;
    if (orb) orb.style.transform = '';
  }

  if (pauseBtn) pauseBtn.addEventListener('click', function () {
    if (!rec.recorder) return;
    if (rec.paused) {
      if (rec.recorder.state === 'paused') rec.recorder.resume();
      if (rec.audioCtx && rec.audioCtx.state === 'suspended') rec.audioCtx.resume();
      rec.paused = false;
    } else {
      if (rec.recorder.state === 'recording') rec.recorder.pause();
      rec.paused = true;
    }
    setPausedUi(rec.paused);
  });
  function setPausedUi(paused) {
    studio.classList.toggle('is-paused', paused);
    pauseLabel.textContent = paused ? t('student.recitations.resume') : t('student.recitations.pause');
    pauseGlyph.innerHTML = paused ? ICON_PLAY : ICON_PAUSE;
  }

  document.querySelectorAll('[data-rec-stop]').forEach(function (el) {
    el.addEventListener('click', function () {
      if (!rec.recorder) return;
      if (rec.recorder.state !== 'inactive') rec.recorder.stop();
      stopTimer();
    });
  });

  function handleStop() {
    stopVisualizer();
    var type = rec.mimeType || 'audio/webm';
    rec.blob = new Blob(rec.chunks, { type: type });
    if (playback) playback.src = URL.createObjectURL(rec.blob);
    if (reviewMeta) reviewMeta.textContent = t('student.recitations.reviewFor', {
      name: sel.title,
      duration: fmt(rec.elapsed),
      size: (rec.blob.size / (1024 * 1024)).toFixed(2)
    });
    if (submitStatus) { submitStatus.textContent = ''; submitStatus.classList.remove('is-error'); }
    if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = t('student.recitations.submitRecitation'); }
    review.hidden = false;
  }

  if (submitBtn) submitBtn.addEventListener('click', function () {
    if (!rec.blob || !requireSession()) return;
    submitBtn.disabled = true;
    submitStatus.classList.remove('is-error');
    submitStatus.textContent = t('student.recitations.uploadingRecitation');

    var ext = rec.mimeType.indexOf('ogg') >= 0 ? 'ogg' : (rec.mimeType.indexOf('mp4') >= 0 ? 'm4a' : 'webm');
    var form = new FormData();
    form.append('ajax', '1');
    form.append('enrollmentId', sel.enrollmentId);
    form.append('duration', rec.elapsed);
    form.append('audio', rec.blob, 'recitation.' + ext);

    post(form).then(function (data) {
      if (data && data.ok) { window.location.href = data.redirect || (ENDPOINT + '?submitted=1'); }
      else { submitBtn.disabled = false; submitStatus.classList.add('is-error'); submitStatus.textContent = (data && data.error) || t('student.recitations.submissionFailed'); }
    }).catch(function () {
      submitBtn.disabled = false; submitStatus.classList.add('is-error'); submitStatus.textContent = t('errors.networkError');
    });
  });

  document.querySelectorAll('[data-rec-discard]').forEach(function (el) {
    el.addEventListener('click', function () { review.hidden = true; launchStudio(); });
  });
  document.querySelectorAll('[data-rec-cancel]').forEach(function (el) { el.addEventListener('click', cancelStudio); });
  function cancelStudio() {
    stopTimer(); stopVisualizer();
    if (rec.recorder && rec.recorder.state !== 'inactive') { try { rec.recorder.stop(); } catch (e) {} }
    rec.recorder = null; rec.blob = null; rec.chunks = [];
    if (rec.stream) { rec.stream.getTracks().forEach(function (t) { t.stop(); }); rec.stream = null; }
    studio.hidden = true; review.hidden = true;
    lockScroll(false);
  }

  /* ===================================================================
   * Recent submissions: inline play + feedback report
   * ================================================================= */
  document.querySelectorAll('[data-rec-play]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      var item = btn.closest('.rec-sub');
      var player = item ? item.querySelector('.rec-sub__player') : null;
      if (!player) return;
      player.hidden = !player.hidden;
      btn.classList.toggle('is-on', !player.hidden);
      var audio = player.querySelector('audio');
      if (audio) { if (player.hidden) audio.pause(); else audio.play().catch(function () {}); }
    });
  });

  var fb = fbModal ? {
    title: $('#recFbTitle'), info: $('#recFbInfo'), audio: $('#recFbAudio'),
    result: $('#recFbResult'), ring: $('#recFbRing'), score: $('#recFbScore'),
    badge: $('#recFbBadge'), scoreDesc: $('#recFbScoreDesc'), feedback: $('#recFbFeedback'),
    pending: $('#recFbPending')
  } : null;

  function scoreDesc(kind) {
    if (kind === 'excellent') return t('student.recitations.scoreExcellent');
    if (kind === 'reviewed') return t('student.recitations.scoreReviewed');
    if (kind === 'improve') return t('student.recitations.scoreImprove');
    return '';
  }
  var RING_COLOR = { excellent: '#059669', reviewed: '#0f766e', improve: '#d97706' };

  function openFeedback(card) {
    if (!fb) return;
    var d = function (k) { return card.getAttribute('data-' + k) || ''; };
    var evaluated = d('evaluated') === 'true';
    var kind = d('status-kind') || 'pending';

    fb.title.textContent = d('title') || t('student.recitations.title');
    fb.info.innerHTML = ''
      + infoRow(t('student.recitations.infoInstructor'), d('instructor'))
      + infoRow(t('student.recitations.infoSubmitted'), d('date'))
      + infoRow(t('student.recitations.infoType'), d('mode'))
      + infoRow(t('student.recitations.infoScheduled'), d('schedule'))
      + infoRow(t('student.recitations.infoPortion'), d('portion'));

    var audioUrl = d('audio');
    if (audioUrl) { fb.audio.src = audioUrl; fb.audio.parentElement.hidden = false; }
    else { fb.audio.removeAttribute('src'); fb.audio.parentElement.hidden = true; }

    if (evaluated) {
      var score = parseInt(d('score'), 10); if (isNaN(score)) score = 0;
      var color = RING_COLOR[kind] || '#0f766e';
      fb.score.textContent = score;
      fb.ring.style.background = 'conic-gradient(' + color + ' ' + (score * 3.6) + 'deg, var(--rec-line) 0)';
      fb.ring.style.setProperty('--ring-color', color);
      fb.badge.className = 'rec-badge rec-badge--' + kind;
      fb.badge.textContent = d('status-label');
      fb.scoreDesc.textContent = scoreDesc(kind);
      var fbk = d('feedback');
      fb.feedback.textContent = fbk && fbk.trim() ? fbk : t('student.recitations.noWrittenFeedback');
      fb.result.hidden = false;
      fb.pending.hidden = true;
    } else {
      fb.result.hidden = true;
      fb.pending.hidden = false;
    }

    fbModal.hidden = false;
    lockScroll(true);
  }
  function infoRow(label, value) {
    if (!value || !value.trim()) return '';
    return '<div class="rec-fb__info-row"><span>' + escapeHtml(label) + '</span><strong>' + escapeHtml(value) + '</strong></div>';
  }
  function closeFeedback() {
    if (!fbModal) return;
    fbModal.hidden = true;
    if (fb && fb.audio) fb.audio.pause();
    lockScroll(false);
  }

  document.querySelectorAll('[data-rec-feedback]').forEach(function (card) {
    card.addEventListener('click', function () { openFeedback(card); });
    card.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openFeedback(card); }
    });
  });
  document.querySelectorAll('[data-rec-fb-dismiss]').forEach(function (el) {
    el.addEventListener('click', closeFeedback);
  });

  var viewAllBtn = $('[data-rec-viewall]');
  if (viewAllBtn) viewAllBtn.addEventListener('click', function () {
    document.querySelectorAll('.rec-row--more').forEach(function (r) { r.hidden = false; });
    var foot = viewAllBtn.closest('.rec-recent__foot');
    if (foot) foot.hidden = true;
  });

  /* ---------- global escape ---------- */
  document.addEventListener('etasmi:localechange', function () {
    if (uploadSubmitBtn && !uploadSubmitBtn.disabled) {
      uploadSubmitBtn.textContent = t('student.recitations.submitRecitation');
    }
    if (submitBtn && !submitBtn.disabled) {
      submitBtn.textContent = t('student.recitations.submitRecitation');
    }
    if (fileHint && fileEl && (!fileEl.files || !fileEl.files.length)) {
      fileHint.textContent = t('student.recitations.fileFormatsHint');
    }
    if (pauseLabel) {
      pauseLabel.textContent = rec.paused ? t('student.recitations.resume') : t('student.recitations.pause');
    }
  });

  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Escape') return;
    if (uploadModal && !uploadModal.hidden) { uploadModal.hidden = true; lockScroll(false); }
    else if (fbModal && !fbModal.hidden) { closeFeedback(); }
    else if (selectPanel && !selectPanel.hidden) { closePanel(); }
  });
})();
