package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.PassListener;
import com.fongmi.android.tv.model.LiveViewModel;
import com.fongmi.android.tv.playback.PlaybackAction;
import com.fongmi.android.tv.playback.PlaybackReset;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.live.LivePlayRequest;
import com.fongmi.android.tv.playback.live.LivePlaybackController;
import com.fongmi.android.tv.playback.live.LivePlaybackHost;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.ChannelAdapter;
import com.fongmi.android.tv.ui.adapter.EpgDataAdapter;
import com.fongmi.android.tv.ui.adapter.EpgDateAdapter;
import com.fongmi.android.tv.ui.adapter.GroupAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDownLive;
import com.fongmi.android.tv.ui.custom.CustomLiveListView;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.PlayerEngineDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class LiveActivity extends PlaybackActivity implements GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, EpgDateAdapter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, PassListener, ConfigListener, LiveListener, LivePlaybackHost {

    private ActivityLiveBinding mBinding;
    private LiveViewModel mViewModel;
    private LivePlaybackController mLive;
    private GroupAdapter mGroupAdapter;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private EpgDateAdapter mEpgDateAdapter;
    private CustomKeyDownLive mKeyDown;
    private Clock mClock;
    private View mOldView;
    private View mFocus2;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private List<Group> mHides;
    private Group mGroup;
    private Channel mChannel;
    private Channel mEpgChannel;
    private EpgData mCurrentEpg;
    /** 节目单当前选中的日期（yyyy-MM-dd）。null = 尚未选择（打开节目单时初始化）。 */
    private String mEpgDate;
    private String mPlaybackKey;
    /** 当前时移流的起点墙钟毫秒（= 上一次时移落点）；0 表示不在时移态。左右键位移以此为准。 */
    private long mShiftAnchor;
    private int count;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()));
    }

    private boolean isEmpty() {
        return getIntent().getBooleanExtra("empty", true);
    }

    private Group getKeep() {
        return mGroupAdapter.get(0);
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected String getPlaybackKey() {
        return mPlaybackKey;
    }

    @Override
    protected PlayerView getPlayerView() {
        return mBinding.player;
    }

    @Override
    protected PlayerSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        mLive.onPlaybackServiceReady();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownLive.create(this);
        mHides = new ArrayList<>();
        mR0 = this::setSelected;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mR4 = this::hideUI;
        setRecyclerView();
        setVideoView();
        setViewModel();
        checkLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        mBinding.channel.setListener(this);
        mBinding.epgData.setListener(this);
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.action.setOnClickListener(view -> onAction());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.widget.shift.setOnClickListener(view -> onShift());
        mBinding.widget.epg.setOnClickListener(view -> onEpg());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mGroupAdapter.getItemCount() > 0) onChildSelected(child, mGroup = mGroupAdapter.get(position));
            }
        });
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
        // 仿电视家：节目单右侧的竖排日期列；节目单行上按「右」经默认焦点搜索进入日期列
        mBinding.epgDates.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mBinding.epgDates.setAdapter(mEpgDateAdapter = new EpgDateAdapter(this));
        mBinding.epgDates.setItemAnimator(null);
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        setSeekNextFocusDown(R.id.config);
        setActionFocusBoundary(mBinding.control.action.getRoot());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player, mBinding.control.action.decode);
    }

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        mBinding.player.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mLive = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.playback(), this::onPlaybackObserved);
        observeWhenServiceReady(mViewModel.error(), this::onLoadErrorObserved);
        observeForever(mViewModel.epg(), this::onEpgLoaded);
        mViewModel.live().observe(this, this::onLiveParsed);
        mViewModel.xml().observe(this, this::onXmlParsed);
    }

    private void onLiveParsed(Live live) {
        mChannelAdapter.setZoneId(live.getZoneId());
        mViewModel.parseXml(live);
        setGroup(live);
        setWidth(live);
    }

    private void onPlaybackObserved(PlaybackResult<LivePlayRequest> result) {
        mLive.onPlaybackResult(result);
    }

    private void onLoadErrorObserved(String msg) {
        if (msg == null || msg.isEmpty()) return;
        resetPlaybackForError(msg);
    }

    private void checkLive() {
        if (isEmpty()) {
            LiveConfig.get().init().load(getCallback());
        } else {
            getLive();
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                getLive();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void getLive() {
        mBinding.control.action.home.setText(LiveConfig.isOnly() ? getString(R.string.live_refresh) : getHome().getName());
        mViewModel.parse(getHome());
        showProgress();
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        for (Group group : live.getGroups()) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.addAll(items);
        setPosition(LiveConfig.get().findKeepPosition(items));
    }

    private void setWidth(Live live) {
        int padding = ResUtil.dp2px(52);
        if (live.getWidth() == 0) for (Group item : live.getGroups()) live.setWidth(Math.max(live.getWidth(), ResUtil.getTextWidth(item.getName(), 16)));
        int width = live.getWidth() == 0 ? 0 : Math.min(live.getWidth() + padding, ResUtil.getScreenWidth() / 4);
        setWidth(mBinding.group, width);
    }

    private Group setWidth(Group group) {
        int padding = ResUtil.dp2px(64);
        if (group.isKeep()) group.setWidth(0);
        if (group.getWidth() == 0) for (Channel item : group.getChannel()) group.setWidth(Math.max(group.getWidth(), ResUtil.getTextWidth(item.getNumber() + item.getName(), 16)));
        int width = group.getWidth() == 0 ? 0 : Math.min(group.getWidth() + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.channel, width);
        return group;
    }

    private void setWidth(Epg epg) {
        setWidth(Collections.singletonList(epg));
    }

    /** 节目单列宽 = 所有日期里最长的节目文本 + 日期列，切换日期时列宽不跳动。 */
    private void setWidth(List<Epg> days) {
        int padding = ResUtil.dp2px(84);
        int minWidth = 0;
        int maxTitle = 0;
        for (Epg day : days) {
            if (day.getList().isEmpty()) continue;
            minWidth = Math.max(minWidth, ResUtil.getTextWidth(day.getList().get(0).getTime(), 14));
            for (EpgData item : day.getList()) maxTitle = Math.max(maxTitle, ResUtil.getTextWidth(item.getTitle(), 16));
        }
        if (maxTitle == 0) return;
        int maxWidth = ResUtil.getScreenWidth() / 2;
        int minContentWidth = Math.min(minWidth + padding, maxWidth);
        int width = Math.clamp(maxTitle + padding, minContentWidth, maxWidth);
        setWidth(mBinding.epgWrap, width + ResUtil.dp2px(76));
    }

    private void setWidth(View view, int width) {
        view.post(() -> {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params.width == width) return;
            params.width = width;
            view.setLayoutParams(params);
        });
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mBinding.group.setSelectedPosition(position[0]);
        mGroup.setPosition(position[1]);
        mLive.selectGroup(mGroup);
        mLive.selectChannel(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mBinding.group.getSelectedPosition() != position;
        if (change) mBinding.group.setSelectedPosition(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        mBinding.channel.setSelectedPosition(mGroup.getPosition());
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, Group group) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        onItemClick(group);
        resetPass();
    }

    private void setSelected() {
        mChannelAdapter.setSelected(mChannel);
        notifyItemChanged(mBinding.channel, mChannelAdapter);
    }

    private void setSelected(EpgData item) {
        mEpgDataAdapter.setSelected(item);
        notifyItemChanged(mBinding.epgData, mEpgDataAdapter);
    }

    private void checkPlay() {
        if (player().isPlaying()) onPaused();
        else onPlay();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).view(mBinding.player.getSubtitleView()).show(this);
        hideControl();
    }

    private void onHome() {
        if (LiveConfig.isOnly()) setLive(getHome());
        else LiveDialog.create().show(this);
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        int index = LiveSetting.getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        return true;
    }

    private void onConfig() {
        HistoryDialog.create().live().readOnly().show(this);
        hideControl();
    }

    private void onAction() {
        checkPlay();
    }

    private void onInvert() {
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
    }

    private void onAcross() {
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
    }

    private void onChange() {
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private void onDecode() {
        player().toggleDecode();
    }

    private void hideUI() {
        App.removeCallbacks(mR4);
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        setPosition();
        setUITimer();
        hideEpg();
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            mLive.nextChannel();
        }

        @Override
        public void onPrev() {
            mLive.prevChannel();
        }

        @Override
        public void onStop() {
            finish();
        }
    };

    @Override
    protected void onPrepare() {
        setPlaybackMode();
    }

    @Override
    protected void onDecodeChanged() {
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onError(String msg) {
        mLive.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mLive.refresh();
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                player().reset();
                break;
            case Player.STATE_ENDED:
                mLive.playbackEnded();
                updatePlayControl(false);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying || isPaused()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.action.action.setText(isPlaying ? R.string.pause : R.string.play);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mBinding.widget.size.setText(player().getSizeText());
    }

    /** 仿电视家：频道行右键呼出节目单。节目单开在「当前焦点那一行」的频道上，不再要求它必须是正在播的
        频道 —— 否则未播放过的频道右键毫无反应，也就无从回放/时移。节目单数据在批量解析时已绑到每个频道。 */
    @Override
    public void showEpg(Channel item) {
        if (item == null) return;
        // 日期条：这个频道所有有节目的日期（批量 XML 一次给 8 天；单频道 {date} 通道给 ±1 天）
        List<Epg> days = new ArrayList<>(item.getDataList());
        days.removeIf(epg -> epg.getList().isEmpty());
        days.sort(Comparator.comparing(Epg::getDate));
        if (days.isEmpty()) return;
        Epg day = pickDay(days);
        List<EpgData> items = day.filterSelected(day.getDate().equals(today()));
        if (items.isEmpty()) return;
        mEpgChannel = item;
        mEpgDate = day.getDate();
        mEpgDateAdapter.addAll(days, mEpgDate);
        mEpgDataAdapter.addAll(items);
        mBinding.epgDates.post(() -> mBinding.epgDates.scrollToPosition(mEpgDateAdapter.getPosition(mEpgDate)));
        focusDay(day, items);
        // 仿电视家：右键呼出节目单时隐藏分组列、保留频道列，节目单贴在频道单右侧
        mBinding.epgWrap.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.GONE);
        mBinding.epgHint.setVisibility(View.GONE);
        mBinding.epgData.requestFocus();
        setWidth(days);
    }

    /** 选定某天后列表落在哪：当前正在播的那条；过去一天没有在播的就落第一条。 */
    private void focusDay(Epg day, List<EpgData> items) {
        EpgData current = day.getCurrent();
        if (current != null) {
            mEpgDataAdapter.setSelected(current);
            mBinding.epgData.setSelectedPosition(Math.max(items.indexOf(current), 0));
        } else {
            mBinding.epgData.setSelectedPosition(0);
        }
    }

    /** 打开节目单时默认落在哪一天：今天；没有今天取今天之前最近的一天；再没有取第一天。 */
    private Epg pickDay(List<Epg> days) {
        String today = today();
        for (Epg day : days) if (day.getDate().equals(today)) return day;
        Epg before = null;
        for (Epg day : days) {
            if (day.getDate().compareTo(today) >= 0) continue;
            if (before == null || day.getDate().compareTo(before.getDate()) > 0) before = day;
        }
        if (before != null) return before;
        Epg next = null;
        for (Epg day : days) {
            if (next == null || day.getDate().compareTo(next.getDate()) < 0) next = day;
        }
        return next;
    }

    private String today() {
        return LocalDate.now(mViewModel.getZoneId()).format(Formatters.DATE);
    }

    @Override
    public void onDatePick(Epg day) {
        List<EpgData> items = day.filterSelected(day.getDate().equals(today()));
        if (items.isEmpty() || mEpgChannel == null) return;
        mEpgDate = day.getDate();
        mEpgDateAdapter.setSelected(mEpgDate);
        mEpgDataAdapter.addAll(items);
        focusDay(day, items);
    }

    @Override
    public void onEdgeLeft() {
        // 日期列上按左键 = 焦点回节目单列（电视家同向：节目单右键进日期列，左键退回）；
        // 再按左会经默认焦点搜索落到频道列，BACK 整个关掉节目单
        mBinding.epgData.requestFocus();
    }

    @Override
    public RecyclerView getRecycler() {
        return mBinding.epgDates;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public void hideEpg() {
        mEpgChannel = null;
        mEpgDate = null;
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.epgWrap.setVisibility(View.GONE);
        mBinding.epgHint.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
    }

    @Override
    public void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl(View view) {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.widget.top.setVisibility(View.VISIBLE);
        App.post(view::requestFocus, 25);
        setR1Callback();
        hideInfo();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showInfo() {
        mBinding.widget.bottom.setVisibility(View.VISIBLE);
        setR3Callback();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.bottom.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork() {
        ImgUtil.load(this, mChannel.getLogo(), new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.player.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.player.setDefaultArtwork(errorDrawable);
            }
        });
    }

    @Override
    public void onItemClick(Group item) {
        mLive.selectGroup(item);
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        PassDialog.create().show(this);
        App.removeCallbacks(mR4);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (!item.getData(mViewModel.getZoneId()).getList().isEmpty() && item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            showEpg(item);
        } else if (mGroup != null) {
            mGroup.setPosition(mBinding.channel.getSelectedPosition());
            mLive.selectChannel(item.group(mGroup));
            hideUI();
        }
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = Keep.exist(item.getName());
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        return true;
    }

    @Override
    public void onItemClick(EpgData item) {
        Channel target = mEpgChannel == null ? mChannel : mEpgChannel;
        if (target == null || mGroup == null) return;
        // 节目单可能开在「还没播放过」的频道上：先把分组位置同步到频道列的焦点行，再交给控制器切台/回放
        int position = mBinding.channel.getSelectedPosition();
        if (position >= 0) mGroup.setPosition(position);
        // 这是「回看」线路（catchup-source），不是时移态 ⇒ 清掉时移锚点，
        // 否则之后按左右键会误判成还在时移里，走「重建流」而不是流内位移。
        mShiftAnchor = 0;
        mLive.selectEpg(target.group(mGroup), item, player().getPosition());
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        keep.save();
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        if (mChannelAdapter.getItemCount() == 0) mBinding.group.requestFocus();
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mCurrentEpg = null;
        mBinding.widget.play.setText("");
        mBinding.widget.state.setVisibility(View.GONE);
        mBinding.widget.shift.setVisibility(View.GONE);
        mBinding.widget.epg.setVisibility(View.GONE);
        mBinding.widget.name.setMaxEms(48);
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.control.action.line.setText(mChannel.getLine());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setVisibility(mChannel.getLineVisible());
    }

    private void onEpgLoaded(Epg epg) {
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        mCurrentEpg = data;
        boolean hasTitle = !data.getTitle().isEmpty();
        // 节目单列正开在别的频道上时，别被「正在播频道」的回调覆盖掉；
        // 面板开在当前频道但用户翻到了别的日期，也别把列表拽回今天
        if (mEpgChannel == null) {
            mEpgDataAdapter.addAll(epg.filter());
            setWidth(epg);
        } else if (mEpgChannel.equals(mChannel) && today().equals(mEpgDate)) {
            mEpgDataAdapter.addAll(epg.filter());
            if (data != null) mEpgDataAdapter.setSelected(data);
        }
        mBinding.widget.name.setMaxEms(hasTitle ? 12 : 48);
        mBinding.widget.play.setText(data.format());
        mBinding.widget.epg.setVisibility(epg.filter().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.widget.shift.setVisibility(mChannel.hasShift() && Setting.isEpgCatchup() ? View.VISIBLE : View.GONE);
        mLive.onEpgChanged(data);
    }

    private void onXmlParsed(boolean success) {
        if (!success) return;
        mChannelAdapter.refresh();
        if (mChannel != null) mViewModel.getEpg(mChannel);
    }

    private void stopPlayer() {
        player().clear();
        player().stop();
    }

    @Override
    public int getGroupCount() {
        return mGroupAdapter.getItemCount();
    }

    @Override
    public int getGroupPosition() {
        return mBinding.group.getSelectedPosition();
    }

    @Override
    public Group getGroup(int position) {
        return mGroupAdapter.get(position);
    }

    @Override
    public boolean isPlayerLive() {
        return player().isLive();
    }

    @Override
    public boolean hasPlaybackSession() {
        return mPlaybackKey != null && service() != null && isOwner() && player().hasPlaySpec();
    }

    @Override
    public boolean isPlaybackServiceReady() {
        return service() != null;
    }

    @Override
    public void restorePlaybackKey(@Nullable String key) {
        if (key == null || !player().hasPlaySpec() || !key.equals(player().getKey())) return;
        updateNavigationKey(mPlaybackKey = key);
    }

    @Override
    public long getPlayerPosition() {
        return player().getPosition();
    }

    @Override
    public ZoneId getZoneId() {
        return mViewModel.getZoneId();
    }

    @Override
    public void onCatchupRequested() {
        hideUI();
    }

    @Override
    public void stopPlaybackForRefresh() {
        stopPlayer();
    }

    @Override
    public void startPlayback(Result result, long position, MediaMetadata metadata) {
        Log.i("KSHIFT", "startPlayback pos=" + position + " url=" + result.getRealUrl());
        startPlayer(mPlaybackKey = result.getRealUrl(), result, false, getHome().getTimeout(), position, metadata);
        // 起播 5s 后回读「流」的真实状态：isLive/duration 能直接判定服务端给的是直播流还是时移/回放流，
        // position 则说明播放器实际落在流内哪个位置。这三个数才是这件事的唯一客观判据。
        App.post(this::logStreamState, 5000);
    }

    private void logStreamState() {
        Log.i("KSHIFT", "after5s isLive=" + player().isLive() + " dur=" + player().getDuration()
                + " pos=" + player().getPosition() + " anchor=" + mShiftAnchor
                + " key=" + mPlaybackKey);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player());
        mBinding.widget.state.setVisibility(View.GONE);
        showError(msg);
    }

    @Override
    public void renderGroupSelection(Group group) {
        mGroup = group;
        mBinding.group.setSelectedPosition(mGroupAdapter.indexOf(group));
    }

    @Override
    public void renderGroupChannels(Group group) {
        mChannelAdapter.addAll(setWidth(group).getChannel());
        mBinding.channel.setSelectedPosition(Math.max(group.getPosition(), 0));
    }

    @Override
    public void renderChannelSelection(Channel channel) {
        App.post(mR0, 100);
        mChannel = channel;
        // 换台即退出时移：锚点必须清掉，否则新频道上按左右键会拿着上一台的墙钟基准去算落点
        mShiftAnchor = 0;
        setArtwork();
        showInfo();
    }

    @Override
    public void renderLineSelection(Channel channel, boolean show) {
        if (show) showInfo();
        else setInfo();
    }

    @Override
    public void renderEpgSelection(EpgData data) {
        setSelected(data);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mBinding.widget.title.setText(metadata.displayTitle);
        mBinding.widget.title.setSelected(true);
    }

    @Override
    public void renderPlaybackState(@Nullable LivePlayRequest request) {
        // 状态徽章：时移/回放播放真正落地才显示；直播请求（含退时移回直播）= 隐藏
        boolean show = request != null && request.isCatchup();
        mBinding.widget.state.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) mBinding.widget.state.setText(request.isShift() ? R.string.live_shift : R.string.epg_catchup);
    }

    @Override
    public void showCatchupReady(EpgData data) {
        Notify.show(getString(R.string.play_ready, data.getTitle()));
    }

    /** 时移 = 回到当前节目的起点重看。起始位置必须给 0（时移流的第一帧就是节目起点），
        原来传的是直播位置 —— 直播流里那个值约等于 0 或干脆是「现在」，两种都等于原地不动，
        用户看到的就是「按了时移没反应」。 */
    private void onShift() {
        if (mChannel == null || !mChannel.hasShift() || !Setting.isEpgCatchup()) return;
        EpgData data = mCurrentEpg == null ? null : (mCurrentEpg.getTitle().isEmpty() ? null : mCurrentEpg);
        if (data == null) data = mChannel.getData(mViewModel.getZoneId()).getCurrent();
        if (data == null) return;
        // 底部栏的时移入口：从该节目起点起播时移流 ⇒ 锚点 = 节目起点，
        // 这样进去之后再按左右键，位移是接着这条流继续走，而不是从「现在」重算。
        mShiftAnchor = data.getStartTime();
        mLive.selectShift(data, 0);
    }

    private void onEpg() {
        if (mChannel == null) return;
        showEpg(mChannel);
    }

    private void resetAdapter() {
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.widget.title.setText("");
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
        mEpgChannel = null;
        mShiftAnchor = 0;
    }

    @Override
    public void setConfig(Config config) {
        Config current = LiveConfig.get().getConfig();
        LiveConfig.load(config, getCallback(current));
    }

    private Callback getCallback(Config config) {
        return new Callback() {
            @Override
            public void start() {
                showProgress();
            }

            @Override
            public void success() {
                setLive(getHome());
            }

            @Override
            public void error(String msg) {
                LiveConfig.load(config, new Callback());
                Notify.show(msg);
                hideProgress();
            }
        };
    }

    @Override
    public void setLive(Live item) {
        if (item.isSelected()) item.getGroups().clear();
        LiveConfig.get().setHome(item);
        player().reset();
        player().clear();
        player().stop();
        resetAdapter();
        hideControl();
        mLive.reset();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    private void unlock(String pass) {
        boolean first = true;
        int position = mGroupAdapter.getItemCount();
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(mGroupAdapter.getItemCount(), item);
            if (first) mBinding.group.setSelectedPosition(position);
            if (first) onItemClick(mGroup = item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case LIVE -> setLive(getHome());
            case PLAYER -> mLive.refresh();
        }
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video, mBinding.control.action.speed);
    }

    private void prevChannel() {
        mLive.prevChannel();
    }

    private void nextChannel() {
        mLive.nextChannel();
    }

    private void prevLine() {
        mLive.prevLine();
    }

    private void nextLine(boolean show) {
        mLive.nextLine(show);
    }

    private void seek(long time) {
        mKeyDown.reset();
        seekTo(time);
        // OSD 不能一直挂着：hideCenter() 原来只有 showProgress() 会调，而这个源上 RTSP
        // 流内 seek 未必真的重新缓冲 ⇒ 前进/后退图标会永远留在屏幕上（用户实测）。
        hideCenter();
    }

    /** 是否具备时移能力。时移走 shift-source 线路，和 EPG 点击的「回看」（catchup-source）是两条线，
        这里只判时移，别把回看能力混进来。 */
    private boolean canShift() {
        return mChannel != null && mChannel.hasShift() && Setting.isEpgCatchup();
    }

    /** 时移位移：offset 是相对「当前看到的时刻」的位移（负 = 往回）。
        直播态：当前播放位置就是当前墙钟时间，基准 base = now；时移态：base = 流起点 + 流内已播
        位置。向前时移的左边界 = 直播的开始时间 = EPG 当前节目的开始时间 —— 最多退到本节目开头，
        不跨到上一节目（用户约定）。
        模板参数必须是 playseek：{b} 就是流的起点、npt 恒从 0 开始，把落点直接写进 URL 重建流
        即可精准落点；该源的 RTSP 流内 seek 实测无效（OSD 位置数字会变、画面不动），因此位移
        一律走重建流，不用 seek。starttime 参数服务端不实现（2026-09-22 DESCRIBE 实证：
        starttime 任何形式都返回 s=live，playseek 才返回 s=vod）。 */
    private void shiftTo(long offset) {
        mKeyDown.reset();
        // 松手即收起落点 OSD。放在最前面是为了「无论跳转成不成功都收」——原先只在成功分支外
        // 的 seek() 里收，时移这条链根本走不到，于是前进/后退图标会一直挂在画面上（用户实测）。
        hideCenter();
        if (!canShift() || mChannel == null) {
            Log.i("KSHIFT", "shiftTo abort canShift=" + canShift() + " channel=" + (mChannel == null ? "null" : mChannel.getName()));
            return;
        }
        long now = System.currentTimeMillis();
        long base = mShiftAnchor > 0 ? mShiftAnchor + Math.max(0, player().getPosition()) : now;
        // 右键追平直播点 = 关闭时移，直接回直播（用户约定：关闭时移后返回直播）
        if (mShiftAnchor > 0 && base + offset >= now - 2000) {
            Log.i("KSHIFT", "shiftTo catch-up live, exit shift");
            mShiftAnchor = 0;
            mLive.backToLive();
            return;
        }
        long target = Math.min(base + offset, now - 2000);   // 不允许越过直播点
        if (target <= 0) {
            Log.i("KSHIFT", "shiftTo abort target<=0 base=" + base);
            return;
        }
        Epg epg = mChannel.getData(mViewModel.getZoneId());
        // 左边界：不早于当前节目的开始时间（直播的开始时间），越界收回到本节目开头
        EpgData current = mCurrentEpg != null && !mCurrentEpg.getTitle().isEmpty()
                ? mCurrentEpg : epg.getCurrent();
        if (current == null) {
            Log.i("KSHIFT", "shiftTo abort current==null epgSize=" + epg.getList().size()
                    + " curTitle=" + (mCurrentEpg == null ? "null" : mCurrentEpg.getTitle()));
            return;
        }
        if (target < current.getStartTime()) target = current.getStartTime();
        EpgData data = epg.findByTime(target);
        Log.i("KSHIFT", "shiftTo offset=" + offset + " base=" + base + " target=" + target
                + " isLive=" + player().isLive() + " epgSize=" + epg.getList().size()
                + " data=" + (data == null ? "null" : data.getTitle() + " " + data.getStartTime() + "~" + data.getEndTime()));
        if (data == null) return;
        mShiftAnchor = target;
        mLive.shiftTo(data, 0, target);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE ? mBinding.control.action.config : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (mKeyDown.hasEvent(event) && service() != null) mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setUITimer() {
        App.post(mR4, Constant.INTERVAL_HIDE);
    }

    @Override
    public boolean dispatch(boolean check) {
        return !check || isGone(mBinding.recycler) && isGone(mBinding.control.getRoot());
    }

    @Override
    public void onShow(String number) {
        mBinding.widget.digital.setText(number);
        mBinding.widget.digital.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFind(String number) {
        mBinding.widget.digital.setVisibility(View.GONE);
        setPosition(LiveConfig.get().findByChannelNumber(number, mGroupAdapter.unmodifiableList()));
    }

    @Override
    public void onSeeking(long time) {
        Log.i("KSHIFT", "onSeeking time=" + time + " isLive=" + player().isLive()
                + " pos=" + player().getPosition() + " dur=" + player().getDuration()
                + " anchor=" + mShiftAnchor + " canShift=" + canShift());
        if (mLive.isLiveRequest() && mShiftAnchor <= 0) {
            // 纯直播态按左右键 = 准备进入时移。只有「频道支持时移」且「往回看」才给反馈，
            // 否则左键还是原来的上一条线路。直播流没有可 seek 的时间轴，position/duration
            // 在直播态取不到值，这里按墙钟把落点显示出来。
            if (!canShift() || time >= 0) return;
            long now = System.currentTimeMillis();
            mBinding.widget.center.setVisibility(View.VISIBLE);
            mBinding.widget.position.setText(Formatters.TIME_SEC.format(Instant.ofEpochMilli(now + time)));
            mBinding.widget.duration.setText(Formatters.TIME_SEC.format(Instant.ofEpochMilli(now)));
        } else {
            // 时移 / 回放态：显示流内进度。duration 就是服务端给的这条流的总长
            // （时移流若被服务端当直播返回，这里会看到 dur=0，一眼能分辨）。
            mBinding.widget.center.setVisibility(View.VISIBLE);
            mBinding.widget.duration.setText(player().getDurationTime());
            mBinding.widget.position.setText(player().getPositionTime(time));
        }
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onKeyUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onKeyDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onKeyLeft(long time) {
        // 路由按「当前播放请求」而不是 player.isLive()：HMS 直播流起播约 5 秒后被 ExoPlayer
        // 翻成非直播（a=range:clock=0-），isLive 会把直播态误判成回放态，左键落进流内 seek
        // 分支（该源流内 seek 实测无效 = 按了没反应，装机两次复现）。
        Log.i("KSHIFT", "onKeyLeft time=" + time + " isLiveReq=" + mLive.isLiveRequest()
                + " canShift=" + canShift() + " anchor=" + mShiftAnchor
                + " ch=" + (mChannel == null ? "null" : mChannel.getName()));
        if (mLive.isLiveRequest()) {
            // 直播态按左 = 进入时移（shift-source 线路）。没有时移能力的频道维持原来的「上一条线路」。
            if (canShift() && time < 0) shiftTo(time);
            else prevLine();
        } else if (mShiftAnchor > 0) {
            // 已在时移态：继续用「重建流」的方式位移（这条源的流内 seek 无效，见 shiftTo 注释）
            shiftTo(time);
        } else App.post(() -> seek(time), 250);
    }

    @Override
    public void onKeyRight(long time) {
        Log.i("KSHIFT", "onKeyRight time=" + time + " isLiveReq=" + mLive.isLiveRequest()
                + " anchor=" + mShiftAnchor);
        if (mLive.isLiveRequest()) nextLine(true);
        else if (mShiftAnchor > 0) shiftTo(time);
        else App.post(() -> seek(time), 250);
    }

    @Override
    public void onKeyCenter() {
        hideInfo();
        showUI();
    }

    @Override
    public void onMenu() {
        showControl(getFocus2());
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        else if (isVisible(mBinding.control.getRoot())) hideControl();
        else onMenu();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.bottom)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else {
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        Source.get().exit();
        App.removeCallbacks(mR0, mR1, mR2, mR3, mR4);
        super.onDestroy();
    }
}
