package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.parser.EpgParser;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Formatters;
import com.github.catvod.utils.Json;
import com.google.gson.annotations.SerializedName;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class Epg {

    @SerializedName("key")
    private String key;
    @SerializedName("date")
    private String date;
    @SerializedName("epg_data")
    private List<EpgData> list;

    private int width;

    public static Epg objectFrom(String str, String key, ZoneId zoneId) {
        if (!Json.isObj(str)) return EpgParser.getEpg(str, key, zoneId);
        try {
            Epg item = App.gson().fromJson(str, Epg.class);
            item.setTime(zoneId);
            item.setKey(key);
            return item;
        } catch (Exception e) {
            return new Epg();
        }
    }

    public static Epg create(String key, String date) {
        Epg item = new Epg();
        item.setKey(key);
        item.setDate(date);
        item.setList(new ArrayList<>());
        return item;
    }

    public String getKey() {
        return TextUtils.isEmpty(key) ? "" : key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDate() {
        return TextUtils.isEmpty(date) ? "" : date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<EpgData> getList() {
        return list == null ? Collections.emptyList() : list;
    }

    public void setList(List<EpgData> list) {
        this.list = list;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public boolean equal(String date) {
        return getDate().equals(date);
    }

    private void setTime(ZoneId zoneId) {
        setList(new ArrayList<>(new LinkedHashSet<>(getList())));
        for (EpgData item : getList()) {
            item.setStartTime(parseEpgTime(getDate().concat(item.getStart()), zoneId));
            item.setEndTime(parseEpgTime(getDate().concat(item.getEnd()), zoneId));
            if (item.getEndTime() < item.getStartTime()) item.checkDay(zoneId);
            item.trans();
        }
    }

    public EpgData getEpgData() {
        for (EpgData item : getList()) if (item.isSelected()) return item;
        return new EpgData();
    }

    /** 当前正在播的那条；一天刚开始还没进区间时退而求其次用最近一条未播的。
        频道列表要在这儿取标题，不能依赖 selected —— 批量解析通道不会走 selected()。 */
    public EpgData getCurrent() {
        for (EpgData item : getList()) if (item.isInRange()) return item;
        for (EpgData item : getList()) if (item.isFuture()) return item;
        return null;
    }

    /** 按 EPG 设置裁剪节目单：过滤掉已播完的节目，以及超出时间跨度的未来节目。
        没有时间信息的条目直接保留，避免整条节目单被清空。 */
    public List<EpgData> filter() {
        long now = System.currentTimeMillis();
        long limit = now + Setting.getEpgSpanMillis();
        boolean past = Setting.isEpgPast();
        List<EpgData> items = new ArrayList<>();
        for (EpgData item : getList()) {
            if (item.getEndTime() == 0) {
                items.add(item);
            } else if ((past || item.getEndTime() >= now) && item.getStartTime() <= limit) {
                items.add(item);
            }
        }
        return items;
    }

    /** 按墙钟时间取节目：优先命中正好覆盖该时间的条目，退而取该时间之前最近的一条。
        直播时移要「跳到对应时间的节目」，就是靠这个把回退量换算成节目。 */
    public EpgData findByTime(long time) {
        EpgData before = null;
        for (EpgData item : getList()) {
            if (item.getStartTime() <= time && time <= item.getEndTime()) return item;
            if (item.getEndTime() <= time && (before == null || item.getEndTime() > before.getEndTime())) before = item;
        }
        return before;
    }

    /** 日期条选中某天后的节目单。过去的日期忽略「隐藏已播节目」设置——
        用户明确翻到那天就是要回看，再按设置过滤就会得到一张空节目单。 */
    public List<EpgData> filterSelected(boolean isToday) {
        if (isToday) return filter();
        long limit = System.currentTimeMillis() + Setting.getEpgSpanMillis();
        List<EpgData> items = new ArrayList<>();
        for (EpgData item : getList()) {
            if (item.getEndTime() == 0 || item.getStartTime() <= limit) items.add(item);
        }
        return items;
    }

    public Epg selected() {
        for (EpgData item : getList()) item.setSelected(item.isInRange());
        return this;
    }

    public int getSelected() {
        for (int i = 0; i < getList().size(); i++) if (getList().get(i).isSelected()) return i;
        return -1;
    }

    public int getInRange() {
        for (int i = 0; i < getList().size(); i++) if (getList().get(i).isInRange()) return i;
        return -1;
    }

    private long parseEpgTime(String source, ZoneId zoneId) {
        try {
            var fmt = source.length() > 16 ? Formatters.EPG_DT_LONG : Formatters.EPG_DT_SHORT;
            return LocalDateTime.parse(source, fmt).atZone(zoneId).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
