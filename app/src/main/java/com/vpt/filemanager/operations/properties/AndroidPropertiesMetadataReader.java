package com.vpt.filemanager.operations.properties;

import android.system.Os;
import android.system.StructStat;

import java.util.Locale;

import com.vpt.filemanager.node.NodePath;

/**
 * Android POSIX metadata reader backed by {@link Os#stat(String)}.
 */
public final class AndroidPropertiesMetadataReader implements PropertiesMetadataReader {
    @Override
    public PropertiesModel.PosixMetadata read(NodePath path) {
        if (!path.isLocal()) {
            return null;
        }
        try {
            StructStat stat = Os.stat(path.path());
            PosixPermission permission = new PosixPermission(stat.st_mode);
            String permissions = String.format(Locale.US, "%s (%s)",
                    permission.toRwxString(),
                    Integer.toOctalString(stat.st_mode & 0777));
            return new PropertiesModel.PosixMetadata(
                    permissions,
                    String.valueOf(stat.st_uid),
                    String.valueOf(stat.st_gid));
        } catch (Exception e) {
            return null;
        }
    }
}
