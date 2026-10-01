package com.desudesu.outlookwebfit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String HOME = "https://outlook.office.com/mail/";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/140.0.0.0 Safari/537.36";
    private static final int FILE_CHOOSER = 41;
    private static final String SESSION_PREFS = "browser_sessions";
    private static final String DEFAULT_SESSION = "default";

    private WebView webView;
    private ProgressBar progress;
    private TextView scaleLabel;
    private EditText addressBar;
    private Button reloadButton;
    private Button smallerButton;
    private Button largerButton;
    private FrameLayout rootFrame;
    private LinearLayout drawer;
    private LinearLayout sessionList;
    private View drawerScrim;
    private SharedPreferences sessionPrefs;
    private CookieManager sessionCookies;
    private String currentSessionId;
    private boolean switchingSession;
    private boolean multiProfileSupported;
    private ValueCallback<Uri[]> pendingFiles;
    private int textScale;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sessionPrefs = getSharedPreferences(SESSION_PREFS, MODE_PRIVATE);
        ensureSessionStore();
        currentSessionId = sessionPrefs.getString("current", DEFAULT_SESSION);
        textScale = getPreferences(MODE_PRIVATE).getInt("textScale", 115);
        buildUi();
        hideStatusBar();
        configureBrowser();

        if (state == null) {
            webView.loadUrl(HOME);
        } else {
            webView.restoreState(state);
            applyLayoutMode(webView.getUrl());
            scheduleOutlookFit();
        }
    }

    private void buildUi() {
        rootFrame = new FrameLayout(this);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(Color.WHITE);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(3), dp(4), dp(3));
        bar.setBackgroundColor(Color.rgb(0, 92, 170));

        Button menu = toolbarButton("☰");
        menu.setTextSize(22);
        Button back = toolbarButton("Back");
        reloadButton = toolbarButton("Reload");
        smallerButton = toolbarButton("A-");
        largerButton = toolbarButton("A+");
        scaleLabel = new TextView(this);
        scaleLabel.setTextColor(Color.WHITE);
        scaleLabel.setGravity(Gravity.CENTER);
        scaleLabel.setTextSize(13);
        updateScaleLabel();

        menu.setOnClickListener(v -> toggleDrawer());
        back.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        reloadButton.setOnClickListener(v -> webView.reload());
        smallerButton.setOnClickListener(v -> changeScale(-10));
        largerButton.setOnClickListener(v -> changeScale(10));

        LinearLayout leftControls = new LinearLayout(this);
        leftControls.setGravity(Gravity.CENTER_VERTICAL);
        leftControls.addView(menu, new LinearLayout.LayoutParams(0, dp(44), .8f));
        leftControls.addView(back, new LinearLayout.LayoutParams(0, dp(44), 1f));

        LinearLayout rightControls = new LinearLayout(this);
        rightControls.setGravity(Gravity.CENTER_VERTICAL);
        rightControls.addView(reloadButton, new LinearLayout.LayoutParams(0, dp(44), 1.25f));
        rightControls.addView(smallerButton, new LinearLayout.LayoutParams(0, dp(44), .7f));
        rightControls.addView(scaleLabel, new LinearLayout.LayoutParams(0, dp(44), .8f));
        rightControls.addView(largerButton, new LinearLayout.LayoutParams(0, dp(44), .7f));

        bar.addView(leftControls, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bar.addView(new Space(this), new LinearLayout.LayoutParams(dp(48), dp(44)));
        bar.addView(rightControls, new LinearLayout.LayoutParams(0, dp(44), 1f));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);

        webView = new WebView(this);
        multiProfileSupported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        if (!DEFAULT_SESSION.equals(currentSessionId)) {
            if (multiProfileSupported) {
                WebViewCompat.setProfile(webView, currentSessionId);
            } else {
                currentSessionId = DEFAULT_SESSION;
                sessionPrefs.edit().putString("current", DEFAULT_SESSION).apply();
            }
        }
        sessionCookies = multiProfileSupported
                ? WebViewCompat.getProfile(webView).getCookieManager()
                : CookieManager.getInstance();

        main.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        main.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        main.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rootFrame.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildDrawer();
        setContentView(rootFrame);
    }

    private Button toolbarButton(String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(2), 0, dp(2), 0);
        return button;
    }

    private void buildDrawer() {
        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(0x88000000);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> hideDrawer());
        rootFrame.addView(drawerScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(Color.WHITE);
        drawer.setElevation(dp(12));
        // Keep the final action above Android's gesture/three-button navigation area.
        drawer.setPadding(dp(12), dp(10), dp(12), dp(58));
        drawer.setVisibility(View.GONE);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Browser sessions");
        title.setTextSize(21);
        title.setTextColor(Color.rgb(0, 74, 135));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        Button close = new Button(this);
        close.setText("×");
        close.setTextSize(24);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> hideDrawer());
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        titleRow.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        drawer.addView(titleRow);

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(15);
        addressBar.setHint("URL or search");
        addressBar.setSelectAllOnFocus(true);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setPadding(dp(10), 0, dp(10), 0);
        ImageButton go = new ImageButton(this);
        go.setImageResource(android.R.drawable.ic_menu_search);
        go.setColorFilter(Color.rgb(0, 92, 170));
        go.setBackgroundColor(Color.TRANSPARENT);
        go.setContentDescription("Go");
        go.setOnClickListener(v -> navigateFromAddressBar());
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigateFromAddressBar();
                return true;
            }
            return false;
        });
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addressRow.addView(go, new LinearLayout.LayoutParams(dp(48), dp(48)));
        drawer.addView(addressRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = new TextView(this);
        heading.setText("Mail accounts");
        heading.setTextSize(14);
        heading.setTextColor(Color.DKGRAY);
        heading.setPadding(dp(4), dp(16), dp(4), dp(6));
        drawer.addView(heading);

        ScrollView scroll = new ScrollView(this);
        sessionList = new LinearLayout(this);
        sessionList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(sessionList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        drawer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = new Button(this);
        add.setText("＋  Add separate session");
        add.setAllCaps(false);
        add.setOnClickListener(v -> promptAddSession());
        drawer.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        int panelWidth = Math.min(dp(310),
                (int) (getResources().getDisplayMetrics().widthPixels * 0.88f));
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(
                panelWidth, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        rootFrame.addView(drawer, drawerParams);
        renderSessions();
    }

    private void toggleDrawer() {
        if (drawer.getVisibility() == View.VISIBLE) hideDrawer();
        else showDrawer();
    }

    private void showDrawer() {
        addressBar.setText(webView.getUrl());
        renderSessions();
        drawerScrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        drawer.bringToFront();
    }

    private void hideDrawer() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(drawer.getWindowToken(), 0);
        addressBar.clearFocus();
        drawer.setVisibility(View.GONE);
        drawerScrim.setVisibility(View.GONE);
    }

    private void ensureSessionStore() {
        String ids = sessionPrefs.getString("ids", "");
        if (ids == null || ids.isEmpty()) {
            sessionPrefs.edit()
                    .putString("ids", DEFAULT_SESSION)
                    .putString("name_" + DEFAULT_SESSION, "Primary mailbox")
                    .putString("current", DEFAULT_SESSION)
                    .apply();
        }
    }

    private List<String> getSessionIds() {
        String stored = sessionPrefs.getString("ids", DEFAULT_SESSION);
        List<String> result = new ArrayList<>();
        if (stored != null && !stored.isEmpty()) {
            result.addAll(Arrays.asList(stored.split(",")));
        }
        if (result.isEmpty()) result.add(DEFAULT_SESSION);
        return result;
    }

    private void saveSessionIds(List<String> ids) {
        StringBuilder value = new StringBuilder();
        for (String id : ids) {
            if (value.length() > 0) value.append(',');
            value.append(id);
        }
        sessionPrefs.edit().putString("ids", value.toString()).apply();
    }

    private String sessionName(String id) {
        return sessionPrefs.getString("name_" + id,
                DEFAULT_SESSION.equals(id) ? "Primary mailbox" : "Mailbox");
    }

    private void renderSessions() {
        if (sessionList == null) return;
        sessionList.removeAllViews();
        List<String> ids = getSessionIds();
        for (String id : ids) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(3), dp(2), dp(3));

            Button select = new Button(this);
            String prefix = id.equals(currentSessionId) ? "●  " : "○  ";
            select.setText(prefix + sessionName(id));
            select.setAllCaps(false);
            select.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            select.setTextSize(15);
            select.setOnClickListener(v -> switchSession(id));

            Button rename = new Button(this);
            rename.setText("Rename");
            rename.setAllCaps(false);
            rename.setTextSize(11);
            rename.setPadding(0, 0, 0, 0);
            rename.setOnClickListener(v -> promptRenameSession(id));

            Button delete = new Button(this);
            delete.setText("Delete");
            delete.setAllCaps(false);
            delete.setTextSize(11);
            delete.setPadding(0, 0, 0, 0);
            delete.setEnabled(ids.size() > 1);
            delete.setOnClickListener(v -> confirmDeleteSession(id));

            row.addView(select, new LinearLayout.LayoutParams(0, dp(52), 1f));
            row.addView(rename, new LinearLayout.LayoutParams(dp(68), dp(48)));
            row.addView(delete, new LinearLayout.LayoutParams(dp(62), dp(48)));
            sessionList.addView(row);
        }
    }

    private void promptRenameSession(String id) {
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText(sessionName(id));
        name.setSelectAllOnFocus(true);
        name.setPadding(dp(20), 0, dp(20), 0);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename session")
                .setView(name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String label = name.getText().toString().trim();
                    if (label.isEmpty()) {
                        name.setError("Enter a name");
                        return;
                    }
                    sessionPrefs.edit().putString("name_" + id, label).apply();
                    renderSessions();
                    dialog.dismiss();
                }));
        dialog.show();
        name.requestFocus();
    }

    private void promptAddSession() {
        if (!multiProfileSupported) {
            Toast.makeText(this, "This WebView does not support separate profiles",
                    Toast.LENGTH_LONG).show();
            return;
        }
        EditText name = new EditText(this);
        name.setHint("Account name, e.g. School");
        name.setSingleLine(true);
        name.setPadding(dp(20), 0, dp(20), 0);
        new AlertDialog.Builder(this)
                .setTitle("Add separate session")
                .setMessage("This creates a new browser profile with its own cookies and login.")
                .setView(name)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (dialog, which) -> {
                    String label = name.getText().toString().trim();
                    if (label.isEmpty()) label = "Mailbox " + (getSessionIds().size() + 1);
                    String id = "mail_" + System.currentTimeMillis();
                    List<String> ids = getSessionIds();
                    ids.add(id);
                    saveSessionIds(ids);
                    sessionPrefs.edit()
                            .putString("name_" + id, label)
                            .putString("current", id)
                            .apply();
                    restartForSession();
                })
                .show();
    }

    private void switchSession(String id) {
        if (id.equals(currentSessionId)) {
            hideDrawer();
            return;
        }
        sessionPrefs.edit().putString("current", id).apply();
        restartForSession();
    }

    private void confirmDeleteSession(String id) {
        if (getSessionIds().size() <= 1) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete " + sessionName(id) + "?")
                .setMessage("Its cookies, website storage, and saved login will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteSession(id))
                .show();
    }

    private void deleteSession(String id) {
        clearSessionData(id);
        List<String> ids = getSessionIds();
        ids.remove(id);
        saveSessionIds(ids);
        SharedPreferences.Editor edit = sessionPrefs.edit().remove("name_" + id);
        boolean deletingCurrent = id.equals(currentSessionId);
        if (deletingCurrent) edit.putString("current", ids.get(0));
        edit.apply();
        if (deletingCurrent) restartForSession();
        else renderSessions();
    }

    private void clearSessionData(String id) {
        try {
            if (id.equals(currentSessionId)) {
                sessionCookies.removeAllCookies(null);
                sessionCookies.flush();
                if (multiProfileSupported) {
                    WebViewCompat.getProfile(webView).getWebStorage().deleteAllData();
                } else {
                    android.webkit.WebStorage.getInstance().deleteAllData();
                }
            } else if (DEFAULT_SESSION.equals(id)) {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                android.webkit.WebStorage.getInstance().deleteAllData();
            } else if (multiProfileSupported) {
                try {
                    ProfileStore.getInstance().deleteProfile(id);
                } catch (RuntimeException loadedProfile) {
                    Profile profile = ProfileStore.getInstance().getProfile(id);
                    if (profile != null) {
                        profile.getCookieManager().removeAllCookies(null);
                        profile.getCookieManager().flush();
                        profile.getWebStorage().deleteAllData();
                    }
                }
            }
        } catch (RuntimeException error) {
            Toast.makeText(this, "The session was removed; some cached files may remain",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void restartForSession() {
        switchingSession = true;
        Intent restart = new Intent(this, MainActivity.class);
        finish();
        startActivity(restart);
        overridePendingTransition(0, 0);
    }

    private void configureBrowser() {
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUserAgentString(DESKTOP_UA);

        // This is the key combination: identify as desktop Chrome, but lay the
        // page out at the phone's actual width instead of shrinking a 980px canvas.
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(textScale);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = sessionCookies;
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                applyLayoutMode(url);
                scheduleOutlookFit();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isOutlookUrl(url)) forcePhoneViewport(view);
                if (!addressBar.hasFocus()) addressBar.setText(url);
                sessionCookies.flush();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                } catch (Exception ignored) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    pendingFiles = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "No file picker is installed", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        long contentLength) {
                download(url, contentDisposition, mimeType);
            }
        });
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            loadUrl(uri.toString());
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void navigateFromAddressBar() {
        String value = addressBar.getText().toString().trim();
        if (value.isEmpty()) return;
        String destination;
        if (value.matches("(?i)^https?://.*")) {
            destination = value;
        } else if (!value.contains(" ") && value.contains(".")) {
            destination = "https://" + value;
        } else {
            destination = "https://www.bing.com/search?q=" + Uri.encode(value);
        }
        hideDrawer();
        loadUrl(destination);
    }

    private void loadUrl(String url) {
        applyLayoutMode(url);
        webView.loadUrl(url);
    }

    private boolean isOutlookUrl(String url) {
        if (url == null) return false;
        String host = Uri.parse(url).getHost();
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.US);
        return host.equals("outlook.office.com") ||
                host.endsWith(".outlook.office.com") ||
                host.equals("outlook.office365.com") ||
                host.endsWith(".outlook.office365.com") ||
                host.equals("outlook.live.com") ||
                host.endsWith(".outlook.live.com") ||
                host.equals("outlook.cloud.microsoft") ||
                host.endsWith(".outlook.cloud.microsoft");
    }

    private void applyLayoutMode(String url) {
        boolean outlook = isOutlookUrl(url);
        webView.getSettings().setUseWideViewPort(!outlook);
        webView.getSettings().setLoadWithOverviewMode(!outlook);
        webView.getSettings().setTextZoom(outlook ? textScale : 100);
    }

    private void scheduleOutlookFit() {
        int[] delays = {100, 400, 1000, 2500};
        for (int delay : delays) {
            webView.postDelayed(() -> {
                String current = webView.getUrl();
                if (isOutlookUrl(current)) {
                    applyLayoutMode(current);
                    forcePhoneViewport(webView);
                }
            }, delay);
        }
    }

    private void hideStatusBar() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            View decor = getWindow().getDecorView();
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideStatusBar();
        if (webView != null) scheduleOutlookFit();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideStatusBar();
    }

    private void forcePhoneViewport(WebView view) {
        String script = "(function(){" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                "m.content='width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes';" +
                "document.documentElement.style.webkitTextSizeAdjust='100%';" +
                "if(window.__outlookWebFitInstalled){if(window.__outlookWebFitApply)window.__outlookWebFitApply();return;}" +
                "window.__outlookWebFitInstalled=true;" +
                "var css='#LeftRail,.o365sx-navbar{display:none!important;width:0!important;' +" +
                "'min-width:0!important;max-width:0!important;overflow:hidden!important}' +" +
                "'#app,#appContainer{max-width:100vw!important}' +" +
                "'#ReadingPaneContainerId{min-width:0!important;max-width:100%!important}';" +
                "var style=document.createElement('style');style.id='outlook-web-fit-style';" +
                "style.textContent=css;(document.head||document.documentElement).appendChild(style);" +
                "var mailCss='html,body{min-width:0!important;max-width:100%!important}' +" +
                "'body{margin-left:auto!important;margin-right:auto!important;overflow-wrap:anywhere!important}' +" +
                "'table{max-width:100%!important}' +" +
                "'img,video,svg{max-width:100%!important;height:auto!important}';" +
                "function fitFrame(frame){try{" +
                "var d=frame.contentDocument;if(!d)return;" +
                "if(!d.getElementById('outlook-web-fit-mail-style')){" +
                "var s=d.createElement('style');s.id='outlook-web-fit-mail-style';s.textContent=mailCss;" +
                "(d.head||d.documentElement).appendChild(s);}" +
                "}catch(e){}}" +
                "var timer=0;window.__outlookWebFitApply=function(){" +
                "document.querySelectorAll('#LeftRail,.o365sx-navbar').forEach(function(el){" +
                "el.style.setProperty('display','none','important');" +
                "el.style.setProperty('width','0','important');" +
                "el.style.setProperty('min-width','0','important');});" +
                "document.querySelectorAll('iframe').forEach(function(frame){fitFrame(frame);" +
                "if(!frame.__outlookWebFitHooked){frame.__outlookWebFitHooked=true;" +
                "frame.addEventListener('load',function(){fitFrame(frame);});}});};" +
                "window.__outlookWebFitApply();" +
                "new MutationObserver(function(){clearTimeout(timer);timer=setTimeout(window.__outlookWebFitApply,80);})" +
                ".observe(document.documentElement,{childList:true,subtree:true});" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private void changeScale(int delta) {
        textScale = Math.max(80, Math.min(180, textScale + delta));
        webView.getSettings().setTextZoom(textScale);
        getPreferences(MODE_PRIVATE).edit().putInt("textScale", textScale).apply();
        updateScaleLabel();
    }

    private void updateScaleLabel() {
        if (scaleLabel != null) scaleLabel.setText(textScale + "%");
    }

    private void download(String url, String disposition, String mime) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            Toast.makeText(this, "This type of download is not supported", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String cookie = sessionCookies.getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                request.addRequestHeader("Cookie", cookie);
            }
            request.addRequestHeader("User-Agent", DESKTOP_UA);
            request.setMimeType(mime);
            request.setTitle(URLUtil.guessFileName(url, disposition, mime));
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, disposition, mime));
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Downloading attachment", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download could not be started", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && pendingFiles != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            pendingFiles.onReceiveValue(result);
            pendingFiles = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (!switchingSession) webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && drawer != null &&
                drawer.getVisibility() == View.VISIBLE) {
            hideDrawer();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (pendingFiles != null) pendingFiles.onReceiveValue(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
