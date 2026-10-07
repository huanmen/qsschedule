package org.example.adskipfixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
}
