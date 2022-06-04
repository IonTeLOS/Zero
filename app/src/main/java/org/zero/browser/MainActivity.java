package org.zero.browser;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private CoordinatorLayout coordinatorLayout;
    private WebView webView;
    private FrameLayout bottomBarContainer;
    private ImageButton menuButton;
    private FrameLayout fullScreen;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private boolean isDarkWebContentEnabled;
    private boolean isDefaultAppAvailable;
    private boolean isFullScreen;

    private int nightModePreference;
    private String startPage;
    private String shortcutTitle;
    private IconCompat shortcutIcon;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        nightModePreference = sharedPreferences.getInt("night_mode", -1);
        switch (nightModePreference) {
            case (1):
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case (2):
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case (-1):
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
        setContentView(R.layout.activity_main);
        coordinatorLayout = findViewById(R.id.coordinatorLayout);
        coordinatorLayout.setBackground(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        webView = findViewById(R.id.webView);
        bottomBarContainer = findViewById(R.id.bottomBarContainer);
        menuButton = findViewById(R.id.menuButton);
        fullScreen = findViewById(R.id.fullScreenContainer);

        // Change WebView settings
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setAppCacheEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        webView.getSettings().setOffscreenPreRaster(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        // Enable dark mode for WebView
        isDarkWebContentEnabled = sharedPreferences.getBoolean("dark_web_content", false);

        int isLightThemeEnabled = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (isLightThemeEnabled == Configuration.UI_MODE_NIGHT_YES && isDarkWebContentEnabled) {
            webView.getSettings().setForceDark(WebSettings.FORCE_DARK_ON);
        } else {
            webView.getSettings().setForceDark(WebSettings.FORCE_DARK_OFF);

        }
        // Handle "menu button" in bottom bar
        menuButton.setOnClickListener(view -> showPopupMenu());

        // Handle downloads
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setMimeType(MimeTypeMap.getSingleton().
                    getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url)));
            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Objects.requireNonNull(downloadManager).enqueue(request);
            Snackbar.make(coordinatorLayout, R.string.download_started, Snackbar.LENGTH_SHORT).show();
        });
        webView.setWebChromeClient(new WebChromeClient() {
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        // Handle intents
        startPage = sharedPreferences.getString("start_page", "https://google.com/");
        Intent intent = getIntent();
        Uri uri = intent.getData();
        String url;
        url = (uri != null ? uri.toString() : startPage);

        // Load either the start page or the URL provided by an intent
        webView.loadUrl(url);

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    String[] PERMISSIONS = {
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                    };
                    request.grant(PERMISSIONS);
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                super.onPermissionRequestCanceled(request);
            }

            // Enter fullscreen
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                super.onShowCustomView(view, callback);
                bottomBarContainer.setVisibility(View.GONE);
                fullScreen.setVisibility(View.VISIBLE);
                fullScreen.addView(view);
                enableFullScreen();
                isFullScreen = true;
            }

            // Exit fullscreen
            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                bottomBarContainer.setVisibility(View.VISIBLE);
                fullScreen.setVisibility(View.GONE);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                int isLightThemeEnabled = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (isLightThemeEnabled == Configuration.UI_MODE_NIGHT_NO) {
                    // Light theme is enabled, restore light status bar and nav bar
                    View decorView = getWindow().getDecorView();
                    decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
                isFullScreen = false;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            // Handle external links
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!URLUtil.isValidUrl(url)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    PackageManager packageManager = getPackageManager();
                    if (intent.resolveActivity(packageManager) != null) {
                        // There is at least one app that can handle the link
                        startActivity(Intent.createChooser(intent, getString(R.string.chooser_open_app)));
                    } else {
                        // There is no app
                        Snackbar.make(coordinatorLayout, R.string.url_cannot_be_loaded, Snackbar.LENGTH_SHORT)
                                .show();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    // Show and handle the popup menu
    private void showPopupMenu() {
        final PopupMenu popupMenu = new PopupMenu(this, menuButton);
        popupMenu.inflate(R.menu.menu_main);
        checkDefaultApps();
        popupMenu.getMenu().findItem(R.id.action_open_with_app).setEnabled(isDefaultAppAvailable);
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.action_search) { // Open Google search
                webView.loadUrl("https://google.com");
                return true;
            }

            else if (itemId == R.id.action_homepage) { // Open Zero browser website
                webView.loadUrl("https://zero.unofficial.app");
                return true;
            }

            else if (itemId == R.id.action_reload) { // Reload website
                webView.reload();
                return true;

            } else if (itemId == R.id.action_share) { // Share URL
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share)));
                return true;

            } else if (itemId == R.id.action_new_window) { // Open new window
                Intent newWindowIntent = new Intent(MainActivity.this, MainActivity.class);
                newWindowIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(newWindowIntent);
                return true;

            } else if (itemId == R.id.action_open_with_app) { // Open current link in default app
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
                startActivity(Intent.createChooser(intent, getString(R.string.chooser_open_app)));
                return true;

            } else if (itemId == R.id.action_add_shortcut) { // Pin website shortcut to launcher if supported
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(MainActivity.this)) {
                    pinShortcut();
                } else {
                    Snackbar.make(coordinatorLayout, R.string.shortcuts_not_supported, Snackbar.LENGTH_SHORT)
                            .show();
                }
                return true;

            }
            else if (itemId == R.id.action_permission) { // Open app permissions
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);

            }
            else if (itemId == R.id.action_set_night_mode) { // Set night mode preference
                switch (nightModePreference) {
                    case (1):
                        popupMenu.getMenu().findItem(R.id.action_night_mode_off).setChecked(true);
                        break;
                    case (2):
                        popupMenu.getMenu().findItem(R.id.action_night_mode_on).setChecked(true);
                        break;
                    case (-1):
                        popupMenu.getMenu().findItem(R.id.action_night_mode_auto).setChecked(true);
                        break;
                }
                popupMenu.getMenu().findItem(R.id.action_enabled_dark_web_content).setChecked(isDarkWebContentEnabled);
                return true;
            } else if (itemId == R.id.action_night_mode_off) {
                nightModePreference = 1;
                setNightModePreference();
                return true;
            } else if (itemId == R.id.action_night_mode_on) {
                nightModePreference = 2;
                setNightModePreference();
                return true;
            } else if (itemId == R.id.action_night_mode_auto) {
                nightModePreference = -1;
                setNightModePreference();
                return true;
            } else if (itemId == R.id.action_enabled_dark_web_content) {
                isDarkWebContentEnabled = !isDarkWebContentEnabled;
                editor = sharedPreferences.edit();
                editor.putBoolean("dark_web_content", isDarkWebContentEnabled);
                editor.apply();
                recreate();
                return true;

            } else if (itemId == R.id.action_set_start_page) { // Set custom start page
                setStartPage();
                return true;

            } else if (itemId == R.id.action_clear_data) { // Clear browsing data
                clearBrowsingData();
                return true;

            } else if (itemId == R.id.action_close_window) { // Close window
                finishAndRemoveTask();
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    // Check if there is a default app to open the current link
    private void checkDefaultApps() {
        String packageName = "org.zero.browser";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : list) {
            packageName = info.activityInfo.packageName;
        }
        isDefaultAppAvailable = !packageName.equals("org.zero.browser");
    }

    // Pin website shortcut to launcher
    private void pinShortcut() {
        // Ask for the shortcut title first
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setIcon(R.drawable.ic_view_grid_plus_outline);
        builder.setTitle(R.string.action_add_shortcut);
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.alert_dialog_text_input_layout, null);
        final TextInputLayout textInputLayout = viewInflated.findViewById(R.id.alertDialogTextInputLayout);
        textInputLayout.setHint(getString(R.string.add_shortcut_input_hint));
        final TextInputEditText textInput = viewInflated.findViewById(R.id.alertDialogTextInput);
        textInput.setText(webView.getTitle());
        builder.setView(viewInflated);

        // Get the title for the shortcut
        builder.setPositiveButton(R.string.add, (dialogInterface, i) -> {
            shortcutTitle = (Objects.requireNonNull(textInput.getText()).toString().trim().isEmpty() ?
                    webView.getTitle() : textInput.getText().toString().trim());
            // Create the icon for the shortcut
            createShortcutIcon();
            // Create the shortcut
            Intent pinShortcutIntent = new Intent(MainActivity.this, MainActivity.class);
            pinShortcutIntent.setData(Uri.parse(webView.getUrl()));
            pinShortcutIntent.setAction(Intent.ACTION_MAIN);
            ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(MainActivity.this, shortcutTitle)
                    .setShortLabel(shortcutTitle)
                    .setLongLabel(shortcutTitle)
                    .setIcon(shortcutIcon)
                    .setIntent(pinShortcutIntent)
                    .build();
            ShortcutManagerCompat.requestPinShortcut(MainActivity.this, shortcutInfo, null);
        });
        // Cancel creating shortcut
        builder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss());
        builder.show();
    }

    // Create launcher icons for shortcuts
    private void createShortcutIcon() {
        // Draw background
        Bitmap icon = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        canvas.drawColor(ContextCompat.getColor(getApplicationContext(), R.color.colorShortcuts));
        // Get the first one or two characters of the shortcut title
        String iconText;
        iconText = (shortcutTitle.length() >= 2 ? shortcutTitle.substring(0, 2) : shortcutTitle.substring(0, 1));
        // Draw the first one or two characters on the background
        Paint paintText = new Paint();
        paintText.setAntiAlias(true);
        paintText.setColor(ContextCompat.getColor(getApplicationContext(), android.R.color.white));
        paintText.setTextSize(128);
        paintText.setFakeBoldText(true);
        paintText.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(iconText, canvas.getWidth() / 2f,
                canvas.getHeight() / 2f - (paintText.descent() + paintText.ascent()) / 2f, paintText);
        // Create icon
        shortcutIcon = IconCompat.createWithAdaptiveBitmap(icon);
    }

    // Save night mode preference and apply new theme
    private void setNightModePreference() {
        editor = sharedPreferences.edit();
        editor.putInt("night_mode", nightModePreference);
        editor.apply();
        recreate();
    }

    // Set custom start page
    private void setStartPage() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setIcon(R.drawable.ic_home_edit_outline);
        builder.setTitle(R.string.action_set_start_page);
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.alert_dialog_text_input_layout, null);
        final TextInputLayout textInputLayout = viewInflated.findViewById(R.id.alertDialogTextInputLayout);
        textInputLayout.setHint(getString(R.string.set_start_page_hint));
        final TextInputEditText textInput = viewInflated.findViewById(R.id.alertDialogTextInput);
        textInput.setText(startPage);
        builder.setView(viewInflated);

        // Get custom URL from text input and save it
        builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
            startPage = (Objects.requireNonNull(textInput.getText()).toString().trim().isEmpty() ?
                    startPage : textInput.getText().toString().trim());
            editor = sharedPreferences.edit();
            editor.putString("start_page", startPage);
            editor.apply();
            webView.loadUrl(startPage);
            Snackbar.make(coordinatorLayout, R.string.start_page_saved, Snackbar.LENGTH_SHORT).show();
        });
        // Cancel
        builder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss());
        builder.show();
    }

    // Clear browsing data
    private void clearBrowsingData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_clear_data)
                .setIcon(R.drawable.ic_delete_outline)
                .setMessage(R.string.clear_data_message)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    webView.clearCache(true);
                    CookieManager.getInstance().removeAllCookies(null);
                    WebStorage.getInstance().deleteAllData();
                    webView.loadUrl(startPage);
                    Snackbar.make(coordinatorLayout, R.string.clear_data_confirmation, Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }

    // Restore fullscreen after losing and gaining focus
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isFullScreen) enableFullScreen();
    }

    // Hide status bar and nav bar when entering fullscreen
    private void enableFullScreen() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Prevent the back button from closing the app
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finishAndRemoveTask();
        }
    }
}
