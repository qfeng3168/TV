package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogEpgBinding;
import com.fongmi.android.tv.setting.LiveSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The Epg the live source does not provide on its own.  The value is stored
 * as a setting rather than on the live config, so every channel of every live
 * source can share one programme list.
 */
public class EpgDialog extends BaseAlertDialog {

    private DialogEpgBinding binding;

    public static EpgDialog create() {
        return new EpgDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogEpgBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        String text = LiveSetting.getEpg();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private void onPositive(View view) {
        LiveSetting.putEpg(binding.text.getText().toString().trim());
        dismiss();
    }

    private void onNegative(View view) {
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.55f);
    }
}
