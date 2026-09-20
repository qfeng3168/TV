package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.databinding.AdapterHomeNavBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 主页左侧分类导航：站点分类 + 若干固定入口（直播 / 设置 / 收藏）。
 * 分类项负责切换右侧分页，固定入口跳出到对应页面——每一项都有真实去处，不做装饰。
 * 固定入口不参与「当前分类」的选中态，所以 activated 只打在分类项上。
 */
public class HomeNavAdapter extends RecyclerView.Adapter<HomeNavAdapter.ViewHolder> {

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

    @Override
    public int getItemCount() {
        return mTypes.size() + mExtras.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHomeNavBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.getRoot().setActivated(position < mTypes.size() && position == mSelected);
        if (position < mTypes.size()) {
            Class item = mTypes.get(position);
            holder.binding.text.setText(item.getTypeName());
            holder.binding.getRoot().setOnClickListener(v -> mListener.onNavClick(position));
            return;
        }
        int resId = mExtras.get(position - mTypes.size());
        holder.binding.text.setText(resId);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onExtra(resId));
    }

    public interface OnClickListener {

        void onNavClick(int position);

        void onExtra(int resId);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHomeNavBinding binding;

        ViewHolder(@NonNull AdapterHomeNavBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
