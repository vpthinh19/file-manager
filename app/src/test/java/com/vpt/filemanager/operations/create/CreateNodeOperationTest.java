package com.vpt.filemanager.operations.create;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.vpt.filemanager.data.db.dao.TrashDao;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.LocalSource;
import com.vpt.filemanager.node.source.ArchiveSource;
import com.vpt.filemanager.operations.support.NodeFileBackend;
import com.vpt.filemanager.operations.trash.TrashStore;
import com.vpt.filemanager.operations.conflict.NameConflictException;

public final class CreateNodeOperationTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private LocalSource localSource;
    private CreateNodeOperation operation;
    private Path rootDir;
    private VirtualNode rootNode;

    @Before
    public void setUp() throws Exception {
        localSource = new LocalSource();
        operation = new CreateNodeOperation(new NodeFileBackend(), new TrashStore(mock(TrashDao.class)));
        rootDir = temp.getRoot().toPath();
        rootNode = localSource.resolve(NodePath.local(rootDir.toString().replace('\\', '/')));
    }

    @Test
    public void createFile_withoutConflict_createsVirtualChild() throws Exception {
        CreateNodeOperation.Result result = operation.execute(new CreateNodeOperation.Input(
                rootNode, CreateNodeType.FILE, "note.txt", ExistingNamePolicy.FAIL));

        assertEquals("note.txt", result.created.name());
        assertTrue(result.mutation.affectsListing(rootNode.path()));
        assertTrue(Files.isRegularFile(rootDir.resolve("note.txt")));
    }

    @Test
    public void createFolder_withoutConflict_createsVirtualChild() throws Exception {
        CreateNodeOperation.Result result = operation.execute(new CreateNodeOperation.Input(
                rootNode, CreateNodeType.FOLDER, "docs", ExistingNamePolicy.FAIL));

        assertEquals("docs", result.created.name());
        assertTrue(Files.isDirectory(rootDir.resolve("docs")));
    }

    @Test
    public void createFile_conflictWithFail_throwsNameConflict() throws Exception {
        Files.write(rootDir.resolve("note.txt"), "old".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThrows(NameConflictException.class, () -> operation.execute(
                new CreateNodeOperation.Input(
                        rootNode, CreateNodeType.FILE, "note.txt", ExistingNamePolicy.FAIL)));
    }

    @Test
    public void createFile_keepBoth_usesUniqueVirtualName() throws Exception {
        Files.write(rootDir.resolve("note.txt"), "old".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        CreateNodeOperation.Result result = operation.execute(new CreateNodeOperation.Input(
                rootNode, CreateNodeType.FILE, "note.txt", ExistingNamePolicy.KEEP_BOTH));

        assertEquals("note (1).txt", result.created.name());
        assertTrue(Files.isRegularFile(rootDir.resolve("note.txt")));
        assertTrue(Files.isRegularFile(rootDir.resolve("note (1).txt")));
    }

    @Test
    public void createFile_replaceInsideArchive_deletesEntryWithoutTrash() throws Exception {
        Path archive = rootDir.resolve("data.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("note.txt"));
            output.write("old".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        ArchiveSource archiveSource = new ArchiveSource();
        VirtualNode archiveRoot = archiveSource.resolve(
                NodePath.inArchive(NodePath.local(archive.toString().replace('\\', '/')), "/"));
        TrashStore trash = mock(TrashStore.class);
        CreateNodeOperation archiveOperation = new CreateNodeOperation(new NodeFileBackend(), trash);

        CreateNodeOperation.Result result = archiveOperation.execute(new CreateNodeOperation.Input(
                archiveRoot, CreateNodeType.FILE, "note.txt", ExistingNamePolicy.REPLACE));

        verify(trash, never()).moveToTrash(org.mockito.ArgumentMatchers.any());
        assertEquals("note.txt", result.created.name());
        assertEquals(0L, result.created.size());
    }
}
