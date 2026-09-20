package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.databinding.AdapterHomeNavBinding;
import com.fongmi.android.tv.databinding.AdapterHomeNavDividerBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 主页左侧分类导航：站点分类 + 一条分组分隔线 + 若干固定入口（直播 / 设置 / 收藏）。
 * 分类项负责切换右侧分页，固定入口跳出到对应页面——每一项都有真实去处，不做装饰。
 * 固定入口不参与「当前分类」的选中态，所以 activated 只打在分类项上。
 * 分隔线只占位不可聚焦，对应奇异果「个人类目 / 内容类目」之间的那条细线。
 */
public class HomeNavAdapter extends RecyclerView.Adapter<HomeNavAdapter.BaseHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_DIVIDER = 1;

    private final OnClickListener mListener;
    private final List<Class> mTypes = new ArrayList<>();
    private final List<Integer> mExtras = new ArrayList<>();
    private int mSelected;

    public HomeNavAdapter(OnClickListener listener) {
        mListener = listener;
    }

    public void setItems(List<Class> types, List<Integer> extras) {
        mTypes.clear();
        mTypes.addAll(types);
        mExtras.clear();
        mExtras.addAll(extras);
        notifyDataSetChanged();
    }

    public int getSelected() {
        return mSelected;
    }

    public void setSelected(int position) {
        if (mSelected == position) return;
        int old = mSelected;
        mSelected = position;
        notifyItemChanged(old);
        notifyItemChanged(position);
    }

    /** 没有固定入口时不需要分隔线；否则它永远紧跟最后一个分类。 */
    private boolean hasDivider() {
        return !mExtras.isEmpty();
    }

    @Override
    public int getItemCount() {
        return mTypes.size() + (hasDivider() ? 1 : 0) + mExtras.size();
    }

    @Override
    public int getItemViewType(int position) {
        return hasDivider() && position == mTypes.size() ? TYPE_DIVIDER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public BaseHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DIVIDER) {
            return new DividerHolder(AdapterHomeNavDividerBinding.inflate(inflater, parent, false));
        }
        return new ItemHolder(AdapterHomeNavBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull BaseHolder holder, int position) {
        if (!(holder instanceof ItemHolder item)) return;
        item.binding.getRoot().setActivated(position < mTypes.size() && position == mSelected);
        if (position < mTypes.size()) {
            Class type = mTypes.get(position);
            item.binding.text.setText(type.getTypeName());
            item.binding.getRoot().setOnClickListener(v -> mListener.onNavClick(position));
            return;
        }
        int resId = mExtras.get(position - mTypes.size() - 1);
        item.binding.text.setText(resId);
        item.binding.getRoot().setOnClickListener(v -> mListener.onExtra(resId));
    }

    public interface OnClickListener {

        void onNavClick(int position);

        void onExtra(int resId);
    }

    public static abstract class BaseHolder extends RecyclerView.ViewHolder {

        BaseHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class ItemHolder extends BaseHolder {

        private final AdapterHomeNavBinding binding;

        ItemHolder(@NonNull AdapterHomeNavBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class DividerHolder extends BaseHolder {

        DividerHolder(@NonNull AdapterHomeNavDividerBinding binding) {
            super(binding.getRoot());
        }
    }
}
