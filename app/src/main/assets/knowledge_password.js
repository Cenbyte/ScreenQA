(function(code) {
    if (window.__screenqaPasswordProbe) { window.__screenqaPasswordProbe.check(); return 'observing'; }
    var probe = { submitted: false, check: check };
    window.__screenqaPasswordProbe = probe;
    function visible(el) { return el && !el.disabled && el.getClientRects().length > 0; }
    function checkDocument(doc) {
        // Wait for the page's own submit handler and blocking scripts to be installed.
        if (doc.readyState === 'loading') return false;
        var input = doc.querySelector('#pwd, #passwd, input[name="pwd"], input[name="password"], input[type="password"]');
        if (!visible(input)) return false;
        var scope = input.closest('form') || input.parentElement;
        var submit = doc.querySelector('#sub, #submit, .passwddiv-btn, .passwd_btn');
        if (!visible(submit) && scope) submit = scope.querySelector('button, input[type="submit"], [onclick]');
        if (!visible(submit)) {
            var controls = doc.querySelectorAll('button, input[type="submit"], a, div[onclick]');
            for (var i=0;i<controls.length;i++) {
                if (visible(controls[i]) && /^(提取|提交|确认|进入|确定)(文件|访问|下载)?$/.test((controls[i].innerText || controls[i].value || '').trim())) {
                    submit = controls[i]; break;
                }
            }
        }
        if (!visible(submit) && !(input.form && input.form.requestSubmit)) return false;
        // Do not replace anything the user has already typed.
        if (input.value && input.value !== code) { probe.submitted = true; return false; }
        var win = doc.defaultView;
        var setter = Object.getOwnPropertyDescriptor(win.HTMLInputElement.prototype, 'value').set;
        setter.call(input, code);
        input.dispatchEvent(new win.Event('input', { bubbles:true }));
        input.dispatchEvent(new win.Event('change', { bubbles:true }));
        probe.submitted = true;
        if (visible(submit)) submit.click(); else input.form.requestSubmit();
        return true;
    }
    function check() {
        if (probe.submitted) return;
        try {
            if (checkDocument(document)) return;
            var frames = document.querySelectorAll('iframe');
            for (var i=0;i<frames.length;i++) {
                try { if (frames[i].contentDocument && checkDocument(frames[i].contentDocument)) return; } catch(e) { }
            }
        } catch(e) { /* Keep the page usable for manual input. */ }
    }
    var pending = false;
    var observer = new MutationObserver(function() {
        if (probe.submitted || pending) return;
        pending = true;
        setTimeout(function() { pending=false; check(); }, 100);
    });
    observer.observe(document.documentElement, { childList:true, subtree:true, attributes:true, attributeFilter:['style','class','hidden'] });
    document.addEventListener('DOMContentLoaded', check, { once:true });
    window.addEventListener('load', check, { once:true });
    document.addEventListener('load', check, true); // Same-origin password iframe loaded later.
    check();
    setTimeout(function() { observer.disconnect(); }, 30000);
    return probe.submitted ? 'submitted' : 'observing';
})(__EXTRACTION_CODE__);
