package com.vpt.filemanager.domain.model;

public final class FileMetadata {
    private final long sizeBytes;
    private final long createdAtMillis;
    private final long lastModifiedMillis;
    private final long lastAccessAtMillis;
    private final PosixPermission permission;
    private final int uid;
    private final int gid;
    private final String ownerName;
    private final String groupName;
    private final String mimeType;
    private final String selinuxContext;

    private FileMetadata(Builder builder) {
        this.sizeBytes = builder.sizeBytes;
        this.createdAtMillis = builder.createdAtMillis;
        this.lastModifiedMillis = builder.lastModifiedMillis;
        this.lastAccessAtMillis = builder.lastAccessAtMillis;
        this.permission = builder.permission;
        this.uid = builder.uid;
        this.gid = builder.gid;
        this.ownerName = builder.ownerName;
        this.groupName = builder.groupName;
        this.mimeType = builder.mimeType;
        this.selinuxContext = builder.selinuxContext;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long lastModifiedMillis() {
        return lastModifiedMillis;
    }

    public long lastAccessAtMillis() {
        return lastAccessAtMillis;
    }

    public PosixPermission permission() {
        return permission;
    }

    public int uid() {
        return uid;
    }

    public int gid() {
        return gid;
    }

    public String ownerName() {
        return ownerName;
    }

    public String groupName() {
        return groupName;
    }

    public String mimeType() {
        return mimeType;
    }

    public String selinuxContext() {
        return selinuxContext;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long sizeBytes = -1;
        private long createdAtMillis = -1;
        private long lastModifiedMillis = -1;
        private long lastAccessAtMillis = -1;
        private PosixPermission permission;
        private int uid = -1;
        private int gid = -1;
        private String ownerName;
        private String groupName;
        private String mimeType = "application/octet-stream";
        private String selinuxContext;

        public Builder sizeBytes(long sizeBytes) {
            this.sizeBytes = sizeBytes;
            return this;
        }

        public Builder createdAtMillis(long createdAtMillis) {
            this.createdAtMillis = createdAtMillis;
            return this;
        }

        public Builder lastModifiedMillis(long lastModifiedMillis) {
            this.lastModifiedMillis = lastModifiedMillis;
            return this;
        }

        public Builder lastAccessAtMillis(long lastAccessAtMillis) {
            this.lastAccessAtMillis = lastAccessAtMillis;
            return this;
        }

        public Builder permission(PosixPermission permission) {
            this.permission = permission;
            return this;
        }

        public Builder uid(int uid) {
            this.uid = uid;
            return this;
        }

        public Builder gid(int gid) {
            this.gid = gid;
            return this;
        }

        public Builder ownerName(String ownerName) {
            this.ownerName = ownerName;
            return this;
        }

        public Builder groupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder selinuxContext(String selinuxContext) {
            this.selinuxContext = selinuxContext;
            return this;
        }

        public FileMetadata build() {
            return new FileMetadata(this);
        }
    }
}

