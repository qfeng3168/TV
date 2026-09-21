package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;

import com.fongmi.android.tv.utils.KeyUtil;

public class CustomLiveListView extends VerticalGridView {

    private Callback listener;

    /** 列表顶部按「上」时跳出列表（默认 false = 在首尾环绕）。
        EPG 节目单开了日期条时置 true：焦点能从第一条节目升到日期条。 */
    private boolean breakOutUp;

    public CustomLiveListView(@NonNull Context context) {
        super(context);
    }

    public CustomLiveListView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomLiveListView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setListener(Callback listener) {
        this.listener = listener;
    }

    public void setBreakOutUp(boolean value) {
        breakOutUp = value;
    }

    private boolean onKeyDown() {
        if (getSelectedPosition() != getAdapter().getItemCount() - 1) return false;
        setSelectedPosition(0);
        return true;
    }

    private boolean onKeyUp() {
        if (breakOutUp && getSelectedPosition() == 0) return false;
        if (getSelectedPosition() != 0) return false;
        setSelectedPosition(getAdapter().getItemCount() - 1);
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (getVisibility() == View.GONE || event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        if (getVisibility() == View.VISIBLE && listener != null) listener.setUITimer();
        if (KeyUtil.isDownKey(event)) return onKeyDown() || super.dispatchKeyEvent(event);
        if (KeyUtil.isUpKey(event)) return onKeyUp() || super.dispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    public interface Callback {

        void setUITimer();
    }
}
