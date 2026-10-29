package com.example.streamwindow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.ArrayList;

/** A live website viewer. No scraping, stream extraction or JavaScript bridges. */
public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://multimovies.wtf/";
    private static final String ANIME_URL = "https://multinime.site/";
    private FrameLayout root, pages, fullContainer;
    private AdBlockRules adRules;
    private LinearLayout shell, content, navigation;
    private Button moviesButton, animeButton, menuButton;
    private boolean television, wideLayout;
    private volatile boolean blockingEnabled = true;
    private int textZoom = 100;
    private String activeSite = HOME_URL;
    private TextView address, error;
    private LinearLayout errorPanel;
    private Button retryButton;
    private final android.os.Handler retryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingRetry;
    private int retryCount;
    private String retryUrl, automaticUrl;
    private long retryNotBefore, resourceNoticeAt;
    private boolean failedMainFrame, retryable, viewingMode, resumed;
    private String accessScript = "";
    private final ArrayList<String> diagnostics = new ArrayList<>();
    private View previousFocus;
    private long lastTvFocusMove;
    private ProgressBar progress;
    private final ArrayList<WebView> tabs = new ArrayList<>();
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCallback;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private WebView current() { return tabs.get(tabs.size() - 1); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try (java.io.Reader input = new java.io.InputStreamReader(getAssets().open("adhosts.txt"), java.nio.charset.StandardCharsets.UTF_8)) {
            adRules = new AdBlockRules(input);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Bundled ad block rules could not load", e);
        }
        blockingEnabled = getPreferences(MODE_PRIVATE).getBoolean("blockingEnabled",true);
        textZoom = getPreferences(MODE_PRIVATE).getInt("textZoom",100);
        activeSite = getPreferences(MODE_PRIVATE).getString("selectedSite",HOME_URL);
        if (!ANIME_URL.equals(activeSite)) activeSite = HOME_URL;
        television = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK)
            == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        television = getPreferences(MODE_PRIVATE).getBoolean("tvMode",television);
        try (java.io.InputStream input = getAssets().open("accessibility.js")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int size;
            while ((size = input.read(buffer)) != -1) bytes.write(buffer,0,size);
            accessScript = new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException("Accessibility asset missing",e); }
        if (Build.VERSION.SDK_INT >= 24) {
            try { WorkerFilter.install(this); }
            catch (RuntimeException unavailableWorker) { /* WebView fallback is shown below if needed. */ }
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16,38,44));
        shell = new LinearLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        navigation = new LinearLayout(this);
        navigation.setBackgroundColor(Color.rgb(16,38,44));
        root.addView(shell, new FrameLayout.LayoutParams(-1,-1));

        address = new TextView(this); // Detached: no app header above website.
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        content.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        error = new TextView(this);
        error.setTextColor(Color.WHITE);
        error.setPadding(dp(16),dp(10),dp(16),dp(10));
        error.setTextSize(television ? 18 : 14);
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setBackgroundColor(Color.rgb(45,34,36));
        errorPanel.addView(error,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions = new LinearLayout(this);
        retryButton = navigationButton("Retry", "Retry failed page", () -> retryManually());
        Button browser = navigationButton("Browser", "Open current page in browser", () -> openBrowser(tabs.isEmpty()?activeSite:current().getUrl()));
        actions.addView(retryButton,new LinearLayout.LayoutParams(0,dp(52),1));
        actions.addView(browser,new LinearLayout.LayoutParams(0,dp(52),1));
        errorPanel.addView(actions);
        errorPanel.setVisibility(View.GONE);
        content.addView(errorPanel,new LinearLayout.LayoutParams(-1,-2));
        pages = new FrameLayout(this);
        content.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        moviesButton = navigationButton("Multi Movie", "Open MultiMovies", () -> switchSite(HOME_URL));
        animeButton = navigationButton("Multi Anime", "Open MultiNime", () -> switchSite(ANIME_URL));
        menuButton = navigationButton("Menu", "Open app controls", () -> showControls());
        moviesButton.setCompoundDrawablesWithIntrinsicBounds(new NavIcon(false,dp(24)),null,null,null);
        animeButton.setCompoundDrawablesWithIntrinsicBounds(new NavIcon(true,dp(24)),null,null,null);
        moviesButton.setCompoundDrawablePadding(dp(8)); animeButton.setCompoundDrawablePadding(dp(8));
        rebuildNavigation();
        fullContainer = new FrameLayout(this);
        fullContainer.setBackgroundColor(Color.BLACK);
        fullContainer.setVisibility(View.GONE);
        root.addView(fullContainer,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        if (Build.VERSION.SDK_INT < 30) root.setFitsSystemWindows(true);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v,insets) -> {
                android.graphics.Insets edges = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                int safe = television && fullView == null && !viewingMode ? dp(24) : 0;
                v.setPadding(Math.max(edges.left,safe), Math.max(edges.top,safe), Math.max(edges.right,safe), Math.max(edges.bottom,safe));
                return insets;
            });
        }
        WebView main;
        try { main = createPage(); }
        catch (RuntimeException unavailableWebView) {
            progress.setVisibility(View.GONE);
            error.setText("This device has no working Android WebView. Update Android System WebView or tap here to open the site in a browser.");
            errorPanel.setVisibility(View.VISIBLE);
            retryButton.setEnabled(false);
            return;
        }
        tabs.add(main);
        pages.addView(main,new FrameLayout.LayoutParams(-1,-1));
        if (getPreferences(MODE_PRIVATE).getInt("adFilterVersion",0) < 2) {
            main.clearCache(true);
            getPreferences(MODE_PRIVATE).edit().putInt("adFilterVersion",2).apply();
        }
        if (television) moviesButton.requestFocus();
        if(state == null || main.restoreState(state) == null) {
            String site = getPreferences(MODE_PRIVATE).getString("selectedSite", HOME_URL);
            main.loadUrl(ANIME_URL.equals(site) ? ANIME_URL : HOME_URL);
        }
    }

    private void switchSite(String destination) {
        if(tabs.isEmpty()) { activeSite = destination; updateSelection(); openBrowser(destination); return; }
        cancelRetry(); retryCount = 0; retryNotBefore = 0;
        leaveFullscreen();
        closeExtraTabs();
        current().stopLoading();
        activeSite = destination;
        updateSelection();
        errorPanel.setVisibility(View.GONE);
        getPreferences(MODE_PRIVATE).edit().putString("selectedSite",destination).apply();
        current().loadUrl(destination);
    }

    private Button navigationButton(String label, String description, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(television ? 18 : 12);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8),dp(4),dp(8),dp(4));
        b.setMinHeight(dp(52));
        b.setMinimumWidth(0);
        b.setContentDescription(description);
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v,focused) -> styleButton(b));
        b.setOnHoverListener((v,event) -> { b.setHovered(event.getAction()!=MotionEvent.ACTION_HOVER_EXIT); styleButton(b); return false; });
        return b;
    }

    private void styleButton(Button b) {
        boolean selected = b == moviesButton ? HOME_URL.equals(activeSite) :
            b == animeButton && ANIME_URL.equals(activeSite);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(b.hasFocus() || b.isHovered() ? Color.rgb(99,213,203) :
            selected ? Color.rgb(28,74,80) : Color.rgb(23,45,53));
        bg.setStroke(dp(b.hasFocus()?3:1), b.hasFocus()?Color.WHITE:
            selected?Color.rgb(99,213,203):Color.rgb(41,67,74));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(Color.rgb(70,130,130)),bg,null));
        b.setTextColor(b.hasFocus() || b.isHovered()?Color.rgb(10,31,36):selected?Color.rgb(140,240,222):Color.rgb(220,232,234));
        b.setSelected(selected);
    }
    private void updateSelection() {
        styleButton(moviesButton); styleButton(animeButton); styleButton(menuButton);
    }
    private void rebuildNavigation() {
        wideLayout = television || getResources().getConfiguration().screenWidthDp >= 600;
        shell.removeAllViews(); navigation.removeAllViews();
        shell.setOrientation(wideLayout?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        navigation.setOrientation(wideLayout?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(8),dp(8),dp(8),dp(8));
        if(wideLayout) {
            TextView brand = new TextView(this);
            brand.setText("STREAM\nWINDOW");
            brand.setTextSize(13); brand.setTextColor(Color.rgb(140,240,222));
            brand.setGravity(Gravity.CENTER); brand.setPadding(0,dp(16),0,dp(22));
            navigation.addView(brand,new LinearLayout.LayoutParams(-1,-2));
        }
        for(Button b:new Button[]{moviesButton,animeButton,menuButton}) {
            LinearLayout.LayoutParams lp = wideLayout ?
                new LinearLayout.LayoutParams(-1,dp(television?72:64)) :
                new LinearLayout.LayoutParams(0,dp(64),b == menuButton ? 0.65f : 1.2f);
            lp.setMargins(dp(4),dp(4),dp(4),dp(4));
            navigation.addView(b,lp);
        }
        if(wideLayout) {
            shell.addView(navigation,new LinearLayout.LayoutParams(dp(television?216:174),-1));
            shell.addView(content,new LinearLayout.LayoutParams(0,-1,1));
        } else {
            shell.addView(content,new LinearLayout.LayoutParams(-1,0,1));
            shell.addView(navigation,new LinearLayout.LayoutParams(-1,-2));
        }
        navigation.setVisibility(viewingMode?View.GONE:View.VISIBLE);
        if (Build.VERSION.SDK_INT < 30 && television) root.setPadding(dp(24),dp(24),dp(24),dp(24));
        updateSelection();
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        rebuildNavigation();
        refreshViewport();
    }
    private void showControls() {
        if(tabs.isEmpty()) { openBrowser(activeSite); return; }
        new AlertDialog.Builder(this).setTitle("Stream Window")
            .setItems(new String[]{"Reload page","Site home","Open in browser",
                "Ad blocking: "+(blockingEnabled?"On":"Off"),"Text size", "TV layout: "+(television?"On":"Off"), "Theater view", "Diagnostics", "About this app"},
                (dialog,which) -> {
                    switch(which) {
                        case 0: retryManually(); break;
                        case 1: switchSite(activeSite); break;
                        case 2: openBrowser(current().getUrl()); break;
                        case 3:
                            blockingEnabled = !blockingEnabled;
                            getPreferences(MODE_PRIVATE).edit().putBoolean("blockingEnabled",blockingEnabled).apply();
                            current().clearCache(true);
                            retryManually();
                            Toast.makeText(this,blockingEnabled?"Ad blocking on":"Ad blocking off for both sites",Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            new AlertDialog.Builder(this).setTitle("Text size")
                                .setSingleChoiceItems(new String[]{"Standard","Larger","Largest"},
                                    textZoom==100?0:textZoom==115?1:2,(d,choice)->{
                                        textZoom = choice==0?100:choice==1?115:130;
                                        getPreferences(MODE_PRIVATE).edit().putInt("textZoom",textZoom).apply();
                                        current().getSettings().setTextZoom(textZoom); d.dismiss();
                                    }).setNegativeButton("Close",null).show();
                            break;
                        case 5:
                            getPreferences(MODE_PRIVATE).edit().putBoolean("tvMode",!television).apply();
                            recreate(); break;
                        case 6:
                            if(fullView != null) leaveFullscreen();
                            viewingMode = !viewingMode;
                            navigation.setVisibility(viewingMode?View.GONE:View.VISIBLE);
                            applyFullscreenBars(viewingMode); refreshViewport(); break;
                        case 7:
                            new AlertDialog.Builder(this).setTitle("Recent diagnostics")
                                .setMessage(diagnostics.isEmpty()?"No failures recorded in this session.":android.text.TextUtils.join("\n\n",diagnostics))
                                .setPositiveButton("Close",null).show(); break;
                        default:
                            new AlertDialog.Builder(this).setTitle("Stream Window 1.5")
                                .setMessage("Live MultiMovies and MultiNime viewer. Android 6 or newer.\n\nAd blocking reduces known ads; video playback depends on the sites. On TV, use the remote arrows and OK. In fullscreen, Down/Right selects the next control and Up/Left selects the previous control. OK activates it. Press Menu for controls. Back exits fullscreen or theater view. External frame players may have limited remote support.")
                                .setPositiveButton("OK",null).show();
                    }
                }).setNegativeButton("Close",null).show();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int direction = TvRemotePolicy.focusDirection(event.getKeyCode());
        if(television && fullView!=null && direction!=0) {
            // Consume BOTH halves of the arrow. Providers may seek on keydown or keyup.
            // Tab uses the focused frame's native focus order, including cross-origin players.
            // No script injection into the provider frame and no synthetic OK/click.
            long now = android.os.SystemClock.uptimeMillis();
            if(event.getAction()==KeyEvent.ACTION_DOWN &&
                (event.getRepeatCount()==0 || now-lastTvFocusMove>=180)) {
                lastTvFocusMove=now;
                int modifiers=direction<0?KeyEvent.META_SHIFT_ON:0;
                super.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB,0,modifiers));
                super.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB,0,modifiers));
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_ESCAPE) { goBack(); return true; }
        if(!tabs.isEmpty()) {
            String action = keyCode==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ? "toggle" :
                keyCode==KeyEvent.KEYCODE_MEDIA_PLAY ? "play" : keyCode==KeyEvent.KEYCODE_MEDIA_PAUSE ? "pause" :
                keyCode==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? "forward" : keyCode==KeyEvent.KEYCODE_MEDIA_REWIND ? "rewind" : null;
            if(action!=null && fullView==null) {
                current().evaluateJavascript("!!(window.__swAccess && window.__swAccess.media('"+action+"'))",value -> {
                    if(!"true".equals(value)) Toast.makeText(this,"Use this provider's player controls.",Toast.LENGTH_SHORT).show();
                }); return true;
            }
        }
        if(keyCode==android.view.KeyEvent.KEYCODE_MENU) { showControls(); return true; }
        return super.onKeyDown(keyCode,event);
    }
    private static final class WorkerFilter {
        @android.annotation.TargetApi(24)
        static void clear() { try { ServiceWorkerController.getInstance().setServiceWorkerClient(null); } catch(RuntimeException ignored) {} }
        @android.annotation.TargetApi(24)
        static void install(MainActivity owner) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return owner.blockResponse(request.getUrl());
                }
            });
        }
    }

    @SuppressWarnings("deprecation")
    private WebView createPage() {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setTextZoom(textZoom);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return blockResponse(request.getUrl());
            }
            // Android 6 uses the older URL callback.
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (blockingEnabled && adRules.blocksHost(uri.getHost())) return true;
                if ("https".equalsIgnoreCase(uri.getScheme())) return false;
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.isForMainFrame() && "https".equalsIgnoreCase(request.getUrl().getScheme())
                    && "multimovies.wtf".equalsIgnoreCase(request.getUrl().getHost())
                    && "/multinime-logo.svg".equals(request.getUrl().getPath())) {
                    switchSite(ANIME_URL);
                    return true;
                }
                if (blockingEnabled && adRules.blocksHost(request.getUrl().getHost())) return true;
                String scheme = request.getUrl().getScheme();
                if ("https".equalsIgnoreCase(scheme)) return false;
                if (request.isForMainFrame() && request.hasGesture()) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Open outside the app?")
                        .setMessage(request.getUrl().toString())
                        .setPositiveButton("Open browser",(d,w) -> openBrowser(request.getUrl().toString()))
                        .setNegativeButton("Cancel",null).show();
                }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (view == current()) {
                    cancelRetry();
                    if (!url.equals(automaticUrl)) { retryCount = 0; retryNotBefore = 0; }
                    automaticUrl = null;
                    failedMainFrame = false; retryable = true; retryUrl = url;
                    errorPanel.setVisibility(View.GONE);
                    progress.setVisibility(View.VISIBLE);
                    address.setText(url);
                }
            }
            @Override public void onPageFinished(WebView view, String url) {
                if(view == current()) {
                    address.setText(url);
                    String host = Uri.parse(url).getHost();
                    if(host!=null && (host.equals("multinime.site") || host.endsWith(".multinime.site"))) {
                        activeSite = ANIME_URL; updateSelection();
                    } else if(host!=null && (host.equals("multimovies.wtf") || host.endsWith(".multimovies.wtf") ||
                        host.equals("multimovies.courses") || host.endsWith(".multimovies.courses"))) {
                        activeSite = HOME_URL; updateSelection();
                    }
                    getPreferences(MODE_PRIVATE).edit().putString("selectedSite",activeSite).apply();
                }
                // Hide only explicitly marked ad containers; leave player and site navigation intact.
                if(blockingEnabled) view.evaluateJavascript("(function(){if(document.getElementById('sw-ad-style'))return;var s=document.createElement('style');s.id='sw-ad-style';s.textContent='.adsbygoogle,[id^=\"google_ads_iframe\"],.ad-banner,.ad-container,[data-ad-slot]{display:none!important}';(document.head||document.documentElement).appendChild(s);})();", null);
                // Keep links to the two sites in this window while unrelated popup windows stay blocked.
                view.evaluateJavascript("(function(){if(window.__swSiteLinks)return;window.__swSiteLinks=true;document.addEventListener('click',function(e){var a=e.target.closest?e.target.closest('a[href]'):null;if(!a)return;try{var u=new URL(a.href,location.href);if(u.protocol!=='https:')return;var h=u.hostname.toLowerCase();if(h==='multimovies.wtf'&&u.pathname==='/multinime-logo.svg'){a.href='https://multinime.site/';a.target='_self';}else if(h==='multinime.site'||h.endsWith('.multinime.site')||h==='multimovies.wtf'||h.endsWith('.multimovies.wtf')||h==='multimovies.courses'||h.endsWith('.multimovies.courses')){a.target='_self';}}catch(x){}},true);})();", null);
                view.evaluateJavascript("window.__swTV="+television+";"+accessScript,null);
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError problem) {
                recordFailure(request.getUrl(),"network "+problem.getErrorCode(),request.isForMainFrame());
                if(request.isForMainFrame() && view == current()) {
                    cancelRetry(); failedMainFrame = true;
                    retryable = "GET".equalsIgnoreCase(request.getMethod());
                    retryUrl = request.getUrl().toString();
                    retryButton.setEnabled(retryable);
                    showError("Page could not load. Check your connection and try again. No automatic network retry.");
                }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                recordFailure(request.getUrl(),"HTTP "+response.getStatusCode(),request.isForMainFrame());
                if(!request.isForMainFrame()) {
                    if(response.getStatusCode()>=500 && !failedMainFrame && android.os.SystemClock.elapsedRealtime()-resourceNoticeAt>15000) {
                        resourceNoticeAt=android.os.SystemClock.elapsedRealtime();
                        Toast.makeText(MainActivity.this,"A page resource is unavailable. See Menu > Diagnostics.",Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if(view != current() || !request.getUrl().toString().equals(view.getUrl())) return;
                failedMainFrame = true;
                retryUrl = request.getUrl().toString();
                retryable = "GET".equalsIgnoreCase(request.getMethod());
                retryButton.setEnabled(retryable);
                String after = null;
                if(response.getResponseHeaders()!=null) for(java.util.Map.Entry<String,String> h:response.getResponseHeaders().entrySet())
                    if("Retry-After".equalsIgnoreCase(h.getKey())) after=h.getValue();
                long delay = RetryPolicy.delay(retryCount,after,System.currentTimeMillis());
                long serverDelay = RetryPolicy.retryAfter(after,System.currentTimeMillis());
                retryNotBefore = serverDelay > Long.MAX_VALUE-System.currentTimeMillis()?Long.MAX_VALUE:System.currentTimeMillis()+serverDelay;
                String reason = "Service unavailable (HTTP "+response.getStatusCode()+") at "+request.getUrl().getHost()+".";
                if(RetryPolicy.eligible(request.getMethod(),response.getStatusCode()) && RetryPolicy.automatic(retryCount,delay)) {
                    showError(reason+" Retrying in "+(delay/1000)+" seconds ("+(retryCount+1)+"/2).");
                    scheduleRetry(view,retryUrl,delay);
                } else {
                    showError(reason+(serverDelay>0?" Server requested a wait before retrying.":" Try again later or open in browser."));
                }
            }
            // Default certificate handling remains in force; invalid TLS is never bypassed.
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if(view != current()) return;
                progress.setProgress(value);
                progress.setVisibility(value < 100 ? View.VISIBLE : View.GONE);
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if(fullView != null) { callback.onCustomViewHidden(); return; }
                previousFocus = getCurrentFocus();
                fullView = view;
                fullCallback = callback;
                fullContainer.addView(view,new FrameLayout.LayoutParams(-1,-1));
                fullContainer.setVisibility(View.VISIBLE);
                shell.setVisibility(View.GONE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                applyFullscreenBars(true);
                view.requestFocus();
                refreshViewport();
            }
            @Override public void onHideCustomView() { leaveFullscreen(); }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message message) {
                // Treat all new-window requests as popups, even when triggered by a tap.
                return false;
            }
            @Override public void onCloseWindow(WebView window) {
                if(tabs.indexOf(window) > 0) closeTab(window);
            }
        });
        web.setDownloadListener((url,ua,disposition,type,length) ->
            new AlertDialog.Builder(this).setTitle("Download link")
                .setMessage("Open this link in your browser?")
                .setPositiveButton("Open",(d,w) -> openBrowser(url))
                .setNegativeButton("Cancel",null).show());
        return web;
    }
    private WebResourceResponse blockResponse(Uri uri) {
        if (!blockingEnabled || !adRules.blocksHost(uri.getHost())) return null;
        return new WebResourceResponse("text/plain", "UTF-8",
            new java.io.ByteArrayInputStream(new byte[0]));
    }
    private void cancelRetry() {
        if(pendingRetry!=null) retryHandler.removeCallbacks(pendingRetry);
        pendingRetry = null;
    }
    private void scheduleRetry(WebView view, String url, long delay) {
        if(pendingRetry!=null || !resumed) return;
        pendingRetry = () -> {
            pendingRetry = null;
            if(!resumed || tabs.isEmpty() || view!=current() || !url.equals(view.getUrl())) return;
            retryCount++;
            automaticUrl = url;
            view.loadUrl(url);
        };
        retryHandler.postDelayed(pendingRetry,delay);
    }
    private void retryManually() {
        if(tabs.isEmpty()) return;
        if(System.currentTimeMillis()<retryNotBefore) {
            Toast.makeText(this,"The server asked us to wait. Please try again later.",Toast.LENGTH_LONG).show(); return;
        }
        if(failedMainFrame && !retryable) {
            Toast.makeText(this,"This request cannot be safely repeated. Return to the site and try again.",Toast.LENGTH_LONG).show(); return;
        }
        cancelRetry(); retryCount=0;
        if(failedMainFrame && retryUrl!=null) current().loadUrl(retryUrl); else current().reload();
    }
    private void recordFailure(Uri uri,String code,boolean main) {
        // Session-only, bounded, no query strings, paths, headers, cookies or credentials.
        String entry = (main?"Page":"Resource")+" · "+uri.getHost()+" · "+code;
        if(!diagnostics.isEmpty() && entry.equals(diagnostics.get(diagnostics.size()-1))) return;
        if(diagnostics.size()>=20) diagnostics.remove(0);
        diagnostics.add(entry);
    }
    private void refreshViewport() {
        root.requestApplyInsets(); root.requestLayout();
        if(fullView!=null) fullView.requestLayout();
        if(!tabs.isEmpty()) current().evaluateJavascript("if(window.__swAccess)window.__swAccess.resize();",null);
    }
    private void applyFullscreenBars(boolean enabled) {
        if(Build.VERSION.SDK_INT<30) {
            root.setFitsSystemWindows(!enabled);
            int safe=television && !enabled?dp(24):0;
            root.setPadding(safe,safe,safe,safe);
        }
        if(enabled) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(Build.VERSION.SDK_INT>=30 && getWindow().getInsetsController()!=null) {
            if(enabled) {
                getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
                getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
        } else getWindow().getDecorView().setSystemUiVisibility(enabled ?
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY : View.SYSTEM_UI_FLAG_VISIBLE);
    }
    private void showError(String message) {
        boolean first = errorPanel.getVisibility()!=View.VISIBLE;
        progress.setVisibility(View.GONE);
        error.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        if(television && first) retryButton.requestFocus();
    }
    private void openBrowser(String url) {
        if(url == null) return;
        Uri uri = Uri.parse(url);
        if(!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this,"Only web links are supported.",Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
        catch(android.content.ActivityNotFoundException e) {
            Toast.makeText(this,"No browser is installed.",Toast.LENGTH_SHORT).show();
        }
    }
    private void leaveFullscreen() {
        if(fullView == null) return;
        fullContainer.removeAllViews();
        fullContainer.setVisibility(View.GONE);
        fullView = null;
        shell.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyFullscreenBars(viewingMode);
        refreshViewport();
        if(previousFocus!=null && previousFocus.isShown()) previousFocus.requestFocus();
        else if(!tabs.isEmpty()) current().requestFocus();
        previousFocus=null;
        WebChromeClient.CustomViewCallback callback = fullCallback;
        fullCallback = null;
        if(callback != null) callback.onCustomViewHidden();
    }
    private void closeTab(WebView tab) {
        leaveFullscreen();
        pages.removeView(tab); tabs.remove(tab); tab.stopLoading(); tab.destroy();
        current().setVisibility(View.VISIBLE);
        address.setText(current().getUrl());
    }
    private void closeExtraTabs() { while(tabs.size()>1) closeTab(current()); }
    private void goBack() {
        if(tabs.isEmpty()) { super.onBackPressed(); return; }
        cancelRetry();
        if(fullView != null) leaveFullscreen();
        else if(viewingMode) { viewingMode=false; navigation.setVisibility(View.VISIBLE); applyFullscreenBars(false); refreshViewport(); menuButton.requestFocus(); }
        else if(current().canGoBack()) current().goBack();
        else if(tabs.size()>1) closeTab(current());
        else new AlertDialog.Builder(this).setTitle("Exit Stream Window?")
            .setNegativeButton("Keep watching",(d,w)->menuButton.requestFocus())
            .setPositiveButton("Exit",(d,w)->finish()).show();
    }
    @Override public void onBackPressed() { goBack(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if(!tabs.isEmpty()) tabs.get(0).saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override protected void onPause() {
        resumed=false;
        if(pendingRetry!=null && failedMainFrame) showError("Service is unavailable. Automatic retry paused. Select Retry when ready.");
        cancelRetry();
        for(WebView tab:tabs) tab.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed=true;
        for(WebView tab:tabs) tab.onResume();
    }
    @Override protected void onDestroy() {
        cancelRetry();
        leaveFullscreen();
        if(Build.VERSION.SDK_INT>=24) WorkerFilter.clear();
        pages.removeAllViews();
        for(WebView tab:tabs) tab.destroy();
        tabs.clear();
        super.onDestroy();
    }
}
