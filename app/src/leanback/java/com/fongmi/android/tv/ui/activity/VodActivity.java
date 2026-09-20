package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.ActivityVodBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.adapter.HomeNavAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.utils.KeyUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;
import java.util.Optional;

/**
 * 首页：顶部品牌条 + 左侧分类导航 + 右侧内容分页。
 * 焦点落在导航项上即切页（带 100ms 去抖），对已选中项按 OK 展开/收起该分类的筛选。
 */
public class VodActivity extends BaseActivity implements HomeNavAdapter.OnClickListener {

    private ActivityVodBinding mBinding;
    private HomeNavAdapter mNav;
    private List<Class> mTypes;
    private int mPending = -1;

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

    private String getKey() {
        return getIntent().getStringExtra("key");
    }

    private Result getResult() {
        return getIntent().getParcelableExtra("result");
    }

    private Class getType() {
        if (mTypes == null || mNav == null) return null;
        int position = mNav.getSelected();
        if (position < 0 || position >= mTypes.size()) return null;
        return mTypes.get(position);
    }

    private FolderFragment getFragment() {
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setNav();
        setPager();
    }

    @Override
    protected void initEvent() {
        mBinding.search.setOnClickListener(v -> SearchActivity.start(this));
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mNav.setSelected(position);
                mBinding.nav.setSelectedPosition(position);
            }
        });
        mBinding.nav.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mTypes == null || position < 0 || position >= mTypes.size()) return;
                if (position == mNav.getSelected()) return;
                mPending = position;
                App.post(mRunnable, 100);
            }
        });
    }

    private void setNav() {
        mTypes = getResult().getTypes();
        mBinding.nav.setAdapter(mNav = new HomeNavAdapter(this));
        mNav.addAll(mTypes);
        mNav.setSelected(0);
        mBinding.nav.setSelectedPosition(0);
        mBinding.nav.requestFocus();
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void switchTo(int position) {
        if (mTypes == null || position < 0 || position >= mTypes.size()) return;
        mNav.setSelected(position);
        mBinding.pager.setCurrentItem(position);
    }

    private final Runnable mRunnable = () -> {
        int position = mPending;
        mPending = -1;
        if (position >= 0 && position != mNav.getSelected()) switchTo(position);
    };

    private boolean isFilterVisible() {
        return Optional.ofNullable(getType()).map(Class::getFilter).orElse(false);
    }

    private void updateFilter() {
        Optional.ofNullable(getType()).ifPresent(this::updateFilter);
    }

    private void updateFilter(Class item) {
        item.setFilter(!item.getFilter());
        getFragment().toggleFilter(item.getFilter());
    }

    public void closeFilter() {
        if (isFilterVisible()) updateFilter();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.CATEGORY) getFragment().onRefresh();
    }

    @Override
    public void onNavClick(int position) {
        if (position == mNav.getSelected()) {
            updateFilter();
            return;
        }
        switchTo(position);
    }

    @Override
    public void onShortcut() {
        KeepActivity.start(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) updateFilter();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onBackInvoked() {
        if (isFilterVisible()) updateFilter();
        else if (getFragment().moveToTop()) return;
        else if (getFragment().canBack()) getFragment().goBack();
        else super.onBackInvoked();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            Class type = mTypes.get(position);
            return FolderFragment.newInstance(getKey(), type);
        }

        @Override
        public int getCount() {
            return mTypes.size();
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
