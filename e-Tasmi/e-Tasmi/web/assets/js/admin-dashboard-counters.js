(function () {
  function easeOutCubic(t) {
    return 1 - Math.pow(1 - t, 3);
  }

  function prefersReducedMotion() {
    try {
      return window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    } catch (e) {
      return false;
    }
  }

  function runCounter(el) {
    var raw = el.getAttribute("data-target");
    var target = parseInt(raw, 10);
    if (isNaN(target)) target = 0;
    if (prefersReducedMotion()) {
      el.textContent = String(target);
      return;
    }
    if (target === 0) {
      el.textContent = "0";
      return;
    }
    var duration = Math.min(1100, 420 + Math.min(target, 400) * 1.6);
    var start = null;
    function step(ts) {
      if (start === null) start = ts;
      var p = Math.min(1, (ts - start) / duration);
      el.textContent = String(Math.round(easeOutCubic(p) * target));
      if (p < 1) {
        requestAnimationFrame(step);
      }
    }
    requestAnimationFrame(step);
  }

  document.addEventListener("DOMContentLoaded", function () {
    if (!document.body.classList.contains("admin-dashboard-page")) return;
    document.querySelectorAll(".counter-value").forEach(runCounter);
  });
})();
