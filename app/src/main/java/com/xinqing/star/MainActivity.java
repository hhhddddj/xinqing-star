package com.xinqing.star;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

public class MainActivity extends Activity {

    private static final String TAG = "XinqingStar";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate start");

        WebView web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);

        // 先加载一个写死的极简 HTML，不碰 assets
        String html = "<!DOCTYPE html><html><body style='background:#060608;color:#e8c766;"
                + "font-family:sans-serif;display:flex;align-items:center;"
                + "justify-content:center;height:100vh;margin:0;font-size:24px;'>"
                + "hello 星云</body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);

        setContentView(web);

        Log.d(TAG, "onCreate done");
    }
}
