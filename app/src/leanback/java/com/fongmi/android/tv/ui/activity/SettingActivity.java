package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ActivitySettingBinding;
import com.fongmi.android.tv.databinding.AdapterSettingItemBinding;
import com.fongmi.android.tv.databinding.AdapterSettingSwitchBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.DecodeSetting;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ConfigDialog;
import com.fongmi.android.tv.ui.dialog.DohDialog;
import com.fongmi.android.tv.ui.dialog.EpgDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener {

    private static final int PAGE_COMMON = 0;
    private static final int PAGE_PLAYER = 1;
    private static final int PAGE_DECODE = 2;
    private static final int PAGE_VOD = 3;
    private static final int PAGE_LIVE = 4;
    private static final int PAGE_EPG = 5;
    private static final int PAGE_NETWORK = 6;
    private static final int PAGE_ABOUT = 7;

    private ActivitySettingBinding mBinding;
    private String[] size;
    private String[] engine;
    private String[] scale;
    private String[] background;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.vod.requestFocus();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        engine = ResUtil.getStringArray(R.array.select_engine);
        scale = ResUtil.getStringArray(R.array.select_scale);
        background = ResUtil.getStringArray(R.array.select_background);
        setCacheText();
        setOtherText();
        setNavText();
        setPlayerText();
        setDecodeText();
        setVodText();
        setLiveText();
        setEpgText();
        setNetworkText();
        showPage(PAGE_COMMON);
    }

    private void setOtherText() {
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
        mBinding.epgText.setText(LiveSetting.getEpg());
    }

    /** 左侧分类导航：点击切换右侧内容块，隐藏的块不可聚焦 */
    private void setNavText() {
        mBinding.navCommon.setOnClickListener(v -> showPage(PAGE_COMMON));
        mBinding.navPlayer.setOnClickListener(v -> showPage(PAGE_PLAYER));
        mBinding.navDecode.setOnClickListener(v -> showPage(PAGE_DECODE));
        mBinding.navVod.setOnClickListener(v -> showPage(PAGE_VOD));
        mBinding.navLive.setOnClickListener(v -> showPage(PAGE_LIVE));
        mBinding.navEpg.setOnClickListener(v -> showPage(PAGE_EPG));
        mBinding.navNetwork.setOnClickListener(v -> showPage(PAGE_NETWORK));
        mBinding.navAbout.setOnClickListener(v -> showPage(PAGE_ABOUT));
    }

    private void showPage(int page) {
        setActivated(mBinding.navCommon, page == PAGE_COMMON);
        setActivated(mBinding.navPlayer, page == PAGE_PLAYER);
        setActivated(mBinding.navDecode, page == PAGE_DECODE);
        setActivated(mBinding.navVod, page == PAGE_VOD);
        setActivated(mBinding.navLive, page == PAGE_LIVE);
        setActivated(mBinding.navEpg, page == PAGE_EPG);
        setActivated(mBinding.navNetwork, page == PAGE_NETWORK);
        setActivated(mBinding.navAbout, page == PAGE_ABOUT);
        mBinding.viewCommon.setVisibility(page == PAGE_COMMON ? View.VISIBLE : View.GONE);
        mBinding.viewPlayer.setVisibility(page == PAGE_PLAYER ? View.VISIBLE : View.GONE);
        mBinding.viewDecode.setVisibility(page == PAGE_DECODE ? View.VISIBLE : View.GONE);
        mBinding.viewVod.setVisibility(page == PAGE_VOD ? View.VISIBLE : View.GONE);
        mBinding.viewLive.setVisibility(page == PAGE_LIVE ? View.VISIBLE : View.GONE);
        mBinding.viewEpg.setVisibility(page == PAGE_EPG ? View.VISIBLE : View.GONE);
        mBinding.viewNetwork.setVisibility(page == PAGE_NETWORK ? View.VISIBLE : View.GONE);
        mBinding.viewAbout.setVisibility(page == PAGE_ABOUT ? View.VISIBLE : View.GONE);
        mBinding.breadcrumb.setText(ResUtil.getString(R.string.setting_crumb, getTabName(page)));
    }

    private void setActivated(View view, boolean activated) {
        view.setActivated(activated);
    }

    private String getTabName(int page) {
        return switch (page) {
            case PAGE_PLAYER -> ResUtil.getString(R.string.setting_tab_player);
            case PAGE_DECODE -> ResUtil.getString(R.string.setting_tab_decode);
            case PAGE_VOD -> ResUtil.getString(R.string.setting_tab_vod);
            case PAGE_LIVE -> ResUtil.getString(R.string.setting_tab_live);
            case PAGE_EPG -> ResUtil.getString(R.string.setting_tab_epg);
            case PAGE_NETWORK -> ResUtil.getString(R.string.setting_tab_network);
            case PAGE_ABOUT -> ResUtil.getString(R.string.setting_tab_about);
            default -> ResUtil.getString(R.string.setting_tab_common);
        };
    }

    private void setPlayerText() {
        mBinding.itemBackground.name.setText(R.string.setting_background);
        mBinding.itemBackground.desc.setText(R.string.setting_background_desc);
        mBinding.itemBackground.value.setText(background[PlayerSetting.getBackground()]);
        mBinding.itemBuffer.name.setText(R.string.setting_buffer);
        mBinding.itemBuffer.desc.setText(R.string.setting_buffer_desc);
        mBinding.itemBuffer.value.setText(ResUtil.getString(R.string.setting_buffer_second, PlayerSetting.getBuffer()));
    }

    private void setDecodeText() {
        mBinding.itemVideoPrefer.name.setText(R.string.setting_video_prefer);
        mBinding.itemVideoPrefer.desc.setText(R.string.setting_video_prefer_desc);
        setSwitch(mBinding.itemVideoPrefer, DecodeSetting.isVideoPrefer());
        mBinding.itemTunnel.name.setText(R.string.setting_tunnel);
        mBinding.itemTunnel.desc.setText(R.string.setting_tunnel_desc);
        setSwitch(mBinding.itemTunnel, DecodeSetting.isTunnel());
    }

    private void setVodText() {
        mBinding.itemEngine.name.setText(R.string.setting_engine);
        mBinding.itemEngine.desc.setText(R.string.setting_engine_desc);
        mBinding.itemEngine.value.setText(engine[PlayerSetting.getEngine()]);
        mBinding.itemPreloadNext.name.setText(R.string.setting_preload_next);
        mBinding.itemPreloadNext.desc.setText(R.string.setting_preload_next_desc);
        setSwitch(mBinding.itemPreloadNext, PreloadSetting.isNextEpisodeEnabled());
    }

    private void setLiveText() {
        mBinding.itemLiveBoot.name.setText(R.string.setting_live_boot);
        mBinding.itemLiveBoot.desc.setText(R.string.setting_live_boot_desc);
        setSwitch(mBinding.itemLiveBoot, LiveSetting.isBoot());
        mBinding.itemLiveChange.name.setText(R.string.setting_live_change);
        mBinding.itemLiveChange.desc.setText(R.string.setting_live_change_desc);
        setSwitch(mBinding.itemLiveChange, LiveSetting.isChange());
        mBinding.itemLiveAcross.name.setText(R.string.setting_live_across);
        mBinding.itemLiveAcross.desc.setText(R.string.setting_live_across_desc);
        setSwitch(mBinding.itemLiveAcross, LiveSetting.isAcross());
        mBinding.itemLiveInvert.name.setText(R.string.setting_live_invert);
        mBinding.itemLiveInvert.desc.setText(R.string.setting_live_invert_desc);
        setSwitch(mBinding.itemLiveInvert, LiveSetting.isInvert());
        mBinding.itemLiveScale.name.setText(R.string.setting_live_scale);
        mBinding.itemLiveScale.desc.setText(R.string.setting_live_scale_desc);
        mBinding.itemLiveScale.value.setText(scale[LiveSetting.getScale()]);
    }

    private void setEpgText() {
        mBinding.itemEpgSpan.name.setText(R.string.setting_epg_span);
        mBinding.itemEpgSpan.desc.setText(R.string.setting_epg_span_desc);
        mBinding.itemEpgSpan.value.setText(Setting.getEpgSpanText());
        mBinding.itemEpgPast.name.setText(R.string.setting_epg_past);
        mBinding.itemEpgPast.desc.setText(R.string.setting_epg_past_desc);
        setSwitch(mBinding.itemEpgPast, Setting.isEpgPast());
        mBinding.itemEpgOffset.name.setText(R.string.setting_epg_offset);
        mBinding.itemEpgOffset.desc.setText(R.string.setting_epg_offset_desc);
        mBinding.itemEpgOffset.value.setText(Setting.getEpgOffsetText());
        mBinding.itemEpgCatchup.name.setText(R.string.setting_epg_catchup);
        mBinding.itemEpgCatchup.desc.setText(R.string.setting_epg_catchup_desc);
        setSwitch(mBinding.itemEpgCatchup, Setting.isEpgCatchup());
    }

    private void setNetworkText() {
        mBinding.itemPreload.name.setText(R.string.setting_preload);
        mBinding.itemPreload.desc.setText(R.string.setting_preload_desc);
        setSwitch(mBinding.itemPreload, PreloadSetting.isEnabled());
    }

    /** 开关只有两种视觉状态：轨道换底色，滑块换落点 */
    private void setSwitch(AdapterSettingSwitchBinding binding, boolean on) {
        binding.track.setBackgroundResource(on ? R.drawable.shape_switch_track_on : R.drawable.shape_switch_track_off);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) binding.knob.getLayoutParams();
        params.gravity = on ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.START | Gravity.CENTER_VERTICAL;
        binding.knob.setLayoutParams(params);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.version.setOnClickListener(this::onVersion);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.epg.setOnClickListener(this::onEpg);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
        mBinding.decode.setOnClickListener(this::onDecode);
        mBinding.itemEngine.getRoot().setOnClickListener(this::setEngine);
        mBinding.itemBackground.getRoot().setOnClickListener(this::setBackground);
        mBinding.itemBuffer.getRoot().setOnClickListener(this::setBuffer);
        mBinding.itemVideoPrefer.getRoot().setOnClickListener(this::setVideoPrefer);
        mBinding.itemTunnel.getRoot().setOnClickListener(this::setTunnel);
        mBinding.itemPreload.getRoot().setOnClickListener(this::setPreload);
        mBinding.itemPreloadNext.getRoot().setOnClickListener(this::setPreloadNext);
        mBinding.itemLiveBoot.getRoot().setOnClickListener(this::setLiveBoot);
        mBinding.itemLiveChange.getRoot().setOnClickListener(this::setLiveChange);
        mBinding.itemLiveAcross.getRoot().setOnClickListener(this::setLiveAcross);
        mBinding.itemLiveInvert.getRoot().setOnClickListener(this::setLiveInvert);
        mBinding.itemLiveScale.getRoot().setOnClickListener(this::setLiveScale);
        mBinding.itemEpgSpan.getRoot().setOnClickListener(this::setEpgSpan);
        mBinding.itemEpgPast.getRoot().setOnClickListener(this::setEpgPast);
        mBinding.itemEpgOffset.getRoot().setOnClickListener(this::setEpgOffset);
        mBinding.itemEpgCatchup.getRoot().setOnClickListener(this::setEpgCatchup);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    private void onVod(View view) {
        ConfigDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        ConfigDialog.create().live().show(this);
    }

    private void onWall(View view) {
        ConfigDialog.create().wall().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private void onEpg(View view) {
        EpgDialog.create().show(this);
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().action().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.create().action().show(this);
    }

    private void onVodHistory(View view) {
        HistoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        HistoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onDecode(View view) {
        SettingDecodeActivity.start(this);
    }

    private void setEngine(View view) {
        PlayerSetting.putEngine(PlayerSetting.isExo() ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO);
        mBinding.itemEngine.value.setText(engine[PlayerSetting.getEngine()]);
    }

    private void setBackground(View view) {
        PlayerSetting.putBackground((PlayerSetting.getBackground() + 1) % background.length);
        mBinding.itemBackground.value.setText(background[PlayerSetting.getBackground()]);
    }

    private void setBuffer(View view) {
        PlayerSetting.nextBuffer();
        mBinding.itemBuffer.value.setText(ResUtil.getString(R.string.setting_buffer_second, PlayerSetting.getBuffer()));
    }

    private void setVideoPrefer(View view) {
        DecodeSetting.putVideoPrefer(!DecodeSetting.isVideoPrefer());
        setSwitch(mBinding.itemVideoPrefer, DecodeSetting.isVideoPrefer());
    }

    private void setTunnel(View view) {
        DecodeSetting.putTunnel(!DecodeSetting.isTunnel());
        setSwitch(mBinding.itemTunnel, DecodeSetting.isTunnel());
    }

    private void setPreload(View view) {
        PreloadSetting.putEnabled(!PreloadSetting.isEnabled());
        setSwitch(mBinding.itemPreload, PreloadSetting.isEnabled());
    }

    private void setPreloadNext(View view) {
        PreloadSetting.putNextEpisodeEnabled(!PreloadSetting.isNextEpisodeEnabled());
        setSwitch(mBinding.itemPreloadNext, PreloadSetting.isNextEpisodeEnabled());
    }

    private void setLiveBoot(View view) {
        LiveSetting.putBoot(!LiveSetting.isBoot());
        setSwitch(mBinding.itemLiveBoot, LiveSetting.isBoot());
    }

    private void setLiveChange(View view) {
        LiveSetting.putChange(!LiveSetting.isChange());
        setSwitch(mBinding.itemLiveChange, LiveSetting.isChange());
    }

    private void setLiveAcross(View view) {
        LiveSetting.putAcross(!LiveSetting.isAcross());
        setSwitch(mBinding.itemLiveAcross, LiveSetting.isAcross());
    }

    private void setLiveInvert(View view) {
        LiveSetting.putInvert(!LiveSetting.isInvert());
        setSwitch(mBinding.itemLiveInvert, LiveSetting.isInvert());
    }

    private void setLiveScale(View view) {
        LiveSetting.putScale((LiveSetting.getScale() + 1) % scale.length);
        mBinding.itemLiveScale.value.setText(scale[LiveSetting.getScale()]);
    }

    private void setEpgSpan(View view) {
        Setting.putEpgSpan((Setting.getEpgSpan() + 1) % Setting.EPG_SPAN_HOURS.length);
        mBinding.itemEpgSpan.value.setText(Setting.getEpgSpanText());
    }

    private void setEpgPast(View view) {
        Setting.putEpgPast(!Setting.isEpgPast());
        setSwitch(mBinding.itemEpgPast, Setting.isEpgPast());
    }

    private void setEpgOffset(View view) {
        int offset = Setting.getEpgOffset() + 1;
        Setting.putEpgOffset(offset > Setting.MAX_EPG_OFFSET ? Setting.MIN_EPG_OFFSET : offset);
        mBinding.itemEpgOffset.value.setText(Setting.getEpgOffsetText());
    }

    private void setEpgCatchup(View view) {
        Setting.putEpgCatchup(!Setting.isEpgCatchup());
        setSwitch(mBinding.itemEpgCatchup, Setting.isEpgCatchup());
    }

    private void onDanmaku(View view) {
        SettingDanmakuActivity.start(this);
    }

    private void onVersion(View view) {
        Updater.create().force().start(this);
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.getWall() == 4 ? 1 : Setting.getWall() + 1);
        Setting.putWallType(0);
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setSize(View view) {
        int index = (PlayerSetting.getSize() + 1) % size.length;
        mBinding.sizeText.setText(size[index]);
        PlayerSetting.putSize(index);
        RefreshEvent.size();
    }

    private void setDoh(View view) {
        DohDialog.create().index(getDohIndex()).show(this);
    }

    @Override
    public void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> BackupManager.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().callback(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                setOtherText();
                setPlayerText();
                setDecodeText();
                setVodText();
                setLiveText();
                setEpgText();
                setNetworkText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }).show(this));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
    }

}
