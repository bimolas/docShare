(function () {
  "use strict";

  function passwordResult(value) {
    return {
      len: value.length >= 8,
      upper: /[A-Z]/.test(value),
      lower: /[a-z]/.test(value),
      number: /\d/.test(value),
      special: /[^A-Za-z0-9]/.test(value)
    };
  }

  function updateStrength(input) {
    if (!input) return;
    var result = passwordResult(input.value);
    var score = [result.len, result.upper, result.lower, result.number, result.special].filter(Boolean).length;
    var feedback = document.getElementById("kc-password-feedback");
    var fill = document.getElementById("kc-strength-fill");
    var label = document.getElementById("kc-strength-label");

    if (feedback) {
      var visible = input.value.length > 0;
      feedback.classList.toggle("has-value", visible);
      feedback.setAttribute("aria-hidden", visible ? "false" : "true");
    }

    var colors = ["#e2e8f0", "#dc2626", "#fb923c", "#eab308", "#22c55e", "#16a34a"];
    var labels = ["", "Very weak", "Weak", "Fair", "Strong", "Very strong"];
    var textColors = ["#94a3b8", "#dc2626", "#fb923c", "#eab308", "#22c55e", "#16a34a"];

    if (fill) {
      fill.style.width = input.value.length ? (score * 20) + "%" : "0%";
      fill.style.background = input.value.length ? colors[score] : colors[0];
    }
    if (label) {
      label.textContent = input.value.length ? labels[score] : "";
      label.style.color = input.value.length ? textColors[score] : textColors[0];
    }

    setHint("hint-len", result.len);
    setHint("hint-upper", result.upper);
    setHint("hint-lower", result.lower);
    setHint("hint-num", result.number);
    setHint("hint-special", result.special);
  }

  function setHint(id, ok) {
    var el = document.getElementById(id);
    if (!el) return;
    el.classList.toggle("ok", !!ok);
  }

  function toggleVisibility(button) {
    var inputId = button.getAttribute("data-target");
    var input = document.getElementById(inputId);
    if (!input) return;
    var visible = input.type === "password";
    input.type = visible ? "text" : "password";
    button.setAttribute("aria-pressed", visible ? "true" : "false");
    button.innerHTML = visible
      ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><line x1="1" y1="1" x2="23" y2="23"/></svg>'
      : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
  }

  function checkConfirm() {
    var password = document.getElementById("password");
    var confirm = document.getElementById("password-confirm");
    var message = document.getElementById("kc-confirm-error");
    if (!password || !confirm) return true;

    var mismatch = confirm.value.length > 0 && confirm.value !== password.value;
    confirm.classList.toggle("kc-error", mismatch);
    if (message) {
      message.hidden = !mismatch;
      message.textContent = mismatch ? "Passwords don't match" : "";
    }
    return !mismatch;
  }

  function registerValidation(event) {
    var form = document.getElementById("kc-register-form");
    if (!form) return;

    var password = document.getElementById("password");
    var confirm = document.getElementById("password-confirm");
    var terms = document.getElementById("kc-terms");

    if (password) password.setCustomValidity("");
    if (confirm) confirm.setCustomValidity("");

    if (password && confirm && confirm.value && confirm.value !== password.value) {
      confirm.setCustomValidity("Passwords don't match");
    }

    if (terms && !terms.checked) {
      terms.setCustomValidity("Please accept the Terms of Service and Privacy Policy.");
    } else if (terms) {
      terms.setCustomValidity("");
    }

    if (!form.checkValidity()) {
      event.preventDefault();
      var invalid = form.querySelector(":invalid");
      if (invalid && typeof invalid.focus === "function") invalid.focus();
      return false;
    }
    return true;
  }

  function bind() {
    document.querySelectorAll(".kc-input-eye[data-target]").forEach(function (button) {
      button.addEventListener("click", function () { toggleVisibility(button); });
    });

    var password = document.getElementById("password");
    var confirm = document.getElementById("password-confirm");
    var form = document.getElementById("kc-register-form");
    var terms = document.getElementById("kc-terms");

    if (password) {
      // Do not show password-strength feedback on initial render.
      // It appears only after the user starts entering a password.
      if (!password.value) {
        var initialFeedback = document.getElementById("kc-password-feedback");
        if (initialFeedback) {
          initialFeedback.classList.remove("has-value");
          initialFeedback.setAttribute("aria-hidden", "true");
        }
      }
      password.addEventListener("input", function () {
        updateStrength(password);
        checkConfirm();
      });
      password.addEventListener("blur", function () {
        if (!password.value) {
          var emptyFeedback = document.getElementById("kc-password-feedback");
          if (emptyFeedback) {
            emptyFeedback.classList.remove("has-value");
            emptyFeedback.setAttribute("aria-hidden", "true");
          }
        }
      });
    }
    if (confirm) {
      confirm.addEventListener("input", checkConfirm);
      confirm.addEventListener("blur", checkConfirm);
    }
    if (terms) terms.addEventListener("change", function () { terms.setCustomValidity(""); });
    if (form) form.addEventListener("submit", registerValidation);

    var updatePassword = document.getElementById("password-new");
    if (updatePassword) {
      updatePassword.addEventListener("input", function () { updateStrength(updatePassword); });
      updatePassword.addEventListener("blur", function () {
        if (!updatePassword.value) {
          var updateFeedback = document.getElementById("kc-password-feedback");
          if (updateFeedback) {
            updateFeedback.classList.remove("has-value");
            updateFeedback.setAttribute("aria-hidden", "true");
          }
        }
      });
      if (!updatePassword.value) {
        var updateFeedbackInitial = document.getElementById("kc-password-feedback");
        if (updateFeedbackInitial) {
          updateFeedbackInitial.classList.remove("has-value");
          updateFeedbackInitial.setAttribute("aria-hidden", "true");
        }
      } else {
        updateStrength(updatePassword);
      }
    }
  }

  window.kcUpdateStrength = function (value) {
    var input = document.getElementById("password") || document.getElementById("password-new");
    if (input) {
      input.value = value || "";
      updateStrength(input);
    }
  };

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", bind);
  else bind();
})();
