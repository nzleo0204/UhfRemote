package com.leo.remote.rfid.demo.ui.common;

import com.leo.remote.core.ui.base.BaseFragment;
import com.leo.remote.rfid.demo.ui.common.ReaderAwareActivity;

/** Fragment base for actions that require an operation-ready reader. */
public abstract class ReaderFragment<A extends ReaderAwareActivity> extends BaseFragment<A> {
    protected final boolean requireReaderOnline() {
        A activity = getAttachActivity();
        return activity != null && activity.requireReaderOnline();
    }
}
