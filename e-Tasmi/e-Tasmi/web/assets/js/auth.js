(function(){
  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  function qs(sel, root){ return (root || document).querySelector(sel); }
  function qsa(sel, root){ return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function iconMarkup(tone){
    if(tone === 'success'){
      return '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" stroke="currentColor" stroke-width="1.6"/><path d="m8.5 12.3 2.2 2.2 4.8-5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    }
    if(tone === 'error'){
      return '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v4m0 3h.01M21 12A9 9 0 1 1 3 12a9 9 0 0 1 18 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>';
    }
    return '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 16v-4m0-4h.01M21 12A9 9 0 1 1 3 12a9 9 0 0 1 18 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>';
  }

  function clearAlerts(host){
    qsa('.auth-alert[data-dynamic="1"]', host).forEach(function(el){ el.remove(); });
  }

  function mountDynamicAlert(host, tone, message){
    if(!host || !message){ return; }
    var wrap = qs('[data-alert-host="1"]', host) || host;
    var alert = document.createElement('div');
    alert.className = 'auth-alert';
    alert.setAttribute('data-tone', tone || 'info');
    alert.setAttribute('data-dynamic', '1');
    alert.innerHTML = iconMarkup(tone || 'info') + '<div>' + message + '</div>';
    wrap.prepend(alert);
  }

  function syncResendEmail(){
    var emailField = qs('[data-login-email="1"]');
    var resendEmail = qs('[data-resend-email="1"]');
    var enterCodeLinks = qsa('[data-enter-code-link]');
    if(!emailField || !resendEmail){ return; }
    function apply(){
      var email = emailField.value || '';
      resendEmail.value = email;
      enterCodeLinks.forEach(function(link){
        var base = link.getAttribute('data-base-href') || link.href.split('?')[0];
        link.href = email ? base + '?email=' + encodeURIComponent(email) : base;
      });
    }
    emailField.addEventListener('input', apply);
    apply();
  }

  function initRoleCards(){
    var input = qs('input[name="role"][data-role-input="1"]');
    var instructorBlock = qs('[data-instructor-fields="1"]');
    var studentBlock = qs('[data-student-fields="1"]');
    var roleTitle = qs('[data-role-title="1"]');
    var roleCopy = qs('[data-role-copy="1"]');
    var srTitle = qs('[data-role-sr-title="1"]');
    var submit = qs('[data-role-submit="1"]');
    if(!input){ return; }

    function applyRole(role){
      var normalized = (role || '').toUpperCase() || 'STUDENT';
      var isInstructor = normalized === 'INSTRUCTOR';
      input.value = normalized;
      qsa('[data-role-card]').forEach(function(card){
        var active = (card.getAttribute('data-role-card') || '').toUpperCase() === normalized;
        card.classList.toggle('is-active', active);
        card.setAttribute('aria-pressed', active ? 'true' : 'false');
      });

      document.body.classList.toggle('etasmi-auth-v2--instructor', isInstructor);
      document.body.classList.toggle('etasmi-auth-v2--student', !isInstructor);

      if(roleTitle){
        roleTitle.textContent = isInstructor ? t('auth.instructorSignup') : t('auth.studentSignup');
      }
      if(roleCopy){
        roleCopy.textContent = isInstructor
          ? t('auth.instructorSignupCopy')
          : t('auth.studentSignupCopy');
      }
      if(srTitle){
        srTitle.textContent = t('js.auth.createAccountMetaTitle', {
          role: isInstructor ? t('auth.instructor') : t('auth.student')
        });
      }
      if(submit){
        submit.textContent = isInstructor ? t('auth.createInstructorAccount') : t('auth.createStudentAccount');
      }

      if(studentBlock){
        studentBlock.hidden = isInstructor;
        qsa('[data-student-required="1"]', studentBlock).forEach(function(field){
          field.required = !isInstructor;
          if(isInstructor && typeof field.setCustomValidity === 'function'){
            field.setCustomValidity('');
          }
        });
      }
      if(instructorBlock){
        instructorBlock.hidden = !isInstructor;
        qsa('[data-instructor-required="1"]', instructorBlock).forEach(function(field){
          field.required = isInstructor;
          if(!isInstructor && typeof field.setCustomValidity === 'function'){
            field.setCustomValidity('');
          }
        });
      }
    }

    qsa('[data-role-card]').forEach(function(card){
      card.addEventListener('click', function(){
        applyRole(card.getAttribute('data-role-card'));
      });
    });

    applyRole(input.value);
  }

  function initPasswordMatch(){
    qsa('form[data-password-check="1"]').forEach(function(form){
      var first = qs('input[name="password"], input[name="newPassword"]', form);
      var second = qs('input[name="confirmPassword"]', form);
      if(!first || !second){ return; }

      function validate(){
        if(!second.value){
          second.setCustomValidity('');
          return;
        }
        second.setCustomValidity(first.value === second.value ? '' : t('validation.passwordMismatch'));
      }

      first.addEventListener('input', validate);
      second.addEventListener('input', validate);
    });
  }

  /** Matches RegistrationService.isStrongPassword */
  function initPasswordStrength(){
    qsa('form[data-password-strength="1"]').forEach(function(form){
      var pw = qs('input[name="password"]', form);
      if(!pw){ return; }
      function rule(){
        var v = pw.value || '';
        if(!v){
          pw.setCustomValidity('');
          return;
        }
        if(v.length < 8){
          pw.setCustomValidity(t('validation.passwordMin8'));
          return;
        }
        var u = false, l = false, d = false;
        for(var i = 0; i < v.length; i++){
          var c = v.charAt(i);
          if(/[A-Z]/.test(c)){ u = true; }
          else if(/[a-z]/.test(c)){ l = true; }
          else if(/[0-9]/.test(c)){ d = true; }
        }
        pw.setCustomValidity(u && l && d ? '' : t('validation.passwordWeakDetailed'));
      }
      pw.addEventListener('input', rule);
      pw.addEventListener('change', rule);
    });
  }

  /** Matches RegistrationService.isValidPhone (8–15 digits after stripping non-digits) */
  function initPhoneDigitsCheck(){
    qsa('form[data-phone-check="1"]').forEach(function(form){
      var ph = qs('input[name="phone"]', form);
      if(!ph){ return; }
      function rule(){
        var raw = ph.value || '';
        if(!raw.trim()){
          ph.setCustomValidity('');
          return;
        }
        var digits = raw.replace(/[^0-9]/g, '');
        ph.setCustomValidity(digits.length >= 8 && digits.length <= 15 ? '' : t('validation.phoneDigitsFormat'));
      }
      ph.addEventListener('input', rule);
      ph.addEventListener('change', rule);
    });
  }

  function clearFormCustomValidity(form){
    qsa('input, select, textarea', form).forEach(function(el){
      if(typeof el.setCustomValidity === 'function'){
        el.setCustomValidity('');
      }
    });
  }

  function refreshClientValidators(form){
    ['password', 'confirmPassword', 'phone'].forEach(function(name){
      var el = form.elements.namedItem(name);
      if(el && el.dispatchEvent){
        el.dispatchEvent(new Event('input', { bubbles: true }));
      }
    });
  }

  function applyServerFieldErrors(form, fieldErrors){
    if(!fieldErrors || typeof fieldErrors !== 'object'){ return; }
    Object.keys(fieldErrors).forEach(function(k){
      var el = form.elements.namedItem(k);
      if(el && typeof el.setCustomValidity === 'function'){
        el.setCustomValidity(String(fieldErrors[k] == null ? '' : fieldErrors[k]));
      }
    });
  }

  function initUploadField(){
    qsa('input[type="file"][data-upload-input="1"]').forEach(function(input){
      var shell = input.closest('[data-upload-shell="1"]');
      var nameNode = shell ? qs('[data-upload-name="1"]', shell) : null;
      if(!nameNode){ return; }

      function update(){
        nameNode.textContent = input.files && input.files.length ? input.files[0].name : t('common.noFileSelected');
      }

      input.addEventListener('change', update);
      update();
    });
  }

  function initAjaxForms(){
    qsa('form[data-ajax="1"]').forEach(function(form){
      if(form.getAttribute('data-ajax-init') === '1'){ return; }
      form.setAttribute('data-ajax-init', '1');

      form.addEventListener('submit', function(e){
        e.preventDefault();
        clearAlerts(form.closest('.auth-card') || form);
        clearFormCustomValidity(form);
        refreshClientValidators(form);

        if(!form.checkValidity()){
          form.reportValidity();
          return;
        }

        var submit = qs('button[type="submit"], input[type="submit"]', form);
        var previous = submit ? submit.innerHTML : '';
        if(submit){
          submit.disabled = true;
          submit.innerHTML = t('common.pleaseWait');
        }

        var hasFile = qsa('input[type="file"]', form).some(function(input){
          return input.files && input.files.length > 0;
        });

        var headers = {
          'X-Requested-With':'XMLHttpRequest',
          'Accept':'application/json'
        };
        var body;

        if(hasFile){
          body = new FormData(form);
        }else{
          var params = new URLSearchParams();
          new FormData(form).forEach(function(value, key){ params.append(key, value); });
          body = params.toString();
          headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
        }

        fetch(form.getAttribute('action') || window.location.href, {
          method:(form.getAttribute('method') || 'POST').toUpperCase(),
          headers:headers,
          body:body,
          credentials:'same-origin'
        }).then(function(res){
          return res.text().then(function(text){
            try {
              return { ok: res.ok, status: res.status, data: text ? JSON.parse(text) : {} };
            } catch (e) {
              return { ok: res.ok, status: res.status, data: null, raw: text };
            }
          });
        }).then(function(wrapped){
          var data = wrapped && wrapped.data;
          if (!data && wrapped && wrapped.raw) {
            throw new Error('non_json');
          }
          data = data || {};
          if(submit){
            submit.disabled = false;
            submit.innerHTML = previous;
          }

          if(data && data.success){
            if(data.message){
              mountDynamicAlert(form.closest('.auth-card') || form, 'success', data.message);
            }
            if(data.redirect){
              window.location.href = data.redirect;
            }
            return;
          }

          var error = data && (data.error || data.message) ? (data.error || data.message) : t('errors.requestFailed');
          mountDynamicAlert(form.closest('.auth-card') || form, 'error', error);
          applyServerFieldErrors(form, data.fieldErrors);

          if((error || '').toLowerCase().indexOf('verify your email') !== -1){
            var resendPanel = qs('[data-resend-panel="1"]');
            if(resendPanel){ resendPanel.hidden = false; }
            syncResendEmail();
          }
        }).catch(function(err){
          if(submit){
            submit.disabled = false;
            submit.innerHTML = previous;
          }
          var msg = err && err.message === 'non_json'
            ? t('errors.nonJson')
            : t('errors.requestFailed');
          mountDynamicAlert(form.closest('.auth-card') || form, 'error', msg);
        });
      });
    });
  }

  function initVerifyStatusPolling(){
    var meta = qs('[data-verify-status="1"]');
    if(!meta){ return; }
    var email = meta.getAttribute('data-email');
    var contextPath = meta.getAttribute('data-context-path') || '';
    if(!email){ return; }

    var poll = function(){
      fetch(contextPath + '/auth/verify-status?email=' + encodeURIComponent(email), {
        method:'GET',
        headers:{'Accept':'application/json'},
        credentials:'same-origin'
      }).then(function(res){
        return res.json();
      }).then(function(data){
        if(data && data.known && data.verified){
          window.location.href = contextPath + '/auth/login?verified=1';
        }
      }).catch(function(){
        // Silent polling failure is acceptable here.
      });
    };

    poll();
    window.setInterval(poll, 5000);
  }

  document.addEventListener('etasmi:localechange', function () {
    initRoleCards();
    initUploadField();
  });

  document.addEventListener('DOMContentLoaded', function(){
    initRoleCards();
    initPasswordStrength();
    initPhoneDigitsCheck();
    initPasswordMatch();
    initUploadField();
    initAjaxForms();
    syncResendEmail();
    initVerifyStatusPolling();
  });
})();
