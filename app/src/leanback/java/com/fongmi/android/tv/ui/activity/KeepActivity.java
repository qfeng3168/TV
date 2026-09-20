package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.HistoryAdapter;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.Notify;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepActivity extends BaseActivity implements KeepAdapter.OnClickListener, HistoryAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mKeepAdapter;
    private HistoryAdapter mHistoryAdapter;
    private Clock mClock;
    private boolean mKeepTab = true;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityKeepBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.clock);
        mKeepAdapter = new KeepAdapter(this);
        mHistoryAdapter = new HistoryAdapter(this);
        setRecyclerView();
        selectTab(true);
    }

    @Override
    protected void initEvent() {
        mBinding.tabKeep.setOnClickListener(v -> selectTab(true));
        mBinding.tabHistory.setOnClickListener(v -> selectTab(false));
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
    }

    private void selectTab(boolean keep) {
        mKeepTab = keep;
        mBinding.tabKeep.setSelected(keep);
        mBinding.tabKeep.setTextColor(keep ? 0xFF00DC5A : 0xFFE4E7EB);
        mBinding.tabHistory.setSelected(!keep);
        mBinding.tabHistory.setTextColor(keep ? 0xFFE4E7EB : 0xFF00DC5A);
        mBinding.recycler.setAdapter(keep ? mKeepAdapter : mHistoryAdapter);
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
        if (keep) getKeep();
        else getHistory();
    }

    private void getKeep() {
        mBinding.progressLayout.showProgress();
        mKeepAdapter.setItems(Keep.getVod(), () -> mBinding.progressLayout.showContent(true, mKeepAdapter.getItemCount()));
    }

    private void getHistory() {
        mBinding.progressLayout.showProgress();
        mHistoryAdapter.setItems(History.get(), () -> mBinding.progressLayout.showContent(true, mHistoryAdapter.getItemCount()));
    }

    private void loadConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.KEEP && mKeepTab) getKeep();
        else if (event.getType() == RefreshEvent.Type.HISTORY && !mKeepTab) getHistory();
    }

    @Override
    public void onItemClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(Keep item) {
        mKeepAdapter.remove(item.delete(), () -> {
            if (mKeepAdapter.getItemCount() == 0) mKeepAdapter.setDelete(false);
        });
    }

    @Override
    public void onItemDelete(History item) {
        mHistoryAdapter.remove(item.delete());
        if (mHistoryAdapter.getItemCount() == 0) mHistoryAdapter.setDelete(false);
    }

    @Override
    public boolean onLongClick() {
        if (mKeepTab) mKeepAdapter.setDelete(!mKeepAdapter.isDelete());
        else mHistoryAdapter.setDelete(!mHistoryAdapter.isDelete());
        return true;
    }

    @Override
    protected void onBackInvoked() {
        if (mKeepTab && mKeepAdapter.isDelete()) {
            mKeepAdapter.setDelete(false);
        } else if (!mKeepTab && mHistoryAdapter.isDelete()) {
            mHistoryAdapter.setDelete(false);
        } else {
            super.onBackInvoked();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }
}
