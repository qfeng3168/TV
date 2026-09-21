package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Catchup {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\$?\\{[^}]*\\})");
    private static final Pattern TAG_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    @SerializedName("type")
    private String type;
    @SerializedName("days")
    private String days;
    @SerializedName("regex")
    private String regex;
    @SerializedName("source")
    private String source;
    @SerializedName("replace")
    private String replace;
    @SerializedName("shift")
    private String shift;
    @SerializedName("shiftSource")
    private String shiftSource;

    public static Catchup PLTV() {
        Catchup item = new Catchup();
        item.setDays("7");
        item.setType("append");
        item.setRegex("/PLTV/");
        item.setReplace("/PLTV/,/TVOD/");
        item.setSource("?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}");
        return item;
    }

    public static Catchup create() {
        return new Catchup();
    }

    public static Catchup decide(Catchup major, Catchup minor) {
        if (!major.isEmpty()) return major;
        if (!minor.isEmpty()) return minor;
        return null;
    }

    public String getType() {
        return TextUtils.isEmpty(type) ? "" : type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDays() {
        return TextUtils.isEmpty(days) ? "" : days;
    }

    public void setDays(String days) {
        this.days = days;
    }

    public String getRegex() {
        return TextUtils.isEmpty(regex) ? "" : regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getReplace() {
        return TextUtils.isEmpty(replace) ? "" : replace;
    }

    public void setReplace(String replace) {
        this.replace = replace;
    }

    public String getSource() {
        return TextUtils.isEmpty(source) ? "" : source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getShift() {
        return TextUtils.isEmpty(shift) ? "" : shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public String getShiftSource() {
        return TextUtils.isEmpty(shiftSource) ? "" : shiftSource;
    }

    public void setShiftSource(String shiftSource) {
        this.shiftSource = shiftSource;
    }

    public boolean hasShift() {
        return !getShiftSource().isEmpty();
    }

    public boolean match(String url) {
        return url.contains(getRegex()) || Pattern.compile(getRegex()).matcher(url).find();
    }

    public boolean isEmpty() {
        return getSource().isEmpty();
    }

    private boolean isDefault() {
        return getType().equals("default");
    }

    private String append(String url, String result) {
        String[] splits = getReplace().split(",", 2);
        if (splits.length == 2) url = url.replaceAll(splits[0], splits[1]);
        if (!TextUtils.isEmpty(URI.create(url).getQuery())) result = result.replace("?", "&");
        return url + result;
    }

    public String format(String url, EpgData data) {
        String result = getSource();
        Matcher matcher = TOKEN_PATTERN.matcher(result);
        while (matcher.find()) result = result.replace(matcher.group(1), format(matcher.group(1), data.getStartTime(), data.getEndTime()));
        return isDefault() ? result : append(url, result);
    }

    public String formatShift(String url, EpgData data) {
        return formatShift(url, data, 0);
    }

    /**
     * 时移流。HMS 中间件把 {b} 当作**流的起点**：流永远从 npt=0 开始，b 写几点，流的第 0 秒就是几点
     * （实测 playseek=15:10-16:00 → a=range:npt=0-3000，b 改成 14:30 就变 npt=0-5400）。
     * 所以把「落点」直接写进 {b} 即可精准起播，不依赖播放器做流内 seek —— 这条源上的
     * RTSP 流内 seek 实测无效（OSD 位置数字会变，画面不动）。
     */
    public String formatShift(String url, EpgData data, long anchorMs) {
        long start = anchorMs > 0 && anchorMs < data.getEndTime() ? anchorMs : data.getStartTime();
        String result = getShiftSource();
        Matcher matcher = TOKEN_PATTERN.matcher(result);
        while (matcher.find()) result = result.replace(matcher.group(1), format(matcher.group(1), start, data.getEndTime()));
        return isDefault() ? result : append(url, result);
    }

    private String formatTime(long millis, String fmt) {
        if (fmt.equals("timestamp")) return String.valueOf(millis / 1000);
        return DateTimeFormatter.ofPattern(fmt, Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private String format(String group, long start, long end) {
        Matcher matcher = TAG_PATTERN.matcher(group);
        if (!matcher.find()) return "";
        String tag = matcher.group(1);
        int paren = tag.indexOf(')');
        if (tag.startsWith("(b") && paren >= 0) return formatTime(start, tag.substring(paren + 1));
        if (tag.startsWith("(e") && paren >= 0) return formatTime(end, tag.substring(paren + 1));
        if (tag.startsWith("utcend:")) return String.valueOf(end / 1000);
        if (tag.startsWith("utc:")) return String.valueOf(start / 1000);
        return "";
    }
}
