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
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 仿电视家：节目单右侧的竖排日期列，每项两行「周X + MM-dd」。
    上下键在日期间移动（首尾环绕），OK 切换节目单到该日期，
    日期上按左键交给宿主（焦点回节目单列）。 */
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

    public int getPosition(String date) {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).getDate().equals(date)) return i;
        return 0;
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
        holder.binding.dateWeek.setText(getWeekLabel(item.getDate()));
        holder.binding.dateValue.setText(getDateLabel(item.getDate()));
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
            // 日期列不挂在 CustomLiveListView 上，交互要自己续面板的自动隐藏计时器
            mListener.setUITimer();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                mListener.onEdgeLeft();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && position == 0) return wrap(mItems.size() - 1);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && position == mItems.size() - 1) return wrap(0);
            return false;
        });
    }

    /** 首尾环绕：滚到另一端并把焦点放过去。 */
    private boolean wrap(int position) {
        RecyclerView recycler = mListener.getRecycler();
        recycler.scrollToPosition(position);
        recycler.post(() -> {
            RecyclerView.ViewHolder h = recycler.findViewHolderForAdapterPosition(position);
            if (h != null) h.itemView.requestFocus();
        });
        return true;
    }

    private String getWeekLabel(String date) {
        LocalDate day = LocalDate.parse(date, Formatters.DATE);
        LocalDate today = LocalDate.now();
        if (day.equals(today)) return mListener.getContext().getString(R.string.epg_today);
        if (day.equals(today.plusDays(1))) return mListener.getContext().getString(R.string.epg_tomorrow);
        return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault());
    }

    private String getDateLabel(String date) {
        return date.length() == 10 ? date.substring(5) : date;
    }

    public interface OnClickListener {

        void onDatePick(Epg item);

        void onEdgeLeft();

        void setUITimer();

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
