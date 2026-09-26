/*
 * e-Tasmi — Student workspace navigation.
 *
 * Handles two orthogonal sidebar states:
 *   • Mobile (≤1100px): slide-in overlay drawer, toggled via body.student-nav-open
 *   • Desktop (≥1101px): collapsible icon rail, toggled via body.student-nav-collapsed
 *
 * The collapsed state is persisted in localStorage under
 * 'etasmi-student-nav-collapsed' ('1' = collapsed, '0' or absent = expanded),
 * and pre-applied in student_ui_head.jspf via data-student-nav="collapsed"
 * on <html> to avoid first-paint flicker.
 */
(function () {
  'use strict';

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  var STORAGE_KEY = 'etasmi-student-nav-collapsed';
  var DESKTOP_MIN_WIDTH = 1101;

  function query(selector, root) {
    return (root || document).querySelector(selector);
  }

  function queryAll(selector, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(selector));
  }

  function isDesktop() {
    return window.innerWidth >= DESKTOP_MIN_WIDTH;
  }

  function safeStorageGet() {
    try { return window.localStorage.getItem(STORAGE_KEY); } catch (e) { return null; }
  }

  function safeStorageSet(value) {
    try { window.localStorage.setItem(STORAGE_KEY, value); } catch (e) { /* ignore */ }
  }

  function initStudentWorkspaceNav() {
    var body = document.body;
    var root = document.documentElement;
    var sidebar = query('[data-student-sidebar]');
    var toggles = queryAll('[data-student-nav-toggle]');
    var dismissers = queryAll('[data-student-nav-dismiss]');

    if (!body || !sidebar || toggles.length === 0) {
      return;
    }

    // Sync the collapsed state that was applied by the pre-paint script in
    // student_ui_head.jspf. The inline script puts it on <html>, we lift it
    // to <body> here so all CSS selectors can use body.student-nav-collapsed.
    if (root.getAttribute('data-student-nav') === 'collapsed' && isDesktop()) {
      body.classList.add('student-nav-collapsed');
    }
    root.removeAttribute('data-student-nav');

    // --- Mobile drawer (body.student-nav-open) ---
    function setDrawerOpen(isOpen) {
      body.classList.toggle('student-nav-open', !!isOpen);
    }

    // --- Desktop rail (body.student-nav-collapsed) ---
    function setCollapsed(isCollapsed, persist) {
      body.classList.toggle('student-nav-collapsed', !!isCollapsed);
      if (persist !== false) {
        safeStorageSet(isCollapsed ? '1' : '0');
      }
    }

    // --- Toggle ARIA state ---
    function refreshToggleAria() {
      var expanded;
      if (isDesktop()) {
        expanded = !body.classList.contains('student-nav-collapsed');
      } else {
        expanded = body.classList.contains('student-nav-open');
      }
      toggles.forEach(function (toggle) {
        toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
      });
    }

    // --- Click handlers ---
    toggles.forEach(function (toggle) {
      toggle.addEventListener('click', function (event) {
        event.preventDefault();
        if (isDesktop()) {
          setCollapsed(!body.classList.contains('student-nav-collapsed'), true);
        } else {
          setDrawerOpen(!body.classList.contains('student-nav-open'));
        }
        refreshToggleAria();
      });
    });

    dismissers.forEach(function (node) {
      node.addEventListener('click', function () {
        setDrawerOpen(false);
        refreshToggleAria();
      });
    });

    // Close the mobile drawer after picking a link. Desktop clicks are not
    // intercepted so navigation happens normally.
    queryAll('.student-shell-sidebar__link', sidebar).forEach(function (link) {
      link.addEventListener('click', function () {
        if (!isDesktop()) {
          setDrawerOpen(false);
          refreshToggleAria();
        }
      });
    });

    // Esc closes mobile drawer.
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && body.classList.contains('student-nav-open')) {
        setDrawerOpen(false);
        refreshToggleAria();
      }

      // Ctrl/Cmd + B toggles the desktop rail — VS Code / Notion convention.
      if ((event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey
          && (event.key === 'b' || event.key === 'B')) {
        if (isDesktop()) {
          event.preventDefault();
          setCollapsed(!body.classList.contains('student-nav-collapsed'), true);
          refreshToggleAria();
        }
      }
    });

    // On resize, make sure the states remain mutually coherent.
    window.addEventListener('resize', function () {
      if (isDesktop()) {
        setDrawerOpen(false);
        // Re-apply persisted collapsed state when crossing the breakpoint.
        var stored = safeStorageGet();
        setCollapsed(stored === '1', false);
      } else {
        // Mobile never uses the collapsed-rail state.
        body.classList.remove('student-nav-collapsed');
      }
      refreshToggleAria();
    });

    refreshToggleAria();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initStudentWorkspaceNav);
  } else {
    initStudentWorkspaceNav();
  }
})();
