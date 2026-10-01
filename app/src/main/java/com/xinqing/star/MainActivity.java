package com.xinqing.star;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    private static final String TAG = "XinqingStar";
    private static final int PICK_IMAGE_CODE = 1002;

    private WebView web;

    // 页面加载后注入：劫持选图按钮 → AndroidBridge；
    // 选完图后不再 reload，直接就地更新 DOM
    private static final String PATCH_JS =
        "(function() {" +
        "  function patch() {" +
        "    var btn = document.getElementById('pickImageBtn');" +
        "    if (!btn || btn._xqPatched) { return !!btn; }" +
        "    var newBtn = btn.cloneNode(true);" +
        "    newBtn._xqPatched = true;" +
        "    btn.parentNode.replaceChild(newBtn, btn);" +
        "    newBtn.addEventListener('click', function() {" +
        "      var t = document.getElementById('moodActionTitle');" +
        "      if (!t) { return; }" +
        "      var m = t.textContent.match(/\\u300c(.+?)\\u300d/);" +
        "      if (!m) { return; }" +
        "      window.__xqPendingMood = m[1];" +
        "      if (window.AndroidBridge && typeof window.AndroidBridge.pickImage === 'function') {" +
        "        window.AndroidBridge.pickImage();" +
        "      }" +
        "    });" +
        "    return true;" +
        "  }" +
        "  if (!patch()) {" +
        "    var obs = new MutationObserver(function() {" +
        "      if (patch()) { obs.disconnect(); }" +
        "    });" +
        "    obs.observe(document.body, { childList: true, subtree: true });" +
        "  }" +
        "  window.__onImagePicked = function(dataUrl) {" +
        "    var name = window.__xqPendingMood;" +
        "    window.__xqPendingMood = null;" +
        "    if (!name || !dataUrl) { return; }" +
        "    try {" +
        "      var list = JSON.parse(localStorage.getItem('moodList') || '[]');" +
        "      var found = false;" +
        "      for (var i = 0; i < list.length; i++) {" +
        "        if (list[i].name === name) { list[i].icon = dataUrl; found = true; break; }" +
        "      }" +
        "      if (!found) { return; }" +
        "      localStorage.setItem('moodList', JSON.stringify(list));" +
        "      var imgHtml = '<img class=\\'mood-icon\\' src=\\'' + dataUrl + '\\' alt=\\'\\'>';" +
        // 1. 心情按钮
        "      var btns = document.querySelectorAll('.mood-btn[data-mood=\\'' + name + '\\']');" +
        "      for (var k = 0; k < btns.length; k++) {" +
        "        btns[k].innerHTML = imgHtml + '<span>' + name + '</span>';" +
        "      }" +
        // 2. 输入框预览（如果当前选中的就是这个心情）
        "      var activeBtn = document.querySelector('.mood-btn.active');" +
        "      if (activeBtn && activeBtn.getAttribute('data-mood') === name) {" +
        "        var prev = document.getElementById('moodPreview');" +
        "        if (prev) { prev.innerHTML = imgHtml; }" +
        "      }" +
        // 3. 时光轴
        "      var items = document.querySelectorAll('.timeline-item');" +
        "      for (var k = 0; k < items.length; k++) {" +
        "        var mn = items[k].querySelector('.mood-name');" +
        "        if (mn && mn.textContent === name) {" +
        "          var em = items[k].querySelector('.emoji');" +
        "          if (em) { em.innerHTML = imgHtml; }" +
        "        }" +
        "      }" +
        // 4. 详情卡片
        "      var rcM = document.getElementById('rcMood');" +
        "      var rcE = document.getElementById('rcEmoji');" +
        "      if (rcM && rcE && rcM.textContent === name) {" +
        "        rcE.innerHTML = imgHtml;" +
        "      }" +
        // 5. 关闭操作面板
        "      var modal = document.getElementById('moodActionModal');" +
        "      if (modal) { modal.classList.remove('show'); }" +
        "    } catch (e) {}" +
        "  };" +
        "  window.__onImagePickCancelled = function() {" +
        "    window.__xqPendingMood = null;" +
        "  };" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(0xFF060608);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(PATCH_JS, null);
            }
        });

        web.addJavascriptInterface(new ImageBridge(), "AndroidBridge");

        setContentView(web);

        try {
            String html = readAsset("index.html");
            web.loadDataWithBaseURL("file:///android_asset/", html,
                    "text/html", "UTF-8", null);
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
            String err = "<!DOCTYPE html><html><body style='background:#060608;"
                    + "color:#ff6b6b;font-family:monospace;padding:20px;font-size:14px;'>"
                    + "<h3>加载失败</h3><pre>" + e.toString() + "</pre></body></html>";
            web.loadDataWithBaseURL(null, err, "text/html", "UTF-8", null);
        }
    }

    private class ImageBridge {
        @JavascriptInterface
        public void pickImage() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_PICK);
                        intent.setType("image/*");
                        startActivityForResult(intent, PICK_IMAGE_CODE);
                    } catch (Exception e) {
                        Log.e(TAG, "pick image failed", e);
                        web.evaluateJavascript("window.__onImagePickCancelled()", null);
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_IMAGE_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                final Uri uri = data.getData();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final String base64 = readAndCompressImage(uri);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (base64 != null) {
                                    web.evaluateJavascript(
                                            "window.__onImagePicked('" + base64 + "')", null);
                                } else {
                                    web.evaluateJavascript(
                                            "window.__onImagePickCancelled()", null);
                                }
                            }
                        });
                    }
                }).start();
            } else {
                web.evaluateJavascript("window.__onImagePickCancelled()", null);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String readAndCompressImage(Uri uri) {
        InputStream is = null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            is = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;

            int sampleSize = 1;
            while (opts.outWidth / sampleSize > 256 || opts.outHeight / sampleSize > 256) {
                sampleSize *= 2;
            }

            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = sampleSize;
            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts2);
            is.close();

            if (bitmap == null) return null;

            int maxSize = 64;
            float scale = Math.min(
                    (float) maxSize / bitmap.getWidth(),
                    (float) maxSize / bitmap.getHeight());
            if (scale < 1f) {
                int newW = Math.max(1, Math.round(bitmap.getWidth() * scale));
                int newH = Math.max(1, Math.round(bitmap.getHeight() * scale));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
            bitmap.recycle();
            byte[] bytes = baos.toByteArray();
            String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            Log.e(TAG, "readAndCompressImage failed", e);
            try { if (is != null) is.close(); } catch (IOException ignored) {}
            return null;
        }
    }

    private String readAsset(String name) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getAssets().open(name);
             BufferedReader r = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

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
