package com.vpt.filemanager.core.util;

import java.util.Locale;

public final class ByteSize {
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private ByteSize() {
    }

    public static String format(long bytes) {
        if (bytes < 0) {
            return "Unknown";
        }
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        if (unit == 0) {
            return bytes + " B";
        }
        return String.format(Locale.US, "%.1f %s", value, UNITS[unit]);
    }
}

