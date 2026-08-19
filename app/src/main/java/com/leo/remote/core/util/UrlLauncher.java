package com.leo.remote.core.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.hjq.toast.Toaster;

/** 外部链接跳转 */
public final class UrlLauncher {

    private UrlLauncher() {}

    public static void open(@NonNull Context context, @NonNull String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Toaster.show("无法打开链接");
        }
    }
}
