package com.fongmi.android.tv.ui.presenter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.fongmi.android.tv.bean.HomeBanner;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterHomeBannerBinding;
import com.fongmi.android.tv.utils.ImgUtil;

/**
 * 首页推荐位：整幅大图 + 左下角标题，复用点播页同一份卡片布局。
 * 点击与长按行为完全委托给 VodPresenter.OnClickListener，不引入第二套语义。
 */
public class BannerPresenter extends Presenter {

    private final VodPresenter.OnClickListener listener;

    public BannerPresenter(VodPresenter.OnClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Presenter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterHomeBannerBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ViewHolder holder = (ViewHolder) viewHolder;
        Vod item = ((HomeBanner) object).getVod();
        if (item == null) return;

        holder.binding.name.setText(item.getName());

        String type = item.getTypeName();
        holder.binding.tag.setText(type);
        holder.binding.tag.setVisibility(TextUtils.isEmpty(type) ? View.GONE : View.VISIBLE);

        String remark = item.getRemarks();
        holder.binding.remark.setText(remark);
        holder.binding.remark.setVisibility(TextUtils.isEmpty(remark) ? View.GONE : View.VISIBLE);

        ImgUtil.load(item.getName(), item.getPic(), holder.binding.image);
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHomeBannerBinding binding;

        ViewHolder(@NonNull AdapterHomeBannerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
