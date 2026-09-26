document.addEventListener("DOMContentLoaded", function () {
  bindTableSearch();
  bindConfirmActions();
});

function bindTableSearch() {
  document.querySelectorAll("[data-table-search]").forEach(function (input) {
    var targetSelector = input.getAttribute("data-table-search");
    var target = document.querySelector(targetSelector);
    if (!target) {
      return;
    }

    input.addEventListener("input", function () {
      var term = input.value.trim().toLowerCase();
      var rows = target.querySelectorAll("tr[data-search-row]");
      var visibleCount = 0;

      rows.forEach(function (row) {
        var haystack = (row.getAttribute("data-search-row") || "").toLowerCase();
        var visible = term === "" || haystack.indexOf(term) !== -1;
        row.style.display = visible ? "" : "none";
        if (visible) {
          visibleCount += 1;
        }
      });

      var emptyState = target.querySelector("[data-empty-search]");
      if (emptyState) {
        emptyState.style.display = visibleCount === 0 ? "" : "none";
      }
    });
  });
}

function bindConfirmActions() {
  document.querySelectorAll("form[data-confirm]").forEach(function (form) {
    form.addEventListener("submit", function (event) {
      var message = form.getAttribute("data-confirm");
      if (message && !window.confirm(message)) {
        event.preventDefault();
      }
    });
  });
}
