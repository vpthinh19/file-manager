package com.vpt.filemanager.component.content.editor;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import android.os.FileObserver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.vpt.filemanager.core.error.DocumentConflictException;
import com.vpt.filemanager.core.error.FileOperationException;
import com.vpt.filemanager.storage.LocalStorageAdapter;
import java.io.File;

/**
 * One editor document session backed by a concrete local path.
 */
public final class DocumentSession {
    private static final long HARD_LIMIT_BYTES = 8L * 1024 * 1024;
    private static final int READ_ONLY_THRESHOLD_BYTES = 1024 * 1024;
    private static final int SNIFF_BYTES = 4096;

    private final String path;
    private final LocalStorageAdapter files;
    private final MutableLiveData<Long> externalInvalidations = new MutableLiveData<>();
    private final FileObserver observer;
    private Baseline baseline;
    private String savedContent = "";
    private boolean loaded;
    private long invalidationSequence;

    DocumentSession(String path, LocalStorageAdapter files) {
        this.path = path;
        this.files = files;
        observer = new FileObserver(new java.io.File(parent(path)),
                FileObserver.DELETE | FileObserver.MOVED_FROM | FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String changed) {
                if (changed == null || path.endsWith("/" + changed)
                        || path.endsWith("\\" + changed)) {
                    externalInvalidations.postValue(++invalidationSequence);
                }
            }
        };
        observer.startWatching();
    }

    public LiveData<Long> externalInvalidations() {
        return externalInvalidations;
    }

    public void close() {
        observer.stopWatching();
    }

    @NonNull
    public LoadResult load(boolean allowBinary) throws FileOperationException {
        File entry = inspectFile();
        if (entry.length() > HARD_LIMIT_BYTES) {
            throw new FileOperationException("File is too large to open.");
        }
        if (!allowBinary && looksBinary()) {
            return LoadResult.binaryApprovalRequired();
        }
        boolean truncated = entry.length() > READ_ONLY_THRESHOLD_BYTES;
        ReadResult read = readDecoded(truncated ? READ_ONLY_THRESHOLD_BYTES : Integer.MAX_VALUE,
                !truncated);
        baseline = new Baseline(entry.length(), entry.lastModified(), read.fingerprint);
        savedContent = read.content;
        loaded = true;
        return LoadResult.document(read.content, !truncated, truncated);
    }

    public boolean isDirty(@NonNull CharSequence content) {
        if (!loaded || content.length() != savedContent.length()) {
            return loaded;
        }
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) != savedContent.charAt(index)) {
                return true;
            }
        }
        return false;
    }

    public void save(@NonNull CharSequence content) throws FileOperationException {
        if (!loaded) {
            throw new FileOperationException("Document is not loaded");
        }
        File current = inspectFile();
        if (!matches(current)) {
            throw new DocumentConflictException("File changed outside the editor");
        }
        byte[] bytes = content.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = files.openWrite(current)) {
            output.write(bytes);
        } catch (IOException error) {
            throw new FileOperationException("Unable to save document", error);
        }
        File saved = inspectFile();
        baseline = new Baseline(saved.length(), saved.lastModified(), digest(bytes));
        savedContent = content.toString();
    }

    public ExternalState inspectExternalState() {
        if (!loaded) {
            return ExternalState.UNCHANGED;
        }
        try {
            return matches(inspectFile()) ? ExternalState.UNCHANGED : ExternalState.MODIFIED;
        } catch (FileOperationException error) {
            return ExternalState.DELETED;
        }
    }

    private File inspectFile() throws FileOperationException {
        File entry = new File(path);
        if (!entry.exists()) throw new FileOperationException("Path not found: " + path);
        if (entry.isDirectory()) {
            throw new FileOperationException("Cannot edit a folder");
        }
        return entry;
    }

    private boolean looksBinary() throws FileOperationException {
        try (InputStream input = files.openRead(inspectFile())) {
            byte[] bytes = new byte[SNIFF_BYTES];
            int count = input.read(bytes);
            for (int index = 0; index < Math.min(Math.max(count, 0), 512); index++) {
                if (bytes[index] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException error) {
            throw new FileOperationException("Unable to inspect file", error);
        }
    }

    private ReadResult readDecoded(int limit, boolean fingerprint) throws FileOperationException {
        MessageDigest digest = fingerprint ? newDigest() : null;
        StringBuilder content = new StringBuilder(Math.min(limit, 16 * 1024));
        char[] buffer = new char[4096];
        try (InputStream raw = files.openRead(inspectFile());
             InputStream input = digest == null ? raw : new java.security.DigestInputStream(raw, digest);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                     StandardCharsets.UTF_8.newDecoder()
                             .onMalformedInput(CodingErrorAction.REPLACE)
                             .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
            while (content.length() < limit) {
                int read = reader.read(buffer, 0, Math.min(buffer.length, limit - content.length()));
                if (read < 0) {
                    break;
                }
                content.append(buffer, 0, read);
            }
        } catch (IOException error) {
            throw new FileOperationException("Unable to read document", error);
        }
        return new ReadResult(content.toString(), digest == null ? null : digest.digest());
    }

    private boolean matches(File current) throws FileOperationException {
        if (current.length() != baseline.size || current.lastModified() != baseline.modifiedAt) {
            return false;
        }
        return baseline.fingerprint == null
                || Arrays.equals(baseline.fingerprint,
                readDecoded(Integer.MAX_VALUE, true).fingerprint);
    }

    private static String parent(String value) {
        int separator = value.lastIndexOf('/');
        return separator <= 0 ? "/" : value.substring(0, separator);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static byte[] digest(byte[] bytes) {
        return newDigest().digest(bytes);
    }

    public enum ExternalState {
        UNCHANGED,
        MODIFIED,
        DELETED
    }

    public static final class LoadResult {
        public final boolean binaryApprovalRequired;
        public final String content;
        public final boolean writable;
        public final boolean truncated;

        private LoadResult(boolean binaryApprovalRequired, String content,
                           boolean writable, boolean truncated) {
            this.binaryApprovalRequired = binaryApprovalRequired;
            this.content = content;
            this.writable = writable;
            this.truncated = truncated;
        }

        static LoadResult binaryApprovalRequired() {
            return new LoadResult(true, "", false, false);
        }

        static LoadResult document(String content, boolean writable, boolean truncated) {
            return new LoadResult(false, content, writable, truncated);
        }
    }

    private record Baseline(long size, long modifiedAt, byte[] fingerprint) {
    }

    private record ReadResult(String content, byte[] fingerprint) {
    }
}
