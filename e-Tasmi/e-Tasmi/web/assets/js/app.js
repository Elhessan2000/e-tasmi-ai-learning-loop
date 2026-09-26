(function(){
  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || key;
  }

  function qs(sel){return document.querySelector(sel);} 
  function qsa(sel){return Array.prototype.slice.call(document.querySelectorAll(sel));}

  function clamp(v, min, max){return Math.min(max, Math.max(min, v));}
  function lerp(a, b, t){return a + (b - a) * t;}

  // Role cards (register template)
  function initRoleCards(){
    var form = qs('form[data-role-cards="1"]');
    if(!form){return;}

    var roleSelect = form.querySelector('select[name="role"]');
    var cards = Array.prototype.slice.call(form.querySelectorAll('.role-card[data-role]'));
    if(!roleSelect || cards.length === 0){return;}

    function setActive(role){
      var normalized = (role || '').toUpperCase();
      cards.forEach(function(btn){
        var isActive = (btn.getAttribute('data-role') || '').toUpperCase() === normalized;
        btn.setAttribute('aria-pressed', isActive ? 'true' : 'false');
      });
    }

    cards.forEach(function(btn){
      btn.addEventListener('click', function(){
        var role = btn.getAttribute('data-role');
        roleSelect.value = role;
        setActive(role);
        try{
          roleSelect.dispatchEvent(new Event('change', {bubbles:true}));
        }catch(e){
          // IE fallback not needed, but keep safe
          var ev = document.createEvent('Event');
          ev.initEvent('change', true, true);
          roleSelect.dispatchEvent(ev);
        }
      });
    });

    setActive(roleSelect.value);
  }

  // Confirm password validation (client-side, non-authoritative)
  function initConfirmPassword(){
    qsa('form[data-confirm-password="1"], form[data-validate="1"]').forEach(function(form){
      var pass = form.querySelector('input[name="password"], input[name="newPassword"], #password');
      var confirm = form.querySelector('input[name="confirmPassword"], #confirmPassword');
      if(!pass || !confirm){return;}

      function validate(){
        var p = pass.value || '';
        var c = confirm.value || '';
        if(c.length === 0){
          confirm.setCustomValidity('');
          return;
        }
        confirm.setCustomValidity(p === c ? '' : t('validation.passwordMismatch'));
      }

      pass.addEventListener('input', validate);
      confirm.addEventListener('input', validate);
      validate();
    });
  }

  // Lightweight client-side table filtering
  function initTableSearch(){
    qsa('input[data-table-search]').forEach(function(input){
      var sel = input.getAttribute('data-table-search');
      if(!sel){return;}
      var root = qs(sel);
      if(!root){return;}

      function getRows(){
        // Supports passing a <tbody> or a <table>
        if(root.tagName && root.tagName.toLowerCase() === 'tbody'){
          return Array.prototype.slice.call(root.querySelectorAll('tr'));
        }
        return Array.prototype.slice.call(root.querySelectorAll('tbody tr'));
      }

      function apply(){
        var q = (input.value || '').trim().toLowerCase();
        var rows = getRows();
        rows.forEach(function(tr){
          var text = (tr.textContent || '').toLowerCase();
          tr.style.display = (q === '' || text.indexOf(q) !== -1) ? '' : 'none';
        });
      }

      input.addEventListener('input', apply);
      apply();
    });
  }

  // Progress bars (set width from data-progress)
  function initProgressBars(){
    qsa('.progress-bar[data-progress]').forEach(function(bar){
      var raw = bar.getAttribute('data-progress');
      var pct = parseInt(raw, 10);
      if(isNaN(pct)){pct = 0;}
      if(pct < 0){pct = 0;}
      if(pct > 100){pct = 100;}
      var span = bar.querySelector('span');
      if(span){
        span.style.width = pct + '%';
      }
    });

    // Student dashboard v2 progress bars
    qsa('.sd-progressbar[data-progress]').forEach(function(bar){
      var raw = bar.getAttribute('data-progress');
      var pct = parseInt(raw, 10);
      if(isNaN(pct)){pct = 0;}
      if(pct < 0){pct = 0;}
      if(pct > 100){pct = 100;}
      var span = bar.querySelector('span');
      if(span){
        span.style.width = pct + '%';
      }
    });
  }

  // Animated counters (generic; used on student dashboard KPI badges)
  function initCounters(){
    var roots = qsa('[data-counters="1"]');
    if(roots.length === 0){return;}

    roots.forEach(function(root){
      var counterEls = Array.prototype.slice.call(root.querySelectorAll('[data-counter="1"][data-target]'));
      if(counterEls.length === 0){return;}

      function animateAll(){
        counterEls.forEach(function(el){
          if(el.getAttribute('data-counter-run') === '1'){return;}
          el.setAttribute('data-counter-run','1');

          var target = parseInt(el.getAttribute('data-target'), 10);
          if(isNaN(target)){target = 0;}
          target = Math.max(0, target);

          var duration = 850;
          var startTs = null;

          function step(ts){
            if(!startTs){startTs = ts;}
            var t = Math.min(1, (ts - startTs) / duration);
            var val = Math.floor(target * t);
            el.textContent = String(val);
            if(t < 1){
              requestAnimationFrame(step);
            }else{
              el.textContent = String(target);
            }
          }

          requestAnimationFrame(step);
        });
      }

      if('IntersectionObserver' in window){
        var ran = false;
        var io = new IntersectionObserver(function(entries){
          entries.forEach(function(entry){
            if(entry.isIntersecting && !ran){
              ran = true;
              animateAll();
              try{io.disconnect();}catch(e){}
            }
          });
        }, {threshold: 0.25});
        io.observe(root);
      }else{
        animateAll();
      }
    });
  }

  // Upload filename display (template-inspired)
  function initUploadFilename(){
    qsa('input[type="file"][name="qualification"]').forEach(function(input){
      var root = input.closest('.input-group') || input.closest('label');
      if(!root){return;}
      var label = root.querySelector('[data-upload-filename="1"]');
      if(!label){return;}

      function update(){
        var name = (input.files && input.files.length > 0) ? input.files[0].name : t('common.noFileSelected');
        label.textContent = name;
      }

      input.addEventListener('change', update);
      update();
    });
  }

  // Instructor role fields toggle
  function initRoleToggle(){
    var roleSelect = qs('select[name="role"]');
    var instructorFields = qs('#instructorFields');
    if(!roleSelect || !instructorFields){return;}

    function setRequired(el, required){
      if(!el){return;}
      el.required = !!required;
      if(!required){
        el.removeAttribute('aria-required');
      } else {
        el.setAttribute('aria-required','true');
      }
    }

    function toggle(){
      var role = (roleSelect.value || '').toUpperCase();
      var isInstructor = role === 'INSTRUCTOR';
      instructorFields.style.display = isInstructor ? 'block' : 'none';

      setRequired(qs('input[name="qualification"]'), isInstructor);
      setRequired(qs('textarea[name="bio"]'), isInstructor);
    }

    roleSelect.addEventListener('change', toggle);
    toggle();
  }

  // Simple client-side inline validation (non-authoritative)
  function initInlineValidation(){
    qsa('form[data-validate="1"]').forEach(function(form){
      form.addEventListener('submit', function(e){
        // Let browser validations run, but ensure custom required fields are updated.
        initRoleToggle();
        if(!form.checkValidity()){
          // Allow browser to show its own messages
          e.preventDefault();
        }
      });
    });
  }

  // Dismissible, auto-hiding alerts
  function initAlerts(){
    qsa('.alert').forEach(function(el){
      if(el.getAttribute('data-alert-init') === '1'){return;}
      el.setAttribute('data-alert-init','1');

      var btn = document.createElement('button');
      btn.type = 'button';
      btn.setAttribute('aria-label', t('common.dismiss'));
      btn.style.cssText = 'margin-left:10px; background:transparent; border:none; cursor:pointer; font-weight:800; float:right;';
      btn.textContent = '×';
      btn.addEventListener('click', function(){
        try{ el.remove(); }catch(e){ el.style.display='none'; }
      });

      el.appendChild(btn);

      window.setTimeout(function(){
        if(!el){return;}
        try{ el.remove(); }catch(e){ el.style.display='none'; }
      }, 4500);
    });
  }

  function showAlert(type, message){
    if(!message){return;}
    var host = qs('.auth-card') || qs('.container') || document.body;
    var div = document.createElement('div');
    var cls = 'alert';
    if(type === 'success'){cls += ' alert-success';}
    else if(type === 'error'){cls += ' alert-error';}
    else if(type === 'warning'){cls += ' alert-warning';}
    else {cls += ' alert-info';}
    div.className = cls;
    div.style.marginTop = '14px';
    div.textContent = message;

    var title = host.querySelector ? host.querySelector('.auth-title') : null;
    if(title && title.parentNode){
      title.parentNode.insertBefore(div, title.nextSibling);
    }else{
      host.insertBefore(div, host.firstChild);
    }
    initAlerts();
  }

  // AJAX form submit (auth flows)
  function initAjaxForms(){
    qsa('form[data-ajax="1"]').forEach(function(form){
      if(form.getAttribute('data-ajax-init') === '1'){return;}
      form.setAttribute('data-ajax-init','1');

      form.addEventListener('submit', function(e){
        e.preventDefault();
        if(form.getAttribute('data-validate') === '1' && !form.checkValidity()){
          return;
        }

        var submitBtn = form.querySelector('button[type="submit"], input[type="submit"]');
        if(submitBtn){
          submitBtn.disabled = true;
          submitBtn.setAttribute('data-prev-text', submitBtn.textContent || '');
          submitBtn.textContent = t('common.loading');
        }

        var url = form.getAttribute('action') || window.location.href;
        var method = (form.getAttribute('method') || 'POST').toUpperCase();

        // Use URL-encoded for non-file forms so server-side getParameter() works.
        // Use multipart only when files are actually being uploaded.
        var headers = { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' };
        var body;

        var fileInputs = Array.prototype.slice.call(form.querySelectorAll('input[type="file"]'));
        var hasSelectedFiles = fileInputs.some(function(inp){
          return inp && inp.files && inp.files.length > 0;
        });

        if(hasSelectedFiles){
          body = new FormData(form);
        }else{
          var params = new URLSearchParams();
          new FormData(form).forEach(function(value, key){
            params.append(key, value);
          });
          body = params;
          headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
        }

        fetch(url, {
          method: method,
          body: body,
          headers: headers,
          credentials: 'same-origin'
        }).then(function(res){
          return res.json();
        }).then(function(data){
          if(submitBtn){
            submitBtn.disabled = false;
            submitBtn.textContent = submitBtn.getAttribute('data-prev-text') || t('common.submit');
          }

          if(data && data.success){
            if(data.message){ showAlert('success', data.message); }
            if(data.redirect){ window.location.href = data.redirect; }
            return;
          }

          var err = (data && (data.error || data.message)) ? (data.error || data.message) : t('errors.requestFailed');
          showAlert('error', err);

          // Login: if email not verified, reveal resend box
          try{
            if((err || '').toLowerCase().indexOf('verify your email') !== -1){
              var box = qs('[data-resend-verify="1"]');
              if(box){ box.style.display = ''; }
              var loginEmail = qs('#loginEmail');
              var hidden = qs('[data-resend-email="1"]');
              if(hidden && loginEmail){ hidden.value = loginEmail.value || ''; }
            }
          }catch(e){}
        }).catch(function(){
          if(submitBtn){
            submitBtn.disabled = false;
            submitBtn.textContent = submitBtn.getAttribute('data-prev-text') || t('common.submit');
          }
          showAlert('error', t('errors.requestFailed'));
        });
      });
    });
  }

  // Home landing interactions (hero slider + counters)
  function initHomeLanding(){
    var landing = qs('.landing');
    if(!landing){return;}

    function initReveal(){
      var revealEls = Array.prototype.slice.call(landing.querySelectorAll('[data-reveal="1"]'));
      if(revealEls.length === 0){return;}

      function set(el, on){
        if(on){
          el.classList.add('is-revealed');
        }else{
          el.classList.remove('is-revealed');
        }
      }

      if('IntersectionObserver' in window){
        var io = new IntersectionObserver(function(entries){
          entries.forEach(function(entry){
            if(entry.isIntersecting){
              set(entry.target, true);
              try{ io.unobserve(entry.target); }catch(e){}
            }
          });
        }, {threshold: 0.18});

        revealEls.forEach(function(el){ io.observe(el); });
      }else{
        revealEls.forEach(function(el){ set(el, true); });
      }
    }

    function initAmbientFloat(){
      var floatEls = Array.prototype.slice.call(landing.querySelectorAll('[data-ambient-float="1"]'));
      if(floatEls.length === 0){return;}

      var start = null;
      var reduce = false;
      try{
        reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      }catch(e){ reduce = false; }
      if(reduce){return;}

      // Gentle, constant motion (works even with iframes)
      function tick(ts){
        if(!start){start = ts;}
        var t = (ts - start) / 1000;

        floatEls.forEach(function(el, idx){
          var a = 0.006 + (idx * 0.001);
          var bob = Math.sin(t * 0.95 + idx) * 6;
          var sway = Math.cos(t * 0.70 + idx * 1.7) * 6;
          var rot = Math.sin(t * 0.55 + idx * 2.2) * 1.25;
          el.style.transform = 'translate3d(' + sway.toFixed(2) + 'px,' + bob.toFixed(2) + 'px,0) rotate(' + rot.toFixed(2) + 'deg)';
          el.style.willChange = 'transform';
        });

        requestAnimationFrame(tick);
      }

      requestAnimationFrame(tick);
    }

    function initFloatCards(){
      var cards = Array.prototype.slice.call(landing.querySelectorAll('[data-float-card="1"], [data-instructor-card="1"]'));
      if(cards.length === 0){return;}

      var reduce = false;
      try{
        reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      }catch(e){ reduce = false; }
      if(reduce){return;}

      var start = null;
      function tick(ts){
        if(!start){start = ts;}
        var t = (ts - start) / 1000;

        cards.forEach(function(el, idx){
          var bob = Math.sin(t * 1.6 + idx * 0.9) * 2.3;
          var sway = Math.cos(t * 1.2 + idx * 1.3) * 1.8;
          var rot = Math.sin(t * 1.15 + idx * 2.1) * 0.8;
          el.style.transform = 'translate3d(' + sway.toFixed(2) + 'px,' + bob.toFixed(2) + 'px,0) rotate(' + rot.toFixed(2) + 'deg)';
          el.style.willChange = 'transform';
        });

        requestAnimationFrame(tick);
      }

      requestAnimationFrame(tick);
    }

    function initLiveClock(){
      var root = landing.querySelector('[data-live-clock="1"]');
      if(!root){return;}

      var canvas = root.querySelector('canvas.lp-live-clock-canvas');
      var timeEl = root.querySelector('[data-clock-time]');
      var dateEl = root.querySelector('[data-clock-date]');
      if(!canvas){return;}
      var ctx = canvas.getContext('2d');
      if(!ctx){return;}

      var reduce = false;
      try{
        reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      }catch(e){ reduce = false; }

      var dpr = 1;
      function resize(){
        var rect = canvas.getBoundingClientRect();
        var cssSize = Math.max(240, Math.min(rect.width || 420, 520));
        dpr = (window.devicePixelRatio || 1);
        var px = Math.floor(cssSize * dpr);
        canvas.width = px;
        canvas.height = px;
      }

      function pad2(n){ return (n < 10 ? '0' : '') + n; }
      function formatTime(d){
        return pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds());
      }
      function formatDate(d){
        try{
          return d.toLocaleDateString(undefined, {weekday:'long', year:'numeric', month:'long', day:'numeric'});
        }catch(e){
          return d.getFullYear() + '-' + pad2(d.getMonth()+1) + '-' + pad2(d.getDate());
        }
      }

      function draw(ts){
        var now = new Date();
        if(timeEl){ timeEl.textContent = formatTime(now); }
        if(dateEl){ dateEl.textContent = formatDate(now); }

        var w = canvas.width;
        var h = canvas.height;
        var cx = w / 2;
        var cy = h / 2;
        var r = Math.min(w, h) * 0.42;

        ctx.clearRect(0, 0, w, h);

        // Background halo
        var halo = ctx.createRadialGradient(cx, cy, r * 0.2, cx, cy, r * 1.2);
        halo.addColorStop(0, 'rgba(5,150,105,0.22)');
        halo.addColorStop(0.55, 'rgba(6,95,70,0.12)');
        halo.addColorStop(1, 'rgba(6,95,70,0.00)');
        ctx.fillStyle = halo;
        ctx.beginPath();
        ctx.arc(cx, cy, r * 1.22, 0, Math.PI * 2);
        ctx.fill();

        // Subtle "energy" noise rings
        var t = (ts || 0) / 1000;
        if(!reduce){
          for(var i = 0; i < 5; i++){
            var rr = r * (0.68 + i * 0.08);
            var a = 0.18 + i * 0.10;
            ctx.strokeStyle = 'rgba(6,95,70,' + a.toFixed(2) + ')';
            ctx.lineWidth = Math.max(1, r * 0.010);
            ctx.beginPath();
            var wob = Math.sin(t * (0.9 + i * 0.2) + i * 1.7) * 0.08;
            ctx.arc(cx, cy, rr, 0 + wob, Math.PI * 2 + wob);
            ctx.stroke();
          }
        }

        // Tick marks
        ctx.save();
        ctx.translate(cx, cy);
        for(var k = 0; k < 60; k++){
          var ang = (Math.PI * 2) * (k / 60);
          var isMajor = (k % 5) === 0;
          var inner = r * (isMajor ? 0.86 : 0.90);
          var outer = r * 0.98;
          ctx.strokeStyle = isMajor ? 'rgba(6,95,70,0.55)' : 'rgba(17,24,39,0.22)';
          ctx.lineWidth = isMajor ? Math.max(2, r * 0.020) : Math.max(1, r * 0.010);
          ctx.beginPath();
          ctx.moveTo(Math.cos(ang) * inner, Math.sin(ang) * inner);
          ctx.lineTo(Math.cos(ang) * outer, Math.sin(ang) * outer);
          ctx.stroke();
        }
        ctx.restore();

        // Hands / progress arcs
        var sec = now.getSeconds() + (now.getMilliseconds() / 1000);
        var min = now.getMinutes() + (sec / 60);
        var hr = (now.getHours() % 12) + (min / 60);

        function arc(pct, radius, width, color){
          ctx.strokeStyle = color;
          ctx.lineWidth = width;
          ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.arc(cx, cy, radius, -Math.PI/2, (-Math.PI/2) + (Math.PI * 2 * pct), false);
          ctx.stroke();
        }

        // Glow layer
        ctx.save();
        ctx.globalCompositeOperation = 'lighter';
        arc(hr / 12, r * 0.74, r * 0.09, 'rgba(6,95,70,0.30)');
        arc(min / 60, r * 0.84, r * 0.07, 'rgba(5,150,105,0.26)');
        arc(sec / 60, r * 0.94, r * 0.035, 'rgba(167,243,208,0.32)');
        ctx.restore();

        // Solid arcs
        arc(hr / 12, r * 0.74, r * 0.05, 'rgba(6,95,70,0.85)');
        arc(min / 60, r * 0.84, r * 0.04, 'rgba(5,150,105,0.80)');
        arc(sec / 60, r * 0.94, r * 0.022, 'rgba(167,243,208,0.90)');

        // Center core
        ctx.fillStyle = 'rgba(6,95,70,0.95)';
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.055, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = 'rgba(255,255,255,0.85)';
        ctx.beginPath();
        ctx.arc(cx, cy, r * 0.018, 0, Math.PI * 2);
        ctx.fill();

        if(!reduce){
          requestAnimationFrame(draw);
        }
      }

      resize();
      window.addEventListener('resize', function(){
        // Avoid thrash
        try{ clearTimeout(resize._t); }catch(e){}
        resize._t = setTimeout(function(){
          resize();
        }, 120);
      });

      if(reduce){
        // Static draw + 1s text updates
        draw(0);
        setInterval(function(){
          var d = new Date();
          if(timeEl){ timeEl.textContent = formatTime(d); }
          if(dateEl){ dateEl.textContent = formatDate(d); }
        }, 1000);
      }else{
        requestAnimationFrame(draw);
      }
    }

    // Try to autoplay the home video (browsers usually require muted autoplay)
    var homeVideo = landing.querySelector('.hero-video video');
    if(homeVideo){
      homeVideo.muted = true;
      try{ homeVideo.setAttribute('playsinline',''); }catch(e){}

      function tryPlay(){
        var p;
        try{ p = homeVideo.play(); }catch(e){ p = null; }
        if(p && typeof p.catch === 'function'){
          p.catch(function(){
            // If blocked, try again after first user interaction
            function onFirstInteraction(){
              document.removeEventListener('click', onFirstInteraction, true);
              document.removeEventListener('touchstart', onFirstInteraction, true);
              try{ homeVideo.play(); }catch(e){}
            }
            document.addEventListener('click', onFirstInteraction, true);
            document.addEventListener('touchstart', onFirstInteraction, true);
          });
        }
      }

      // Attempt immediately and once metadata is ready
      tryPlay();
      homeVideo.addEventListener('loadedmetadata', tryPlay);
    }

    // Hero slider
    var slider = landing.querySelector('[data-hero-slider="1"]');
    var slides = slider ? Array.prototype.slice.call(slider.querySelectorAll('.hero-slide[data-slide]')) : [];
    var dots = Array.prototype.slice.call(landing.querySelectorAll('[data-hero-dot]'));

    function setActiveSlide(n){
      slides.forEach(function(el){
        var isActive = el.getAttribute('data-slide') === String(n);
        if(isActive){
          el.classList.add('is-active');
        }else{
          el.classList.remove('is-active');
        }
      });
      dots.forEach(function(btn){
        var isActive = btn.getAttribute('data-hero-dot') === String(n);
        if(isActive){
          btn.classList.add('is-active');
          btn.setAttribute('aria-current','true');
        }else{
          btn.classList.remove('is-active');
          btn.removeAttribute('aria-current');
        }
      });
      current = n;
    }

    var current = 1;
    if(slides.length > 0){
      setActiveSlide(1);

      dots.forEach(function(btn){
        btn.addEventListener('click', function(){
          var n = parseInt(btn.getAttribute('data-hero-dot'), 10);
          if(!isNaN(n)){
            setActiveSlide(n);
          }
        });
      });

      // Auto-rotate (pause on hover)
      var intervalMs = 5200;
      var timer = null;

      function start(){
        if(timer){return;}
        timer = setInterval(function(){
          var next = current + 1;
          if(next > slides.length){next = 1;}
          setActiveSlide(next);
        }, intervalMs);
      }

      function stop(){
        if(timer){
          clearInterval(timer);
          timer = null;
        }
      }

      landing.addEventListener('mouseenter', stop);
      landing.addEventListener('mouseleave', start);
      start();
    }

    // Counters (run once when visible)
    var countersRoot = landing.querySelector('[data-counters="1"]');
    if(countersRoot){
      var counterEls = Array.prototype.slice.call(countersRoot.querySelectorAll('[data-counter="1"][data-target]'));
      var hasRun = false;

      function animate(){
        if(hasRun){return;}
        hasRun = true;

        counterEls.forEach(function(el){
          var target = parseInt(el.getAttribute('data-target'), 10);
          if(isNaN(target)){return;}

          var duration = 900;
          var startTs = null;

          function step(ts){
            if(!startTs){startTs = ts;}
            var t = Math.min(1, (ts - startTs) / duration);
            var val = Math.floor(target * t);
            el.textContent = String(val);
            if(t < 1){
              requestAnimationFrame(step);
            }else{
              el.textContent = String(target);
            }
          }

          requestAnimationFrame(step);
        });
      }

      if('IntersectionObserver' in window){
        var io = new IntersectionObserver(function(entries){
          entries.forEach(function(entry){
            if(entry.isIntersecting){
              animate();
              try{io.disconnect();}catch(e){}
            }
          });
        }, {threshold: 0.25});
        io.observe(countersRoot);
      }else{
        // Fallback
        animate();
      }
    }

    // Extra landing motion (scoped, safe)
    initReveal();
    initAmbientFloat();
    initFloatCards();
    initLiveClock();
  }

  document.addEventListener('DOMContentLoaded', function(){
    initRoleCards();
    initRoleToggle();
    initConfirmPassword();
    initUploadFilename();
    initInlineValidation();
    initAlerts();
    initAjaxForms();
    initHomeLanding();
    initTableSearch();
    initProgressBars();
    initCounters();

    // Login page: keep resend verification email in sync
    try{
      var loginEmail = qs('#loginEmail');
      var hidden = qs('[data-resend-email="1"]');
      if(loginEmail && hidden){
        loginEmail.addEventListener('input', function(){ hidden.value = loginEmail.value || ''; });
      }
    }catch(e){}
  });
})();
