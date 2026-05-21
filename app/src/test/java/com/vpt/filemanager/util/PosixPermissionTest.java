package com.vpt.filemanager.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PosixPermissionTest {
    @Test
    public void formatsModeBits() {
        PosixPermission permission = new PosixPermission(0755);

        assertTrue(permission.isReadable(PosixPermission.Who.USER));
        assertTrue(permission.isExecutable(PosixPermission.Who.GROUP));
        assertFalse(permission.isWritable(PosixPermission.Who.OTHER));
        assertEquals("rwxr-xr-x", permission.toRwxString());
        assertEquals("00755", permission.toOctalString());
    }

    @Test
    public void formatsSpecialBits() {
        assertEquals("rwsr-xr-x", new PosixPermission(04755).toRwxString());
        assertEquals("rwxr-sr-x", new PosixPermission(02755).toRwxString());
        assertEquals("rwxr-xr-t", new PosixPermission(01755).toRwxString());
    }
}

