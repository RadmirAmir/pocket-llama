package dev.pocketllama;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.*;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A closed, local asset origin. No network page can receive either native bridge. */
public final class UiHost {
    public static final String ORIGIN = "https://pocket.local/assets/";
    private static final Set<String> ASSETS = new HashSet<>(Arrays.asList(
        "native-rpc.js", "notes.html", "notes.css", "notes.js", "studio.html", "studio.css", "studio.js", "photos.html", "photos.css", "photos.js", "design.css", "theme.js", "i18n.js", "chat.html", "chat.css", "fonts/Manrope.ttf"));
    private UiHost() {}
    public static String appearance(Context c) { return c.getSharedPreferences("interface", Context.MODE_PRIVATE).getString("appearance", "auto"); }
    public static void appearance(Activity a, FrameLayout root, WebView web, String value) {
        if (!Arrays.asList("auto", "light", "dark").contains(value)) throw new IllegalArgumentException(dev.pocketllama.I18n.t("Неизвестная тема"));
        a.getSharedPreferences("interface", Context.MODE_PRIVATE).edit().putString("appearance", value).apply();
        colors(a, root, web);
    }
    public static void colors(Activity a, FrameLayout root, WebView web) {
        String theme = appearance(a);
        boolean dark = theme.equals("dark") || theme.equals("auto") && (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int color = Color.parseColor(dark ? "#181A19" : "#F7F6F2");
        root.setBackgroundColor(color); web.setBackgroundColor(color);
        a.getWindow().setStatusBarColor(color); a.getWindow().setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= 30) a.getWindow().getInsetsController().setSystemBarsAppearance(
            dark ? 0 : android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        else a.getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    public static void attach(Activity a, FrameLayout root, WebView web) { attach(a,root,web,null); }
    public interface PrivateContent { WebResourceResponse load(String url); }
    public static void syncLanguage(WebView web) {
        if(web!=null&&!I18n.language().equals(web.getTag())){web.setTag(I18n.language());web.reload();}
    }
    public static void attach(Activity a, FrameLayout root, WebView web, PrivateContent content) {
        web.setTag(I18n.language());
        colors(a, root, web);
        if (Build.VERSION.SDK_INT >= 30) {
            a.getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                root.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
                return insets;
            });
            root.requestApplyInsets();
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(false);s.setCacheMode(WebSettings.LOAD_NO_CACHE);s.setSaveFormData(false);web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        s.setTextZoom(Math.round(100*a.getResources().getConfiguration().fontScale));
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Android's blockNetworkLoads also rejects the virtual HTTPS origin before interception.
        // Every request is answered below, either from a small asset allowlist or with 403.
        // CSP additionally forbids connections, frames and scripts from other origins.
        s.setBlockNetworkLoads(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("data:image/")) return null;
                if (url.startsWith(ORIGIN)) {
                    if(content!=null&&url.startsWith(ORIGIN+"private/"))return content.load(url);
                    String path = url.substring(ORIGIN.length());
                    if (ASSETS.contains(path)) try {
                        String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "application/javascript" : "font/ttf";
                        return new WebResourceResponse(mime, path.endsWith(".ttf") ? null : "UTF-8", a.getAssets().open(path));
                    } catch (Exception ignored) { }
                }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
        });
    }
}
