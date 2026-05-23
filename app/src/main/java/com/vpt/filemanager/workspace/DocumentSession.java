package com.vpt.filemanager.workspace;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;

/**
 * Workspace-owned editor session for one virtual file node.
 *
 * <p>A session owns document I/O, its saved-content baseline, external change validation and
 * publication of save mutations. It keeps no Android view or sora-editor objects.
 */
public final class DocumentSession implements AutoCloseable {
    private static final long HARD_LIMIT_BYTES = 8L * 1024 * 1024;
    private static final int READ_ONLY_THRESHOLD_BYTES = 1024 * 1024;
    private static final int SNIFF_BYTES = 4096;
    private static final int NULL_SCAN_WINDOW = 512;

    private final NodePath path;
    private final NodeFactory nodeFactory;
    private final WorkspaceStore workspace;
    private final MutableLiveData<Long> externalInvalidations = new MutableLiveData<>();

    private Baseline baseline;
    private String savedContent = "";
    private boolean loaded;
    private boolean closed;
    private long invalidationSequence;

    DocumentSession(@NonNull NodePath path,
                    @NonNull NodeFactory nodeFactory,
                    @NonNull WorkspaceStore workspace) {
        this.path = path;
        this.nodeFactory = nodeFactory;
        this.workspace = workspace;
    }

    @NonNull
    public NodePath path() {
        return path;
    }

    @NonNull
    public LiveData<Long> externalInvalidations() {
        return externalInvalidations;
    }

    /**
     * Loads the current virtual node. Binary files require one explicit approval before content is
     * decoded for display.
     */
    @NonNull
    public LoadResult load(boolean allowBinary) throws NodeException {
        requireOpen();
        VirtualNode node = resolveFile();
        if (node.size() > HARD_LIMIT_BYTES) {
            throw new NodeException("File is too large to open.");
        }
        if (!allowBinary && looksBinary(node)) {
            return LoadResult.binaryApprovalRequired();
        }
        boolean truncated = node.size() > READ_ONLY_THRESHOLD_BYTES;
        int characterLimit = truncated ? READ_ONLY_THRESHOLD_BYTES : Integer.MAX_VALUE;
        ReadResult read = readDecoded(node, characterLimit, !truncated);
        boolean writable = !truncated && node.source().supportsWrite();
        synchronized (this) {
            baseline = new Baseline(node.size(), node.modifiedAt(), read.fingerprint);
            savedContent = read.content;
            loaded = true;
        }
        return LoadResult.document(read.content, writable, truncated);
    }

    /**
     * Tests editor content against the session savepoint without allocating a second full string.
     */
    public synchronized boolean isDirty(@NonNull CharSequence content) {
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

    /**
     * Saves content only when the virtual node still matches the baseline loaded or last saved by
     * this session. This prevents silent overwrites after external modifications.
     */
    @NonNull
    public SaveResult save(@NonNull CharSequence content) throws NodeException {
        requireOpen();
        String text = content.toString();
        VirtualNode current = resolveFile();
        Baseline expected;
        synchronized (this) {
            if (!loaded) {
                throw new NodeException("Document is not loaded");
            }
            expected = baseline;
        }
        if (!matchesBaseline(current, expected)) {
            throw new ConflictException("File changed outside the editor");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = current.openWrite()) {
            output.write(bytes);
        } catch (IOException e) {
            throw new NodeException("Unable to save: " + path.name(), e);
        }
        VirtualNode saved = resolveFile();
        synchronized (this) {
            baseline = new Baseline(saved.size(), saved.modifiedAt(), digest(bytes));
            savedContent = text;
        }
        MutationResult mutation = MutationResult.builder()
                .changedContainer(path.parent())
                .build();
        workspace.publishFromDocument(this, mutation);
        return new SaveResult(mutation);
    }

    /**
     * Resolves an invalidation signal into a stable state for UI policy.
     */
    @NonNull
    public ExternalState inspectExternalState() {
        Baseline expected;
        synchronized (this) {
            if (!loaded) {
                return ExternalState.UNCHANGED;
            }
            expected = baseline;
        }
        try {
            VirtualNode current = resolveFile();
            return matchesBaseline(current, expected)
                    ? ExternalState.UNCHANGED : ExternalState.MODIFIED;
        } catch (NodeException e) {
            return ExternalState.DELETED;
        }
    }

    void onExternalInvalidation() {
        synchronized (this) {
            if (closed) {
                return;
            }
            invalidationSequence++;
            externalInvalidations.postValue(invalidationSequence);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        workspace.closeDocument(this);
    }

    private void requireOpen() throws NodeException {
        synchronized (this) {
            if (closed) {
                throw new NodeException("Document session is closed");
            }
        }
    }

    private VirtualNode resolveFile() throws NodeException {
        VirtualNode node = nodeFactory.fromPath(path);
        if (node.isFolder()) {
            throw new NodeException("Cannot edit a folder: " + path);
        }
        return node;
    }

    private static boolean looksBinary(VirtualNode node) throws NodeException {
        try (InputStream input = node.openRead()) {
            byte[] buffer = new byte[SNIFF_BYTES];
            int read = input.read(buffer);
            int scanEnd = Math.min(Math.max(read, 0), NULL_SCAN_WINDOW);
            for (int index = 0; index < scanEnd; index++) {
                if (buffer[index] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new NodeException("Unable to inspect: " + node.path().name(), e);
        }
    }

    @NonNull
    private static ReadResult readDecoded(VirtualNode node, int charLimit, boolean fingerprint)
            throws NodeException {
        MessageDigest digest = fingerprint ? newDigest() : null;
        StringBuilder result = new StringBuilder(Math.min(charLimit, 16 * 1024));
        char[] chars = new char[4096];
        try (InputStream raw = node.openRead();
             InputStream source = digest == null
                     ? raw : new java.security.DigestInputStream(raw, digest);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     source,
                     StandardCharsets.UTF_8.newDecoder()
                             .onMalformedInput(CodingErrorAction.REPLACE)
                             .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
            while (result.length() < charLimit) {
                int remaining = charLimit - result.length();
                int count = reader.read(chars, 0, Math.min(chars.length, remaining));
                if (count < 0) {
                    break;
                }
                result.append(chars, 0, count);
            }
        } catch (IOException e) {
            throw new NodeException("Unable to read: " + node.path().name(), e);
        }
        return new ReadResult(result.toString(), digest == null ? null : digest.digest());
    }

    private static boolean matchesBaseline(VirtualNode current, Baseline expected)
            throws NodeException {
        if (current.size() != expected.size || current.modifiedAt() != expected.modifiedAt) {
            return false;
        }
        if (expected.fingerprint == null) {
            return true;
        }
        ReadResult currentContent = readDecoded(current, Integer.MAX_VALUE, true);
        return Arrays.equals(expected.fingerprint, currentContent.fingerprint);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] digest(byte[] bytes) {
        MessageDigest digest = newDigest();
        return digest.digest(bytes);
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

        private static LoadResult binaryApprovalRequired() {
            return new LoadResult(true, "", false, false);
        }

        private static LoadResult document(String content, boolean writable, boolean truncated) {
            return new LoadResult(false, content, writable, truncated);
        }
    }

    public static final class SaveResult {
        @NonNull public final MutationResult mutation;

        private SaveResult(@NonNull MutationResult mutation) {
            this.mutation = mutation;
        }
    }

    public static final class ConflictException extends NodeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    private static final class Baseline {
        final long size;
        final long modifiedAt;
        final byte[] fingerprint;

        Baseline(long size, long modifiedAt, byte[] fingerprint) {
            this.size = size;
            this.modifiedAt = modifiedAt;
            this.fingerprint = fingerprint;
        }
    }

    private static final class ReadResult {
        final String content;
        final byte[] fingerprint;

        ReadResult(String content, byte[] fingerprint) {
            this.content = content;
            this.fingerprint = fingerprint;
        }
    }
}
