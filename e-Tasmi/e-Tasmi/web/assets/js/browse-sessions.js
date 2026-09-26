(function () {
  "use strict";

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  function qs(sel, root) {
    return (root || document).querySelector(sel);
  }

  function qsa(sel, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(sel));
  }

  var overlay = qs("[data-browse-ss-modal]");
  var chips = qsa("[data-chip]");
  var filterSelect = qs("[data-browse-ss-filter]");
  var sortSelect = qs("[data-browse-ss-sort]");
  var grid = qs(".browse-ss-grid");
  var countEl = qs("[data-browse-ss-count]");
  var cards = qsa(".browse-ss-card");
  var lastModalBtn = null;
  var activeChip = { type: "all", value: "all" };

  function parseFilterValue(raw) {
    var parts = (raw || "all:all").split(":");
    return { type: parts[0] || "all", value: parts[1] || "all" };
  }

  function setActiveChip(type, value) {
    activeChip = { type: type, value: value };
    chips.forEach(function (chip) {
      var chipType = chip.getAttribute("data-chip-type") || "all";
      var chipValue = chip.getAttribute("data-chip-value") || "all";
      var isActive = chipType === type && chipValue === value;
      chip.classList.toggle("is-active", isActive);
      chip.setAttribute("aria-pressed", isActive ? "true" : "false");
    });
  }

  cards.forEach(function (card, i) {
    card.setAttribute("data-order", String(i));
  });

  function setOverlay(open) {
    if (!overlay) return;
    overlay.classList.toggle("is-open", open);
    overlay.setAttribute("aria-hidden", open ? "false" : "true");
    document.body.style.overflow = open ? "hidden" : "";
    if (open) {
      var c = qs(".browse-ss-modal__close", overlay);
      if (c) c.focus();
    }
  }

  function openModal(btn) {
    if (!overlay) return;
    lastModalBtn = btn;
    var title = btn.getAttribute("data-modal-title") || "";
    var instructor = btn.getAttribute("data-modal-instructor") || "";
    var date = btn.getAttribute("data-modal-date") || "";
    var time = btn.getAttribute("data-modal-time") || "";
    var mode = btn.getAttribute("data-modal-mode") || "";
    var fee = btn.getAttribute("data-modal-fee") || "";
    var description = btn.getAttribute("data-modal-description") || "";
    var checkout = btn.getAttribute("data-checkout-url") || "#";
    var isFree = btn.getAttribute("data-is-free") === "true";

    var elTitle = qs("[data-modal-field='title']", overlay);
    var elInstr = qs("[data-modal-field='instructor']", overlay);
    var elDate = qs("[data-modal-field='date']", overlay);
    var elTime = qs("[data-modal-field='time']", overlay);
    var elMode = qs("[data-modal-field='mode']", overlay);
    var elAmt = qs("[data-modal-field='amount']", overlay);
    var elDesc = qs("[data-modal-field='description']", overlay);
    var aboutBox = qs("[data-modal-about]", overlay);
    var elSub = qs("[data-modal-subtitle]", overlay);
    var confirm = qs(".browse-ss-modal__confirm", overlay);
    var paymentMethodRow = qs("[data-modal-payment-method-row]", overlay);

    if (elTitle) elTitle.textContent = title;
    if (elInstr) elInstr.textContent = instructor;
    if (elDate) elDate.textContent = date;
    if (elTime) elTime.textContent = time;
    if (elMode) elMode.textContent = mode;
    if (elAmt) elAmt.textContent = fee;
    if (elDesc) elDesc.textContent = description;
    if (aboutBox) {
      aboutBox.hidden = description.trim() === "";
    }
    if (elSub) {
      elSub.textContent = isFree
        ? t("student.browse.confirmSubtitleFree")
        : t("student.browse.confirmSubtitlePaid");
    }
    if (confirm) {
      confirm.setAttribute("href", checkout);
      confirm.textContent = isFree
        ? t("student.browse.confirmEnrollment")
        : t("student.browse.confirmPayment");
    }
    if (paymentMethodRow) {
      paymentMethodRow.style.display = isFree ? "none" : "";
    }

    setOverlay(true);
  }

  qsa("[data-browse-open-modal]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      openModal(btn);
    });
  });

  if (overlay) {
    var closeBtn = qs(".browse-ss-modal__close", overlay);
    if (closeBtn) {
      closeBtn.addEventListener("click", function () {
        setOverlay(false);
      });
    }
    overlay.addEventListener("click", function (e) {
      if (e.target === overlay) setOverlay(false);
    });
  }

  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && overlay && overlay.classList.contains("is-open")) {
      setOverlay(false);
    }
  });

  document.addEventListener("etasmi:localechange", function () {
    if (overlay && overlay.classList.contains("is-open") && lastModalBtn) {
      openModal(lastModalBtn);
    }
  });

  function cardMatchesChip(card) {
    if (activeChip.type === "all") return true;
    if (activeChip.type === "status") {
      var st = card.getAttribute("data-filter-status") || "";
      // Upcoming = scheduled but not started; Pending = payment awaiting verification.
      return st === activeChip.value;
    }
    return true;
  }

  function applyChipFilter() {
    var visible = 0;
    cards.forEach(function (card) {
      var show = cardMatchesChip(card);
      card.setAttribute("data-hidden-filter", show ? "0" : "1");
      if (show) visible++;
    });
    if (countEl) countEl.textContent = String(visible);
  }

  chips.forEach(function (chip) {
    chip.addEventListener("click", function () {
      var type = chip.getAttribute("data-chip-type") || "all";
      var value = chip.getAttribute("data-chip-value") || "all";
      setActiveChip(type, value);
      if (filterSelect) {
        filterSelect.value = type + ":" + value;
      }
      applyChipFilter();
    });
  });

  if (filterSelect) {
    filterSelect.addEventListener("change", function () {
      var parsed = parseFilterValue(filterSelect.value);
      setActiveChip(parsed.type, parsed.value);
      applyChipFilter();
    });
  }

  function num(value) {
    var n = parseFloat(value);
    return isNaN(n) ? 0 : n;
  }

  function attr(el, name) {
    return el.getAttribute(name) || "";
  }

  function applySort() {
    if (!grid || !sortSelect) return;
    var mode = sortSelect.value || "default";
    var sorted = cards.slice();
    sorted.sort(function (a, b) {
      switch (mode) {
        case "newest":
          return attr(b, "data-sort-date").localeCompare(attr(a, "data-sort-date"));
        case "oldest":
          return attr(a, "data-sort-date").localeCompare(attr(b, "data-sort-date"));
        case "popular":
          return num(attr(b, "data-sort-popular")) - num(attr(a, "data-sort-popular"));
        case "price_asc":
          return num(attr(a, "data-sort-price")) - num(attr(b, "data-sort-price"));
        case "price_desc":
          return num(attr(b, "data-sort-price")) - num(attr(a, "data-sort-price"));
        case "az":
          return attr(a, "data-sort-title").localeCompare(attr(b, "data-sort-title"));
        case "za":
          return attr(b, "data-sort-title").localeCompare(attr(a, "data-sort-title"));
        default:
          return num(attr(a, "data-order")) - num(attr(b, "data-order"));
      }
    });
    sorted.forEach(function (card) {
      grid.appendChild(card);
    });
  }

  if (sortSelect) {
    sortSelect.addEventListener("change", applySort);
  }

  applyChipFilter();
})();
