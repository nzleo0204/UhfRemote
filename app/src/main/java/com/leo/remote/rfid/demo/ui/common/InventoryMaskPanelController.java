package com.leo.remote.rfid.demo.ui.common;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.leo.remote.R;
import com.leo.remote.rfid.sdk.model.HexCodec;
import com.leo.remote.rfid.sdk.model.InventoryMaskConfig;
import com.leo.remote.rfid.sdk.inventory.InventoryMaskFormParser;
import com.leo.remote.rfid.sdk.model.ProtocolEncoding;
import com.leo.remote.rfid.sdk.model.TagProtocol;
import com.leo.remote.core.util.ViewUtils;

/** Owns the shared mask-panel binding and presentation state used by both RFID pages. */
public final class InventoryMaskPanelController {
    public enum Appearance { INVENTORY, SINGLE_TAG }

    public interface Listener {
        void onApplyMask();
        void onClearMask();
    }

    private final Fragment fragment;
    private final Appearance appearance;
    private final Listener listener;
    private final View panelContent;
    private final Spinner bankSpinner;
    private final EditText offsetView;
    private final EditText lengthView;
    private final EditText hexView;
    private final MaterialButton toggleButton;
    private final TextView lengthHintView;
    private final TextView statusView;
    private final ImageView expandView;

    private TagProtocol protocol = TagProtocol.ISO_18000_6C;
    private InventoryMaskConfig activeMask;
    private boolean connected;
    private boolean operationInFlight;
    private boolean expanded;

    public InventoryMaskPanelController(@NonNull Fragment fragment, @NonNull View root,
            @NonNull Appearance appearance, @NonNull Listener listener) {
        this.fragment = fragment;
        this.appearance = appearance;
        this.listener = listener;
        panelContent = root.findViewById(R.id.ll_inventory_mask_content);
        bankSpinner = root.findViewById(R.id.sp_inventory_mask_bank);
        offsetView = root.findViewById(R.id.et_inventory_mask_offset);
        lengthView = root.findViewById(R.id.et_inventory_mask_length);
        hexView = root.findViewById(R.id.et_inventory_mask_hex);
        toggleButton = root.findViewById(R.id.btn_inventory_mask_toggle);
        lengthHintView = root.findViewById(R.id.tv_inventory_mask_length_hint);
        statusView = root.findViewById(R.id.tv_inventory_mask_status);
        expandView = root.findViewById(R.id.iv_inventory_mask_expand);

        bankSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                offsetView.setText(String.valueOf(
                        ProtocolEncoding.defaultMaskOffsetBits(protocol, position)));
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) { render(); }
        };
        hexView.addTextChangedListener(watcher);
        lengthView.addTextChangedListener(watcher);
        hexView.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) { return; }
            int bitLength = hexView.getText().toString().trim().length() * 4;
            if (protocol == TagProtocol.ISO_18000_6B) { bitLength -= bitLength % 8; }
            lengthView.setText(bitLength == 0 ? "" : String.valueOf(bitLength));
        });
        root.findViewById(R.id.row_inventory_mask_toggle).setOnClickListener(view -> {
            expanded = !expanded;
            render();
        });
        toggleButton.setOnClickListener(view -> {
            if (activeMask == null) { listener.onApplyMask(); }
            else { listener.onClearMask(); }
        });
        updateProtocol(protocol);
    }

    public void updateProtocol(@NonNull TagProtocol value) {
        protocol = value;
        int labels = switch (protocol) {
            case ISO_18000_6C -> R.array.single_bank_labels_6c;
            case ISO_18000_6B -> R.array.inventory_mask_bank_uid;
            case GJB_7377_1 -> R.array.inventory_mask_bank_epc;
            case GB_T_29768 -> R.array.single_bank_labels_gb;
        };
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                fragment.requireContext(), labels, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bankSpinner.setAdapter(adapter);
        bankSpinner.setSelection(protocol == TagProtocol.ISO_18000_6C
                || protocol == TagProtocol.GB_T_29768 ? 1 : 0);
        offsetView.setText(String.valueOf(ProtocolEncoding.defaultMaskOffsetBits(protocol,
                bankSpinner.getSelectedItemPosition())));
        render();
    }

    public void setConnected(boolean value) { connected = value; render(); }
    public void setOperationInFlight(boolean value) { operationInFlight = value; render(); }
    public void setExpanded(boolean value) { expanded = value; render(); }
    public boolean isExpanded() { return expanded; }

    public void setActiveMask(@Nullable InventoryMaskConfig config) {
        activeMask = config;
        if (config != null) { bind(config); }
        render();
    }

    @Nullable public InventoryMaskConfig getActiveMask() { return activeMask; }

    @NonNull
    public InventoryMaskFormParser.Result parse() {
        return InventoryMaskFormParser.parse(protocol, bankSpinner.getSelectedItemPosition(),
                offsetView.getText().toString(), lengthView.getText().toString(),
                hexView.getText().toString());
    }

    public void fill(int bank, @NonNull String hex) {
        if (bank < 0 || bank >= bankSpinner.getCount() || hex.isEmpty()) { return; }
        bankSpinner.setSelection(bank);
        offsetView.setText(String.valueOf(ProtocolEncoding.defaultMaskOffsetBits(protocol, bank)));
        hexView.setText(hex);
        lengthView.setText(String.valueOf(hex.length() * 4));
        expanded = true;
        render();
    }

    public void focus(@Nullable InventoryMaskFormParser.Error error) {
        EditText target = switch (error == null ? InventoryMaskFormParser.Error.HEX_INVALID : error) {
            case OFFSET_INVALID, OFFSET_OUT_OF_RANGE -> offsetView;
            case LENGTH_INVALID, LENGTH_NOT_POSITIVE, LENGTH_EXCEEDS_DATA,
                    SIX_B_LENGTH_NOT_BYTE_ALIGNED -> lengthView;
            default -> hexView;
        };
        target.requestFocus();
    }

    private void bind(InventoryMaskConfig config) {
        int position = switch (protocol) {
            case ISO_18000_6C, GB_T_29768 -> config.bank;
            case ISO_18000_6B, GJB_7377_1 -> 0;
        };
        if (position >= 0 && position < bankSpinner.getCount()) {
            bankSpinner.setSelection(position);
        }
        offsetView.setText(String.valueOf(config.offsetBits));
        lengthView.setText(String.valueOf(config.lengthBits));
        hexView.setText(HexCodec.encode(config.getMask(), config.getMaskByteLength()));
    }

    private void render() {
        boolean formEnabled = connected && !operationInFlight;
        boolean formValid = updateLengthHint();
        panelContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        panelContent.setAlpha(formEnabled ? 1f : 0.48f);
        expandView.setImageResource(appearance == Appearance.SINGLE_TAG && expanded
                ? R.drawable.arrows_top_ic : R.drawable.arrows_bottom_ic);
        if (appearance == Appearance.INVENTORY) {
            expandView.setRotation(expanded ? 180f : 0f);
            expandView.setContentDescription(fragment.getString(expanded
                    ? R.string.inventory_mask_collapse : R.string.inventory_mask_expand));
        } else {
            expandView.setRotation(0f);
        }
        ViewUtils.setEnabledRecursively(panelContent, formEnabled);
        offsetView.setEnabled(formEnabled && protocol != TagProtocol.ISO_18000_6B);
        boolean masked = activeMask != null;
        toggleButton.setText(masked
                ? R.string.inventory_mask_cancel : R.string.inventory_mask_apply);
        toggleButton.setBackgroundTintList(ContextCompat.getColorStateList(fragment.requireContext(),
                masked ? R.color.rfid_danger_button_background
                        : R.color.rfid_primary_button_background));
        toggleButton.setTextColor(ContextCompat.getColorStateList(fragment.requireContext(),
                R.color.rfid_primary_button_text));
        toggleButton.setEnabled(formEnabled && (masked || formValid));
        renderStatus(masked);
    }

    private void renderStatus(boolean masked) {
        statusView.setVisibility(View.VISIBLE);
        if (appearance == Appearance.INVENTORY) {
            statusView.setBackgroundResource(masked
                    ? R.drawable.rfid_chip_red_bg : R.drawable.rfid_chip_blue_bg);
        } else {
            statusView.setBackgroundResource(masked
                    ? R.drawable.rfid_chip_green_bg : R.drawable.rfid_chip_gray_bg);
            statusView.setTextColor(ContextCompat.getColor(fragment.requireContext(), masked
                    ? R.color.white : R.color.rfid_text_secondary));
        }
        if (!masked) {
            statusView.setText(R.string.inventory_mask_inactive);
            return;
        }
        Object bank = bankSpinner.getSelectedItem();
        statusView.setText(fragment.getString(R.string.inventory_mask_active,
                bank == null ? "" : bank.toString(), activeMask.offsetBits,
                activeMask.lengthBits));
    }

    private boolean updateLengthHint() {
        String hex = hexView.getText().toString().trim();
        if (hex.isEmpty()) {
            setLengthHint(R.string.inventory_mask_length_hint_empty, false);
            return false;
        }
        if ((hex.length() & 1) != 0 || !hex.matches("[0-9A-Fa-f]+")) {
            setLengthHint(R.string.inventory_mask_length_hint_odd, true, hex.length());
            return false;
        }
        int dataBits = hex.length() * 4;
        int length;
        try { length = Integer.parseInt(lengthView.getText().toString()); }
        catch (NumberFormatException error) {
            setLengthHint(R.string.inventory_mask_length_positive, true);
            return false;
        }
        if (length <= 0) {
            setLengthHint(R.string.inventory_mask_length_positive, true);
            return false;
        }
        if (length > dataBits) {
            setLengthHint(R.string.inventory_mask_length_hint_short, true, length, dataBits);
            return false;
        }
        if (protocol == TagProtocol.ISO_18000_6B && (length & 7) != 0) {
            setLengthHint(R.string.inventory_mask_6b_byte_aligned, true);
            return false;
        }
        setLengthHint(R.string.inventory_mask_length_hint_ok, false,
                hex.length() / 2, dataBits, length);
        return true;
    }

    private void setLengthHint(int message, boolean warning, Object... arguments) {
        lengthHintView.setText(arguments.length == 0
                ? fragment.getString(message) : fragment.getString(message, arguments));
        lengthHintView.setTextColor(ContextCompat.getColor(fragment.requireContext(),
                warning ? R.color.rfid_warning : R.color.rfid_text_muted));
    }
}
