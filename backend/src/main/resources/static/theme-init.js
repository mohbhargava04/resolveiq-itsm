// Applies the saved display settings (theme, text size, reading aids) before first paint, so the
// page never flashes the wrong theme or jumps in size. A separate file (not an inline <script>) so
// the Content-Security-Policy can forbid inline scripts. Keep in step with src/lib/display.ts.
(function () {
  try {
    var root = document.documentElement;
    var p = {};
    try { p = JSON.parse(localStorage.getItem('riq-display') || '{}') || {}; } catch (e) { p = {}; }
    // before display settings existed, only 'light' | 'dark' was stored under riq-theme
    var theme = p.theme || (localStorage.getItem('riq-theme') || 'system').toUpperCase();
    var systemDark = matchMedia('(prefers-color-scheme: dark)').matches;
    var name = { LIGHT: 'light', DARK: 'dark', DIM: 'dim', WARM: 'warm', HIGH_CONTRAST: 'contrast' }[theme]
      || (systemDark ? 'dark' : 'light');
    root.dataset.theme = name;
    root.classList.toggle('dark', name === 'dark' || name === 'dim');
    root.dataset.textSize = { SMALL: 'small', LARGE: 'large', EXTRA_LARGE: 'xl' }[p.textSize] || 'default';
    if (p.reduceMotion) root.dataset.motion = 'reduce';
    if (p.easierReading) root.dataset.reading = 'easy';
    if (p.underlineLinks) root.dataset.links = 'underline';
  } catch (e) {}
})();
