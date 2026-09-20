package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.databinding.AdapterHomeFuncNavBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页左侧功能导航：竖排入口，焦点落到项上即可见选中态。
 * 与点播页的分类导航共用 selector_home_nav 背景，保持全 App 导航视觉一致。
 */
public class HomeFuncNavAdapter extends RecyclerView.Adapter<HomeFuncNavAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Func> items = new ArrayList<>();

    public HomeFuncNavAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Func> items) {
        this.items.clear();
        this.items.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHomeFuncNavBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Func item = items.get(position);
        holder.binding.text.setText(item.getText());
        holder.binding.icon.setImageResource(item.getDrawable());
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    public interface OnClickListener {

        void onItemClick(Func item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHomeFuncNavBinding binding;

        ViewHolder(@NonNull AdapterHomeFuncNavBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
