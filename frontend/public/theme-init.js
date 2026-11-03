// Applies the saved theme before first paint so dark mode never flashes white.
// A separate file (not an inline <script>) so the Content-Security-Policy can forbid inline scripts.
(function () {
  try {
    var pref = localStorage.getItem('riq-theme') || 'system';
    var dark = pref === 'dark' || (pref === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('dark', dark);
  } catch (e) {}
})();
