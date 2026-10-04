/* =====================================================================
 * e-Tasmi · Recitation studio
 *
 * Steps: choose session -> record or upload -> review & submit.
 * Backend contract (StudentRecitationServlet):
 *   POST {endpoint} (multipart, ajax=1) -> { ok, redirect } | { ok:false, error }
 *   fields: enrollmentId, audio, duration?, parentRecitationId? (Practice Again)
 * ===================================================================== */
(function () {
  'use strict';

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  var root = document.querySelector('[data-rec-root]');
  if (!root) return;
  var ENDPOINT = root.getAttribute('data-rec-endpoint');
  var $ = function (sel) { return root.querySelector(sel); };

  var viewAll = $('[data-rec-viewall]');
  if (viewAll) viewAll.addEventListener('click', function () {
    root.querySelectorAll('[data-rec-more]').forEach(function (row) { row.hidden = false; });
    viewAll.hidden = true;
  });

  var studioCard = $('[data-rec-studio]');
  if (!studioCard) return;

  var steps = studioCard.querySelectorAll('[data-rec-steps] li');
  var step2 = $('#recStep2');
  var review = $('#recReview');
  var processing = $('#recProcessing');

  var trigger = $('#recSessionTrigger');
  var listbox = $('#recSessionList');
  var placeholder = trigger ? trigger.querySelector('.lx-select__placeholder') : null;
  var current = $('[data-rec-current]');
  var chosen = $('#recChosen');
  var chosenPortion = $('#recChosenPortion');
  var chosenInstructor = $('#recChosenInstructor');
  var chosenSchedule = $('#recChosenSchedule');

  var recorderEl = $('#recStudio');
  var stateLabel = $('#recStateLabel');
  var timerEl = $('#recTimer');
  var canvas = $('#recWave');
  var captureTitle = $('#recCaptureTitle');
  var captureHint = $('#recCaptureHint');
  var startBtn = $('[data-rec-start]');
  var stopBtn = $('[data-rec-stop]');
  var pauseBtn = $('[data-rec-pause]');
  var pauseLabel = $('#recPauseLabel');

  var fileEl = $('#recFile');
  var fileHint = $('#recFileHint');
  var dropzone = $('#recDropzone');
  var reviewFileBtn = $('[data-rec-review-file]');

  var playback = $('#recPlayback');
  var reviewMeta = $('#recReviewMeta');
  var reviewDuration = $('#recReviewDuration');
  var reviewSession = $('#recReviewSession');
  var reviewPassage = $('#recReviewPassage');
  var submitBtn = $('#recSubmitBtn');
  var submitLabel = $('#recSubmitLabel');
  var submitStatus = $('#recSubmitStatus');

  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  var sel = { enrollmentId: '', title: '', portion: '' };
  var rec = {
    stream: null, recorder: null, chunks: [], blob: null, file: null, objectUrl: null,
    mimeType: '', elapsed: 0, tick: null, paused: false, ignoreStop: false,
    audioCtx: null, analyser: null, raf: null, history: []
  };

  function fmt(s) { s = Math.max(0, Math.floor(s)); var m = Math.floor(s / 60), r = s % 60; return m + ':' + (r < 10 ? '0' + r : r); }
  function mb(bytes) { return (bytes / (1024 * 1024)).toFixed(2); }
  function supportsRecording() { return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && typeof MediaRecorder !== 'undefined'); }
  function pickMimeType() {
    if (typeof MediaRecorder === 'undefined' || !MediaRecorder.isTypeSupported) return '';
    var prefs = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];
    for (var i = 0; i < prefs.length; i++) { if (MediaRecorder.isTypeSupported(prefs[i])) return prefs[i]; }
    return '';
  }
  function setI18n(el, key, vars) {
    if (!el) return;
    el.setAttribute('data-i18n', key);
    el.textContent = t(key, vars);
  }
  function setStatus(message, isError) {
    if (!submitStatus) return;
    submitStatus.textContent = message || '';
    submitStatus.classList.toggle('is-error', !!isError);
  }
  function enter(stage) {
    stage.classList.remove('is-entering');
    void stage.offsetWidth;
    stage.classList.add('is-entering');
  }

  /* ---------- steps ---------- */
  function setStep(n) {
    steps.forEach(function (li) {
      var s = Number(li.getAttribute('data-step'));
      li.classList.toggle('is-done', s < n);
      li.classList.toggle('is-current', s === n);
      if (s === n) li.setAttribute('aria-current', 'step'); else li.removeAttribute('aria-current');
    });
    [[step2, 2], [review, 3], [processing, 4]].forEach(function (pair) {
      if (!pair[0]) return;
      var show = pair[1] === n;
      var wasHidden = pair[0].hidden;
      pair[0].hidden = !show;
      if (show && wasHidden) enter(pair[0]);
    });
    if (trigger) trigger.disabled = n >= 4;
  }

  /* ---------- step 1: session listbox ---------- */
  var options = Array.prototype.slice.call(root.querySelectorAll('[data-rec-session]'));
  var activeIndex = -1;

  function isOpen() { return listbox && !listbox.hidden; }
  function setActive(i) {
    if (!options.length) return;
    activeIndex = (i + options.length) % options.length;
    options.forEach(function (o, k) { o.classList.toggle('is-active', k === activeIndex); });
    listbox.setAttribute('aria-activedescendant', options[activeIndex].id);
    var o = options[activeIndex];
    if (o.offsetTop < listbox.scrollTop) listbox.scrollTop = o.offsetTop;
    else if (o.offsetTop + o.offsetHeight > listbox.scrollTop + listbox.clientHeight) listbox.scrollTop = o.offsetTop + o.offsetHeight - listbox.clientHeight;
  }
  function openList() {
    if (!listbox || trigger.disabled) return;
    listbox.hidden = false;
    trigger.setAttribute('aria-expanded', 'true');
    trigger.parentNode.classList.add('is-open');
    var selectedIndex = options.findIndex(function (o) { return o.getAttribute('aria-selected') === 'true'; });
    setActive(selectedIndex >= 0 ? selectedIndex : 0);
    listbox.focus();
  }
  function closeList(refocus) {
    if (!listbox || listbox.hidden) return;
    listbox.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');
    trigger.parentNode.classList.remove('is-open');
    if (refocus) trigger.focus();
  }

  function selectSession(option) {
    var changed = option.getAttribute('data-enrollment-id') !== sel.enrollmentId;
    options.forEach(function (o) { o.setAttribute('aria-selected', o === option ? 'true' : 'false'); });
    sel.enrollmentId = option.getAttribute('data-enrollment-id') || '';
    sel.title = option.getAttribute('data-title') || '';
    sel.portion = option.getAttribute('data-portion') || '';
    if (placeholder) placeholder.hidden = true;
    if (current) { current.hidden = false; current.textContent = sel.title; }
    trigger.classList.add('has-value');
    if (chosenPortion) chosenPortion.textContent = sel.portion || '—';
    if (chosenInstructor) chosenInstructor.textContent = option.getAttribute('data-instructor') || '—';
    if (chosenSchedule) chosenSchedule.textContent = option.getAttribute('data-schedule') || '—';
    if (chosen) {
      var first = chosen.hidden;
      chosen.hidden = false;
      if (first || changed) enter(chosen);
    }
    if (reviewSession) reviewSession.textContent = sel.title || '—';
    if (reviewPassage) reviewPassage.textContent = sel.portion || '—';
    if (review && !review.hidden) return;
    if (step2 && step2.hidden) { showMethod('record'); setStep(2); }
  }

  if (trigger && listbox) {
    trigger.addEventListener('click', function () { if (isOpen()) closeList(true); else openList(); });
    trigger.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        openList();
      }
    });
    listbox.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowDown') { e.preventDefault(); setActive(activeIndex + 1); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(activeIndex - 1); }
      else if (e.key === 'Home') { e.preventDefault(); setActive(0); }
      else if (e.key === 'End') { e.preventDefault(); setActive(options.length - 1); }
      else if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        if (activeIndex >= 0) selectSession(options[activeIndex]);
        closeList(true);
      } else if (e.key === 'Escape' || e.key === 'Tab') {
        if (e.key === 'Escape') e.preventDefault();
        closeList(e.key === 'Escape');
      }
    });
    options.forEach(function (o, i) {
      o.addEventListener('mousemove', function () { if (activeIndex !== i) setActive(i); });
      o.addEventListener('click', function () { selectSession(o); closeList(true); });
    });
    document.addEventListener('mousedown', function (e) {
      if (isOpen() && !trigger.parentNode.contains(e.target)) closeList(false);
    });
  }

  /* ---------- step 2: method ---------- */
  function showMethod(method) {
    root.querySelectorAll('[data-rec-method]').forEach(function (btn) {
      var on = btn.getAttribute('data-rec-method') === method;
      btn.classList.toggle('is-active', on);
      btn.setAttribute('aria-selected', on ? 'true' : 'false');
    });
    root.querySelectorAll('[data-rec-pane]').forEach(function (pane) {
      pane.hidden = pane.getAttribute('data-rec-pane') !== method;
    });
  }
  root.querySelectorAll('[data-rec-method]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (recorderEl && recorderEl.getAttribute('data-state') !== 'idle') return;
      showMethod(btn.getAttribute('data-rec-method'));
    });
  });

  /* ---------- recorder ---------- */
  function setRecorderState(state) {
    if (!recorderEl) return;
    recorderEl.setAttribute('data-state', state);
    var live = state !== 'idle';
    if (startBtn) startBtn.hidden = live;
    if (stopBtn) stopBtn.hidden = !live;
    if (pauseBtn) pauseBtn.hidden = !live;
    if (trigger) trigger.disabled = live;
    if (state === 'idle') {
      setI18n(stateLabel, 'student.recitations.readyToRecord');
      setI18n(captureTitle, 'student.recitations.tapToStart');
      setI18n(captureHint, 'student.recitations.tapToStartHint');
      if (timerEl) timerEl.textContent = '0:00';
    } else if (state === 'paused') {
      setI18n(stateLabel, 'student.recitations.recordingPaused');
      setI18n(captureTitle, 'student.recitations.recordingPaused');
      setI18n(captureHint, 'student.recitations.recordingPausedHint');
      setI18n(pauseLabel, 'student.recitations.resume');
    } else {
      setI18n(stateLabel, 'student.recitations.recordingLive');
      setI18n(captureTitle, 'student.recitations.recordingInProgress');
      setI18n(captureHint, 'student.recitations.recordingInProgressHint');
      setI18n(pauseLabel, 'student.recitations.pause');
    }
    if (pauseBtn) pauseBtn.setAttribute('aria-pressed', state === 'paused' ? 'true' : 'false');
  }

  if (startBtn) startBtn.addEventListener('click', function () {
    if (!sel.enrollmentId) return;
    if (!supportsRecording()) { alert(t('student.recitations.recordingNotSupported')); return; }
    startBtn.disabled = true;
    navigator.mediaDevices.getUserMedia({ audio: true })
      .then(function (stream) { rec.stream = stream; startBtn.disabled = false; startRecording(); })
      .catch(function (err) {
        startBtn.disabled = false;
        alert(err && err.name === 'NotAllowedError' ? t('student.recitations.micBlocked') : t('student.recitations.recordingFailed'));
      });
  });

  function startRecording() {
    rec.chunks = []; rec.blob = null; rec.file = null; rec.elapsed = 0; rec.paused = false; rec.history = [];
    rec.mimeType = pickMimeType();
    try {
      rec.recorder = rec.mimeType ? new MediaRecorder(rec.stream, { mimeType: rec.mimeType }) : new MediaRecorder(rec.stream);
    } catch (e) { rec.recorder = new MediaRecorder(rec.stream); }
    if (!rec.mimeType) rec.mimeType = rec.recorder.mimeType || 'audio/webm';
    rec.recorder.ondataavailable = function (e) { if (e.data && e.data.size > 0) rec.chunks.push(e.data); };
    rec.recorder.onstop = function () { if (!rec.ignoreStop) finishRecording(); };
    rec.recorder.start(250);
    setRecorderState('live');
    startTimer();
    startVisualizer();
    if (stopBtn) stopBtn.focus();
  }

  function startTimer() {
    stopTimer();
    rec.tick = setInterval(function () {
      if (rec.paused) return;
      rec.elapsed += 1;
      if (timerEl) timerEl.textContent = fmt(rec.elapsed);
    }, 1000);
  }
  function stopTimer() { if (rec.tick) { clearInterval(rec.tick); rec.tick = null; } }

  function startVisualizer() {
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      rec.audioCtx = new AC();
      var src = rec.audioCtx.createMediaStreamSource(rec.stream);
      rec.analyser = rec.audioCtx.createAnalyser();
      rec.analyser.fftSize = 512;
      src.connect(rec.analyser);
    } catch (e) { rec.analyser = null; }
    drawWave();
  }

  /* Scrolling level history: each bar is one frame's loudness, newest on the right. */
  function drawWave() {
    if (!canvas) return;
    var ctx = canvas.getContext('2d');
    var data = new Uint8Array(rec.analyser ? rec.analyser.fftSize : 512);
    var bars = 64;
    var styles = getComputedStyle(recorderEl);
    var live = (styles.getPropertyValue('--lx-wave') || '#7ee2c3').trim();
    var idle = (styles.getPropertyValue('--lx-wave-idle') || 'rgba(255,255,255,0.2)').trim();
    var frameCount = 0;
    function frame() {
      rec.raf = requestAnimationFrame(frame);
      frameCount++;
      if (frameCount % 3 !== 0) return;
      var level = 0;
      if (rec.analyser && !rec.paused) {
        rec.analyser.getByteTimeDomainData(data);
        var sum = 0;
        for (var i = 0; i < data.length; i++) { var v = (data[i] - 128) / 128; sum += v * v; }
        level = Math.min(1, Math.sqrt(sum / data.length) * 4);
      }
      if (rec.paused) return;
      rec.history.push(level);
      if (rec.history.length > bars) rec.history.shift();
      var w = canvas.width, h = canvas.height, gap = 4, barW = (w - gap * (bars - 1)) / bars, mid = h / 2;
      ctx.clearRect(0, 0, w, h);
      var offset = bars - rec.history.length;
      for (var b = 0; b < bars; b++) {
        var val = b < offset ? 0 : rec.history[b - offset];
        var bh = Math.max(4, val * h * 0.9);
        ctx.fillStyle = b < offset ? idle : live;
        var x = b * (barW + gap);
        roundRect(ctx, x, mid - bh / 2, barW, bh, Math.min(barW / 2, 3));
        ctx.fill();
      }
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
    if (canvas) canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
  }

  if (pauseBtn) pauseBtn.addEventListener('click', function () {
    if (!rec.recorder) return;
    if (rec.paused) {
      if (rec.recorder.state === 'paused') rec.recorder.resume();
      rec.paused = false;
      setRecorderState('live');
    } else {
      if (rec.recorder.state === 'recording') rec.recorder.pause();
      rec.paused = true;
      setRecorderState('paused');
    }
  });

  if (stopBtn) stopBtn.addEventListener('click', function () {
    if (!rec.recorder) return;
    stopTimer();
    if (rec.recorder.state !== 'inactive') rec.recorder.stop();
  });

  function finishRecording() {
    stopVisualizer();
    stopStream();
    rec.blob = new Blob(rec.chunks, { type: rec.mimeType || 'audio/webm' });
    rec.recorder = null;
    setRecorderState('idle');
    openReview(rec.blob, fmt(rec.elapsed), '');
  }

  function stopStream() {
    if (rec.stream) { rec.stream.getTracks().forEach(function (track) { track.stop(); }); rec.stream = null; }
  }

  /* ---------- upload ---------- */
  if (fileEl) fileEl.addEventListener('change', function () {
    var f = fileEl.files && fileEl.files[0];
    if (reviewFileBtn) reviewFileBtn.disabled = !f;
    if (dropzone) dropzone.classList.toggle('has-file', !!f);
    if (fileHint) {
      if (f) { fileHint.removeAttribute('data-i18n'); fileHint.textContent = f.name + ' · ' + mb(f.size) + ' MB'; }
      else setI18n(fileHint, 'student.recitations.fileFormatsHint');
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
      try {
        var dt = new DataTransfer(); dt.items.add(files[0]);
        fileEl.files = dt.files;
        fileEl.dispatchEvent(new Event('change', { bubbles: true }));
      } catch (err) {}
    });
  }
  if (reviewFileBtn) reviewFileBtn.addEventListener('click', function () {
    var f = fileEl && fileEl.files && fileEl.files[0];
    if (!f || !sel.enrollmentId) return;
    rec.blob = null;
    rec.file = f;
    openReview(f, '', f.name + ' · ' + mb(f.size) + ' MB');
  });

  /* ---------- step 3: review & submit ---------- */
  if (playback) playback.addEventListener('durationchange', function () {
    if (reviewDuration && isFinite(playback.duration) && playback.duration > 0) {
      reviewDuration.textContent = fmt(playback.duration);
    }
  });

  function openReview(media, duration, meta) {
    if (rec.objectUrl) URL.revokeObjectURL(rec.objectUrl);
    rec.objectUrl = URL.createObjectURL(media);
    if (reviewDuration) reviewDuration.textContent = duration || '—';
    if (reviewSession) reviewSession.textContent = sel.title || '—';
    if (reviewPassage) reviewPassage.textContent = sel.portion || '—';
    if (reviewMeta) { reviewMeta.textContent = meta || ''; reviewMeta.hidden = !meta; }
    if (playback) { playback.src = rec.objectUrl; playback.load(); }
    setStatus('');
    setSubmitting(false);
    setStep(3);
    if (submitBtn && studioCard.getBoundingClientRect().top < 0) {
      review.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
    }
  }

  function setSubmitting(on) {
    if (!submitBtn) return;
    submitBtn.disabled = on;
    submitBtn.classList.toggle('is-busy', on);
    var discard = $('[data-rec-discard]');
    if (discard) discard.disabled = on;
    setI18n(submitLabel, on ? 'student.recitations.uploading' : 'student.recitations.submitRecitationBtn');
  }

  function appendPracticeParent(form) {
    var parentId = root.getAttribute('data-practice-parent') || '';
    var enrollmentId = root.getAttribute('data-practice-enrollment') || '';
    if (parentId && enrollmentId && sel.enrollmentId === enrollmentId) {
      form.append('parentRecitationId', parentId);
    }
  }

  if (submitBtn) submitBtn.addEventListener('click', function () {
    if ((!rec.blob && !rec.file) || !sel.enrollmentId) return;
    if (playback) { try { playback.pause(); } catch (e) {} }
    setSubmitting(true);
    setStatus(t('student.recitations.uploadingRecitation'));

    var form = new FormData();
    form.append('ajax', '1');
    form.append('enrollmentId', sel.enrollmentId);
    if (rec.blob) {
      var ext = rec.mimeType.indexOf('ogg') >= 0 ? 'ogg' : (rec.mimeType.indexOf('mp4') >= 0 ? 'm4a' : 'webm');
      form.append('duration', rec.elapsed);
      form.append('audio', rec.blob, 'recitation.' + ext);
    } else {
      form.append('audio', rec.file);
    }
    appendPracticeParent(form);

    fetch(ENDPOINT, { method: 'POST', body: form, credentials: 'same-origin' })
      .then(function (res) { return res.json().catch(function () { return { ok: false, error: t('errors.unexpectedResponse') }; }); })
      .then(function (data) {
        if (data && data.ok) {
          var target = data.redirect || (ENDPOINT + '?submitted=1');
          closeList(false);
          setStep(4);
          steps.forEach(function (li) { li.classList.add('is-done'); li.classList.remove('is-current'); });
          setTimeout(function () { window.location.href = target; }, reduceMotion ? 300 : 1600);
          return;
        }
        setSubmitting(false);
        setStatus((data && data.error) || t('student.recitations.submissionFailed'), true);
      })
      .catch(function () {
        setSubmitting(false);
        setStatus(t('errors.networkError'), true);
      });
  });

  root.querySelectorAll('[data-rec-discard]').forEach(function (el) {
    el.addEventListener('click', function () {
      var method = rec.file ? 'upload' : 'record';
      releaseCapture(false);
      showMethod(method);
      setStep(2);
    });
  });

  function releaseCapture(clearFile) {
    stopTimer(); stopVisualizer();
    rec.ignoreStop = true;
    if (rec.recorder && rec.recorder.state !== 'inactive') { try { rec.recorder.stop(); } catch (e) {} }
    rec.ignoreStop = false;
    rec.recorder = null; rec.blob = null; rec.chunks = []; rec.paused = false;
    if (clearFile) {
      rec.file = null;
      if (fileEl) { fileEl.value = ''; fileEl.dispatchEvent(new Event('change')); }
    }
    stopStream();
    setRecorderState('idle');
    if (playback) { try { playback.pause(); } catch (e) {} playback.removeAttribute('src'); playback.load(); }
    if (rec.objectUrl) { URL.revokeObjectURL(rec.objectUrl); rec.objectUrl = null; }
  }

  window.addEventListener('beforeunload', function () { stopStream(); });

  /* Practice Again: preselect the parent enrollment so the attempt is linked. */
  var practiceEnrollment = root.getAttribute('data-practice-enrollment');
  if (practiceEnrollment) {
    options.forEach(function (option) {
      if (option.getAttribute('data-enrollment-id') === practiceEnrollment) selectSession(option);
    });
  }
})();
