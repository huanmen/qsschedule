package org.example.adskipfixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.util.Log;

public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String mode = getIntent().getStringExtra("mode");
        if ("splash".equals(mode) || "interstitial".equals(mode) || "ordinary-close".equals(mode)) {
            showGenericFixture(mode);
            return;
        }
        boolean ad = "ad".equals(getIntent().getStringExtra("mode"));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(40, 80, 40, 40);
        TextView status = new TextView(this);
        status.setId(R.id.fixture_status);
        status.setTextSize(24);
        status.setText(ad ? "广告展示中" : "普通页面");
        page.addView(status);
        if (ad) {
            TextView marker = new TextView(this);
            marker.setId(R.id.ad_container);
            marker.setText("广告");
            page.addView(marker);
        }
        Button close = new Button(this);
        close.setId(R.id.close_btn);
        close.setText("关闭");
        close.setOnClickListener(v -> status.setText(ad ? "广告已关闭" : "普通按钮被点击"));
        page.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(page);
    }

    private void showGenericFixture(String mode) {
        FrameLayout page = new FrameLayout(this);
        TextView status = new TextView(this);
        status.setId(R.id.fixture_status);
        status.setTextSize(24);
        status.setText("Fixture: " + mode);
        FrameLayout.LayoutParams info = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        page.addView(status, info);
        Button close = new Button(this);
        close.setText("splash".equals(mode) ? "跳过 5s" : "关闭");
        boolean popup = !"splash".equals(mode);
        FrameLayout container = new FrameLayout(this);
        if ("interstitial".equals(mode)) {
            container.setId(R.id.tobid_interstitial_ad_detail);
            close.setId(R.id.tobid_interstitial_skip_text);
        }
        FrameLayout.LayoutParams box = new FrameLayout.LayoutParams(popup ? -1 : -1, popup ? 700 : -1,
            popup ? Gravity.CENTER : Gravity.TOP);
        box.leftMargin = popup ? 60 : 0;
        box.rightMargin = popup ? 60 : 0;
        page.addView(container, box);
        FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(200, 110, Gravity.TOP | Gravity.RIGHT);
        button.topMargin = 40;
        button.rightMargin = 40;
        close.setOnClickListener(v -> {
            Log.i("AdSkipFixture", "clicked mode=" + mode);
            container.removeView(close);
            status.setText("Fixture dismissed: " + mode);
        });
        container.addView(close, button);
        setContentView(page);
        Log.i("AdSkipFixture", "ready mode=" + mode);
    }
}
