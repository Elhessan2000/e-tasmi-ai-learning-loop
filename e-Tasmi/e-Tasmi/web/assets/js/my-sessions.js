(function () {
  var tabs = document.querySelectorAll(".mys-tabs__btn[data-mys-tab]");
  if (!tabs.length) return;

  function panelFor(name) {
    return document.getElementById("mys-panel-" + name);
  }

  function activate(name) {
    tabs.forEach(function (btn) {
      var on = btn.getAttribute("data-mys-tab") === name;
      btn.setAttribute("aria-selected", on ? "true" : "false");
    });
    ["upcoming", "completed"].forEach(function (key) {
      var el = panelFor(key);
      if (!el) return;
      el.hidden = key !== name;
    });
  }

  tabs.forEach(function (btn) {
    btn.addEventListener("click", function () {
      var name = btn.getAttribute("data-mys-tab");
      if (name) activate(name);
    });
  });
})();
