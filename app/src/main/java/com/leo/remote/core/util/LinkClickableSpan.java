package com.leo.remote.core.util;

import android.text.style.ClickableSpan;
import android.view.View;
import androidx.annotation.NonNull;

/**



 *    点击跳转链接的 ClickableSpan
 */
public class LinkClickableSpan extends ClickableSpan {

   private final String targetUrl;

   public LinkClickableSpan(@NonNull String url) {
      targetUrl = url;
   }

   @Override
   public void onClick(@NonNull View widget) {
      UrlLauncher.open(widget.getContext(), targetUrl);
   }
}
