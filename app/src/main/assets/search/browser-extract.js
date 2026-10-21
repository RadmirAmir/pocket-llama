(function () {
  // Read the visible search page only. Never click links or solve challenges.
  var sources = [], seen = Object.create(null), provider = location.hostname;
  var headings = document.querySelectorAll('h3, #b_results h2, a[data-testid="result-title-a"], .snippet-title');
  for (var i = 0; i < Math.min(headings.length, 64) && sources.length < 6; i++) {
    var heading = headings[i], anchor = heading.closest('a') || heading.querySelector('a');
    if (!anchor || heading.closest('[data-text-ad], [data-ad]')) continue;
    var url;
    try {
      url = new URL(anchor.href, location.href);
      if (url.hostname === provider && url.pathname === '/url') url = new URL(url.searchParams.get('q') || url.searchParams.get('url'));
    } catch (_) { continue; }
    if (url.protocol !== 'https:' && url.protocol !== 'http:') continue;
    if (url.hostname === provider && /\/(search|sorry|preferences|settings|accounts)(\/|$)/.test(url.pathname)) continue;
    if (seen[url.href]) continue;
    var title = (heading.innerText || heading.textContent || '').trim().slice(0, 240);
    if (!title) continue;
    var container = heading.closest('.MjjYud, .g, .b_algo, .snippet, article, [data-testid="result"]');
    if (!container) {
      container = anchor;
      for (var depth = 0; depth < 4 && container.parentElement && (container.innerText || '').length < title.length + 80; depth++) container = container.parentElement;
    }
    var text = (container.innerText || '').replace(title, '').replace(/\s+/g, ' ').trim().slice(0, 1200);
    var source = {url: url.href.slice(0, 2048), title: title, text: text || title};
    if (!text) source.headlineOnly = true;
    var time = container.querySelector('time[datetime]');
    if (time && !isNaN(Date.parse(time.getAttribute('datetime')))) source.published = new Date(time.getAttribute('datetime')).toISOString();
    sources.push(source); seen[url.href] = true;
  }
  var text = (document.body && document.body.innerText || '').slice(0, 12000);
  var challenge = !sources.length && (/captcha|unusual traffic|необычн.{0,15}трафик|подтвердите.{0,40}(робот|человек)|verify.{0,30}human/i.test(text) || !!document.querySelector('iframe[src*="recaptcha"], iframe[src*="hcaptcha"]'));
  return JSON.stringify({sources: sources, challenge: challenge});
}());
