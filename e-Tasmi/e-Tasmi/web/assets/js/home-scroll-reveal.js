/* e-Tasmi — scroll-reveal controller for home.jsp.
 * Minimal, dependency-free. Uses IntersectionObserver to toggle the
 * `.is-visible` class on `[data-reveal]` elements as they enter view,
 * and auto-assigns a stagger index to children of `[data-reveal-group]`.
 *
 * Progressive enhancement: the hidden initial state in CSS is gated on
 * `html.etasmi-reveal-ready`, which is only added when we can animate.
 * No JS / no IO support / reduced motion => content stays visible. */
(function () {
  "use strict";

  var root = document.documentElement;
  var prefersReduced =
    window.matchMedia &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  // Bail out gracefully: leave everything visible, skip hidden state.
  if (prefersReduced || !("IntersectionObserver" in window)) {
    return;
  }

  // Opt in to the hidden-until-revealed state only now that we can reveal.
  root.classList.add("etasmi-reveal-ready");

  function assignStagger() {
    var groups = document.querySelectorAll("[data-reveal-group]");
    for (var g = 0; g < groups.length; g++) {
      var items = groups[g].querySelectorAll("[data-reveal]");
      for (var i = 0; i < items.length; i++) {
        // Respect an author-set index; otherwise derive from DOM order.
        if (!items[i].style.getPropertyValue("--item-index")) {
          items[i].style.setProperty("--item-index", String(i));
        }
      }
    }
  }

  function init() {
    var targets = document.querySelectorAll("[data-reveal]");
    if (!targets.length) {
      return;
    }

    assignStagger();

    var observer = new IntersectionObserver(
      function (entries, obs) {
        for (var i = 0; i < entries.length; i++) {
          var entry = entries[i];
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            obs.unobserve(entry.target); // reveal once, then release.
          }
        }
      },
      {
        root: null,
        rootMargin: "0px 0px -10% 0px",
        threshold: 0.15
      }
    );

    for (var t = 0; t < targets.length; t++) {
      observer.observe(targets[t]);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
