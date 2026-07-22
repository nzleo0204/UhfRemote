package com.leo.remote.ui.fragment.home;

import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.leo.remote.R;
import com.leo.remote.app.AppFragment;
import com.leo.remote.ui.activity.HomeActivity;

/**
 * RFID 读写器参数配置页。
 */
public final class ReaderConfigFragment extends AppFragment<HomeActivity> {

    private TextView mPowerValueView;

    public static ReaderConfigFragment newInstance() {
        return new ReaderConfigFragment();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.reader_config_fragment;
    }

    @Override
    protected void initView() {
        mPowerValueView = findViewById(R.id.tv_config_power_value);
        SeekBar powerSeekBar = findViewById(R.id.sb_config_power);
        powerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(@NonNull SeekBar seekBar, int progress, boolean fromUser) {
                mPowerValueView.setText(getString(R.string.rfid_power_value, progress));
            }

            @Override
            public void onStartTrackingTouch(@NonNull SeekBar seekBar) {
                // no-op
            }

            @Override
            public void onStopTrackingTouch(@NonNull SeekBar seekBar) {
                // no-op
            }
        });
    }

    @Override
    protected void initData() {
        mPowerValueView.setText(getString(R.string.rfid_power_value, 26));
    }
}
