package com.vpt.filemanager.browser.action.properties;

import android.system.Os;
import android.system.StructStat;

import java.util.Locale;

import javax.inject.Inject;


public final class AndroidPropertiesMetadataReader implements PropertiesMetadataReader {
    @Inject
    public AndroidPropertiesMetadataReader() {
    }

    @Override
    public PropertiesModel.PosixMetadata read(String path) {
        try {
            StructStat stat = Os.stat(path);
            int mode = stat.st_mode & 0777;
            String permissions = String.format(Locale.US, "%s (%s)",
                    rwx(mode), Integer.toOctalString(mode));
            return new PropertiesModel.PosixMetadata(permissions, String.valueOf(stat.st_uid),
                    String.valueOf(stat.st_gid));
        } catch (Exception error) {
            return null;
        }
    }

    private static String rwx(int mode) {
        StringBuilder result = new StringBuilder(9);
        int[] bits = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
        char[] symbols = {'r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x'};
        for (int index = 0; index < bits.length; index++) {
            result.append((mode & bits[index]) != 0 ? symbols[index] : '-');
        }
        return result.toString();
    }
}
