package com.github.rebane621.ruffleapk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    AssetHTTPServer server;
    MyWebView browser;
    boolean isKeyboardShowing = false;
    boolean keyboardBlocked = false;
    ImageView bn_keyboard;
    boolean isMuted = false;
    ImageView bn_settings;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestFullscreenMode(FULLSCREEN_MODE_REQUEST_ENTER,(r)->{});
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.hide();

        bn_keyboard = findViewById(R.id.bn_keyboard);
        bn_keyboard.setOnTouchListener((v,e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN)
                toggleKeyboard();
            return true;
        });
        bn_settings = findViewById(R.id.bn_settings);
        bn_settings.setOnTouchListener((v,e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN)
                showSettings();
            return true;
        });

        browser = findViewById(R.id.browser);

        //hack in a keyboard listener
        View rootView = findViewById(R.id.main);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Rect r = new Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;
                boolean keyboardNow = keypadHeight > screenHeight * 0.15;
                boolean change = isKeyboardShowing != keyboardNow;
                if (change) onKeyboardVisibilityChanged(isKeyboardShowing = keyboardNow);
            });

        WebSettings settings = browser.getSettings();
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false); //allow http
        }
        settings.setUserAgentString("RuffleAPK/1.0 (Internal)");

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        server = new AssetHTTPServer(this);
        server.onReady((port) -> {
            handler.post(() -> {
                System.out.println("Requesting content!");
                browser.loadUrl("http://127.0.0.1:" + port);
            });
        });
        exec.execute(server);
    }

    void toggleKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        keyboardBlocked = !keyboardBlocked;
        if (keyboardBlocked) {
            View view = getCurrentFocus();
            if (view == null) view = browser;
            view.clearFocus();
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            bn_keyboard.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.accent_red)));
            browser.setBlockKeybaord(true);
        } else {
            browser.setBlockKeybaord(false);
            bn_keyboard.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.accent)));
            imm.showSoftInput(browser, 0);
        }
    }

    void showSettings() {
        Rect rect = new Rect();
        browser.getDrawingRect(rect);
        MotionEvent event = MotionEvent.obtain(
                SystemClock.uptimeMillis()-3000,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_DOWN,
                rect.width()*0.9f, 0f, 0);
        browser.dispatchTouchEvent(event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        browser.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        server.running = false;
    }

    void onKeyboardVisibilityChanged(boolean opened) {
        if (opened && keyboardBlocked) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(browser.getWindowToken(), 0);
        }
    }

}