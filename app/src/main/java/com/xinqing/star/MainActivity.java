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
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final String TAG = "XinqingStar";
    private static final int PICK_IMAGE_CODE = 1002;
    private static final int SAVE_FILE_CODE = 1003;

    private WebView web;
    private String pendingJson = null;

    private static final String PATCH_JS =
        "(function() {" +
        "  if (window._xqPatched) { return; }" +
        "  window._xqPatched = true;" +

        /* ===== A. 让图标/颜色从 localStorage 读（有缓存） ===== */
        "  var _origIconFn = window.moodIconHtmlByName;" +
        "  var _origColorFn = window.moodColorByName;" +
        "  var _iconCache = null, _lastIconJson = null;" +
        "  var _colorCache = null, _lastColorJson = null;" +
        "  function refreshCache() {" +
        "    try {" +
        "      var json = localStorage.getItem('moodList') || '[]';" +
        "      if (json !== _lastIconJson) {" +
        "        _lastIconJson = json; _colorCache = null;" +
        "        try { _iconCache = JSON.parse(json); } catch (e) { _iconCache = []; }" +
        "      }" +
        "    } catch (e) { _iconCache = []; }" +
        "  }" +
        "  window.moodIconHtmlByName = function(name) {" +
        "    refreshCache();" +
        "    var list = _iconCache || [];" +
        "    for (var i = 0; i < list.length; i++) {" +
        "      if (list[i].name === name) {" +
        "        var m = list[i];" +
        "        if (m.icon) { return '<img class=\"mood-icon\" src=\"' + m.icon + '\" alt=\"\">'; }" +
        "        return '<span>' + (m.emoji || '😀') + '</span>';" +
        "      }" +
        "    }" +
        "    return _origIconFn ? _origIconFn(name) : '<span>❓</span>';" +
        "  };" +
        "  window.moodColorByName = function(name) {" +
        "    refreshCache();" +
        "    var list = _iconCache || [];" +
        "    for (var i = 0; i < list.length; i++) {" +
        "      if (list[i].name === name) { return list[i].color || '#666'; }" +
        "    }" +
        "    return _origColorFn ? _origColorFn(name) : '#666';" +
        "  };" +

        /* ===== B. 保存记录时自动补 time ===== */
        "  var _origSetItem = localStorage.setItem;" +
        "  var _lastRecordsJson = localStorage.getItem('moodRecords') || '[]';" +
        "  var _times = {};" +
        "  try { _times = JSON.parse(localStorage.getItem('_xqMoodTimes') || '{}'); } catch (e) {}" +
        "  localStorage.setItem = function(key, value) {" +
        "    if (key === 'moodRecords') {" +
        "      try {" +
        "        try { _times = JSON.parse(localStorage.getItem('_xqMoodTimes') || '{}'); } catch (e) {}" +
        "        var newArr = JSON.parse(value);" +
        "        var oldArr = JSON.parse(_lastRecordsJson);" +
        "        var maxOldId = 0;" +
        "        for (var i = 0; i < oldArr.length; i++) { if (oldArr[i].id > maxOldId) { maxOldId = oldArr[i].id; } }" +
        "        var d = new Date();" +
        "        var ts = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);" +
        "        var changed = false;" +
        "        for (var i = 0; i < newArr.length; i++) {" +
        "          var rec = newArr[i];" +
        "          if (!rec.time) {" +
        "            if (_times[rec.id]) { rec.time = _times[rec.id]; changed = true; }" +
        "            else if (rec.id > maxOldId) { rec.time = ts; changed = true; }" +
        "          }" +
        "          if (rec.time) { _times[rec.id] = rec.time; }" +
        "        }" +
        "        try { _origSetItem.call(localStorage, '_xqMoodTimes', JSON.stringify(_times)); } catch (e) {}" +
        "        if (changed) { value = JSON.stringify(newArr); }" +
        "        _lastRecordsJson = value;" +
        "      } catch (e) {}" +
        "    }" +
        "    return _origSetItem.call(localStorage, key, value);" +
        "  };" +

        /* ===== C. 详情卡片 / 时光轴显示时间 ===== */
        "  function wrapShow() {" +
        "    if (typeof window.showRecordCard !== 'function') { return false; }" +
        "    if (window.showRecordCard._xqWrapped) { return true; }" +
        "    var orig = window.showRecordCard;" +
        "    window.showRecordCard = function(record) {" +
        "      orig(record);" +
        "      if (!record || !record.id) { return; }" +
        "      try {" +
        "        var recs = JSON.parse(localStorage.getItem('moodRecords') || '[]');" +
        "        var rec = null;" +
        "        for (var i = 0; i < recs.length; i++) { if (recs[i].id === record.id) { rec = recs[i]; break; } }" +
        "        if (rec && rec.time) {" +
        "          var el = document.getElementById('rcDate');" +
        "          if (el) { el.textContent = record.date + ' ' + rec.time; }" +
        "        }" +
        "      } catch (e) {}" +
        "    };" +
        "    window.showRecordCard._xqWrapped = true;" +
        "    return true;" +
        "  }" +
        "  function wrapTimeline() {" +
        "    if (typeof window.renderTimeline !== 'function') { return false; }" +
        "    if (window.renderTimeline._xqWrapped) { return true; }" +
        "    var orig = window.renderTimeline;" +
        "    window.renderTimeline = function() {" +
        "      orig();" +
        "      try {" +
        "        var recs = JSON.parse(localStorage.getItem('moodRecords') || '[]');" +
        "        var items = document.querySelectorAll('.timeline-item');" +
        "        for (var k = 0; k < items.length; k++) {" +
        "          var del = items[k].querySelector('.delete-btn');" +
        "          if (!del) { continue; }" +
        "          var id = Number(del.dataset.id);" +
        "          var rec = null;" +
        "          for (var i = 0; i < recs.length; i++) { if (recs[i].id === id) { rec = recs[i]; break; } }" +
        "          if (rec && rec.time) {" +
        "            var de = items[k].querySelector('.date');" +
        "            if (de) { de.textContent = rec.date + ' ' + rec.time; }" +
        "          }" +
        "        }" +
        "      } catch (e) {}" +
        "    };" +
        "    window.renderTimeline._xqWrapped = true;" +
        "    return true;" +
        "  }" +

        /* ===== D. 选图按钮 → AndroidBridge ===== */
        "  function patchPick() {" +
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

        /* ===== E. 下载文件按钮 → AndroidBridge ===== */
        "  function patchDownload() {" +
        "    var btn = document.getElementById('downloadExportBtn');" +
        "    if (!btn || btn._xqDlPatched) { return !!btn; }" +
        "    var newBtn = btn.cloneNode(true);" +
        "    newBtn._xqDlPatched = true;" +
        "    btn.parentNode.replaceChild(newBtn, btn);" +
        "    newBtn.addEventListener('click', function() {" +
        "      var ta = document.getElementById('exportText');" +
        "      if (!ta) { return; }" +
        "      var json = ta.value;" +
        "      var d = new Date();" +
        "      var p2 = function(n) { return ('0' + n).slice(-2); };" +
        "      var stamp = d.getFullYear() + p2(d.getMonth() + 1) + p2(d.getDate());" +
        "      var filename = 'xinqing-star-' + stamp + '.json';" +
        "      if (window.AndroidBridge && typeof window.AndroidBridge.saveBackup === 'function') {" +
        "        window.AndroidBridge.saveBackup(json, filename);" +
        "      }" +
        "    });" +
        "    return true;" +
        "  }" +

        /* ===== F. 导入按钮：捕获阶段抓图，原逻辑跑完后补回 ===== */
        "  function patchImport() {" +
        "    var btn = document.getElementById('doImportBtn');" +
        "    if (!btn || btn._xqImportPatched) { return !!btn; }" +
        "    btn._xqImportPatched = true;" +
        "    btn.addEventListener('click', function() {" +
        "      var backupIcons = {};" +
        "      try {" +
        "        var ta = document.getElementById('importText');" +
        "        if (ta) {" +
        "          var parsed = JSON.parse(ta.value.trim());" +
        "          if (parsed && Array.isArray(parsed.moods)) {" +
        "            for (var i = 0; i < parsed.moods.length; i++) {" +
        "              var m = parsed.moods[i];" +
        "              if (m && m.name && (m.icon || m.color)) {" +
        "                backupIcons[m.name] = { icon: m.icon || null, color: m.color || null };" +
        "              }" +
        "            }" +
        "          }" +
        "        }" +
        "      } catch (e) { return; }" +
        "      if (!backupIcons || Object.keys(backupIcons).length === 0) { return; }" +
        "      setTimeout(function() {" +
        "        try {" +
        "          var current = JSON.parse(localStorage.getItem('moodList') || '[]');" +
        "          var changed = false;" +
        "          for (var i = 0; i < current.length; i++) {" +
        "            var b = backupIcons[current[i].name];" +
        "            if (!b) { continue; }" +
        "            if (b.icon && current[i].icon !== b.icon) { current[i].icon = b.icon; changed = true; }" +
        "            if (b.color && current[i].color !== b.color) { current[i].color = b.color; changed = true; }" +
        "          }" +
        "          if (changed) {" +
        "            localStorage.setItem('moodList', JSON.stringify(current));" +
        "            if (typeof window.refreshAll === 'function') { window.refreshAll(); }" +
        "          }" +
        "        } catch (e) {}" +
        "      }, 0);" +
        "    }, true);" +
        "    return true;" +
        "  }" +

        /* ===== G. 回调 ===== */
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
        "      _lastIconJson = null;" +
        "      if (typeof window.refreshAll === 'function') { window.refreshAll(); }" +
        "      var modal = document.getElementById('moodActionModal');" +
        "      if (modal) { modal.classList.remove('show'); }" +
        "    } catch (e) {}" +
        "  };" +
        "  window.__onImagePickCancelled = function() { window.__xqPendingMood = null; };" +
        "  window.__onBackupSaved = function() {" +
        "    var btn = document.getElementById('downloadExportBtn');" +
        "    if (!btn) { return; }" +
        "    var old = btn.textContent;" +
        "    btn.textContent = '已保存 ✓';" +
        "    setTimeout(function() { btn.textContent = old; }, 1600);" +
        "  };" +
        "  window.__onBackupSaveCancelled = function() {};" +

        /* ===== H. 启动劫持 ===== */
        "  function tryWrap() { return wrapShow() && wrapTimeline(); }" +
        "  if (!tryWrap()) {" +
        "    var t = setInterval(function() { if (tryWrap()) { clearInterval(t); } }, 200);" +
        "    setTimeout(function() { clearInterval(t); }, 5000);" +
        "  }" +
        "  function patchAll() { return patchPick() && patchDownload() && patchImport(); }" +
        "  if (!patchAll()) {" +
        "    var obs = new MutationObserver(function() {" +
        "      if (patchAll()) { obs.disconnect(); }" +
        "    });" +
        "    obs.observe(document.body, { childList: true, subtree: true });" +
        "  }" +
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

        @JavascriptInterface
        public void saveBackup(final String json, final String suggestedName) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        pendingJson = json;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/json");
                        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
                        startActivityForResult(intent, SAVE_FILE_CODE);
                    } catch (Exception e) {
                        Log.e(TAG, "save backup failed", e);
                        pendingJson = null;
                        web.evaluateJavascript("window.__onBackupSaveCancelled()", null);
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

        if (requestCode == SAVE_FILE_CODE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                final Uri uri = data.getData();
                final String json = pendingJson;
                pendingJson = null;
                if (json != null) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final boolean ok = writeTextToUri(uri, json);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (ok) {
                                        web.evaluateJavascript(
                                                "window.__onBackupSaved()", null);
                                    } else {
                                        web.evaluateJavascript(
                                                "window.__onBackupSaveCancelled()", null);
                                    }
                                }
                            });
                        }
                    }).start();
                }
            } else {
                pendingJson = null;
                web.evaluateJavascript("window.__onBackupSaveCancelled()", null);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean writeTextToUri(Uri uri, String text) {
        OutputStream os = null;
        try {
            os = getContentResolver().openOutputStream(uri);
            if (os == null) return false;
            os.write(text.getBytes("UTF-8"));
            os.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "write failed", e);
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (IOException ignored) {}
        }
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
