package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.impl.Diffable;

/**
 * 首页推荐位的数据载体：把首轮结果的首位影片提到内容区顶部整幅展示。
 *
 * equals 恒为真，便于用 EMPTY 作为查找键在 adapter 中定位、增删该唯一行。
 */
public class HomeBanner implements Diffable<HomeBanner> {

    public static final HomeBanner EMPTY = new HomeBanner(null);

    private final Vod vod;

    public static HomeBanner create(Vod vod) {
        return vod == null ? EMPTY : new HomeBanner(vod);
    }

    public HomeBanner(Vod vod) {
        this.vod = vod;
    }

    public Vod getVod() {
        return vod;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HomeBanner;
    }

    @Override
    public boolean isSameItem(HomeBanner other) {
        return true;
    }

    @Override
    public boolean isSameContent(HomeBanner other) {
        return other != null && vod == other.vod;
    }
}
