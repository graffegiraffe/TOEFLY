// "Create account" stays disabled until the required consents are checked (AC-01.01.4); the server checks them too.
(function () {
  function init() {
    var form = document.getElementById("kc-register-form") || document.getElementById("kc-update-profile-form");
    if (!form) {
      return;
    }
    var student = form.querySelector('input[name="accountType"][value="STUDENT"]');
    var anyType = form.querySelector('input[name="accountType"]:checked');
    if (student && !anyType) {
      student.checked = true;
    }
    var consents = ["termsAccepted", "privacyAccepted", "ageConfirmed"]
      .map(function (name) { return form.querySelector('input[name="' + name + '"]'); })
      .filter(Boolean);
    var submit = form.querySelector('[type="submit"]');
    if (!submit || consents.length === 0) {
      return;
    }
    function update() {
      submit.disabled = !consents.every(function (box) { return box.checked; });
    }
    consents.forEach(function (box) { box.addEventListener("change", update); });
    update();
  }
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
