package com.fongmi.android.tv.playback.live;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;

import java.util.Objects;

public final class LivePlayRequest {

    private final Channel channel;
    private final EpgData data;
    private final String group;
    private final long position;
    private final long shiftAnchor;
    private final int line;
    private final boolean shift;

    private LivePlayRequest(@NonNull Channel channel, @Nullable EpgData data, long position, boolean shift, long shiftAnchor) {
        this.channel = channel;
        this.data = data;
        this.group = channel.getGroup() == null ? "" : channel.getGroup().getName();
        this.position = position;
        this.line = channel.getIndex();
        this.shift = shift;
        this.shiftAnchor = shiftAnchor;
    }

    public static LivePlayRequest live(@NonNull Channel channel, long position) {
        return new LivePlayRequest(channel, null, position, false, 0);
    }

    public static LivePlayRequest catchup(@NonNull Channel channel, @NonNull EpgData data, long position) {
        return new LivePlayRequest(channel, data, position, false, 0);
    }

    public static LivePlayRequest shift(@NonNull Channel channel, @NonNull EpgData data, long position) {
        return shift(channel, data, position, 0);
    }

    /**
     * @param position 流内起播位置（毫秒）
     * @param anchor   时移流起点对应的墙钟毫秒（写进 playseek 的 {b}），0 = 用节目起点
     */
    public static LivePlayRequest shift(@NonNull Channel channel, @NonNull EpgData data, long position, long anchor) {
        return new LivePlayRequest(channel, data, position, true, anchor);
    }

    public Channel getChannel() {
        return channel;
    }

    @NonNull
    public EpgData getCatchupData() {
        if (data == null) throw new IllegalStateException("Not a catchup request");
        return data;
    }

    public long getPosition() {
        return position;
    }

    public long getShiftAnchor() {
        return shiftAnchor;
    }

    public boolean isCatchup() {
        return data != null;
    }

    public boolean isShift() {
        return shift;
    }

    public boolean matches(@Nullable Channel current) {
        String currentGroup = current == null || current.getGroup() == null ? "" : current.getGroup().getName();
        return channel.equals(current) && group.equals(currentGroup) && line == current.getIndex();
    }

    public boolean matches(@Nullable LivePlayRequest request) {
        return request != null && channel.equals(request.channel) && group.equals(request.group) && line == request.line && position == request.position && shift == request.shift && shiftAnchor == request.shiftAnchor && Objects.equals(data, request.data);
    }
}
