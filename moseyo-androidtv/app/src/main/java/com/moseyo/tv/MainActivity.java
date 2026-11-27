package com.moseyo.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Moseyo for Android TV / Google TV.
 *
 * The TV app is the hosted Moseyo TV web app - the very same one the Hisense
 * and LG TVs run - shown full screen. So every TV feature lands on every TV at
 * once, and new features need no new APK. This shell only adds what a browser
 * page can't do by itself on Android TV:
 *  - the remote's Back button reaches the app (as the Escape key it already
 *    understands), and leaving the app from its own "exit" works;
 *  - the screen stays on while a video plays (and only then);
 *  - videos may start without a touch, and cookies/storage persist.
 */
public class MainActivity extends Activity {

    private WebView webView;
    private boolean showingErrorPage = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " MoseyoAndroidTV/" + BuildConfig.VERSION_NAME);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new Bridge(), "MoseyoAndroid");
        webView.setWebChromeClient(new WebChromeClient() {
            // No grey "play" placeholder over videos before their first frame.
            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!showingErrorPage) {
                    view.evaluateJavascript(KEEP_SCREEN_ON_SCRIPT, null);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showErrorPage();
                }
            }
        });

        setContentView(webView);
        hideSystemBars();
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(BuildConfig.START_URL);
        }
        webView.requestFocus();
    }

    // Reports video play/pause back to the shell so the screen stays on only while watching.
    private static final String KEEP_SCREEN_ON_SCRIPT =
        "(function(){if(window.__moseyoKeepOn)return;window.__moseyoKeepOn=true;"
            + "function sync(){var on=false;var vids=document.getElementsByTagName('video');"
            + "for(var i=0;i<vids.length;i++){var v=vids[i];if(!v.paused&&!v.ended&&v.readyState>2){on=true;break;}}"
            + "try{MoseyoAndroid.setKeepScreenOn(on);}catch(e){}}"
            + "['playing','pause','ended','emptied','abort','error'].forEach(function(t){document.addEventListener(t,sync,true);});"
            + "setInterval(sync,30000);})();";

    private void showErrorPage() {
        showingErrorPage = true;
        String html = "<html><body style='background:#121014;color:#fff;font-family:sans-serif;display:flex;"
            + "align-items:center;justify-content:center;height:100vh;margin:0;text-align:center'>"
            + "<div><div style='font-size:40px;font-weight:700;color:#f5a623'>Moseyo</div>"
            + "<p style='font-size:24px'>Can't reach Moseyo right now.</p>"
            + "<p style='font-size:20px;opacity:.7'>Check the TV's internet connection, then press OK to try again.</p></div>"
            + "<script>document.addEventListener('keydown',function(e){if(e.keyCode===13||e.keyCode===23){location.href='"
            + BuildConfig.START_URL + "';}});</script></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (showingErrorPage) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    finish();
                }
                return true;
            }
            // Android doesn't pass Back to web pages; hand it over as Escape, which the
            // Moseyo TV app already treats as Back (it decides when to exit).
            String type = event.getAction() == KeyEvent.ACTION_DOWN ? "keydown" : "keyup";
            if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
                String script = "(function(){var t=document.activeElement||document.body||document;"
                    + "var e=new KeyboardEvent('" + type + "',{key:'Escape',code:'Escape',keyCode:27,which:27,"
                    + "repeat:" + (event.getRepeatCount() > 0) + ",bubbles:true,cancelable:true});"
                    // Not every WebView honours keyCode in the constructor: pin it on the event itself.
                    + "try{Object.defineProperty(e,'keyCode',{get:function(){return 27;}});"
                    + "Object.defineProperty(e,'which',{get:function(){return 27;}});}catch(x){}"
                    + "t.dispatchEvent(e);})();";
                webView.evaluateJavascript(script, null);
            }
            return true;
        }
        if (showingErrorPage && event.getAction() == KeyEvent.ACTION_UP
            && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            showingErrorPage = false;
            webView.loadUrl(BuildConfig.START_URL);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void hideSystemBars() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /** What the Moseyo TV web app can ask of the shell (window.MoseyoAndroid). */
    private final class Bridge {
        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(MainActivity.this::finish);
        }

        @JavascriptInterface
        public void setKeepScreenOn(final boolean on) {
            runOnUiThread(() -> {
                if (on) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public String platform() {
            return "android-tv";
        }

        @JavascriptInterface
        public String version() {
            return BuildConfig.VERSION_NAME;
        }
    }
}
