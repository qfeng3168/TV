package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.databinding.AdapterChannelBinding;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Channel> mItems;
    private ZoneId zoneId;

    public ChannelAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        zoneId = ZoneId.systemDefault();
    }

    /** 节目单按直播源自己的时区算，频道行上的「正在播」必须跟着一起走。 */
    public void setZoneId(ZoneId zoneId) {
        if (zoneId == null) return;
        this.zoneId = zoneId;
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int index = mItems.indexOf(item);
        if (index < 0) return;
        mItems.remove(index);
        notifyItemRemoved(index);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Channel get(int position) {
        return mItems.get(position);
    }

    public void setSelected(Channel selected) {
        for (Channel item : mItems) item.setSelected(selected);
        notifyDataSetChanged();
    }

    /** 节目单是异步解析的，解析完要重画一遍频道行才能把「正在播」带出来。 */
    public void refresh() {
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        String playing = item.getPlaying(zoneId);
        holder.binding.name.setText(item.getShow());
        holder.binding.number.setText(item.getNumber());
        holder.binding.playing.setText(playing);
        holder.binding.playing.setVisibility(playing.isEmpty() ? View.GONE : View.VISIBLE);
        bindProgress(holder, item.getData(zoneId).getCurrent());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setRightListener(() -> mListener.showEpg(item));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> mListener.onLongClick(item));
    }

    /** 仿电视家：正在播的节目下方画一条白色进度线，已播比例实时计算；没有进行中的节目就不显示。 */
    private void bindProgress(ViewHolder holder, @Nullable EpgData current) {
        boolean live = current != null && current.isInRange();
        holder.binding.progress.setVisibility(live ? View.VISIBLE : View.GONE);
        if (!live) return;
        long span = current.getEndTime() - current.getStartTime();
        float fraction = span <= 0 ? 0 : (System.currentTimeMillis() - current.getStartTime()) / (float) span;
        fraction = Math.max(0f, Math.min(1f, fraction));
        // 注意：父容器是 appcompat 的 LinearLayoutCompat，子 View 必须用同族 LayoutParams，
        // 塞 android.widget.LinearLayout$LayoutParams 会在 measure 时 ClassCastException。
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, fraction);
        holder.binding.progressBar.setLayoutParams(params);
    }

    public interface OnClickListener {

        void showEpg(Channel item);

        void onItemClick(Channel item);

        boolean onLongClick(Channel item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelBinding binding;

        ViewHolder(@NonNull AdapterChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
