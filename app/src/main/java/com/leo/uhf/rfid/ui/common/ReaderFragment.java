package com.leo.uhf.rfid.ui.common;

import com.leo.uhf.core.ui.base.BaseFragment;
import com.leo.uhf.rfid.ui.common.ReaderAwareActivity;

/** 需要读写器处于可操作状态的 RFID 页面基类。 */
public abstract class ReaderFragment extends BaseFragment<ReaderAwareActivity> {
    protected final boolean requireReaderOnline() {
        ReaderAwareActivity activity = getAttachActivity();
        return activity != null && activity.requireReaderOnline();
    }
}
