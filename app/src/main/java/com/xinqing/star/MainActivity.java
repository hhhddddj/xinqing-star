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

        web.setWebViewClient(new WebViewClient());

        // 关键：JS 通过这个桥调起系统相册
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

    /** JS 侧的桥接对象 */
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
                                    // base64 里不会有单引号，直接拼接是安全的
                                    web.evaluateJavascript(
                                            "window.__onImagePicked('" + base64 + "')",
                                            null);
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

    /** 读图 → 缩放到最大 64×64 → 转 PNG base64 */
    private String readAndCompressImage(Uri uri) {
        InputStream is = null;
        try {
            // 1. 先读尺寸
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            is = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;

            // 2. 计算采样率，避免大图 OOM
            int sampleSize = 1;
            while (opts.outWidth / sampleSize > 256 || opts.outHeight / sampleSize > 256) {
                sampleSize *= 2;
            }

            // 3. 真正解码
            BitmapFactory.Options opts2 = new BitmapFactory.Options();
            opts2.inSampleSize = sampleSize;
            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, opts2);
            is.close();

            if (bitmap == null) return null;

            // 4. 精确缩放到 64×64
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

            // 5. 转 PNG base64
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
