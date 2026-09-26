document.addEventListener("DOMContentLoaded", () => {
  const toggle = document.querySelector("[data-landing-nav-toggle]");
  const panel = document.querySelector("[data-landing-nav-panel]");

  if (!toggle || !panel) {
    return;
  }

  const closeMenu = () => {
    toggle.classList.remove("is-open");
    panel.classList.remove("is-open");
    toggle.setAttribute("aria-expanded", "false");
  };

  toggle.addEventListener("click", () => {
    const isOpen = panel.classList.toggle("is-open");
    toggle.classList.toggle("is-open", isOpen);
    toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
  });

  panel.querySelectorAll("a").forEach((link) => {
    link.addEventListener("click", () => {
      if (window.innerWidth <= 860) {
        closeMenu();
      }
    });
  });

  window.addEventListener("resize", () => {
    if (window.innerWidth > 860) {
      closeMenu();
    }
  });
});
