/**
 * e-Tasmi Quran Assistant — real-time voice conversation layer.
 *
 * Turn-based, hands-free pipeline that feels like ChatGPT Voice Mode:
 *   mic capture -> silence detection (VAD) -> POST /voice/turn (STT + GPT + TTS)
 *   -> render transcript + autoplay spoken reply -> resume listening.
 *
 * Reuses the conversation history, mode, context and rendering helpers from
 * quran-assistant.js via window.EtasmiQuranAssistant.
 */
(function () {
  'use strict';

  // --- Tunables -------------------------------------------------------------
  // Tuned for a snappy, ChatGPT-like turn: end the turn quickly after the
  // student stops speaking so the reply starts almost immediately.
  var SILENCE_MS = 650;         // trailing silence that ends a turn
  var SPEECH_THRESHOLD = 0.022; // normalized RMS above which we treat audio as speech
  var MIN_SPEECH_MS = 250;      // ignore blips shorter than this
  var MAX_TURN_MS = 20000;      // hard cap on a single utterance
  var MIN_BLOB_BYTES = 1400;    // discard near-empty recordings

  // --- State ----------------------------------------------------------------
  var bridge = null;
  var els = {};
  var stream = null;
  var recorder = null;
  var chunks = [];
  var audioCtx = null;
  var analyser = null;
  var sourceNode = null;
  var vadRaf = null;
  var ttsAudio = null;
  var ttsUrl = null;

  var inited = false;
  var active = false;       // conversation loop running
  var listening = false;    // mic currently capturing a turn
  var busy = false;         // a turn is being processed / spoken
  var heardSpeech = false;
  var speechStartedAt = 0;
  var silenceSince = 0;
  var turnStartedAt = 0;

  function t(key, vars) {
    if (bridge && bridge.t) return bridge.t(key, vars);
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  function locale() {
    return (window.EtasmiI18n && EtasmiI18n.getLocale && EtasmiI18n.getLocale()) || 'en';
  }

  function init() {
    if (inited) return;
    bridge = window.EtasmiQuranAssistant;
    var root = (bridge && bridge.root) || document.querySelector('[data-eqa-root]');
    if (!root) return;
    inited = true;

    els.root = root;
    els.voice = root.querySelector('[data-eqa-voice]');
    if (!els.voice) return;

    els.status = els.voice.querySelector('[data-eqa-voice-status]');
    els.transcript = els.voice.querySelector('[data-eqa-voice-transcript]');
    els.orb = els.voice.querySelector('[data-eqa-voice-orb]');
    els.orbAvatar = els.voice.querySelector('[data-eqa-voice-avatar]');
    els.micBtn = els.voice.querySelector('[data-eqa-voice-mic]');
    els.stopBtn = els.voice.querySelector('[data-eqa-voice-stop]');
    els.closeBtn = els.voice.querySelector('[data-eqa-voice-close]');

    if (els.orbAvatar && bridge && bridge.robotHtml) {
      els.orbAvatar.innerHTML = bridge.robotHtml();
    }

    var startButtons = root.querySelectorAll('[data-eqa-voice-start]');
    Array.prototype.forEach.call(startButtons, function (btn) {
      btn.addEventListener('click', enterVoiceMode);
    });
    if (els.micBtn) els.micBtn.addEventListener('click', function () {
      if (!active) startConversation();
    });
    if (els.stopBtn) els.stopBtn.addEventListener('click', stopConversation);
    if (els.closeBtn) els.closeBtn.addEventListener('click', exitVoiceMode);

    // Public hook so the chat panel (quran-assistant.js) can fully tear down
    // voice mode whenever it closes or minimizes — prevents the overlay or a
    // live mic from lingering and re-appearing on the next open.
    window.EtasmiQuranVoice = {
      exit: exitVoiceMode,
      isOpen: function () { return !!els.voice && !els.voice.hidden; }
    };
  }

  // --- Mode open / close ----------------------------------------------------
  function enterVoiceMode() {
    if (!supported()) {
      setStatus(t('quranAssistant.voice.unsupported'));
      showOverlay();
      return;
    }
    if (bridge && bridge.openPanel && (!bridge.isOpen || !bridge.isOpen())) {
      bridge.openPanel();
    }
    showOverlay();
    startConversation();
  }

  function showOverlay() {
    els.voice.hidden = false;
    els.voice.setAttribute('aria-hidden', 'false');
    els.root.classList.add('is-voice');
  }

  function hideOverlay() {
    els.voice.hidden = true;
    els.voice.setAttribute('aria-hidden', 'true');
    els.root.classList.remove('is-voice');
  }

  function exitVoiceMode() {
    stopConversation();
    hideOverlay();
  }

  function supported() {
    return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia &&
              window.MediaRecorder && (window.AudioContext || window.webkitAudioContext));
  }

  // --- Conversation lifecycle ----------------------------------------------
  function startConversation() {
    if (active) return;
    setState('connecting');
    setStatus(t('quranAssistant.voice.requesting'));

    navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
      .then(function (s) {
        stream = s;
        var Ctx = window.AudioContext || window.webkitAudioContext;
        audioCtx = new Ctx();
        sourceNode = audioCtx.createMediaStreamSource(stream);
        analyser = audioCtx.createAnalyser();
        analyser.fftSize = 2048;
        sourceNode.connect(analyser);

        active = true;
        toggleControls(true);
        startListening();
      })
      .catch(function () {
        setState('error');
        setStatus(t('quranAssistant.voice.micDenied'));
        active = false;
        toggleControls(false);
      });
  }

  function stopConversation() {
    active = false;
    stopListening(true);
    stopPlayback();
    if (vadRaf) { cancelAnimationFrame(vadRaf); vadRaf = null; }
    if (stream) {
      stream.getTracks().forEach(function (track) { track.stop(); });
      stream = null;
    }
    if (audioCtx) {
      try { audioCtx.close(); } catch (e) {}
      audioCtx = null;
    }
    analyser = null;
    sourceNode = null;
    setState('idle');
    setStatus(t('quranAssistant.voice.statusIdle'));
    toggleControls(false);
  }

  // --- Listening + voice activity detection --------------------------------
  function startListening() {
    if (!active || !stream) return;
    if (busy) return;

    chunks = [];
    heardSpeech = false;
    speechStartedAt = 0;
    silenceSince = 0;
    turnStartedAt = Date.now();

    var mime = pickMime();
    try {
      recorder = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream);
    } catch (e) {
      recorder = new MediaRecorder(stream);
    }
    recorder.ondataavailable = function (ev) {
      if (ev.data && ev.data.size > 0) chunks.push(ev.data);
    };
    recorder.onstop = onRecorderStop;
    recorder.start();

    listening = true;
    setState('listening');
    setStatus(t('quranAssistant.voice.listening'));
    monitorVad();
  }

  function stopListening(discard) {
    listening = false;
    if (vadRaf) { cancelAnimationFrame(vadRaf); vadRaf = null; }
    if (recorder && recorder.state !== 'inactive') {
      recorder._discard = !!discard;
      try { recorder.stop(); } catch (e) {}
    }
  }

  function monitorVad() {
    if (!listening || !analyser) return;
    var buf = new Uint8Array(analyser.fftSize);
    analyser.getByteTimeDomainData(buf);

    var sum = 0;
    for (var i = 0; i < buf.length; i++) {
      var v = (buf[i] - 128) / 128;
      sum += v * v;
    }
    var rms = Math.sqrt(sum / buf.length);
    var now = Date.now();

    if (rms > SPEECH_THRESHOLD) {
      if (!heardSpeech) { heardSpeech = true; speechStartedAt = now; }
      silenceSince = 0;
      setOrbLevel(rms);
    } else if (heardSpeech) {
      if (!silenceSince) silenceSince = now;
      var spokenLongEnough = (now - speechStartedAt) > MIN_SPEECH_MS;
      if (spokenLongEnough && (now - silenceSince) > SILENCE_MS) {
        stopListening(false);
        return;
      }
    }

    if ((now - turnStartedAt) > MAX_TURN_MS && heardSpeech) {
      stopListening(false);
      return;
    }
    vadRaf = requestAnimationFrame(monitorVad);
  }

  function onRecorderStop() {
    var discard = recorder && recorder._discard;
    var type = (recorder && recorder.mimeType) || 'audio/webm';
    var blob = chunks.length ? new Blob(chunks, { type: type }) : null;
    chunks = [];
    recorder = null;

    if (discard || !active) return;
    if (!heardSpeech || !blob || blob.size < MIN_BLOB_BYTES) {
      // Nothing meaningful captured — keep listening.
      if (active) startListening();
      return;
    }
    sendTurn(blob, type);
  }

  // --- Turn round-trip ------------------------------------------------------
  function sendTurn(blob, type) {
    busy = true;
    setState('thinking');
    setStatus(t('quranAssistant.voice.thinking'));
    if (bridge && bridge.setStatus) bridge.setStatus(t('quranAssistant.voice.thinking'));

    var ext = type.indexOf('mp4') >= 0 ? 'mp4' : (type.indexOf('ogg') >= 0 ? 'ogg' : 'webm');
    var form = new FormData();
    form.append('audio', blob, 'speech.' + ext);
    form.append('mode', (bridge && bridge.getMode && bridge.getMode()) || 'general');
    form.append('lang', locale());
    if (bridge && bridge.getHistory) {
      form.append('history', JSON.stringify(bridge.getHistory() || []));
    }
    if (bridge && bridge.buildContextPayload) {
      var ctx = bridge.buildContextPayload();
      if (ctx) form.append('context', JSON.stringify(ctx));
    }

    fetch(bridge.apiVoiceTurn, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
      body: form
    })
      .then(function (res) { return res.json().then(function (data) { return { res: res, data: data }; }); })
      .then(function (out) {
        var data = out.data || {};
        if (!out.res.ok || !data.ok) {
          handleTurnError(data.message || t('quranAssistant.voice.error'));
          return;
        }
        if (data.noSpeech || !data.transcript) {
          busy = false;
          setStatus(t('quranAssistant.voice.didNotCatch'));
          if (active) startListening();
          return;
        }

        renderUser(data.transcript);
        renderAssistant(data.reply || '');
        if (bridge && bridge.pushHistory) {
          bridge.pushHistory('user', data.transcript);
          bridge.pushHistory('assistant', data.reply || '');
        }

        if (data.audio) {
          playReply(data.audio, data.audioMime || 'audio/mpeg');
        } else {
          // No audio came back; show the reply and resume listening.
          busy = false;
          if (active) startListening();
        }
      })
      .catch(function () {
        handleTurnError(t('quranAssistant.voice.networkError'));
      });
  }

  function handleTurnError(message) {
    busy = false;
    setState('error');
    setStatus(message);
    if (active) {
      setTimeout(function () { if (active) startListening(); }, 1500);
    }
  }

  // --- Playback -------------------------------------------------------------
  function playReply(base64, mime) {
    stopPlayback();
    try {
      var bytes = base64ToBytes(base64);
      var blob = new Blob([bytes], { type: mime });
      ttsUrl = URL.createObjectURL(blob);
      ttsAudio = new Audio(ttsUrl);
    } catch (e) {
      busy = false;
      if (active) startListening();
      return;
    }

    setState('speaking');
    setStatus(t('quranAssistant.voice.speaking'));

    var resume = function () {
      stopPlayback();
      busy = false;
      if (active) startListening();
    };
    ttsAudio.onended = resume;
    ttsAudio.onerror = resume;
    var p = ttsAudio.play();
    if (p && p.catch) p.catch(resume);
  }

  function stopPlayback() {
    if (ttsAudio) {
      try { ttsAudio.pause(); } catch (e) {}
      ttsAudio.onended = null;
      ttsAudio.onerror = null;
      ttsAudio = null;
    }
    if (ttsUrl) {
      URL.revokeObjectURL(ttsUrl);
      ttsUrl = null;
    }
  }

  function base64ToBytes(b64) {
    var binary = atob(b64);
    var len = binary.length;
    var bytes = new Uint8Array(len);
    for (var i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

  // --- Rendering ------------------------------------------------------------
  function renderUser(text) {
    appendTranscript('user', text, false);
    if (bridge && bridge.addUser) bridge.addUser(text);
  }

  function renderAssistant(reply) {
    var html = (bridge && bridge.formatReply) ? bridge.formatReply(reply) : escapeHtml(reply);
    appendTranscript('assistant', html, true);
    if (bridge && bridge.formatAndAddAssistant) bridge.formatAndAddAssistant(reply);
  }

  function appendTranscript(role, content, isHtml) {
    if (!els.transcript) return;
    var row = document.createElement('div');
    row.className = 'eqa-vline eqa-vline--' + role;
    var who = document.createElement('span');
    who.className = 'eqa-vline__who';
    who.textContent = role === 'user'
      ? t('quranAssistant.voice.you')
      : t('quranAssistant.voice.assistant');
    var body = document.createElement('div');
    body.className = 'eqa-vline__body';
    if (isHtml) body.innerHTML = content; else body.textContent = content;
    row.appendChild(who);
    row.appendChild(body);
    els.transcript.appendChild(row);
    els.transcript.scrollTop = els.transcript.scrollHeight;
  }

  // --- UI state helpers -----------------------------------------------------
  function setState(state) {
    if (!els.voice) return;
    els.voice.setAttribute('data-state', state);
  }

  function setStatus(text) {
    if (els.status) els.status.textContent = text;
  }

  function setOrbLevel(rms) {
    if (!els.orb) return;
    var scale = Math.min(1.35, 1 + rms * 4);
    els.orb.style.setProperty('--eqa-voice-level', scale.toFixed(3));
  }

  function toggleControls(running) {
    if (els.micBtn) els.micBtn.hidden = running;
    if (els.stopBtn) els.stopBtn.hidden = !running;
  }

  function pickMime() {
    var candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus'];
    if (!window.MediaRecorder || !MediaRecorder.isTypeSupported) return '';
    for (var i = 0; i < candidates.length; i++) {
      if (MediaRecorder.isTypeSupported(candidates[i])) return candidates[i];
    }
    return '';
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // Stop audio capture/playback if the student navigates away.
  window.addEventListener('beforeunload', function () { stopConversation(); });

  if (window.EtasmiQuranAssistant) {
    init();
  } else {
    document.addEventListener('etasmi-quran-assistant-ready', init, { once: true });
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () {
        if (window.EtasmiQuranAssistant) init();
      }, { once: true });
    }
  }
}());
