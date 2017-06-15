package com.simon.android_pullrefreshlayout;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.simon.core.pullrefresh.widget.PullRefreshLayout;

public class MainActivity extends AppCompatActivity {

    PullRefreshLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        layout = (PullRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        layout.setOnRefreshListener(new PullRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                layout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        layout.setRefreshing(false);
                    }
                }, 4000);
            }
        });
    }
}
