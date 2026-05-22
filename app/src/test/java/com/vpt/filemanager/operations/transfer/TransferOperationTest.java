package com.vpt.filemanager.operations.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.LocalSource;
import com.vpt.filemanager.operations.FileOps;
import com.vpt.filemanager.operations.TrashOps;

public final class TransferOperationTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private LocalSource localSource;
    private TransferOperation operation;
    private Path rootDir;
    private Path srcDir;
    private Path dstDir;
    private VirtualNode dstNode;

    @Before
    public void setUp() throws Exception {
        localSource = new LocalSource();
        operation = new TransferOperation(new FileOps(), new TrashOps(mock(TrashDao.class)));
        rootDir = temp.getRoot().toPath();
        srcDir = Files.createDirectories(rootDir.resolve("src"));
        dstDir = Files.createDirectories(rootDir.resolve("dst"));
        dstNode = localSource.resolve(filePathOf(dstDir));
    }

    @Test
    public void copy_noConflict_copiesFile() throws Exception {
        Path src = Files.write(srcDir.resolve("a.txt"), "A".getBytes(StandardCharsets.UTF_8));

        TransferOperation.Result result = operation.execute(new TransferOperation.Input(
                List.of(localSource.resolve(filePathOf(src))),
                dstNode,
                TransferKind.COPY,
                conflict -> TransferConflictDecision.CANCEL,
                FileOps.CancellationToken.neverCancelled()));

        assertEquals(1, result.ok);
        assertEquals(0, result.failed);
        assertEquals("A", readUtf8(dstDir.resolve("a.txt")));
        assertTrue(Files.exists(src));
    }

    @Test
    public void copy_conflictKeepBoth_usesUniqueName() throws Exception {
        Path src = Files.write(srcDir.resolve("a.txt"), "new".getBytes(StandardCharsets.UTF_8));
        Files.write(dstDir.resolve("a.txt"), "old".getBytes(StandardCharsets.UTF_8));

        TransferOperation.Result result = operation.execute(new TransferOperation.Input(
                List.of(localSource.resolve(filePathOf(src))),
                dstNode,
                TransferKind.COPY,
                conflict -> TransferConflictDecision.KEEP_BOTH,
                FileOps.CancellationToken.neverCancelled()));

        assertEquals(1, result.ok);
        assertEquals("old", readUtf8(dstDir.resolve("a.txt")));
        assertEquals("new", readUtf8(dstDir.resolve("a (1).txt")));
    }

    @Test
    public void copy_conflictCancel_abortsRemainingItems() throws Exception {
        Path first = Files.write(srcDir.resolve("a.txt"), "A".getBytes(StandardCharsets.UTF_8));
        Path second = Files.write(srcDir.resolve("b.txt"), "B".getBytes(StandardCharsets.UTF_8));
        Files.write(dstDir.resolve("a.txt"), "old".getBytes(StandardCharsets.UTF_8));

        TransferOperation.Result result = operation.execute(new TransferOperation.Input(
                List.of(localSource.resolve(filePathOf(first)), localSource.resolve(filePathOf(second))),
                dstNode,
                TransferKind.COPY,
                conflict -> TransferConflictDecision.CANCEL,
                FileOps.CancellationToken.neverCancelled()));

        assertEquals(0, result.ok);
        assertEquals(0, result.failed);
        assertEquals(2, result.cancelledRemaining);
        assertEquals("old", readUtf8(dstDir.resolve("a.txt")));
        assertTrue(Files.notExists(dstDir.resolve("b.txt")));
    }

    private static NodePath filePathOf(Path nio) {
        return NodePath.local(nio.toString().replace('\\', '/'));
    }

    private static String readUtf8(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
