package com.xinqing.star;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 心情星云 —— WebView 壳
 *
 * 本文件由 classes.dex 的符号表还原（D8 8.2.2-dev, release 模式, 源文件名 MainActivity.java）。
 * 类结构、方法签名、调用的 API 与常量字符串均取自 dex 的 type_ids / method_ids / string_ids，
 * 语句顺序为按语义推断的等价实现。
 *
 * 修改说明：
 * - API 30+ 走 WindowInsetsController 实现沉浸式全屏（SYSTEM_UI_FLAG_* 在 R 之后对导航栏失效）。
 * - onBackPressed 保留，仅压制 deprecation 警告（项目未引入 androidx）。
 */
public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Window win = getWindow();

        // 无标题栏
        win.requestFeature(Window.FEATURE_NO_TITLE);

        // 与 index.html 的 body 背景 (#060608) 保持一致，避免冷启动白闪
        final int bg = 0xFF060608;
        win.setStatusBarColor(bg);
        win.setNavigationBarColor(bg);

        // 刘海屏内容延伸到 cutout 区域（API 28+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = win.getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            win.setAttributes(attrs);
        }

        // 沉浸式全屏：状态栏 + 导航栏都让给内容
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+：SYSTEM_UI_FLAG_HIDE_NAVIGATION / FULLSCREEN 已失效，改走 controller
            WindowInsetsController c = win.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            win.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        web = new WebView(this);
        web.setBackgroundColor(bg);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);              // 星图全靠 JS 跑
        s.setDomStorageEnabled(true);              // localStorage 存心情记录
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient());
        setContentView(web);

        web.loadUrl("file:///android_asset/index.html");
    }

    /** 返回键优先回退网页历史，没有历史时才退出 */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
