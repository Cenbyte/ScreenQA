(function () {
  // Only compact the observed Lanzou file-card template; leave content and links intact.
  function compact() {
    var icon = document.querySelector('.mb > .mico');
    if (!icon || !document.querySelector('.mb > .md') || !document.querySelector('.mb .mh #ddown')) return;
    var spacer = icon.previousElementSibling;
    if (spacer && spacer.tagName === 'DIV' && spacer.childElementCount === 0 &&
        !spacer.textContent.trim() && parseFloat(getComputedStyle(spacer).marginTop) > 0) {
      spacer.style.setProperty('display', 'none', 'important');
    }
  }
  if (window.__screenqaKnowledgeCompact) { compact(); return; }
  window.__screenqaKnowledgeCompact = true;
  compact();
  var pending = false;
  var observer = new MutationObserver(function () {
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () { pending = false; compact(); });
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', compact, { once: true });
  window.addEventListener('pagehide', function () { observer.disconnect(); }, { once: true });
})();
