package com.vpt.filemanager.workspace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.vpt.filemanager.node.NodeFactory;
import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.source.LocalSource;
import com.vpt.filemanager.node.source.ArchiveSource;

public final class DocumentSessionTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private NodePath documentPath;
    private Path document;
    private WorkspaceStore workspace;
    private DocumentSession session;

    @Before
    public void setUp() throws Exception {
        document = Files.write(temp.getRoot().toPath().resolve("note.txt"),
                "hello".getBytes(StandardCharsets.UTF_8));
        documentPath = NodePath.local(document.toString().replace('\\', '/'));
        LocalSource localSource = new LocalSource();
        NodeFactory factory = mock(NodeFactory.class);
        when(factory.fromPath(documentPath)).thenAnswer(ignored -> localSource.resolve(documentPath));
        workspace = mock(WorkspaceStore.class);
        session = new DocumentSession(documentPath, factory, workspace);
    }

    @Test
    public void loadAndSave_ownSavepointAndPublishParentMutation() throws Exception {
        DocumentSession.LoadResult loaded = session.load(false);

        assertEquals("hello", loaded.content);
        assertFalse(session.isDirty("hello"));
        assertTrue(session.isDirty("hello!"));

        DocumentSession.SaveResult saved = session.save("updated");

        assertEquals("updated", new String(Files.readAllBytes(document), StandardCharsets.UTF_8));
        assertFalse(session.isDirty("updated"));
        assertTrue(saved.mutation.affectsListing(documentPath.parent()));
        verify(workspace).publishFromDocument(session, saved.mutation);
    }

    @Test
    public void save_detectsSameMetadataExternalRewriteByFingerprint() throws Exception {
        session.load(false);
        FileTime loadedModifiedTime = Files.getLastModifiedTime(document);
        Files.write(document, "HELLO".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(document, loadedModifiedTime);

        assertEquals(DocumentSession.ExternalState.MODIFIED, session.inspectExternalState());
        assertThrows(DocumentSession.ConflictException.class, () -> session.save("mine"));
        assertEquals("HELLO", new String(Files.readAllBytes(document), StandardCharsets.UTF_8));
    }

    @Test
    public void inspectExternalState_reportsDeletedNode() throws Exception {
        session.load(false);
        Files.delete(document);

        assertEquals(DocumentSession.ExternalState.DELETED, session.inspectExternalState());
    }

    @Test
    public void archiveEntry_loadAndSave_usesSameDocumentSessionContract() throws Exception {
        Path archive = temp.getRoot().toPath().resolve("notes.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("note.txt"));
            output.write("old".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        NodePath entryPath = NodePath.inArchive(
                NodePath.local(archive.toString().replace('\\', '/')), "/note.txt");
        ArchiveSource archiveSource = new ArchiveSource();
        NodeFactory factory = mock(NodeFactory.class);
        when(factory.fromPath(entryPath)).thenAnswer(ignored -> archiveSource.resolve(entryPath));
        WorkspaceStore archiveWorkspace = mock(WorkspaceStore.class);
        DocumentSession archiveSession = new DocumentSession(entryPath, factory, archiveWorkspace);

        assertTrue(archiveSession.load(false).writable);
        DocumentSession.SaveResult saved = archiveSession.save("new content");

        assertEquals("new content", archiveSession.load(false).content);
        assertTrue(saved.mutation.affectsListing(entryPath.parent()));
        assertTrue(saved.mutation.affectsListing(NodePath.local(
                archive.getParent().toString().replace('\\', '/'))));
        verify(archiveWorkspace).publishFromDocument(archiveSession, saved.mutation);
    }
}
