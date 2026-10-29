(function () {
  'use strict';
  if (window.__swAccess) { window.__swAccess.resize(); return; }
  var tv = !!window.__swTV;
  var style = document.createElement('style');
  style.id = 'sw-accessibility';
  style.textContent =
    'html.sw-tv :focus{outline:4px solid #8cf0de!important;outline-offset:3px!important;}' +
    'html.sw-tv a,html.sw-tv button,html.sw-tv input,html.sw-tv select{scroll-margin:32px;}' +
    'html.sw-tv{scroll-padding:32px;}' +
    'video:fullscreen,video:-webkit-full-screen{width:100%;height:100%;object-fit:contain;}' +
    '@media(prefers-reduced-motion:reduce){html.sw-tv{scroll-behavior:auto;}}';
  (document.head || document.documentElement).appendChild(style);
  if (tv) document.documentElement.classList.add('sw-tv');
  var selector = 'a[href],button,input,select,textarea,video,iframe,[tabindex],[role="button"],[role="link"]';
  function visible(el) {
    var r = el.getBoundingClientRect(), s = getComputedStyle(el);
    return !el.disabled && el.getAttribute('aria-hidden') !== 'true' &&
      r.width > 0 && r.height > 0 &&
      s.visibility !== 'hidden' && s.display !== 'none' && !el.closest('[inert],[aria-hidden="true"]');
  }
  function candidates() {
    var modal = document.querySelector('dialog[open],[role="dialog"][aria-modal="true"]');
    var scope = modal && visible(modal) ? modal : document;
    return Array.prototype.filter.call(scope.querySelectorAll(selector), function(el){return visible(el) && el.getAttribute("tabindex") !== "-1";});
  }
  function focus(el) {
    if (!el) return false;
    if (!el.hasAttribute('tabindex') && !/^(A|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(el.tagName)) el.tabIndex = 0;
    try { el.focus({preventScroll: true}); } catch (_) { el.focus(); }
    try { el.scrollIntoView({block: 'nearest', inline: 'nearest', behavior: 'auto'}); }
    catch (_) { el.scrollIntoView(false); }
    return document.activeElement === el;
  }
  function editing(el) { return el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName)); }
  function move(key) {
    var items = candidates(), active = document.activeElement;
    if (!items.length || editing(active)) return false;
    if (items.indexOf(active) < 0) return focus(items[0]);
    var a = active.getBoundingClientRect(), ax = a.left + a.width/2, ay = a.top + a.height/2;
    var horizontal = key === 'ArrowLeft' || key === 'ArrowRight', positive = key === 'ArrowRight' || key === 'ArrowDown';
    var best = null, score = Infinity;
    items.forEach(function (el) {
      if (el === active || active.contains(el) || el.contains(active)) return;
      var b = el.getBoundingClientRect(), dx = b.left+b.width/2-ax, dy = b.top+b.height/2-ay;
      var primary = horizontal ? dx : dy, secondary = Math.abs(horizontal ? dy : dx);
      if ((positive ? primary : -primary) <= 1) return;
      var cost = Math.abs(primary) + secondary * 3;
      if (cost < score) { best = el; score = cost; }
    });
    return focus(best);
  }
  function video() {
    // Never cross an iframe origin or replace the site's player controls.
    var videos = Array.prototype.filter.call(document.querySelectorAll('video'), visible);
    return videos.filter(function(v){return !v.paused;})[0] || videos[0];
  }
  function media(action) {
    var v = video();
    if (!v || editing(document.activeElement)) return false;
    if (action === 'toggle' || action === 'play') {
      if (action === 'toggle' && !v.paused) v.pause();
      else { var p = v.play(); if (p && p.catch) p.catch(function(){}); }
    } else if (action === 'pause') v.pause();
    else if (action === 'forward' || action === 'rewind') {
      if (!v.seekable || !v.seekable.length) return false;
      var n = v.seekable.length - 1;
      v.currentTime = Math.max(v.seekable.start(n), Math.min(v.seekable.end(n), v.currentTime + (action === 'forward' ? 10 : -10)));
    } else return false;
    return true;
  }
  function resize() {
    // Let each provider lay out its own timeline and controls at the new viewport.
    window.dispatchEvent(new Event('resize'));
  }
  var resizePending = false;
  function queueResize() {
    if (resizePending) return;
    resizePending = true;
    requestAnimationFrame(function(){resizePending=false;resize();});
  }
  document.addEventListener('fullscreenchange', queueResize);
  document.addEventListener('webkitfullscreenchange', queueResize);
  window.addEventListener('orientationchange', queueResize);
  document.addEventListener('keydown', function(e) {
    if (!tv || e.defaultPrevented || e.altKey || e.ctrlKey || e.metaKey || editing(e.target)) return;
    if (/^Arrow(Left|Right|Up|Down)$/.test(e.key) && move(e.key)) { e.preventDefault(); e.stopPropagation(); }
    else if (e.key === 'Enter' && e.target && e.target.tagName === 'VIDEO' && media('toggle')) { e.preventDefault(); }
    else if (e.key === 'Enter' && e.target && e.target.matches('[role="button"],[role="link"]') && !e.target.matches('button,a')) {
      e.target.click(); e.preventDefault();
    }
  }, false);
  // Add browser lazy loading only to offscreen images that the site has not configured.
  if ('loading' in HTMLImageElement.prototype) {
    Array.prototype.forEach.call(document.images, function(img) {
      if (!img.hasAttribute('loading') && img.getBoundingClientRect().top > innerHeight * 2) img.loading = 'lazy';
    });
  }
  window.__swAccess = {move:move, media:media, resize:resize};
})();
