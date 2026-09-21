package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.databinding.AdapterEpgDateBinding;
import com.fongmi.android.tv.utils.Formatters;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 节目单顶部的日期条：一行 chip，每个 chip 是一个有节目数据的日期。
    左右键在 chip 间移动（到右边缘环绕回第一个），OK 切换节目单到该日期，
    第一个 chip 上按左键交给宿主（焦点回频道列）。 */
public class EpgDateAdapter extends RecyclerView.Adapter<EpgDateAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Epg> mItems;
    private String mSelected;

    public EpgDateAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<Epg> items, String selected) {
        mItems.clear();
        mItems.addAll(items);
        mSelected = selected;
        notifyDataSetChanged();
    }

    public void setSelected(String date) {
        if (date.equals(mSelected)) return;
        mSelected = date;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpgDateBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Epg item = mItems.get(position);
        holder.binding.getRoot().setText(getLabel(item.getDate()));
        holder.binding.getRoot().setSelected(item.getDate().equals(mSelected));
        holder.binding.getRoot().setOnClickListener(v -> {
            mListener.onDatePick(item);
            // notifyDataSetChanged 重建 ViewHolder 后焦点会掉，拉回到当前选中的 chip 上
            RecyclerView recycler = mListener.getRecycler();
            recycler.post(() -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos < 0) pos = mItems.indexOf(item);
                RecyclerView.ViewHolder h = recycler.findViewHolderForAdapterPosition(pos);
                if (h != null) h.itemView.requestFocus();
            });
        });
        holder.binding.getRoot().setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position == 0) {
                mListener.onEdgeLeft();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && position == mItems.size() - 1) {
                mListener.getRecycler().scrollToPosition(0);
                mListener.getRecycler().post(() -> {
                    RecyclerView.ViewHolder first = mListener.getRecycler().findViewHolderForAdapterPosition(0);
                    if (first != null) first.itemView.requestFocus();
                });
                return true;
            }
            return false;
        });
    }

    private String getLabel(String date) {
        String today = LocalDate.now().format(Formatters.DATE);
        if (date.equals(today)) return mListener.getContext().getString(R.string.epg_today);
        return date.length() == 10 ? date.substring(5) : date;
    }

    public interface OnClickListener {

        void onDatePick(Epg item);

        void onEdgeLeft();

        RecyclerView getRecycler();

        Context getContext();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpgDateBinding binding;

        ViewHolder(@NonNull AdapterEpgDateBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
