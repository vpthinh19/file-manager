package com.vpt.filemanager.operations.rename;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.LocalSource;
import com.vpt.filemanager.operations.FileOps;

public final class RenameNodeOperationTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private LocalSource localSource;
    private RenameNodeOperation operation;
    private Path rootDir;

    @Before
    public void setUp() {
        localSource = new LocalSource();
        operation = new RenameNodeOperation(new FileOps());
        rootDir = temp.getRoot().toPath();
    }

    @Test
    public void renameFile_changesPathAndPreservesContent() throws Exception {
        Path oldPath = Files.write(rootDir.resolve("old.txt"),
                "hello".getBytes(StandardCharsets.UTF_8));
        VirtualNode oldNode = localSource.resolve(filePathOf(oldPath));

        VirtualNode renamed = operation.execute(new RenameNodeOperation.Input(
                oldNode, "new.txt"));

        assertEquals("new.txt", renamed.name());
        assertFalse(Files.exists(oldPath));
        assertTrue(Files.exists(rootDir.resolve("new.txt")));
        assertEquals("hello", new String(
                Files.readAllBytes(rootDir.resolve("new.txt")), StandardCharsets.UTF_8));
    }

    @Test
    public void renameFile_blankName_throwsNodeException() throws Exception {
        Path oldPath = Files.write(rootDir.resolve("old.txt"),
                "hello".getBytes(StandardCharsets.UTF_8));
        VirtualNode oldNode = localSource.resolve(filePathOf(oldPath));

        assertThrows(NodeException.class, () -> operation.execute(
                new RenameNodeOperation.Input(oldNode, " ")));
    }

    private static NodePath filePathOf(Path nio) {
        return NodePath.local(nio.toString().replace('\\', '/'));
    }
}
