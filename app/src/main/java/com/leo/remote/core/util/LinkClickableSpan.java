package com.leo.remote.core.util;

import android.text.style.ClickableSpan;
import android.view.View;
import androidx.annotation.NonNull;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2023/06/24
 *    desc   : 点击跳转链接的 ClickableSpan
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
