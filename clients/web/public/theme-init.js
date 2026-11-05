// Applies the theme chosen in Settings → Appearance (System by default) before React renders (docs/07 §2.11).
(function () {
  var stored;
  try {
    stored = localStorage.getItem('toefly.theme');
  } catch (e) {
    stored = null;
  }
  var dark = stored === 'dark' || (stored !== 'light' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.toggle('dark', dark);
  try {
    if (localStorage.getItem('toefly.reduceTransparency') === 'true') {
      document.documentElement.dataset.transparency = 'reduced';
    }
  } catch (e) {
    // Storage blocked: glass stays on.
  }
})();
