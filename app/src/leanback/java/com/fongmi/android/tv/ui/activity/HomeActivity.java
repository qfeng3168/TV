package com.fongmi.android.tv.ui.activity;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivityVodBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.HomeNavAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 主页 = 点播页（对齐设计稿 01）：
 * 顶部品牌条 + 左侧分类导航 + 右侧内容分页。导航里除了站点分类，还固定挂
 * 「直播 / 设置 / 收藏」三个入口，不再单独做一层功能宫格页。
 * 焦点落到分类项上即切页（100ms 去抖）；对已选中项按 OK 展开/收起该分类的筛选。
 * VodActivity 继承本类，只把内容来源换成 intent 里带的文件夹结果。
 */
public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, HomeNavAdapter.OnClickListener {

    private ActivityVodBinding mBinding;
    private HomeNavAdapter mNav;
    private SiteViewModel mViewModel;
    private List<Class> mTypes = new ArrayList<>();
    private Clock mClock;
    private int mPending = -1;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    /**
     * 是否为 App 入口页。入口页才做启动期初始化与退出清理；
     * 作为文件夹浏览页复用时（VodActivity）不能重复做，否则会把底下主页的配置清掉。
     */
    protected boolean isEntry() {
        return true;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVodBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isEntry()) SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        if (isEntry()) {
            PermissionUtil.requestNotify(this);
            DLNARendererService.start(this);
            Updater.create().start(this);
        }
        setNav();
        setPager();
        setViewModel();
        initConfig();
        setTitle();
        setLogo();
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.search.setOnClickListener(v -> SearchActivity.start(this));
        mBinding.nav.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (position < 0 || position >= mTypes.size()) return;
                if (position == mNav.getSelected()) return;
                mPending = position;
                App.post(mRunnable, 100);
            }
        });
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mNav.setSelected(position);
                mBinding.nav.setSelectedPosition(position);
            }
        });
    }

    /**
     * 分页内容取自哪个站点。主页用当前配置的首页站点；
     * 文件夹浏览页（VodActivity）用 intent 里带的 key，可能是另一个站点。
     */
    protected String getKey() {
        return VodConfig.get().getHome().getKey();
    }

    private void setNav() {
        mBinding.nav.setAdapter(mNav = new HomeNavAdapter(this));
        // 设计稿：分类项行距 20px @2x = 10dp
        mBinding.nav.setVerticalSpacing(ResUtil.dp2px(10));
        mNav.setItems(mTypes, getExtraItems());
    }

    private List<Integer> getExtraItems() {
        List<Integer> items = new ArrayList<>();
        if (LiveConfig.hasUrl()) items.add(R.string.home_live);
        items.add(R.string.home_setting);
        items.add(R.string.home_keep);
        return items;
    }

    private void setPager() {
        mBinding.pager.setAdapter(new PageAdapter(getSupportFragmentManager()));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> onContent(result));
    }

    /**
     * 内容就绪：分类列表既驱动左侧导航，也决定右侧分页数量。
     * 子类把 loadContent() 换成自己的数据来源即可复用整套渲染。
     */
    protected void onContent(Result result) {
        if (isFinishing() || isDestroyed()) return;
        Cache.clear().put(result);
        mTypes = result.getTypes();
        mNav.setItems(mTypes, getExtraItems());
        mNav.setSelected(0);
        if (mBinding.pager.getAdapter() != null) mBinding.pager.getAdapter().notifyDataSetChanged();
        mBinding.pager.setCurrentItem(0);
        mBinding.nav.setSelectedPosition(0);
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setFocus();
    }

    protected void loadContent() {
        mViewModel.homeContent();
    }

    private Class getType() {
        int position = mNav == null ? -1 : mNav.getSelected();
        if (mTypes == null || position < 0 || position >= mTypes.size()) return null;
        return mTypes.get(position);
    }

    private FolderFragment getFragment() {
        if (mBinding.pager.getAdapter() == null || mTypes.isEmpty()) return null;
        return (FolderFragment) mBinding.pager.getAdapter().instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
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
        Optional.ofNullable(getFragment()).ifPresent(f -> f.toggleFilter(item.getFilter()));
    }

    public void closeFilter() {
        if (isFilterVisible()) updateFilter();
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    /**
     * 顶部标识：配置自带 logo 就用配置的（保持换源后仍有站点识别度），
     * 没有就用品牌标记——设计稿左上角放的是品牌图形，不是站点头像。
     */
    private void setLogo() {
        if (TextUtils.isEmpty(getConfig().getLogo())) mBinding.logo.setImageResource(R.drawable.ic_home_logo);
        else ImgUtil.logo(mBinding.logo);
    }

    private void setFocus() {
        mBinding.nav.requestFocus();
        App.post(() -> mBinding.title.setFocusable(true), 500);
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        if (!isEntry()) return;
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setFocus();
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            FileChooser.getUri(intent, uri -> loadLive(UrlUtil.toLocalUrl(uri)));
        } else {
            FileChooser.getUri(intent, uri -> VideoActivity.file(this, uri));
        }
    }

    private void loadLive(String url) {
        if (isFinishing() || isDestroyed()) return;
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                setLogo();
                break;
            case COMMON:
                mNav.setItems(mTypes, getExtraItems());
                break;
            case BOOT:
                if (isEntry()) LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                loadContent();
                setTitle();
                break;
            case SIZE:
                loadContent();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (!isEntry()) return;
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (!isEntry()) return;
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history());
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onNavClick(int position) {
        if (position >= mTypes.size()) return;
        if (position == mNav.getSelected()) {
            updateFilter();
            return;
        }
        switchTo(position);
    }

    @Override
    public void onExtra(int resId) {
        if (resId == R.string.home_live) LiveActivity.start(this);
        else if (resId == R.string.home_setting) SettingActivity.start(this);
        else if (resId == R.string.home_keep) KeepActivity.start(this);
    }

    @Override
    public void showDialog() {
        SiteDialog.create().show(this);
    }

    @Override
    public void onRefresh() {
        loadContent();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        Product.setNavOffset(getNavOffset());
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
        Product.setNavOffset(0);
    }

    /**
     * 左侧导航占掉的横向空间，必须与 activity_vod.xml 里的实际留白一致，
     * 否则海报会按整屏宽计算，导致每行放不下预设列数。
     */
    private int getNavOffset() {
        // 内容区左边界 = 外边距 + 导航栏宽 + 栏间距（= 162dp），加上 getSpec 里已含的
        // 左右 24dp 外边距，海报正好按内容区剩余宽度均分。
        int px = getResources().getDimensionPixelSize(R.dimen.kiwi_body_padding)
                + getResources().getDimensionPixelSize(R.dimen.kiwi_nav_width)
                + getResources().getDimensionPixelSize(R.dimen.kiwi_body_gap);
        return ResUtil.px2dp(px);
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (isFilterVisible()) {
            updateFilter();
        } else if (Optional.ofNullable(getFragment()).map(FolderFragment::moveToTop).orElse(false)) {
            return;
        } else if (Optional.ofNullable(getFragment()).map(FolderFragment::canBack).orElse(false)) {
            Optional.ofNullable(getFragment()).ifPresent(FolderFragment::goBack);
        } else if (PlaybackService.isRunning()) {
            Util.moveToBackground(this);
        } else {
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        Product.setNavOffset(0);
        if (!isEntry()) {
            super.onDestroy();
            return;
        }
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        BackupManager.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }

    class PageAdapter extends FragmentStatePagerAdapter {

        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return FolderFragment.newInstance(getKey(), mTypes.get(position));
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
