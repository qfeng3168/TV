package com.fongmi.android.tv;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;

public class Product {

    /**
     * 页面级左侧导航占用的横向空间（栏宽 + 栏与内容之间的间距），单位 dp。
     * 主页/点播页的内容区不是整屏宽，不扣掉这块就会按整屏宽算海报宽度，
     * 结果是每行塞不下预设列数、最后一列被截断。
     * 由承载导航的 Activity 在 onResume / onPause 里成对设置与清零。
     */
    private static int navOffset = 0;

    public static void setNavOffset(int dp) {
        navOffset = dp;
    }

    public static int getDeviceType() {
        return 0;
    }

    public static int getColumn() {
        return Math.abs(PlayerSetting.getSize() - 7);
    }

    public static int getColumn(Style style) {
        return style.isLand() ? getColumn() - 1 : getColumn();
    }

    public static int[] getSpec(Style style) {
        int column = getColumn(style);
        // 48dp = 内容区左右各 24dp 外边距；navOffset 只算导航栏宽 + 栏间距（不含外边距，
        // 见 HomeActivity.getNavOffset()）。列距走资源，避免和布局里的实际间距各说各话
        // ——写死 16dp 时列距只有 16dp，比奇异果实测的 24dp 挤，整屏看起来发闷。
        int gap = App.get().getResources().getInteger(R.integer.kiwi_grid_gap_dp);
        int space = ResUtil.dp2px(48 + navOffset) + ResUtil.dp2px(gap * (column - 1));
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(space, column, style);
    }

    public static int[] getSpec(int space, int column, Style style) {
        int base = ResUtil.getScreenWidth() - space;
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    public static int getEms() {
        return Math.min(Math.round((float) ResUtil.getScreenWidth() / ResUtil.sp2px(24)), 35);
    }
}
