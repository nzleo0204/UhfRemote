package com.leo.remote.rfid.demo.ui.common;

import com.leo.remote.app.AppFragment;
import com.leo.remote.app.ReaderAwareActivity;

/** Fragment base for actions that require an operation-ready reader. */
public abstract class ReaderFragment<A extends ReaderAwareActivity> extends AppFragment<A> {
    protected final boolean requireReaderOnline() {
        A activity = getAttachActivity();
        return activity != null && activity.requireReaderOnline();
    }
}
