package com.leo.uhf.core.ui.widget;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hjq.custom.widget.view.RegexEditText;
import com.leo.uhf.R;

/** Four-octet IPv4 editor with numeric input and per-octet validation. */
public final class IpAddressInputView extends LinearLayout {

    public interface OnEditingChangedListener {
        void onEditingChanged(boolean editing);
    }

    private final RegexEditText[] octets = new RegexEditText[4];
    private Runnable doneAction;
    private OnEditingChangedListener editingChangedListener;
    private boolean editing;

    public IpAddressInputView(@NonNull Context context) {
        this(context, null);
    }

    public IpAddressInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IpAddressInputView(@NonNull Context context, @Nullable AttributeSet attrs,
                              int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.ip_address_input_view, this, true);
        octets[0] = findViewById(R.id.et_ip_octet_1);
        octets[1] = findViewById(R.id.et_ip_octet_2);
        octets[2] = findViewById(R.id.et_ip_octet_3);
        octets[3] = findViewById(R.id.et_ip_octet_4);
        bindOctets();
    }

    private void bindOctets() {
        for (int index = 0; index < octets.length; index++) {
            RegexEditText octet = octets[index];
            int currentIndex = index;
            octet.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3), new OctetFilter()});
            octet.setOnFocusChangeListener((view, hasFocus) -> dispatchEditingState());
            octet.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && octet.getText().length() == 0 && currentIndex > 0) {
                    octets[currentIndex - 1].requestFocus();
                    octets[currentIndex - 1].setSelection(octets[currentIndex - 1].length());
                    return true;
                }
                return false;
            });
            octet.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable value) {
                    clearError();
                    if (value.length() == 3 && currentIndex < octets.length - 1
                            && octet.hasFocus()) {
                        octets[currentIndex + 1].requestFocus();
                    }
                }
            });
        }
        octets[3].setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            if (doneAction != null) {
                doneAction.run();
            }
            return true;
        });
    }

    public void setAddress(@Nullable String address) {
        String[] values = address == null ? new String[0] : address.split("\\.", -1);
        for (int index = 0; index < octets.length; index++) {
            octets[index].setText(values.length == octets.length ? values[index] : "");
        }
    }

    @NonNull
    public String getAddress() {
        StringBuilder address = new StringBuilder();
        for (RegexEditText octet : octets) {
            if (address.length() > 0) {
                address.append('.');
            }
            address.append(octet.getText());
        }
        return address.toString();
    }

    public void setOnDoneAction(@Nullable Runnable action) {
        doneAction = action;
    }

    public void setOnEditingChangedListener(@Nullable OnEditingChangedListener listener) {
        editingChangedListener = listener;
    }

    public boolean hasInputFocus() {
        return getFocusedInput() != null;
    }

    @Nullable
    public View getFocusedInput() {
        for (RegexEditText octet : octets) {
            if (octet.hasFocus()) {
                return octet;
            }
        }
        return null;
    }

    public void clearInputFocus() {
        for (RegexEditText octet : octets) {
            octet.clearFocus();
        }
    }

    public void setError(@Nullable CharSequence error) {
        octets[3].setError(error);
    }

    public void clearError() {
        for (RegexEditText octet : octets) {
            octet.setError(null);
        }
    }

    private void dispatchEditingState() {
        post(() -> {
            boolean current = hasInputFocus();
            if (current == editing) {
                return;
            }
            editing = current;
            if (editingChangedListener != null) {
                editingChangedListener.onEditingChanged(current);
            }
        });
    }

    private static final class OctetFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                                   int dstart, int dend) {
            String candidate = dest.subSequence(0, dstart).toString()
                    + source.subSequence(start, end)
                    + dest.subSequence(dend, dest.length());
            if (candidate.isEmpty()) {
                return null;
            }
            for (int index = 0; index < candidate.length(); index++) {
                if (!Character.isDigit(candidate.charAt(index))) {
                    return "";
                }
            }
            if ((candidate.length() > 1 && candidate.charAt(0) == '0')
                    || Integer.parseInt(candidate) > 255) {
                return "";
            }
            return null;
        }
    }
}
