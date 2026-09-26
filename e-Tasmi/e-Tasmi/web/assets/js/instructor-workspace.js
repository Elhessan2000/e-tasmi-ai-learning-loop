/*
 * e-Tasmi — Instructor workspace navigation.
 *
 * Handles two orthogonal sidebar states:
 *   • Mobile (≤1100px): slide-in overlay drawer, toggled via body.instructor-nav-open
 *   • Desktop (≥1101px): collapsible icon rail, toggled via body.instructor-nav-collapsed
 *
 * The collapsed state is persisted in localStorage under
 * 'etasmi-instructor-nav-collapsed' ('1' = collapsed, '0' or absent = expanded),
 * and pre-applied in instructor_ui_head.jspf via data-instructor-nav="collapsed"
 * on <html> to avoid first-paint flicker.
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'etasmi-instructor-nav-collapsed';
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

  function syncRootNavAttr(root, isCollapsed) {
    if (!root) return;
    if (isCollapsed && isDesktop()) {
      root.setAttribute('data-instructor-nav', 'collapsed');
    } else {
      root.removeAttribute('data-instructor-nav');
    }
  }

  function initInstructorWorkspaceNav() {
    var body = document.body;
    var root = document.documentElement;
    var sidebar = query('[data-instructor-sidebar]');
    var toggles = queryAll('[data-instructor-nav-toggle]');
    var dismissers = queryAll('[data-instructor-nav-dismiss]');

    if (!body || !sidebar || toggles.length === 0) {
      return;
    }

    var shouldCollapse = root.getAttribute('data-instructor-nav') === 'collapsed' && isDesktop();
    if (shouldCollapse) {
      body.classList.add('instructor-nav-collapsed');
    }
    syncRootNavAttr(root, body.classList.contains('instructor-nav-collapsed'));

    function setDrawerOpen(isOpen) {
      body.classList.toggle('instructor-nav-open', !!isOpen);
    }

    function notifyLayoutShift() {
      window.dispatchEvent(new Event('resize'));
    }

    function setCollapsed(isCollapsed, persist) {
      body.classList.toggle('instructor-nav-collapsed', !!isCollapsed);
      syncRootNavAttr(root, !!isCollapsed);
      if (persist !== false) {
        safeStorageSet(isCollapsed ? '1' : '0');
      }
      window.requestAnimationFrame(notifyLayoutShift);
      window.setTimeout(notifyLayoutShift, 320);
    }

    function refreshToggleAria() {
      var expanded;
      if (isDesktop()) {
        expanded = !body.classList.contains('instructor-nav-collapsed');
      } else {
        expanded = body.classList.contains('instructor-nav-open');
      }
      toggles.forEach(function (toggle) {
        toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
      });
    }

    toggles.forEach(function (toggle) {
      toggle.addEventListener('click', function (event) {
        event.preventDefault();
        if (isDesktop()) {
          setCollapsed(!body.classList.contains('instructor-nav-collapsed'), true);
        } else {
          setDrawerOpen(!body.classList.contains('instructor-nav-open'));
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

    queryAll('.instructor-shell-sidebar__link', sidebar).forEach(function (link) {
      link.addEventListener('click', function () {
        if (!isDesktop()) {
          setDrawerOpen(false);
          refreshToggleAria();
        }
      });
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && body.classList.contains('instructor-nav-open')) {
        setDrawerOpen(false);
        refreshToggleAria();
      }

      if ((event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey
          && (event.key === 'b' || event.key === 'B')) {
        if (isDesktop()) {
          event.preventDefault();
          setCollapsed(!body.classList.contains('instructor-nav-collapsed'), true);
          refreshToggleAria();
        }
      }
    });

    window.addEventListener('resize', function () {
      if (isDesktop()) {
        setDrawerOpen(false);
        var stored = safeStorageGet();
        setCollapsed(stored === '1', false);
      } else {
        body.classList.remove('instructor-nav-collapsed');
        root.removeAttribute('data-instructor-nav');
      }
      refreshToggleAria();
    });

    refreshToggleAria();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initInstructorWorkspaceNav);
  } else {
    initInstructorWorkspaceNav();
  }
})();
