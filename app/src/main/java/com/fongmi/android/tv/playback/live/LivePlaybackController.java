package com.fongmi.android.tv.playback.live;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.setting.LiveSetting;

public class LivePlaybackController {

    private final LivePlaybackHost host;
    private final LiveDataSource dataSource;
    private final LivePlaybackState state;

    public LivePlaybackController(LivePlaybackHost host, LiveDataSource dataSource, LivePlaybackState state) {
        this.host = host;
        this.dataSource = dataSource;
        this.state = state;
    }

    public void reset() {
        state.reset();
    }

    public void selectGroup(Group group) {
        state.setGroup(group);
        host.renderGroupSelection(group);
        host.renderGroupChannels(group);
    }

    public void selectChannel(Channel channel) {
        if (channel == null) return;
        LivePlayRequest activeRequest = state.getActiveRequest();
        boolean restore = activeRequest != null && activeRequest.matches(channel);
        state.setChannel(channel);
        host.renderChannelSelection(channel);
        if (restore) restorePlaybackIfNeeded();
        else {
            state.clearPlayback();
            requestLive();
        }
    }

    public boolean selectEpg(EpgData data) {
        return selectEpg(data, C.TIME_UNSET);
    }

    /** 在「还没播放过」的频道上选节目：先把播放状态切到这个频道，再按节目起播。
        正在播的那条 = 用户就是想看这个台，走直播；已播完的走回放/时移。
        （原来这里只能靠 state 里正在播的频道，别的频道点节目单没反应。） */
    public boolean selectEpg(Channel channel, EpgData data, long startPositionMs) {
        if (channel == null || data == null) return false;
        if (channel.equals(state.getChannel())) return selectEpg(data, startPositionMs);
        boolean live = data.isInRange();
        if (live) host.renderEpgSelection(data);
        selectChannel(channel);
        return live || selectEpg(data, C.TIME_UNSET);
    }

    public boolean selectEpg(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null || data == null) return false;
        // A channel that only declares shift-source (no catchup, not RTSP)
        // has no catchup URL to build, so an EPG click here goes through the
        // shift path instead of silently failing.
        if (channel.hasShift() && !channel.hasCatchup() && !channel.isRtsp()) return selectShift(data, startPositionMs);
        if (data.isSelected()) return requestCatchup(data, startPositionMs);
        if (!channel.hasCatchup() && !channel.isRtsp()) return false;
        host.showCatchupReady(data);
        host.renderEpgSelection(data);
        return requestCatchup(data, C.TIME_UNSET);
    }

    public boolean selectShift(EpgData data) {
        return selectShift(data, C.TIME_UNSET);
    }

    public boolean selectShift(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null || data == null) return false;
        if (!channel.hasShift()) return false;
        if (data.isSelected()) return requestShift(data, startPositionMs);
        host.renderEpgSelection(data);
        return requestShift(data, C.TIME_UNSET);
    }

    public boolean shiftTo(EpgData data, long positionMs) {
        return shiftTo(data, positionMs, 0);
    }

    /** 时移跳转（直播态左右键专用）：这条是「时移」线路，跟 EPG 点击的「回看」是两条线。
        回看走 catchup-source、一条节目一个固定起止；时移走 shift-source。
        anchorMs = 时移流的起点墙钟毫秒，会被写进 shift-source 的 {b}。注意模板参数必须是
        playseek：HMS 服务端把 {b} 当流起点（npt 恒从 0 开始），starttime 参数不实现
        （2026-09-22 DESCRIBE 实证：starttime 返回 s=live，playseek 才返回 s=vod）。
        把落点写在 URL 上就能精准起播，不依赖流内 seek（该源实测 RTSP 流内 seek 无效）。 */
    public boolean shiftTo(EpgData data, long positionMs, long anchorMs) {
        Channel channel = state.getChannel();
        if (channel == null || data == null) return false;
        if (!channel.hasShift()) return false;
        host.renderEpgSelection(data);
        return requestShift(data, positionMs, anchorMs);
    }

    /** 直播态判断按「当前播放请求」而不是 player.isLive()：HMS 直播流起播约 5 秒后被
        ExoPlayer 翻成非直播（a=range:clock=0- 语义），isLive() 会把直播态误判成回放态，
        左键就落进流内 seek 分支（该源流内 seek 实测无效 = 按了没反应）。shift 请求的
        data 也非 null，isCatchup() 对 shift 同样成立，一个判断同时覆盖回看与时移。 */
    public boolean isLiveRequest() {
        LivePlayRequest request = state.getActiveRequest();
        return request == null || !request.isCatchup();
    }

    /** 退出时移回直播（右键追平直播点用）。 */
    public void backToLive() {
        requestLive();
    }

    public void onPlaybackResult(PlaybackResult<LivePlayRequest> playback) {
        if (playback == null || cannotApply(playback)) return;
        applyPlaybackResult(playback.result(), playback.request());
    }

    private void applyPlaybackResult(Result result, LivePlayRequest request) {
        String realUrl = result.getRealUrl();
        if (result.hasMsg() || TextUtils.isEmpty(realUrl)) failPlayback(result.getMsg());
        else startResolvedPlayback(result, request, realUrl);
    }

    private void startResolvedPlayback(Result result, LivePlayRequest request, String realUrl) {
        long position = result.hasPosition() ? result.getPosition() : request.getPosition();
        Log.i("KSHIFT", "startResolved shift=" + request.isShift() + " anchor=" + request.getShiftAnchor()
                + " pos=" + position + " url=" + realUrl);
        state.setPlayingRequest(request, realUrl);
        host.renderPlaybackState(request);
        host.startPlayback(result, position, publishPlaybackMetadata(getEpgData(request)));
    }

    private void failPlayback(String msg) {
        state.clearPendingRequest();
        playbackError(msg);
    }

    public void playbackError(String msg) {
        host.resetPlaybackForError(msg);
        fallbackAfterError();
    }

    public void playbackEnded() {
        if (host.isPlayerLive()) playNextProgram();
        else nextChannel();
    }

    public void onEpgChanged(EpgData data) {
        if (state.getChannel() != null) publishPlaybackMetadata(data);
    }

    public void refresh() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        LivePlayRequest request = state.getActiveRequest();
        if (request != null && request.isCatchup() && request.matches(channel)) {
            long startPositionMs = host.hasPlaybackSession() ? host.getPlayerPosition() : request.getPosition();
            LivePlayRequest restored = request.isShift() ? LivePlayRequest.shift(channel, request.getCatchupData(), startPositionMs, request.getShiftAnchor()) : LivePlayRequest.catchup(channel, request.getCatchupData(), startPositionMs);
            requestPlayback(restored, true);
        } else {
            requestLive();
        }
    }

    public void onPlaybackServiceReady() {
        host.restorePlaybackKey(state.getPlaybackKey());
        syncPlaybackMetadata();
        if (restorePlaybackIfNeeded()) return;
        if (state.getChannel() != null && state.getActiveRequest() == null) requestLive();
    }

    private void syncPlaybackMetadata() {
        MediaMetadata metadata = state.getPlaybackMetadata();
        if (metadata != null) host.renderPlaybackMetadata(metadata);
    }

    private boolean restorePlaybackIfNeeded() {
        LivePlayRequest request = state.getPlayingRequest();
        if (request == null || !request.matches(state.getChannel()) || host.hasPlaybackSession()) return false;
        refresh();
        return true;
    }

    public void prevChannel() {
        moveChannel(-1);
    }

    public void nextChannel() {
        moveChannel(1);
    }

    public void prevLine() {
        switchLine(false, true);
    }

    public void nextLine(boolean show) {
        switchLine(true, show);
    }

    private void switchLine(boolean next, boolean show) {
        Channel channel = state.getChannel();
        if (channel == null || channel.isOnly()) return;
        channel.switchLine(next);
        host.renderLineSelection(channel, show);
        requestLive();
    }

    private void moveChannel(int delta) {
        Group group = state.getGroup();
        if (group == null || group.isEmpty()) return;
        int size = group.getChannel().size();
        int position = group.getPosition() + delta;
        boolean limit = position < 0 || position >= size;
        if (LiveSetting.isAcross() && limit) moveGroup(delta);
        else group.setPosition(limit ? wrap(position, size) : position);
        group = state.getGroup();
        if (group != null && !group.isEmpty()) selectChannel(group.current());
    }

    private void moveGroup(int delta) {
        int count = host.getGroupCount();
        if (count <= 1) return;
        Group current = state.getGroup();
        int position = host.getGroupPosition();
        for (int i = 0; i < count; i++) {
            position = wrap(position + delta, count);
            Group target = host.getGroup(position);
            if (target.equals(current)) return;
            if (target.skip() || target.isEmpty()) continue;
            state.setGroup(target);
            host.renderGroupSelection(target);
            host.renderGroupChannels(target);
            target.setPosition(delta > 0 ? 0 : target.getChannel().size() - 1);
            return;
        }
    }

    private int wrap(int position, int size) {
        return ((position % size) + size) % size;
    }

    private void fallbackAfterError() {
        Channel channel = state.getChannel();
        if (!LiveSetting.isChange() || channel == null || channel.isLast()) return;
        nextLine(true);
    }

    private void playNextProgram() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        EpgData data = getNextEpgData(channel);
        if (data != null && selectEpg(data)) return;
        data = getCurrentEpgData(channel);
        if (data != null) host.renderEpgSelection(data);
        refresh();
    }

    private void requestLive() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        LiveConfig.get().setKeep(channel);
        requestPlayback(LivePlayRequest.live(channel, C.TIME_UNSET), true);
    }

    private boolean requestCatchup(EpgData data, long startPositionMs) {
        Channel channel = state.getChannel();
        if (channel == null) return false;
        requestPlayback(LivePlayRequest.catchup(channel, data, startPositionMs), false);
        return true;
    }

    private boolean requestShift(EpgData data, long startPositionMs) {
        return requestShift(data, startPositionMs, 0);
    }

    private boolean requestShift(EpgData data, long startPositionMs, long anchorMs) {
        Channel channel = state.getChannel();
        if (channel == null) return false;
        requestPlayback(LivePlayRequest.shift(channel, data, startPositionMs, anchorMs), false);
        return true;
    }

    private void requestPlayback(LivePlayRequest request, boolean showProgress) {
        if (!host.isPlaybackServiceReady()) return;
        state.setPendingRequest(request);
        host.stopPlaybackForRefresh();
        publishPlaybackMetadata(getEpgData(request));
        if (request.isCatchup()) host.onCatchupRequested();
        if (showProgress) host.showProgress();
        dataSource.getUrl(request);
    }

    private EpgData getEpgData(LivePlayRequest request) {
        return request.isCatchup() ? request.getCatchupData() : getCurrentEpgData(request.getChannel());
    }

    @Nullable
    private EpgData getNextEpgData(Channel channel) {
        if (channel == null) return null;
        Epg epg = channel.getData(host.getZoneId());
        int current = epg.getInRange();
        int position = epg.getSelected() + 1;
        return position <= current && position > 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }

    @Nullable
    private EpgData getCurrentEpgData(Channel channel) {
        if (channel == null) return null;
        Epg epg = channel.getData(host.getZoneId()).selected();
        int position = epg.getSelected();
        return position >= 0 && position < epg.getList().size() ? epg.getList().get(position) : null;
    }

    private MediaMetadata publishPlaybackMetadata(@Nullable EpgData liveData) {
        LivePlayRequest request = state.getActiveRequest();
        Channel channel = request == null ? state.getChannel() : request.getChannel();
        EpgData data = request != null && request.isCatchup() ? request.getCatchupData() : liveData;
        MediaMetadata metadata = buildPlaybackMetadata(channel, data);
        state.setPlaybackMetadata(metadata);
        host.renderPlaybackMetadata(metadata);
        return metadata;
    }

    private MediaMetadata buildPlaybackMetadata(Channel channel, EpgData data) {
        String title = channel == null ? "" : channel.getShow();
        String logo = channel == null ? "" : channel.getLogo();
        String artist = data == null ? "" : data.format();
        String name = data == null ? "" : data.getTitle();
        return MediaItemFactory.buildMetadata(title, artist, logo, name);
    }

    private boolean cannotApply(PlaybackResult<LivePlayRequest> playback) {
        LivePlayRequest pending = state.getPendingRequest();
        LivePlayRequest request = playback.request();
        return pending == null || !pending.matches(request) || !request.matches(state.getChannel());
    }
}
