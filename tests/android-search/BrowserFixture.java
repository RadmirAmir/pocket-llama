package dev.pocketllama.searchtest;

/** Synthetic page only, injected exclusively into the disposable diagnostic APK. */
public final class BrowserFixture {
    public static final String PAGE="<html><body><script>"+
        "function results(){document.body.innerHTML='<div class=\"g\"><a href=\"https://example.org/browser\"><h3>Native browser fixture</h3></a><p>Real WebView JavaScript. '+(typeof window.Pocket==='undefined'?'No native bridge.':'UNSAFE BRIDGE')+' '+navigator.userAgent+'</p></div>';document.cookie='pocket_fixture=1; Secure; SameSite=Lax';}"+
        "if(location.search.indexOf('challenge')>=0){document.body.innerHTML='<p>CAPTCHA: Verify you are human.</p><button id=\"continue\">Continue fixture</button>';document.getElementById('continue').onclick=results;}else results();"+
        "</script></body></html>";
}
