package com.vpt.filemanager.util;

public final class PosixPermission {
    private final int mode;

    public PosixPermission(int mode) {
        this.mode = mode;
    }

    public int mode() {
        return mode;
    }

    public boolean isReadable(Who who) {
        return (mode & mask(who, 04)) != 0;
    }

    public boolean isWritable(Who who) {
        return (mode & mask(who, 02)) != 0;
    }

    public boolean isExecutable(Who who) {
        return (mode & mask(who, 01)) != 0;
    }

    public boolean isSetuid() {
        return (mode & 04000) != 0;
    }

    public boolean isSetgid() {
        return (mode & 02000) != 0;
    }

    public boolean isSticky() {
        return (mode & 01000) != 0;
    }

    public String toRwxString() {
        StringBuilder out = new StringBuilder(9);
        append(out, Who.USER);
        append(out, Who.GROUP);
        append(out, Who.OTHER);
        if (isSetuid()) {
            out.setCharAt(2, out.charAt(2) == 'x' ? 's' : 'S');
        }
        if (isSetgid()) {
            out.setCharAt(5, out.charAt(5) == 'x' ? 's' : 'S');
        }
        if (isSticky()) {
            out.setCharAt(8, out.charAt(8) == 'x' ? 't' : 'T');
        }
        return out.toString();
    }

    public String toOctalString() {
        return String.format("0%04o", mode & 07777);
    }

    private void append(StringBuilder out, Who who) {
        out.append(isReadable(who) ? 'r' : '-');
        out.append(isWritable(who) ? 'w' : '-');
        out.append(isExecutable(who) ? 'x' : '-');
    }

    private static int mask(Who who, int bit) {
        switch (who) {
            case USER:
                return bit << 6;
            case GROUP:
                return bit << 3;
            case OTHER:
                return bit;
            default:
                throw new IllegalArgumentException("Unknown target");
        }
    }

    public enum Who {
        USER,
        GROUP,
        OTHER
    }
}

