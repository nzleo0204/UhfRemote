package com.leo.rfid.sdk.inventory;

import com.leo.rfid.sdk.model.*;


/** Holds the Query Sel value that must be restored when an inventory mask is cleared. */
final class InventoryMaskSelection {
    private Integer selected;

    synchronized boolean capture(int[] queryValues) {
        if (queryValues == null || queryValues.length < 3) { return false; }
        if (selected == null) { selected = queryValues[2]; }
        return true;
    }

    synchronized boolean isCaptured() {
        return selected != null;
    }

    synchronized int restoreValue() {
        if (selected == null) {
            throw new IllegalStateException("Query Sel has not been captured");
        }
        return selected;
    }

    synchronized void clear() {
        selected = null;
    }
}
