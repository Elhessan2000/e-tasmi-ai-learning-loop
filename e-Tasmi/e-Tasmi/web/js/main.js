document.addEventListener("DOMContentLoaded", function () {
  bindRevealOnScroll();
  bindScrollState();
  bindProgressBars();
  bindCounters();
  bindNavToggles();
});

function bindRevealOnScroll() {
  var revealNodes = document.querySelectorAll("[data-reveal]");
  if (!revealNodes.length) {
    return;
  }

  revealNodes.forEach(function (node) {
    var delay = node.getAttribute("data-reveal-delay");
    if (delay) {
      node.style.setProperty("--reveal-delay", delay + "ms");
    }
  });

  if (!("IntersectionObserver" in window)) {
    revealNodes.forEach(function (node) {
      node.classList.add("is-visible");
    });
    return;
  }

  var observer = new IntersectionObserver(function (entries) {
    entries.forEach(function (entry) {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.15 });

  revealNodes.forEach(function (node) {
    observer.observe(node);
  });
}

function bindScrollState() {
  function apply() {
    if (window.scrollY > 24) {
      document.body.classList.add("is-scrolled");
    } else {
      document.body.classList.remove("is-scrolled");
    }
  }

  apply();
  window.addEventListener("scroll", apply, { passive: true });
}

function bindProgressBars() {
  document.querySelectorAll(".sd-progressbar[data-progress], .progress-bar[data-progress]").forEach(function (bar) {
    var raw = parseInt(bar.getAttribute("data-progress"), 10);
    if (isNaN(raw)) {
      raw = 0;
    }
    raw = Math.min(100, Math.max(0, raw));
    var inner = bar.querySelector("span");
    if (inner) {
      inner.style.width = raw + "%";
    }
  });
}

function bindCounters() {
  var roots = document.querySelectorAll("[data-counters=\"1\"]");
  if (!roots.length) {
    return;
  }

  roots.forEach(function (root) {
    var counters = root.querySelectorAll("[data-counter=\"1\"][data-target]");
    if (!counters.length) {
      return;
    }

    function animate() {
      counters.forEach(function (counter) {
        if (counter.getAttribute("data-counter-run") === "1") {
          return;
        }
        counter.setAttribute("data-counter-run", "1");

        var target = parseInt(counter.getAttribute("data-target"), 10);
        if (isNaN(target)) {
          target = 0;
        }
        target = Math.max(0, target);

        var duration = 850;
        var startTime = null;

        function step(timestamp) {
          if (!startTime) {
            startTime = timestamp;
          }
          var progress = Math.min(1, (timestamp - startTime) / duration);
          counter.textContent = String(Math.floor(target * progress));
          if (progress < 1) {
            window.requestAnimationFrame(step);
          } else {
            counter.textContent = String(target);
          }
        }

        window.requestAnimationFrame(step);
      });
    }

    if ("IntersectionObserver" in window) {
      var observer = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            animate();
            observer.disconnect();
          }
        });
      }, { threshold: 0.2 });
      observer.observe(root);
    } else {
      animate();
    }
  });
}

function bindNavToggles() {
  var toggles = document.querySelectorAll("[data-nav-toggle]");
  if (!toggles.length) {
    return;
  }

  toggles.forEach(function (toggle) {
    var topbar = toggle.closest(".topbar");
    var panel = topbar ? topbar.querySelector("[data-nav-panel]") : null;
    if (!panel) {
      return;
    }

    function setOpen(isOpen) {
      panel.classList.toggle("is-open", isOpen);
      toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
    }

    toggle.addEventListener("click", function () {
      var isOpen = toggle.getAttribute("aria-expanded") === "true";
      setOpen(!isOpen);
    });

    document.addEventListener("click", function (event) {
      if (!topbar.contains(event.target)) {
        setOpen(false);
      }
    });

    window.addEventListener("resize", function () {
      if (window.innerWidth > 820) {
        setOpen(false);
      }
    });
  });
}
