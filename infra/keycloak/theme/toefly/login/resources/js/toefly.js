// Sign-up form (AC-01.01.1, AC-01.01.4): "I'm preparing for TOEFL" is preselected, and "Create account" stays
// disabled until the required consents are checked; the server checks them too.
(function () {
  var CONSENTS = ["termsAccepted", "privacyAccepted", "ageConfirmed"];

  function init() {
    var form = document.getElementById("kc-register-form") || document.getElementById("kc-update-profile-form");
    if (!form) {
      return;
    }
    var student = form.querySelector('input[name="accountType"][value="STUDENT"]');
    if (student && !form.querySelector('input[name="accountType"]:checked')) {
      student.checked = true;
    }
    var boxes = CONSENTS.map(function (name) {
      return form.querySelector('input[type="checkbox"][name="' + name + '"]');
    }).filter(Boolean);
    var submit = form.querySelector('[type="submit"]');
    if (boxes.length === 0) {
      return;
    }
    function update() {
      boxes.forEach(function (box) {
        // The empty companion field of a checkbox group would otherwise reach Keycloak next to "accepted".
        var empty = document.getElementById(box.name + "-empty");
        if (empty) {
          empty.disabled = box.checked;
        }
      });
      if (submit) {
        submit.disabled = !boxes.every(function (box) { return box.checked; });
      }
    }
    boxes.forEach(function (box) { box.addEventListener("change", update); });
    update();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
