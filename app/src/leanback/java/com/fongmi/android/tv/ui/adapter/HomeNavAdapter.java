package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.databinding.AdapterHomeNavBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页左侧导航：站点分类 + 一个固定的收藏/历史入口。
 * 分类项负责切页，固定入口跳 KeepActivity——每一项都有真实去处，不做装饰。
 */
public class HomeNavAdapter extends RecyclerView.Adapter<HomeNavAdapter.ViewHolder> {

    private static final int SHORTCUT_COUNT = 1;

    private final OnClickListener mListener;
    private final List<Class> mItems;
    private int mSelected;

    public HomeNavAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<Class> items) {
        mItems.clear();
        mItems.addAll(items);
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

    private boolean isShortcut(int position) {
        return position >= mItems.size();
    }

    @Override
    public int getItemCount() {
        return mItems.size() + SHORTCUT_COUNT;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHomeNavBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (isShortcut(position)) {
            holder.binding.text.setText(R.string.home_keep);
            holder.binding.getRoot().setActivated(false);
            holder.binding.getRoot().setOnClickListener(v -> mListener.onShortcut());
            return;
        }
        holder.binding.text.setText(mItems.get(position).getTypeName());
        holder.binding.getRoot().setActivated(position == mSelected);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onNavClick(position));
    }

    public interface OnClickListener {

        void onNavClick(int position);

        void onShortcut();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHomeNavBinding binding;

        ViewHolder(@NonNull AdapterHomeNavBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
