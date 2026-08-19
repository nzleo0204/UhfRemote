package com.leo.uhf.business.feedback.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;
import com.leo.uhf.R;
import com.leo.uhf.core.aop.SingleClick;
import com.leo.uhf.core.data.DataCallback;
import com.leo.uhf.business.feedback.data.model.FeedbackDraft;
import com.leo.uhf.business.feedback.data.model.FeedbackType;
import com.leo.uhf.business.common.data.BusinessRepositories;
import com.leo.uhf.business.common.ui.BusinessPageActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 提供用户提交意见反馈的页面。
 */
public final class FeedbackActivity extends BusinessPageActivity {
    private FeedbackType selectedType = FeedbackType.PRODUCT;
    private final List<String> imagePaths = new ArrayList<>();
    private TextView productTab;
    private TextView orderTab;
    private TextView requirementTab;
    private EditText orderNoView;
    private EditText titleView;
    private EditText detailView;
    private LinearLayout imageContainer;

    public static void start(Context context) {
        context.startActivity(new Intent(context, FeedbackActivity.class));
    }

    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::addImage);
    private final ActivityResultLauncher<Void> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(), this::addCameraImage);

    @Override
    protected int getLayoutId() {
        return R.layout.feedback_activity;
    }

    @Override
    protected void initPageView() {
        productTab = findViewById(R.id.tv_feedback_product);
        orderTab = findViewById(R.id.tv_feedback_order);
        requirementTab = findViewById(R.id.tv_feedback_requirement);
        orderNoView = findViewById(R.id.et_feedback_order_no);
        titleView = findViewById(R.id.et_feedback_title);
        detailView = findViewById(R.id.et_feedback_detail);
        imageContainer = findViewById(R.id.ll_feedback_images);
        productTab.setOnClickListener(v -> selectType(FeedbackType.PRODUCT));
        orderTab.setOnClickListener(v -> selectType(FeedbackType.ORDER));
        requirementTab.setOnClickListener(v -> selectType(FeedbackType.REQUIREMENT));
        findViewById(R.id.tv_feedback_camera).setOnClickListener(v -> requestImagePermission(true));
        findViewById(R.id.tv_feedback_add_image).setOnClickListener(v -> requestImagePermission(false));
        findViewById(R.id.tv_feedback_submit).setOnClickListener(v -> submit());
    }

    @Override
    protected void initData() {
        selectType(FeedbackType.PRODUCT);
    }

    private void selectType(FeedbackType type) {
        selectedType = type;
        bindTab(productTab, type == FeedbackType.PRODUCT);
        bindTab(orderTab, type == FeedbackType.ORDER);
        bindTab(requirementTab, type == FeedbackType.REQUIREMENT);
    }

    private void bindTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.rfid_button_bg : R.drawable.rfid_field_bg);
        tab.setTextColor(ContextCompat.getColor(this, selected ? R.color.white : R.color.rfid_text_muted));
    }

    private void requestImagePermission(boolean camera) {
        if (!camera) {
            pickImage.launch("image/*");
            return;
        }
        XXPermissions.with(this).permission(PermissionLists.getCameraPermission()).request((grantedList, deniedList) -> {
            if (deniedList.isEmpty()) {
                takePicture.launch(null);
            } else {
                toast(R.string.feedback_permission_denied);
            }
        });
    }

    private void addImage(Uri uri) {
        if (uri == null) {
            return;
        }
        if (imagePaths.size() >= 6) {
            toast(R.string.feedback_image_limit);
            return;
        }
        addImagePath(compressImage(uri));
    }

    private void addCameraImage(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        File output = new File(getCacheDir(), "feedback-camera-" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            addImagePath(output.getAbsolutePath());
        } catch (IOException error) {
            toast(R.string.feedback_image_save_failed);
        } finally {
            bitmap.recycle();
        }
    }

    private void addImagePath(String path) {
        if (imagePaths.size() >= 6) {
            toast(R.string.feedback_image_limit);
            return;
        }
        imagePaths.add(path);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundResource(R.drawable.rfid_thumb_bg);
        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (path.startsWith("content:")) {
            imageView.setImageURI(Uri.parse(path));
        } else {
            imageView.setImageBitmap(BitmapFactory.decodeFile(path));
        }
        tile.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        TextView removeView = new TextView(this);
        removeView.setText("×");
        removeView.setGravity(android.view.Gravity.CENTER);
        removeView.setTextColor(ContextCompat.getColor(this, R.color.white));
        removeView.setTextSize(16);
        removeView.setBackgroundResource(R.drawable.rfid_close_ic);
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.dp_20),
                getResources().getDimensionPixelSize(R.dimen.dp_20), android.view.Gravity.END | android.view.Gravity.TOP);
        tile.addView(removeView, removeParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.dp_72),
                getResources().getDimensionPixelSize(R.dimen.dp_72));
        params.setMarginStart(getResources().getDimensionPixelSize(R.dimen.dp_10));
        removeView.setOnClickListener(v -> {
            imagePaths.remove(path);
            imageContainer.removeView(tile);
        });
        imageContainer.addView(tile, Math.max(1, imageContainer.getChildCount() - 1), params);
    }

    private String compressImage(Uri uri) {
        File output = new File(getCacheDir(), "feedback-" + System.currentTimeMillis() + ".jpg");
        try (InputStream input = getContentResolver().openInputStream(uri);
                FileOutputStream stream = new FileOutputStream(output)) {
            if (input == null) {
                return uri.toString();
            }
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                return uri.toString();
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            bitmap.recycle();
            return output.getAbsolutePath();
        } catch (IOException e) {
            return uri.toString();
        }
    }

    @SingleClick
    private void submit() {
        String title = titleView.getText().toString().trim();
        String detail = detailView.getText().toString().trim();
        if (title.isEmpty()) {
            toast(R.string.feedback_title_empty);
            return;
        }
        if (detail.isEmpty()) {
            toast(R.string.feedback_detail_empty);
            return;
        }
        FeedbackDraft draft = new FeedbackDraft(selectedType,
                orderNoView.getText().toString().trim(), title, detail, List.copyOf(imagePaths));
        showLoadingDialog(getString(R.string.feedback_submitting));
        BusinessRepositories.feedback().submitFeedback(draft, new DataCallback<>() {
            @Override
            public void onSuccess(Boolean data) {
                hideLoadingDialog();
                if (Boolean.TRUE.equals(data)) {
                    toast(R.string.feedback_submit_success);
                    finish();
                } else {
                    toast(R.string.feedback_submit_failed);
                }
            }

            @Override
            public void onFail(Exception e) {
                hideLoadingDialog();
                toast(e.getMessage());
            }
        });
    }
}
