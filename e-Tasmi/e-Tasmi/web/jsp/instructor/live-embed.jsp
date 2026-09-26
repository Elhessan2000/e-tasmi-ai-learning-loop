<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%
    String pageTitle = (String) request.getAttribute("zoomPageTitle");
    if (pageTitle == null || pageTitle.isBlank()) {
        pageTitle = "Live session";
    }
    String cfgJson = (String) request.getAttribute("zoomClientConfigJson");
    if (cfgJson == null) {
        cfgJson = "{}";
    }
    String ctx = request.getContextPath();
    if (ctx == null) {
        ctx = "";
    }
    String sdkVer = (String) request.getAttribute("zoomWebSdkVersion");
    if (sdkVer == null || sdkVer.isBlank()) {
        sdkVer = "3.11.2";
    }
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex,nofollow">
    <title data-i18n="meta.liveSessionTitle"><%= pageTitle.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") %> · e-Tasmi</title>
    <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/student-zoom-live-embed.css?v=20260612-zoom-host1">
    <script src="https://source.zoom.us/<%= sdkVer %>/lib/vendor/react.min.js"></script>
    <script src="https://source.zoom.us/<%= sdkVer %>/lib/vendor/react-dom.min.js"></script>
    <script src="https://source.zoom.us/<%= sdkVer %>/lib/vendor/redux.min.js"></script>
    <script src="https://source.zoom.us/<%= sdkVer %>/lib/vendor/redux-thunk.min.js"></script>
    <script src="https://source.zoom.us/<%= sdkVer %>/lib/vendor/lodash.min.js"></script>
    <script src="https://source.zoom.us/zoom-meeting-<%= sdkVer %>.min.js"></script>
</head>
<body class="zoom-live-page">
<script id="etasmi-zoom-boot" type="application/json"><%= cfgJson %></script>

<header class="zoom-live-topbar" data-i18n="student.liveSession.toolbarAria" data-i18n-attr="aria-label" aria-label="Session toolbar">
    <h1 class="zoom-live-topbar__title"><%= pageTitle.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") %></h1>
    <div class="zoom-live-topbar__actions">
        <a class="zoom-live-btn" href="<%= LocaleSupport.localizedUrl(request, "/instructor/sessions") %>" data-i18n="student.liveSession.back">Back</a>
        <a class="zoom-live-btn zoom-live-btn--primary" id="zoom-fallback-btn" href="#" style="display:none" rel="noopener" data-i18n="student.liveSession.openInZoom">Open in Zoom</a>
    </div>
</header>

<div id="zoom-live-status" class="zoom-live-status" role="status">
    <p class="zoom-live-status__title" data-i18n="student.liveSession.connecting">Connecting to your class…</p>
    <p class="zoom-live-status__detail" id="zoom-live-status-detail" data-i18n="student.liveSession.loadingDetail">Loading the meeting. Allow the browser to use your microphone if prompted.</p>
    <div class="zoom-live-status__actions" id="zoom-live-status-actions" style="display:none"></div>
    <p class="zoom-live-hint" data-i18n="student.liveSession.hint">If the room does not start, use "Open in Zoom" or go back and try again.</p>
</div>

<script>
(function () {
  var bootEl = document.getElementById('etasmi-zoom-boot');
  var cfg;
  try {
    cfg = JSON.parse(bootEl.textContent || '{}');
  } catch (e) {
    cfg = {};
  }

  var statusBox = document.getElementById('zoom-live-status');
  var detailEl = document.getElementById('zoom-live-status-detail');
  var actionsEl = document.getElementById('zoom-live-status-actions');
  var fallbackBtn = document.getElementById('zoom-fallback-btn');

  function formatZoomError(errObj) {
    if (errObj == null || errObj === '') {
      return '';
    }
    if (typeof errObj === 'string') {
      return errObj;
    }
    try {
      var parts = [];
      var walk = function (x, depth) {
        if (depth > 5 || x == null) {
          return;
        }
        if (typeof x === 'string' || typeof x === 'number' || typeof x === 'boolean') {
          parts.push(String(x));
          return;
        }
        if (typeof x !== 'object') {
          return;
        }
        ['errorCode', 'errorMessage', 'reason', 'status', 'type', 'message', 'desc', 'result'].forEach(function (k) {
          if (x[k] != null && x[k] !== '') {
            if (k === 'result' && typeof x[k] === 'object') {
              try {
                parts.push('result: ' + JSON.stringify(x[k]));
              } catch (je) {
                parts.push('result: [object]');
              }
            } else {
              parts.push(k + ': ' + String(x[k]));
            }
          }
        });
        if (typeof x.error === 'object' && x.error != null) {
          walk(x.error, depth + 1);
        }
      };
      walk(errObj, 0);
      if (parts.length) {
        return parts.join('\n');
      }
      return JSON.stringify(errObj);
    } catch (e) {
      return String(errObj);
    }
  }

  function showError(title, detail, errObj) {
    statusBox.style.display = '';
    statusBox.classList.add('zoom-live-status--error');
    statusBox.querySelector('.zoom-live-status__title').textContent = title;
    var tech = formatZoomError(errObj);
    var base = detail || 'Something went wrong.';
    detailEl.textContent = tech ? (base + '\n\nTechnical detail:\n' + tech) : base;
    actionsEl.style.display = 'flex';
    actionsEl.innerHTML = '';
    if (cfg.fallbackUrl) {
      fallbackBtn.href = cfg.fallbackUrl;
      fallbackBtn.style.display = 'inline-flex';
    }
    var retry = document.createElement('a');
    retry.className = 'zoom-live-btn';
    retry.href = window.location.href;
    retry.textContent = 'Retry';
    actionsEl.appendChild(retry);
  }

  function hideStatus() {
    statusBox.style.display = 'none';
  }

  var ZLOG = '[e-Tasmi Zoom Host]';
  function delay(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }

  /** Zoom Web SDK needs a secure context: HTTPS or localhost. */
  function browserJoinLikelyWorks() {
    if (typeof window.isSecureContext === 'boolean' && window.isSecureContext) {
      return true;
    }
    var h = location.hostname || '';
    return h === 'localhost' || h === '127.0.0.1' || h === '[::1]';
  }

  if (!browserJoinLikelyWorks() && cfg.fallbackUrl) {
    statusBox.classList.add('zoom-live-status--error');
    statusBox.querySelector('.zoom-live-status__title').textContent = 'Open in Zoom for this network';
    detailEl.textContent = 'In-browser hosting needs HTTPS (or localhost). Use the button below to start the meeting in the Zoom app.';
    fallbackBtn.href = cfg.fallbackUrl;
    fallbackBtn.style.display = 'inline-flex';
    fallbackBtn.textContent = 'Open in Zoom';
    actionsEl.style.display = 'flex';
    actionsEl.innerHTML = '';
    return;
  }

  runZoomJoin();

  function runZoomJoin() {
  if (typeof ZoomMtg === 'undefined') {
    showError('Meeting SDK failed to load', 'The Zoom script could not be loaded. Check your network or try opening in Zoom.', null);
    return;
  }

  console.log(ZLOG, 'secureContext=', window.isSecureContext, 'origin=', window.location.origin, 'leaveUrl(config)=', cfg.leaveUrl);

  var ver = cfg.sdkVersion || '3.11.2';
  ZoomMtg.setZoomJSLib('https://source.zoom.us/' + ver + '/lib', '/av');
  ZoomMtg.preLoadWasm();

  var afterPrepare = Promise.resolve();
  try {
    var prepRet = ZoomMtg.prepareWebSDK();
    if (prepRet && typeof prepRet.then === 'function') {
      afterPrepare = prepRet.catch(function (e) {
        console.warn(ZLOG, 'prepareWebSDK rejected', e);
        return undefined;
      });
    } else {
      /* prepareWebSDK is often async without a Promise — wait briefly so i18n/load does not hang forever */
      afterPrepare = delay(400);
    }
  } catch (prepErr) {
    afterPrepare = Promise.reject(prepErr);
  }

  var afterI18n = afterPrepare
    .then(function () {
      return delay(50);
    })
    .then(function () {
      if (ZoomMtg.i18n && typeof ZoomMtg.i18n.load === 'function') {
        var lp = ZoomMtg.i18n.load('en-US');
        if (lp && typeof lp.then === 'function') {
          return Promise.race([
            lp,
            delay(20000).then(function () {
              throw new Error('Zoom i18n.load timed out after 20s (blocked network, adblock, or prepareWebSDK not ready).');
            })
          ]);
        }
      }
      return Promise.resolve();
    });

  afterI18n.then(function () {
  var sigUrl = cfg.signatureUrl;
  if (!sigUrl) {
    showError('Configuration error', 'Missing signature URL.', null);
    return;
  }

  console.log(ZLOG, 'Fetching signature', sigUrl);

  return fetch(sigUrl, {
    credentials: 'same-origin',
    headers: {
      'Accept': 'application/json',
      'X-Requested-With': 'XMLHttpRequest'
    }
  })
    .then(function (r) {
      return r.text().then(function (text) {
        var j = null;
        try {
          j = text ? JSON.parse(text) : null;
        } catch (e) {
          j = null;
        }
        return { ok: r.ok, status: r.status, body: j, raw: text };
      });
    })
    .then(function (res) {
      if (res.status === 401 || (res.body && res.body.error === 'unauthenticated')) {
        window.location.href = (cfg.contextPath || '') + '/auth/login?expired=1';
        return;
      }
      var data = res.body;
      if (!data || data.ok !== true) {
        var code = data && data.error ? data.error : 'unknown';
        var msg = data && data.message ? data.message : 'Could not authorize this meeting.';
        if (!data && !res.ok) {
          msg = 'Unexpected response from server. Your session may have expired — try logging in again.';
        }
        if (code === 'access_denied' || res.status === 403) {
          window.location.href = (cfg.contextPath || '') + '/instructor/sessions?errorMessage=' + encodeURIComponent('You do not have access to host this session.');
          return;
        }
        if (code === 'sdk_not_configured' && cfg.fallbackUrl) {
          window.location.href = cfg.fallbackUrl;
          return;
        }
        if ((code === 'passcode_missing' || code === 'meeting_id_missing') && cfg.fallbackUrl) {
          fallbackBtn.href = cfg.fallbackUrl;
          fallbackBtn.style.display = 'inline-flex';
        }
        showError('Unable to host in the browser', msg, null);
        return;
      }

      if (!data.sdkKey || String(data.sdkKey).trim() === '') {
        showError('Meeting configuration error', 'Missing Zoom SDK key from server. Try Open in Zoom or contact support.', null);
        if (cfg.fallbackUrl) {
          fallbackBtn.href = cfg.fallbackUrl;
          fallbackBtn.style.display = 'inline-flex';
        }
        return;
      }

      /* Zoom Web SDK validates leaveUrl against the current page origin. */
      var leavePath = (cfg.contextPath || '') + (cfg.leavePath || '/instructor/sessions');
      var leaveUrl = window.location.origin + leavePath;
      if (cfg.leaveUrl && String(cfg.leaveUrl).indexOf('://') > 0) {
        try {
          var cfgU = new URL(cfg.leaveUrl);
          if (cfgU.origin === window.location.origin) {
            leaveUrl = cfg.leaveUrl;
          } else {
            console.warn(ZLOG, 'Overriding leaveUrl to match page origin (was:', cfg.leaveUrl, ')');
          }
        } catch (e1) {
          console.warn(ZLOG, 'Using origin-based leaveUrl; server leaveUrl not parseable');
        }
      }
      var mn = data.meetingNumber;
      if (mn != null && typeof mn !== 'string') {
        mn = String(mn);
      }
      if (mn) {
        mn = mn.trim();
      }
      var sdkKeyStr = String(data.sdkKey).trim();
      var uname = (data.userName || cfg.defaultUserName || 'Instructor').trim();
      var roleHost = typeof data.jwtRole === 'number' ? data.jwtRole : 1;
      var sig = data.signature ? String(data.signature) : '';
      var passPlain = data.passWord != null ? String(data.passWord) : '';
      if (data.signedWith) {
        console.log(ZLOG, 'API says JWT signed with:', data.signedWith, '(expect meeting_sdk_secret, not OAuth)');
      }
      console.log(ZLOG, 'ZoomMtg.join PRE-CALL role=', roleHost, 'meetingNumber=', mn, 'hasZak=', !!data.zak);
      try {
        var ou = new URL(leaveUrl);
        var loc = window.location;
        if (ou.origin !== loc.origin) {
          showError('Configuration error', 'leaveUrl origin must match this page (' + loc.origin + ').', null);
          return;
        }
      } catch (urlErr) {
        showError('Configuration error', 'leaveUrl is not a valid URL.', null);
        return;
      }

      var joinWatch = null;
      var clearJoinWatch = function () {
        if (joinWatch) {
          clearTimeout(joinWatch);
          joinWatch = null;
        }
      };

      ZoomMtg.init({
        leaveUrl: leaveUrl,
        patchJsMedia: true,
        debug: !!cfg.zoomWebDebug,
        isShowJoiningErrorDialog: true,
        success: function () {
          console.log(ZLOG, 'ZoomMtg.init success');
          joinWatch = setTimeout(function () {
            console.error(ZLOG, 'ZoomMtg.join success callback not fired within 90s.');
            showError('Start timed out', 'Zoom did not report a successful start within 90 seconds. Typical causes: invalid Meeting SDK signature, missing ZAK token, or wrong meeting number/passcode. See Technical detail if the SDK surfaced an error.', null);
            if (cfg.fallbackUrl) {
              fallbackBtn.href = cfg.fallbackUrl;
              fallbackBtn.style.display = 'inline-flex';
            }
          }, 90000);

          var joinArgs = {
            sdkKey: sdkKeyStr,
            apiKey: sdkKeyStr,
            signature: sig,
            meetingNumber: mn,
            userName: uname,
            userEmail: '',
            success: function (joinRes) {
              console.log(ZLOG, 'ZoomMtg.join success', joinRes);
              clearJoinWatch();
              hideStatus();
            },
            error: function (err) {
              clearJoinWatch();
              console.error(ZLOG, 'ZoomMtg.join error', err);
              showError('Zoom SDK: start failed', 'The meeting could not be started in the browser.', err);
            }
          };
          if (passPlain.length > 0) {
            joinArgs.passWord = passPlain;
          }
          /* ZAK is required to start a meeting as host before it is running. */
          if (data.zak) {
            joinArgs.zak = String(data.zak);
          }
          ZoomMtg.join(joinArgs);
        },
        error: function (err) {
          clearJoinWatch();
          console.error(ZLOG, 'ZoomMtg.init error', err);
          showError('Zoom SDK: init failed', 'The meeting viewer could not start.', err);
        }
      });
    })
    .catch(function () {
      showError('Network error', 'Could not reach the server to start the meeting.', null);
    });
  }).catch(function (err) {
    showError('Meeting viewer failed to start', 'Prepare step failed — try Open in Zoom.', err);
  });
  }
})();
</script>
</body>
</html>
