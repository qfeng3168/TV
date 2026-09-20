package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;

/**
 * 文件夹浏览页：内容直接来自 intent 里带的 Result，不参与首页刷新。
 * 「左侧分类导航 + 右侧分页」整套渲染都在 HomeActivity 里，这里只换数据来源，
 * 所以主页与文件夹页的视觉、交互完全一致。
 */
public class VodActivity extends HomeActivity {

    public static void start(Activity activity, Result result) {
        start(activity, VodConfig.get().getHome().getKey(), result);
    }

    public static void start(Activity activity, String key, Result result) {
        if (result == null || result.getTypes().isEmpty()) return;
        Intent intent = new Intent(activity, VodActivity.class);
        intent.putExtra("key", key);
        intent.putExtra("result", result);
        activity.startActivity(intent);
    }

    @Override
    protected boolean isEntry() {
        return false;
    }

    @Override
    protected String getKey() {
        String key = getIntent().getStringExtra("key");
        return key == null ? super.getKey() : key;
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        Result result = getIntent().getParcelableExtra("result");
        if (result == null || result.getTypes().isEmpty()) {
            finish();
            return;
        }
        onContent(result);
    }

    /** 内容来自 intent，首页刷新事件不应该覆盖它 */
    @Override
    protected void loadContent() {
    }
}
