/* Compact audio player shared by the student studio, student result and instructor evaluation pages.
   Markup hooks: [data-rr-player] > audio[data-rr-audio], [data-rr-play], [data-rr-time],
   [data-rr-seek], [data-rr-mute], [data-rr-rate], optional canvas[data-rr-wave].
   The waveform is decoded from the real audio file; when decoding is not possible the seek bar stays. */
(function () {
  var RATES = [1, 1.25, 1.5, 0.75];
  var MAX_WAVE_BYTES = 40 * 1024 * 1024;
  var PEAKS = 480;

  function fmt(seconds) {
    if (!isFinite(seconds) || seconds < 0) return '0:00';
    var m = Math.floor(seconds / 60);
    var s = Math.floor(seconds % 60);
    return m + ':' + (s < 10 ? '0' : '') + s;
  }

  function decode(buffer) {
    var Offline = window.OfflineAudioContext || window.webkitOfflineAudioContext;
    if (!Offline) return Promise.reject(new Error('no-audio-context'));
    var ctx = new Offline(1, 44100, 44100);
    return new Promise(function (resolve, reject) {
      var p = ctx.decodeAudioData(buffer, resolve, reject);
      if (p && p.then) p.then(resolve, reject);
    });
  }

  function peaksOf(audioBuffer) {
    var data = audioBuffer.getChannelData(0);
    var size = Math.max(1, Math.floor(data.length / PEAKS));
    var out = new Array(PEAKS);
    var max = 0;
    for (var i = 0; i < PEAKS; i++) {
      var start = i * size, end = Math.min(data.length, start + size), sum = 0, n = 0;
      for (var j = start; j < end; j += 4) { sum += data[j] * data[j]; n++; }
      out[i] = n ? Math.sqrt(sum / n) : 0;
      if (out[i] > max) max = out[i];
    }
    for (var k = 0; k < PEAKS; k++) out[k] = max > 0 ? out[k] / max : 0;
    return out;
  }

  function bind(player) {
    if (!player || player.getAttribute('data-bound') === '1') return;
    var audio = player.querySelector('[data-rr-audio]');
    if (!audio) return;
    player.setAttribute('data-bound', '1');

    var play = player.querySelector('[data-rr-play]');
    var time = player.querySelector('[data-rr-time]');
    var seek = player.querySelector('[data-rr-seek]');
    var mute = player.querySelector('[data-rr-mute]');
    var rate = player.querySelector('[data-rr-rate]');
    var canvas = player.querySelector('[data-rr-wave]');
    var probingDuration = false;
    var peaks = null;
    var waveSrc = '';
    var abort = null;
    var raf = null;

    function ratio() {
      var dur = audio.duration;
      return isFinite(dur) && dur > 0 ? Math.min(1, (audio.currentTime || 0) / dur) : 0;
    }

    function draw() {
      if (!canvas || !peaks) return;
      var cssW = canvas.clientWidth, cssH = canvas.clientHeight;
      if (!cssW || !cssH) return;
      var dpr = window.devicePixelRatio || 1;
      if (canvas.width !== Math.round(cssW * dpr) || canvas.height !== Math.round(cssH * dpr)) {
        canvas.width = Math.round(cssW * dpr);
        canvas.height = Math.round(cssH * dpr);
      }
      var ctx = canvas.getContext('2d');
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, cssW, cssH);
      var styles = getComputedStyle(player);
      var played = (styles.getPropertyValue('--lx-wave-played') || '#13715f').trim();
      var rest = (styles.getPropertyValue('--lx-wave-rest') || '#cbd5e1').trim();
      var barW = 2.5, gap = 1.75, step = barW + gap;
      var bars = Math.max(1, Math.floor((cssW + gap) / step));
      var progressX = ratio() * cssW;
      for (var b = 0; b < bars; b++) {
        var from = Math.floor((b / bars) * peaks.length);
        var to = Math.max(from + 1, Math.floor(((b + 1) / bars) * peaks.length));
        var v = 0;
        for (var i = from; i < to; i++) if (peaks[i] > v) v = peaks[i];
        var h = Math.max(2, v * cssH * 0.94);
        var x = b * step;
        ctx.fillStyle = x + barW / 2 <= progressX ? played : rest;
        ctx.fillRect(x, (cssH - h) / 2, barW, h);
      }
    }

    function loop() {
      draw();
      raf = audio.paused ? null : requestAnimationFrame(loop);
    }

    function loadWave() {
      if (!canvas || !window.fetch) return;
      var src = audio.currentSrc || audio.getAttribute('src') || '';
      if (!src) {
        var source = audio.querySelector('source');
        src = source ? source.src : '';
      }
      if (!src || src === waveSrc) return;
      waveSrc = src;
      peaks = null;
      player.classList.remove('has-wave');
      if (abort) abort.abort();
      abort = window.AbortController ? new AbortController() : null;
      var controller = abort;
      fetch(src, { credentials: 'same-origin', signal: controller ? controller.signal : undefined })
        .then(function (res) {
          var len = Number(res.headers.get('content-length') || 0);
          if (!res.ok || len > MAX_WAVE_BYTES) {
            if (controller) controller.abort();
            throw new Error('skip');
          }
          return res.arrayBuffer();
        })
        .then(decode)
        .then(function (buffer) {
          if (src !== waveSrc) return;
          peaks = peaksOf(buffer);
          player.classList.add('has-wave');
          draw();
        })
        .catch(function () {});
    }

    function sync() {
      if (probingDuration) return;
      var dur = audio.duration;
      var cur = audio.currentTime || 0;
      var known = isFinite(dur) && dur > 0;
      if (time) time.textContent = known ? fmt(cur) + ' / ' + fmt(dur) : fmt(cur);
      if (seek) {
        seek.disabled = !known;
        if (known) seek.value = String(Math.round((cur / dur) * 1000));
        seek.style.setProperty('--lx-pct', (known ? (cur / dur) * 100 : 0).toFixed(2) + '%');
      }
      if (!raf) draw();
    }

    // MediaRecorder WebM files report an infinite duration until the end is read.
    audio.addEventListener('loadedmetadata', function () {
      if (audio.duration === Infinity) {
        probingDuration = true;
        audio.currentTime = 1e101;
        return;
      }
      sync();
    });
    audio.addEventListener('durationchange', function () {
      if (probingDuration && isFinite(audio.duration)) {
        probingDuration = false;
        audio.currentTime = 0;
      }
      sync();
    });
    audio.addEventListener('timeupdate', sync);
    audio.addEventListener('play', function () {
      player.classList.add('is-playing');
      if (play) play.setAttribute('aria-label', play.getAttribute('data-label-pause') || 'Pause');
      if (canvas && !raf) raf = requestAnimationFrame(loop);
    });
    audio.addEventListener('pause', function () {
      player.classList.remove('is-playing');
      if (play) play.setAttribute('aria-label', play.getAttribute('data-label-play') || 'Play');
    });
    audio.addEventListener('ended', function () { player.classList.remove('is-playing'); });
    audio.addEventListener('loadstart', function () {
      probingDuration = false;
      player.classList.remove('is-error', 'is-playing');
      if (play) play.disabled = false;
      sync();
      loadWave();
    });
    audio.addEventListener('error', function () {
      player.classList.add('is-error');
      if (play) play.disabled = true;
    }, true);

    if (play) play.addEventListener('click', function () {
      if (audio.paused) {
        var p = audio.play();
        if (p && p.catch) p.catch(function () {});
      } else {
        audio.pause();
      }
    });
    if (seek) seek.addEventListener('input', function () {
      if (!isFinite(audio.duration) || audio.duration <= 0) return;
      audio.currentTime = (Number(seek.value) / 1000) * audio.duration;
    });
    if (mute) mute.addEventListener('click', function () {
      audio.muted = !audio.muted;
      player.classList.toggle('is-muted', audio.muted);
      mute.setAttribute('aria-pressed', audio.muted ? 'true' : 'false');
    });
    if (rate) rate.addEventListener('click', function () {
      var i = RATES.indexOf(audio.playbackRate);
      var next = RATES[(i + 1) % RATES.length];
      audio.playbackRate = next;
      rate.textContent = next + 'x';
    });
    if (canvas && window.ResizeObserver) new ResizeObserver(function () { draw(); }).observe(canvas);
    sync();
    loadWave();
  }

  window.LLPlayer = { bind: bind };

  function init() {
    document.querySelectorAll('[data-rr-player]').forEach(bind);
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
