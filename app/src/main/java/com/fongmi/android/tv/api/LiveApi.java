package com.fongmi.android.tv.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.parser.EpgParser;
import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.net.OkHttp;

import java.time.LocalDate;
import java.time.ZoneId;

public class LiveApi {

    private static final String TAG = LiveApi.class.getSimpleName();

    public static void parse(@NonNull Live item) throws Exception {
        applyGlobalEpg(item);
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    /** 没有 tvg-url 的纯 m3u 也挂上设置页配的 Epg，走 parseXml 的批量通道：
        只下载一次并落盘缓存，而不是让每个频道各发一遍 HTTP。 */
    private static void applyGlobalEpg(@NonNull Live item) {
        if (!item.getEpg().isEmpty()) return;
        String epg = LiveSetting.getEpg();
        if (epg.isEmpty()) return;
        item.setEpg(epg);
        Log.i(TAG, "applyGlobalEpg url=" + epg);
    }

    public static boolean parseXml(@NonNull Live item) {
        return item.getEpgXml().stream().map(url -> startXml(item, url)).reduce(false, Boolean::logicalOr);
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        String today = LocalDate.now(zoneId).format(Formatters.DATE);
        for (int offset : new int[]{-1, 0, 1}) fetchEpgDay(item, zoneId, offset);
        return item.getDataList().stream().filter(epg -> epg.equal(today)).findFirst().orElseGet(Epg::new).selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Source.get().stop();
        Result result = item.result();
        result.setUrl(Source.get().fetch(result));
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        return getUrl(item, data, false);
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data, boolean useShift) throws Exception {
        Result result = getUrl(item);
        result.setUrl(useShift ? item.getCatchup().formatShift(result.getRealUrl(), data) : item.getCatchup().format(result.getRealUrl(), data));
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        return result;
    }

    private static boolean startXml(Live item, String url) {
        try {
            EpgParser.start(item, url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
        if (!need) return;
        try {
            item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), zoneId));
        } catch (Exception e) {
            // 单天拉取失败不能拖垮另外两天：任务超时会 interrupt 掉当前连接，
            // 抛上来会让整个节目单变成空。
            Log.w(TAG, "fetchEpgDay failed date=" + date + " url=" + url + " error=" + e.getMessage());
        }
    }
}
