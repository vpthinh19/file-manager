package com.vpt.filemanager.domain.model;

public enum HashAlgorithm {
    MD5("MD5"),
    SHA1("SHA-1"),
    SHA256("SHA-256");

    private final String messageDigestName;

    HashAlgorithm(String messageDigestName) {
        this.messageDigestName = messageDigestName;
    }

    public String messageDigestName() {
        return messageDigestName;
    }
}

