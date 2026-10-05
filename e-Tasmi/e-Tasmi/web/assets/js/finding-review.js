/*
 * Instructor finding review workspace.
 * Every decision goes through the existing /instructor/evaluations actions; the server
 * answers with the recitation's current findings, which replace the local copy.
 */
(function () {
  'use strict';

  var STORE_KEY = 'lx-eval-last-action';
  var TYPES = ['MISSING_WORD', 'INCORRECT_WORD', 'EXTRA_WORD', 'PASSAGE_MISMATCH', 'PRONUNCIATION_OBSERVATION', 'OTHER'];
  var TYPE_META = {
    MISSING_WORD: { tone: 'missing', one: ['typeMissingWord', 'Missing word'], group: ['missingWords', 'Missing words'], tag: ['tagMissing', 'Missing'] },
    INCORRECT_WORD: { tone: 'incorrect', one: ['typeIncorrectWord', 'Incorrect word'], group: ['incorrectWords', 'Incorrect words'], tag: ['tagIncorrect', 'Incorrect'] },
    EXTRA_WORD: { tone: 'extra', one: ['typeExtraWord', 'Extra word'], group: ['extraWords', 'Extra words'], tag: ['tagExtra', 'Extra'] },
    PASSAGE_MISMATCH: { tone: 'passage', one: ['typePassageMismatch', 'Passage mismatch'], group: ['typePassageMismatch', 'Passage mismatch'], tag: ['tagPassage', 'Passage'] },
    PRONUNCIATION_OBSERVATION: { tone: 'pronunciation', one: ['typePronunciation', 'Pronunciation observation'], group: ['groupPronunciation', 'Pronunciation observations'], tag: ['tagPronunciation', 'Pronunciation'] },
    OTHER: { tone: 'other', one: ['typeOther', 'Other'], group: ['typeOther', 'Other'], tag: ['tagOther', 'Other'] }
  };
  var STATUS_META = {
    PENDING: { tone: 'pending', key: 'shortPending', text: 'Pending', mark: '\u25CF' },
    ACCEPTED: { tone: 'ok', key: 'shortAccepted', text: 'Accepted', mark: '\u2713' },
    EDITED: { tone: 'ok', key: 'shortEdited', text: 'Edited', mark: '\u2713' },
    REJECTED: { tone: 'none', key: 'shortRejected', text: 'Rejected', mark: '\u2715' },
    INSTRUCTOR_ADDED: { tone: 'ok', key: 'shortAdded', text: 'Added', mark: '\u2713' }
  };
  var ADDABLE_TYPES = ['MISSING_WORD', 'INCORRECT_WORD', 'EXTRA_WORD', 'PRONUNCIATION_OBSERVATION', 'OTHER'];

  var ICON = {
    check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    cross: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg>',
    pen: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 20h4L19 9l-4-4L4 16v4z" stroke-linejoin="round"/></svg>',
    prev: '<svg class="lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M15 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    next: '<svg class="lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    send: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true"><path d="M4.5 12L20 4.5 16 20l-4.2-6.3L4.5 12z" stroke-linejoin="round"/><path d="M11.8 13.7L20 4.5" stroke-linecap="round"/></svg>',
    ai: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3.5l1.9 4.6 4.6 1.9-4.6 1.9L12 16.5l-1.9-4.6L5.5 10l4.6-1.9z" stroke-linejoin="round"/></svg>',
    you: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 20a7 7 0 0114 0" stroke-linecap="round"/></svg>'
  };

  function interpolate(text, vars) {
    if (!vars) return text;
    return String(text).replace(/\{\{\s*(\w+)\s*\}\}/g, function (m, k) { return vars[k] != null ? vars[k] : m; });
  }
  function tx(key, fallback, vars) {
    var full = 'instructor.evaluations.' + key;
    var out = window.EtasmiI18n && typeof EtasmiI18n.t === 'function' ? EtasmiI18n.t(full, vars) : null;
    return !out || out === full ? interpolate(fallback, vars) : out;
  }
  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function has(value) { return value != null && String(value).trim() !== ''; }
  function ayahOf(verseKey) {
    if (!has(verseKey)) return '';
    var colon = verseKey.lastIndexOf(':');
    return colon < 0 ? verseKey : verseKey.slice(colon + 1);
  }
  function whereOf(f) {
    var parts = [];
    if (has(f.verseKey)) parts.push(tx('ayahN', 'Ayah {{n}}', { n: ayahOf(f.verseKey) }));
    if (f.word != null) parts.push(tx('wordN', 'Word {{n}}', { n: f.word }));
    return parts.length ? parts.join(' \u00B7 ') : tx('unassignedPosition', 'Unassigned position');
  }
  function typeMeta(type) { return TYPE_META[type] || TYPE_META.OTHER; }
  function statusMeta(status) { return STATUS_META[status] || STATUS_META.PENDING; }
  function isPending(f) { return f.status === 'PENDING'; }
  function shownExpected(f) { return f.instructorOwned || f.status === 'EDITED' ? (has(f.iExpected) ? f.iExpected : f.expected) : f.expected; }
  function shownHeard(f) { return f.instructorOwned || f.status === 'EDITED' ? (has(f.iHeard) ? f.iHeard : f.heard) : f.heard; }

  function init(dialog) {
    var dataNode = dialog.querySelector('[data-lx-review-data]');
    var findings = [];
    try { findings = JSON.parse(dataNode ? dataNode.textContent : '[]') || []; } catch (err) { findings = []; }

    var recitationId = dialog.getAttribute('data-recitation-id') || '';
    var sessionId = dialog.getAttribute('data-session-id') || '';
    var actionUrl = dialog.getAttribute('data-action-url') || window.location.pathname;
    var frozen = dialog.getAttribute('data-frozen') === '1';
    var canAdd = dialog.getAttribute('data-can-add') === '1';
    var defaultVerse = dialog.getAttribute('data-default-verse') || '';

    var els = {
      title: dialog.querySelector('[data-lx-rw-title]'),
      sub: dialog.querySelector('[data-lx-rw-sub]'),
      overall: dialog.querySelector('[data-lx-rw-overall]'),
      tabs: dialog.querySelector('[data-lx-rw-tabs]'),
      list: dialog.querySelector('[data-lx-rw-list]'),
      detail: dialog.querySelector('[data-lx-rw-detail]')
    };

    var state = { filter: 'ALL', selectedId: null, mode: 'view', busy: false, dirty: false, error: '', notice: '', exit: 'review-return' };
    var openerScrollY = 0;
    var openerPct = null;

    function byId(id) {
      for (var i = 0; i < findings.length; i++) if (String(findings[i].id) === String(id)) return findings[i];
      return null;
    }
    function inFilter(f, filter) { return filter === 'ALL' || f.type === filter; }
    function visible() { return findings.filter(function (f) { return inFilter(f, state.filter); }); }
    function presentTypes() { return TYPES.filter(function (type) { return findings.some(function (f) { return f.type === type; }); }); }
    function pendingCount(list) { return list.filter(isPending).length; }
    function selected() { return byId(state.selectedId); }

    function permissions(f) {
      var decidable = !frozen;
      return {
        accept: decidable && f.status === 'PENDING' && !f.instructorOwned,
        edit: decidable && f.status !== 'REJECTED',
        reject: decidable && f.status !== 'REJECTED'
      };
    }

    /* Next pending after the current position in the open group, then in any other group. */
    function nextPending(fromId) {
      var list = visible();
      var start = 0;
      for (var i = 0; i < list.length; i++) if (String(list[i].id) === String(fromId)) { start = i + 1; break; }
      for (var k = 0; k < list.length; k++) {
        var candidate = list[(start + k) % list.length];
        if (isPending(candidate)) return { finding: candidate, filter: state.filter };
      }
      for (var t = 0; t < TYPES.length; t++) {
        var type = TYPES[t];
        if (type === state.filter) continue;
        for (var j = 0; j < findings.length; j++) {
          if (findings[j].type === type && isPending(findings[j])) {
            return { finding: findings[j], filter: state.filter === 'ALL' ? 'ALL' : type };
          }
        }
      }
      return null;
    }

    /* ---------- rendering ---------- */
    function render() {
      renderHeader();
      renderTabs();
      renderList();
      renderDetail();
    }

    function renderHeader() {
      var list = visible();
      var pending = pendingCount(list);
      els.title.textContent = state.filter === 'ALL'
        ? tx('allFindings', 'All findings')
        : tx(typeMeta(state.filter).group[0], typeMeta(state.filter).group[1]);
      var count = list.length === 1 ? tx('findingCountOne', '1 finding', { count: 1 }) : tx('findingCountMany', '{{count}} findings', { count: list.length });
      var progress = pending === 0
        ? '<span class="lx-rw__done">' + ICON.check + esc(tx('groupComplete', 'Complete')) + '</span>'
        : esc(tx('verifiedRemaining', '{{verified}} verified \u00B7 {{remaining}} remaining', { verified: list.length - pending, remaining: pending }));
      els.sub.innerHTML = esc(count) + '<span aria-hidden="true"> \u00B7 </span>' + progress;

      var total = findings.length;
      var totalPending = pendingCount(findings);
      var verified = total - totalPending;
      var pct = total === 0 ? 100 : Math.round(verified * 100 / total);
      var html = '<div class="lx-rw__meter' + (totalPending === 0 ? ' is-complete' : '') + '">'
        + '<span class="lx-rw__meterlabel">' + esc(tx('findingsVerifiedOfTotal', '{{count}} / {{total}} findings verified', { count: verified, total: total })) + '</span>'
        + '<span class="lx-rw__track" role="progressbar" aria-valuemin="0" aria-valuemax="' + total + '" aria-valuenow="' + verified + '"><span style="--lx-pct:' + pct + '%"></span></span>'
        + '</div>';
      if (!frozen && total > 0 && totalPending === 0) {
        html += '<button type="button" class="lx-btn lx-btn--primary lx-btn--sm" data-lx-rw-publish>' + ICON.send
          + '<span>' + esc(tx('continueToPublish', 'Continue to Publish')) + '</span></button>';
      }
      els.overall.innerHTML = html;
    }

    function renderTabs() {
      var types = presentTypes();
      if (types.length < 2) { els.tabs.hidden = true; els.tabs.innerHTML = ''; return; }
      els.tabs.hidden = false;
      var tabs = [{ id: 'ALL', label: tx('allFindings', 'All findings'), list: findings }];
      types.forEach(function (type) {
        tabs.push({ id: type, label: tx(typeMeta(type).group[0], typeMeta(type).group[1]), list: findings.filter(function (f) { return f.type === type; }) });
      });
      els.tabs.innerHTML = tabs.map(function (tab) {
        var pending = pendingCount(tab.list);
        var on = tab.id === state.filter;
        var badge = pending === 0
          ? '<span class="lx-rw__tabbadge is-done" aria-label="' + esc(tx('groupComplete', 'Complete')) + '">' + ICON.check + '</span>'
          : '<span class="lx-rw__tabbadge">' + pending + '</span>';
        return '<button type="button" role="tab" class="lx-rw__tab' + (on ? ' is-active' : '') + '" aria-selected="' + on + '" data-lx-rw-tab="' + tab.id + '">'
          + (tab.id === 'ALL' ? '' : '<i class="lx-tone--' + typeMeta(tab.id).tone + '" aria-hidden="true"></i>')
          + '<span>' + esc(tab.label) + '</span>' + badge + '</button>';
      }).join('');
    }

    function rowWord(f) {
      if (f.type === 'EXTRA_WORD' || f.type === 'INCORRECT_WORD') return shownHeard(f) || shownExpected(f);
      return shownExpected(f) || shownHeard(f);
    }

    function renderList() {
      var list = visible();
      var showType = state.filter === 'ALL';
      if (!list.length) {
        els.list.innerHTML = '<li class="lx-rw__listempty">' + esc(tx('noFindingsStored', 'No findings were stored for this analysis.')) + '</li>';
        return;
      }
      els.list.innerHTML = list.map(function (f) {
        var st = statusMeta(f.status);
        var on = String(f.id) === String(state.selectedId);
        var word = rowWord(f);
        return '<li><button type="button" class="lx-rw__row is-' + st.tone + (on ? ' is-selected' : '') + '" data-lx-rw-row="' + f.id + '"' + (on ? ' aria-current="true"' : '') + '>'
          + '<span class="lx-rw__mark" aria-hidden="true">' + st.mark + '</span>'
          + '<span class="lx-rw__rowmain"><span class="lx-rw__rowwhere">' + esc(whereOf(f)) + '</span>'
          + (showType ? '<span class="lx-rw__rowtype lx-tone--' + typeMeta(f.type).tone + '">' + esc(tx(typeMeta(f.type).tag[0], typeMeta(f.type).tag[1])) + '</span>' : '')
          + '</span>'
          + (has(word) ? '<span class="lx-rw__rowword lx-arabic" lang="ar" dir="rtl">' + esc(word) + '</span>' : '<span></span>')
          + '<span class="lx-rw__rowstatus">' + esc(tx(st.key, st.text)) + '</span>'
          + '</button></li>';
      }).join('');
      var current = els.list.querySelector('.is-selected');
      if (current && typeof current.scrollIntoView === 'function') current.scrollIntoView({ block: 'nearest' });
    }

    function pairCell(label, value, emptyLabel, cls) {
      return '<div class="' + cls + '"><dt>' + esc(label) + '</dt>'
        + (has(value)
          ? '<dd class="lx-arabic" lang="ar" dir="rtl">' + esc(value) + '</dd>'
          : '<dd class="is-empty">' + esc(emptyLabel) + '</dd>')
        + '</div>';
    }

    function renderNav(f) {
      var list = visible();
      var index = -1;
      for (var i = 0; i < list.length; i++) if (String(list[i].id) === String(f.id)) { index = i; break; }
      return '<footer class="lx-rw__nav">'
        + '<button type="button" class="lx-btn lx-btn--ghost lx-btn--sm" data-lx-rw-nav="-1"' + (index <= 0 ? ' disabled' : '') + '>' + ICON.prev + '<span>' + esc(tx('prevFinding', 'Previous')) + '</span></button>'
        + '<span class="lx-rw__pos">' + esc(tx('findingOf', 'Finding {{n}} of {{total}}', { n: index + 1, total: list.length })) + '</span>'
        + '<button type="button" class="lx-btn lx-btn--ghost lx-btn--sm" data-lx-rw-nav="1"' + (index < 0 || index >= list.length - 1 ? ' disabled' : '') + '><span>' + esc(tx('nextFinding', 'Next')) + '</span>' + ICON.next + '</button>'
        + '</footer>';
    }

    function renderView(f) {
      var tm = typeMeta(f.type);
      var st = statusMeta(f.status);
      var perms = permissions(f);
      var expected = shownExpected(f);
      var heard = shownHeard(f);
      var html = '<article class="lx-rw__card">';
      if (state.notice) html += '<p class="lx-rw__notice" role="status">' + esc(state.notice) + '</p>';
      html += '<div class="lx-rw__meta">'
        + '<span class="lx-rw__type lx-tone--' + tm.tone + '"><i aria-hidden="true"></i>' + esc(tx(tm.one[0], tm.one[1])) + '</span>'
        + (f.instructorOwned
          ? '<span class="lx-source lx-source--you">' + ICON.you + '<span>' + esc(tx('sourceInstructor', 'Instructor')) + '</span></span>'
          : '<span class="lx-source lx-source--ai">' + ICON.ai + '<span>' + esc(tx('sourceAi', 'AI')) + '</span></span>')
        + '<span class="lx-rw__status is-' + st.tone + '">' + esc(tx(st.key, st.text)) + '</span>'
        + '</div>';
      html += '<h3 class="lx-rw__where">' + esc(whereOf(f)) + (has(f.verseKey) ? ' <small dir="ltr">' + esc(f.verseKey) + '</small>' : '') + '</h3>';
      if (has(expected) || has(heard)) {
        html += '<dl class="lx-rw__pair">'
          + pairCell(tx('expectedLabel', 'Expected'), expected, f.type === 'EXTRA_WORD' ? tx('notInPassage', 'Not in the passage') : '\u2014', 'is-expected')
          + pairCell(tx('heardLabel', 'Heard'), heard, f.type === 'MISSING_WORD' ? tx('notRecited', 'Not recited') : '\u2014', 'is-heard')
          + '</dl>';
      }
      if (!f.instructorOwned && has(f.explanation)) {
        html += '<div class="lx-rw__explain"><b>' + esc(tx('aiExplanation', 'AI explanation')) + '</b><p>' + esc(f.explanation) + '</p></div>';
      }
      if (f.type === 'PRONUNCIATION_OBSERVATION') {
        html += '<p class="lx-rw__caution">' + esc(tx('pronunciationCaution', 'Unverified acoustic note. Listen to the recording before accepting it. This is not a tajw\u012Bd verdict.')) + '</p>';
      }
      var ownLines = [];
      if (!f.instructorOwned && f.status === 'EDITED') {
        if (has(f.iExpected) || has(f.iHeard)) ownLines.push('<span>' + esc(tx('correctionApplied', 'Your correction is shown above.')) + '</span>');
      }
      if (has(f.iExplanation)) ownLines.push('<span>' + esc(f.iExplanation) + '</span>');
      if (has(f.iNote)) ownLines.push('<span class="lx-note">' + esc(f.iNote) + '</span>');
      if (ownLines.length) {
        html += '<div class="lx-rw__own"><b>' + esc(tx(f.instructorOwned ? 'instructorNoteLabel' : 'instructorVersion', f.instructorOwned ? 'Instructor note' : 'Your version')) + '</b>' + ownLines.join('') + '</div>';
      }
      if (state.error) html += '<p class="lx-rw__error" role="alert">' + esc(state.error) + '</p>';
      if (frozen) {
        html += '<p class="lx-rw__frozen">' + esc(tx('findingsFrozen', 'The evaluation is saved, so these findings are final.')) + '</p>';
      } else if (perms.accept || perms.edit || perms.reject) {
        html += '<div class="lx-rw__actions">';
        if (perms.accept) {
          html += '<button type="button" class="lx-act lx-act--accept" data-lx-rw-act="accept"><span class="lx-btn__spinner" aria-hidden="true"></span>' + ICON.check
            + '<span>' + esc(tx('accept', 'Accept')) + '</span><kbd aria-hidden="true">A</kbd></button>';
        }
        if (perms.edit) {
          html += '<button type="button" class="lx-act lx-act--edit" data-lx-rw-act="edit">' + ICON.pen
            + '<span>' + esc(tx('edit', 'Edit')) + '</span><kbd aria-hidden="true">E</kbd></button>';
        }
        if (perms.reject) {
          html += '<button type="button" class="lx-act lx-act--reject" data-lx-rw-act="reject"><span class="lx-btn__spinner" aria-hidden="true"></span>' + ICON.cross
            + '<span>' + esc(f.instructorOwned ? tx('withdraw', 'Withdraw') : tx('reject', 'Reject')) + '</span><kbd aria-hidden="true">R</kbd></button>';
        }
        html += '</div>';
        if (!isPending(f)) {
          html += '<p class="lx-rw__hint">' + esc(tx('decisionReversible', 'You can still change this decision until the evaluation is saved.')) + '</p>';
        }
      }
      html += '</article>';
      html += renderNav(f);
      return html;
    }

    function field(id, label, control) {
      return '<div><label class="lx-label" for="' + id + '">' + label + '</label>' + control + '</div>';
    }

    function renderEdit(f) {
      var base = 'lx-rw-edit-' + f.id;
      var startExpected = has(f.iExpected) ? f.iExpected : (f.expected || '');
      var startHeard = has(f.iHeard) ? f.iHeard : (f.heard || '');
      var html = '<form class="lx-rw__card lx-rw__form" data-lx-rw-form="edit" novalidate>'
        + '<div class="lx-rw__formhead"><h3 class="lx-rw__where">' + esc(tx('editFinding', 'Edit finding')) + '</h3><span class="lx-rw__formwhere">'
        + esc(tx(typeMeta(f.type).one[0], typeMeta(f.type).one[1])) + ' \u00B7 ' + esc(whereOf(f)) + '</span></div>'
        + '<div class="lx-edit__grid">'
        + field(base + '-exp', esc(tx('correctedExpected', 'Corrected expected text')),
          '<input class="lx-input lx-arabic" lang="ar" dir="rtl" id="' + base + '-exp" name="instructorExpectedText" maxlength="255" value="' + esc(startExpected) + '"/>')
        + field(base + '-heard', esc(tx('correctedHeard', 'Corrected heard text')),
          '<input class="lx-input lx-arabic" lang="ar" dir="rtl" id="' + base + '-heard" name="instructorHeardText" maxlength="255" value="' + esc(startHeard) + '"/>')
        + '</div>'
        + field(base + '-why', esc(tx('explanationForStudent', 'Explanation for the student')),
          '<textarea class="lx-input" id="' + base + '-why" name="instructorExplanation" rows="2">' + esc(f.iExplanation || '') + '</textarea>')
        + field(base + '-note', '<span>' + esc(tx('noteLabel', 'Note')) + '</span> <small>' + esc(tx('optional', '(optional)')) + '</small>',
          '<textarea class="lx-input" id="' + base + '-note" name="instructorNote" rows="2">' + esc(f.iNote || '') + '</textarea>')
        + (state.error ? '<p class="lx-rw__error" role="alert">' + esc(state.error) + '</p>' : '')
        + '<div class="lx-edit__actions">'
        + '<button type="button" class="lx-btn lx-btn--ghost lx-btn--sm" data-lx-rw-cancel>' + esc(tx('cancel', 'Cancel')) + '</button>'
        + '<button type="submit" class="lx-btn lx-btn--primary lx-btn--sm"><span class="lx-btn__spinner" aria-hidden="true"></span><span>' + esc(tx('saveEdit', 'Save edit')) + '</span></button>'
        + '</div></form>';
      return html;
    }

    function renderAdd() {
      var base = 'lx-rw-add-' + recitationId;
      var current = selected();
      var verse = current && has(current.verseKey) ? current.verseKey : defaultVerse;
      var preset = state.filter !== 'ALL' && ADDABLE_TYPES.indexOf(state.filter) !== -1 ? state.filter : 'MISSING_WORD';
      var options = ADDABLE_TYPES.map(function (type) {
        return '<option value="' + type + '"' + (type === preset ? ' selected' : '') + '>' + esc(tx(typeMeta(type).one[0], typeMeta(type).one[1])) + '</option>';
      }).join('');
      return '<form class="lx-rw__card lx-rw__form" data-lx-rw-form="add" novalidate>'
        + '<div class="lx-rw__formhead"><h3 class="lx-rw__where">' + esc(tx('addInstructorFinding', 'Add Instructor Finding')) + '</h3>'
        + '<span class="lx-source lx-source--you">' + ICON.you + '<span>' + esc(tx('sourceInstructor', 'Instructor')) + '</span></span></div>'
        + '<p class="lx-note">' + esc(tx('addFindingNote', 'Saved as instructor-added. It goes to the student and does not block publishing.')) + '</p>'
        + '<div class="lx-edit__grid lx-edit__grid--3">'
        + field(base + '-type', esc(tx('findingType', 'Type')), '<select class="lx-input" id="' + base + '-type" name="findingType">' + options + '</select>')
        + field(base + '-verse', esc(tx('verseKeyLabel', 'Verse')), '<input class="lx-input" id="' + base + '-verse" name="verseKey" maxlength="12" placeholder="1:5" dir="ltr" value="' + esc(verse) + '"/>')
        + field(base + '-word', esc(tx('wordPosition', 'Word')), '<input class="lx-input" id="' + base + '-word" name="wordPosition" type="number" min="1" dir="ltr"/>')
        + '</div><div class="lx-edit__grid">'
        + field(base + '-exp', esc(tx('expectedLabel', 'Expected')), '<input class="lx-input lx-arabic" lang="ar" dir="rtl" id="' + base + '-exp" name="instructorExpectedText" maxlength="255"/>')
        + field(base + '-heard', esc(tx('heardLabel', 'Heard')), '<input class="lx-input lx-arabic" lang="ar" dir="rtl" id="' + base + '-heard" name="instructorHeardText" maxlength="255"/>')
        + '</div>'
        + field(base + '-why', esc(tx('whatYouObserved', 'What you observed')), '<textarea class="lx-input" id="' + base + '-why" name="instructorExplanation" rows="2"></textarea>')
        + (state.error ? '<p class="lx-rw__error" role="alert">' + esc(state.error) + '</p>' : '')
        + '<div class="lx-edit__actions">'
        + '<button type="button" class="lx-btn lx-btn--ghost lx-btn--sm" data-lx-rw-cancel>' + esc(tx('cancel', 'Cancel')) + '</button>'
        + '<button type="submit" class="lx-btn lx-btn--primary lx-btn--sm"><span class="lx-btn__spinner" aria-hidden="true"></span><span>' + esc(tx('addFinding', 'Add finding')) + '</span></button>'
        + '</div></form>';
    }

    function renderComplete() {
      var counts = { ACCEPTED: 0, EDITED: 0, REJECTED: 0, INSTRUCTOR_ADDED: 0 };
      findings.forEach(function (f) { if (counts[f.status] != null) counts[f.status]++; });
      var items = ['ACCEPTED', 'EDITED', 'REJECTED', 'INSTRUCTOR_ADDED'].filter(function (s) { return counts[s] > 0; }).map(function (s) {
        return '<li class="is-' + statusMeta(s).tone + '"><strong>' + counts[s] + '</strong><span>' + esc(tx(statusMeta(s).key, statusMeta(s).text)) + '</span></li>';
      }).join('');
      return '<article class="lx-rw__card lx-rw__complete">'
        + '<span class="lx-rw__completemark" aria-hidden="true">' + ICON.check + '</span>'
        + '<h3>' + esc(tx('reviewComplete', 'All findings verified')) + '</h3>'
        + '<p>' + esc(tx('reviewCompleteSub', 'Every finding has a decision. Continue to the final evaluation to add the score and publish.')) + '</p>'
        + (items ? '<ul class="lx-rw__tally">' + items + '</ul>' : '')
        + '<div class="lx-rw__completeactions">'
        + '<button type="button" class="lx-btn lx-btn--primary" data-lx-rw-publish>' + ICON.send + '<span>' + esc(tx('continueToPublish', 'Continue to Publish')) + '</span></button>'
        + '<button type="button" class="lx-btn lx-btn--ghost" data-lx-rw-revisit>' + esc(tx('reviewDecisions', 'Review decisions')) + '</button>'
        + '</div></article>';
    }

    function renderDetail() {
      var f = selected();
      var html;
      if (state.mode === 'add') html = renderAdd();
      else if (state.mode === 'complete') html = renderComplete();
      else if (!f) html = '<p class="lx-rw__placeholder">' + esc(findings.length ? tx('selectFinding', 'Select a finding to review it.') : tx('noFindingsStored', 'No findings were stored for this analysis.')) + '</p>';
      else if (state.mode === 'edit') html = renderEdit(f);
      else html = renderView(f);
      els.detail.innerHTML = html;
      if (state.busy) setBusy(true);
    }

    function setBusy(on) {
      state.busy = on;
      dialog.classList.toggle('is-busy', on);
      els.detail.querySelectorAll('button, input, textarea, select').forEach(function (el) { el.disabled = on; });
      if (on && state.pendingButton) {
        var button = els.detail.querySelector(state.pendingButton);
        if (button) button.classList.add('is-busy');
      }
    }

    /* ---------- selection ---------- */
    function select(id, opts) {
      state.selectedId = id;
      state.mode = 'view';
      state.error = '';
      if (!opts || !opts.keepNotice) state.notice = '';
      renderList();
      renderDetail();
      if (opts && opts.focusDetail) focusDetail();
    }

    function focusDetail() {
      var primary = els.detail.querySelector('[data-lx-rw-act="accept"], [data-lx-rw-act="edit"], [data-lx-rw-act="reject"], [data-lx-rw-publish]');
      if (primary && !isNarrow()) primary.focus({ preventScroll: true });
      else els.detail.focus({ preventScroll: true });
      if (isNarrow()) els.detail.scrollIntoView({ block: 'start', behavior: 'smooth' });
    }

    function isNarrow() { return window.matchMedia && window.matchMedia('(max-width: 860px)').matches; }

    function step(delta) {
      var list = visible();
      var index = -1;
      for (var i = 0; i < list.length; i++) if (String(list[i].id) === String(state.selectedId)) { index = i; break; }
      var target = list[index + delta];
      if (target) select(target.id);
    }

    function setFilter(filter) {
      state.filter = filter;
      var list = visible();
      var current = selected();
      if (!current || !inFilter(current, filter)) {
        var firstPending = list.filter(isPending)[0];
        state.selectedId = (firstPending || list[0] || {}).id || null;
      }
      state.mode = state.selectedId ? 'view' : state.mode;
      state.notice = '';
      state.error = '';
      render();
    }

    /* ---------- server round-trips ---------- */
    function post(params) {
      var body = new URLSearchParams();
      body.set('recitationId', recitationId);
      body.set('sessionId', sessionId);
      Object.keys(params).forEach(function (key) { if (params[key] != null) body.set(key, params[key]); });
      return fetch(actionUrl, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        },
        body: body.toString()
      }).then(function (res) {
        return res.json().catch(function () { return null; }).then(function (data) { return { status: res.status, data: data }; });
      });
    }

    function failureText(result) {
      if (result && result.status === 401) return tx('sessionExpired', 'Your session has expired. Reload the page and sign in again.');
      if (result && result.status === 403) return tx('actionForbidden', 'You do not have access to this recitation.');
      if (result && result.data && result.data.error) return result.data.error;
      return tx('actionFailed', 'The change could not be saved. Check your connection and try again.');
    }

    function run(params, buttonSelector, onSuccess) {
      if (state.busy) return;
      state.error = '';
      state.pendingButton = buttonSelector;
      setBusy(true);
      post(params).then(function (result) {
        state.pendingButton = null;
        setBusy(false);
        if (!result.data || !result.data.ok) {
          state.error = failureText(result);
          renderDetail();
          focusAfterError();
          return;
        }
        state.dirty = true;
        if (!dialog.open) { onClosed(); return; }
        if (!Array.isArray(result.data.findings)) { leave('review-return'); return; }
        var previousIds = findings.map(function (f) { return String(f.id); });
        findings = result.data.findings;
        onSuccess(previousIds);
      }).catch(function () {
        state.pendingButton = null;
        setBusy(false);
        state.error = failureText(null);
        renderDetail();
        focusAfterError();
      });
    }

    function focusAfterError() {
      var target = els.detail.querySelector('[data-lx-rw-form] input:not([type="hidden"]), [data-lx-rw-form] select, [data-lx-rw-act]');
      (target || els.detail).focus({ preventScroll: true });
    }

    function advanceFrom(id) {
      var decidedType = (byId(id) || {}).type;
      var next = nextPending(id);
      if (!next) {
        state.mode = 'complete';
        state.notice = '';
        render();
        focusDetail();
        return;
      }
      var notice = '';
      var groupDone = decidedType && next.finding.type !== decidedType
        && pendingCount(findings.filter(function (f) { return f.type === decidedType; })) === 0;
      if (groupDone) {
        notice = tx('groupDoneMoved', '{{done}} verified. Moved to {{next}}.', {
          done: tx(typeMeta(decidedType).group[0], typeMeta(decidedType).group[1]),
          next: tx(typeMeta(next.finding.type).group[0], typeMeta(next.finding.type).group[1])
        });
      }
      state.filter = next.filter;
      state.selectedId = next.finding.id;
      state.mode = 'view';
      state.notice = notice;
      render();
      focusDetail();
    }

    function decide(kind) {
      var f = selected();
      if (!f || frozen) return;
      var perms = permissions(f);
      if (kind === 'accept' && perms.accept) {
        run({ action: 'accept_finding', findingId: f.id }, '[data-lx-rw-act="accept"]', function () { advanceFrom(f.id); });
      } else if (kind === 'reject' && perms.reject) {
        run({ action: 'reject_finding', findingId: f.id }, '[data-lx-rw-act="reject"]', function () { advanceFrom(f.id); });
      } else if (kind === 'edit' && perms.edit) {
        state.mode = 'edit';
        state.error = '';
        renderDetail();
        var first = els.detail.querySelector('input, textarea');
        if (first) first.focus();
      }
    }

    function submitForm(form) {
      var kind = form.getAttribute('data-lx-rw-form');
      var values = {};
      Array.prototype.forEach.call(form.elements, function (el) { if (el.name) values[el.name] = el.value; });
      if (kind === 'edit') {
        var f = selected();
        if (!f) return;
        values.action = 'edit_finding';
        values.findingId = f.id;
        run(values, 'button[type="submit"]', function () { advanceFrom(f.id); });
      } else if (kind === 'add') {
        values.action = 'add_finding';
        run(values, 'button[type="submit"]', function (previousIds) {
          var added = findings.filter(function (row) { return previousIds.indexOf(String(row.id)) === -1; })[0];
          state.mode = 'view';
          if (added) {
            state.filter = state.filter === 'ALL' || state.filter === added.type ? state.filter : added.type;
            state.selectedId = added.id;
            state.notice = tx('findingAddedNotice', 'Finding added. It is already verified and goes to the student.');
          }
          render();
          focusDetail();
        });
      }
    }

    function cancelForm() {
      state.error = '';
      if (state.mode === 'add' && !selected()) {
        var first = visible()[0];
        state.selectedId = first ? first.id : null;
      }
      state.mode = 'view';
      renderDetail();
      focusDetail();
    }

    /* ---------- open / close ---------- */
    function open(filter, opts) {
      openerScrollY = window.scrollY;
      var bar = document.querySelector('[data-lx-progress]');
      openerPct = bar ? bar.getAttribute('data-pct') : null;
      state.filter = filter && presentTypes().indexOf(filter) !== -1 ? filter : 'ALL';
      state.exit = 'review-return';
      state.error = '';
      state.notice = '';
      var list = visible();
      var firstPending = list.filter(isPending)[0];
      state.selectedId = (firstPending || list[0] || {}).id || null;
      state.mode = opts && opts.add && canAdd ? 'add' : 'view';
      render();
      document.documentElement.classList.add('lx-rw-open');
      if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open', '');
      if (state.mode === 'add') {
        var firstField = els.detail.querySelector('select, input');
        if (firstField) firstField.focus();
      } else {
        focusDetail();
      }
    }

    function leave(exit) {
      state.exit = exit || 'review-return';
      if (typeof dialog.close === 'function' && dialog.open) dialog.close(); else { dialog.removeAttribute('open'); onClosed(); }
    }

    function onClosed() {
      document.documentElement.classList.remove('lx-rw-open');
      if (!state.dirty && state.exit !== 'continue-publish') return;
      if (!state.dirty && state.exit === 'continue-publish') {
        var form = document.querySelector('form[data-lx-eval-form]');
        if (form) {
          form.scrollIntoView({ behavior: 'smooth', block: 'start' });
          var score = form.querySelector('input[name="score"]');
          if (score) score.focus({ preventScroll: true });
        }
        return;
      }
      try {
        sessionStorage.setItem(STORE_KEY, JSON.stringify({
          recitation: recitationId,
          action: state.exit,
          scrollY: openerScrollY,
          pct: openerPct
        }));
      } catch (err) {}
      window.location.reload();
    }

    dialog.addEventListener('close', onClosed);
    dialog.addEventListener('cancel', function (e) {
      if (state.busy) { e.preventDefault(); return; }
      if (state.mode === 'edit' || state.mode === 'add') { e.preventDefault(); cancelForm(); return; }
      state.exit = 'review-return';
    });

    dialog.addEventListener('click', function (e) {
      if (e.target.closest('[data-lx-review-close]')) { leave('review-return'); return; }
      var publish = e.target.closest('[data-lx-rw-publish]');
      if (publish) { leave('continue-publish'); return; }
      if (state.busy) return;
      var tab = e.target.closest('[data-lx-rw-tab]');
      if (tab) { setFilter(tab.getAttribute('data-lx-rw-tab')); return; }
      var row = e.target.closest('[data-lx-rw-row]');
      if (row) { select(row.getAttribute('data-lx-rw-row'), { focusDetail: isNarrow() }); return; }
      var nav = e.target.closest('[data-lx-rw-nav]');
      if (nav && !nav.disabled) { step(parseInt(nav.getAttribute('data-lx-rw-nav'), 10)); return; }
      var act = e.target.closest('[data-lx-rw-act]');
      if (act && !act.disabled) { decide(act.getAttribute('data-lx-rw-act')); return; }
      if (e.target.closest('[data-lx-rw-cancel]')) { cancelForm(); return; }
      if (e.target.closest('[data-lx-rw-add]')) {
        state.mode = 'add';
        state.error = '';
        state.notice = '';
        renderDetail();
        var firstField = els.detail.querySelector('select, input');
        if (firstField) firstField.focus();
        if (isNarrow()) els.detail.scrollIntoView({ block: 'start', behavior: 'smooth' });
        return;
      }
      if (e.target.closest('[data-lx-rw-revisit]')) {
        var first = visible()[0] || findings[0];
        state.mode = 'view';
        if (first) select(first.id);
      }
    });

    dialog.addEventListener('submit', function (e) {
      var form = e.target.closest('[data-lx-rw-form]');
      if (!form) return;
      e.preventDefault();
      e.stopPropagation();
      submitForm(form);
    });

    document.addEventListener('keydown', function (e) {
      if (!dialog.open) return;
      /* Browsers may ignore preventDefault on the dialog's cancel event, so Escape is claimed here. */
      if (e.key === 'Escape' && (state.busy || state.mode === 'edit' || state.mode === 'add')) {
        e.preventDefault();
        if (!state.busy) cancelForm();
        return;
      }
      if (e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey) return;
      var tag = e.target && e.target.tagName;
      if (/INPUT|TEXTAREA|SELECT/.test(tag) || state.busy || state.mode !== 'view') return;
      var key = e.key;
      if (key === 'ArrowDown' || key === 'j') { e.preventDefault(); step(1); }
      else if (key === 'ArrowUp' || key === 'k') { e.preventDefault(); step(-1); }
      else if (key === 'a' || key === 'A') { e.preventDefault(); decide('accept'); }
      else if (key === 'e' || key === 'E') { e.preventDefault(); decide('edit'); }
      else if (key === 'r' || key === 'R') { e.preventDefault(); decide('reject'); }
    });

    return { open: open };
  }

  function boot() {
    var dialogs = document.querySelectorAll('dialog[data-lx-review]');
    if (!dialogs.length) return;
    var workspaces = {};
    dialogs.forEach(function (dialog) { workspaces[dialog.id] = init(dialog); });
    document.addEventListener('click', function (e) {
      var opener = e.target.closest('[data-lx-review-open]');
      if (!opener) return;
      var workspace = workspaces[opener.getAttribute('aria-controls')];
      if (!workspace) return;
      e.preventDefault();
      workspace.open(opener.getAttribute('data-lx-review-open'), { add: opener.hasAttribute('data-lx-review-add') });
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();
