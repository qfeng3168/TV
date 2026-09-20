package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

public class Setting {

    private static final int MIN_WALL = 0;
    private static final int MAX_WALL = 4;
    private static final int MIN_WALL_TYPE = 0;
    private static final int MAX_WALL_TYPE = 2;
    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;

    /** 节目单时间跨度的可选值（小时），0 号位对应 6 小时，依次翻倍 */
    public static final int[] EPG_SPAN_HOURS = {6, 12, 24, 48};
    private static final int DEFAULT_EPG_SPAN = 2;
    public static final int MIN_EPG_OFFSET = -12;
    public static final int MAX_EPG_OFFSET = 14;
    private static final int DEFAULT_EPG_OFFSET = 8;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static int getWall() {
        return Math.clamp(Prefers.getInt("wall", 1), MIN_WALL, MAX_WALL);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", Math.clamp(wall, MIN_WALL, MAX_WALL));
    }

    public static int getWallType() {
        return Math.clamp(Prefers.getInt("wall_type", 0), MIN_WALL_TYPE, MAX_WALL_TYPE);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", Math.clamp(type, MIN_WALL_TYPE, MAX_WALL_TYPE));
    }

    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static int getSiteMode() {
        return Math.clamp(Prefers.getInt("site_mode"), MIN_SITE_MODE, MAX_SITE_MODE);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", Math.clamp(mode, MIN_SITE_MODE, MAX_SITE_MODE));
    }

    public static int getSyncMode() {
        return Math.clamp(Prefers.getInt("sync_mode"), MIN_SYNC_MODE, MAX_SYNC_MODE);
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", Math.clamp(mode, MIN_SYNC_MODE, MAX_SYNC_MODE));
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    /** 节目单时间跨度在 EPG_SPAN_HOURS 中的下标，默认 24 小时 */
    public static int getEpgSpan() {
        return Math.clamp(Prefers.getInt("epg_span", DEFAULT_EPG_SPAN), 0, EPG_SPAN_HOURS.length - 1);
    }

    public static void putEpgSpan(int span) {
        Prefers.put("epg_span", Math.clamp(span, 0, EPG_SPAN_HOURS.length - 1));
    }

    public static String getEpgSpanText() {
        return ResUtil.getString(R.string.setting_epg_span_hour, EPG_SPAN_HOURS[getEpgSpan()]);
    }

    /** 超出该窗口的节目不再展示，0 点起算的当天节目不受影响 */
    public static long getEpgSpanMillis() {
        return EPG_SPAN_HOURS[getEpgSpan()] * 60L * 60L * 1000L;
    }

    /** 是否在节目单里保留已经播完的节目 */
    public static boolean isEpgPast() {
        return Prefers.getBoolean("epg_past", true);
    }

    public static void putEpgPast(boolean past) {
        Prefers.put("epg_past", past);
    }

    /** 节目单时间校正，单位小时，默认 +08:00 */
    public static int getEpgOffset() {
        return Math.clamp(Prefers.getInt("epg_offset", DEFAULT_EPG_OFFSET), MIN_EPG_OFFSET, MAX_EPG_OFFSET);
    }

    public static void putEpgOffset(int offset) {
        Prefers.put("epg_offset", Math.clamp(offset, MIN_EPG_OFFSET, MAX_EPG_OFFSET));
    }

    public static String getEpgOffsetText() {
        int offset = getEpgOffset();
        return (offset >= 0 ? "+" : "-") + String.format(Locale.getDefault(), "%02d:00", Math.abs(offset));
    }

    /** m3u 未声明时区时，节目单按这里配置的偏移解析 */
    public static ZoneId getEpgZoneId() {
        return ZoneId.ofOffset("UTC", ZoneOffset.ofHours(getEpgOffset()));
    }

    /** 回看与时移（Catchup / Shift）入口总开关 */
    public static boolean isEpgCatchup() {
        return Prefers.getBoolean("epg_catchup", true);
    }

    public static void putEpgCatchup(boolean catchup) {
        Prefers.put("epg_catchup", catchup);
    }
}
