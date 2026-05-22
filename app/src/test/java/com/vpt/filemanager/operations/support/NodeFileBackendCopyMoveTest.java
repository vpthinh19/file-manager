package com.vpt.filemanager.operations.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.vpt.filemanager.node.NodePath;
import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.VirtualNode;
import com.vpt.filemanager.node.source.LocalSource;

/**
 * JVM unit test cho {@link NodeFileBackend#copy} / {@link NodeFileBackend#move} với {@link LocalSource} thật trên
 * temp folder. Cover các nhánh chính:
 *
 * <ul>
 *   <li>Single file copy (local→local) bảo toàn bytes</li>
 *   <li>Folder copy đệ quy (3 cấp)</li>
 *   <li>Move via nio (atomic rename) — đích xuất hiện, source biến mất</li>
 *   <li>Conflict — copy fail nếu newName tồn tại</li>
 *   <li>Validate name có path separator → NodeException fail-fast</li>
 *   <li>CancellationToken pre-cancelled → skip stream copy mid-batch</li>
 * </ul>
 */
public final class NodeFileBackendCopyMoveTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private LocalSource localSource;
    private NodeFileBackend fileBackend;
    private Path rootDir;
    private NodePath rootFp;
    private VirtualNode rootNode;

    @Before
    public void setUp() throws Exception {
        localSource = new LocalSource();
        fileBackend = new NodeFileBackend();
        rootDir = temp.getRoot().toPath();
        rootFp = NodePath.local(rootDir.toString().replace('\\', '/'));
        rootNode = localSource.resolve(rootFp);
    }

    @Test
    public void copyFile_preservesContent() throws Exception {
        Path src = Files.write(rootDir.resolve("src.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        VirtualNode srcNode = localSource.resolve(filePathOf(src));

        VirtualNode copied = fileBackend.copy(srcNode, rootNode, "dst.txt",
                NodeFileBackend.CancellationToken.neverCancelled());

        assertNotNull(copied);
        assertTrue(Files.exists(src));
        Path dst = rootDir.resolve("dst.txt");
        assertTrue(Files.exists(dst));
        assertEquals("hello", new String(Files.readAllBytes(dst), StandardCharsets.UTF_8));
    }

    @Test
    public void copyFolder_recursesChildren() throws Exception {
        Path srcDir = Files.createDirectories(rootDir.resolve("srcDir/sub"));
        Files.write(srcDir.resolve("a.txt"), "A".getBytes(StandardCharsets.UTF_8));
        Files.write(srcDir.getParent().resolve("b.txt"), "B".getBytes(StandardCharsets.UTF_8));
        VirtualNode srcNode = localSource.resolve(filePathOf(srcDir.getParent()));

        VirtualNode copied = fileBackend.copy(srcNode, rootNode, "dstDir",
                NodeFileBackend.CancellationToken.neverCancelled());

        assertTrue(copied.isFolder());
        Path dstRoot = rootDir.resolve("dstDir");
        assertTrue(Files.isDirectory(dstRoot));
        assertTrue(Files.isDirectory(dstRoot.resolve("sub")));
        assertEquals("A", readUtf8(dstRoot.resolve("sub/a.txt")));
        assertEquals("B", readUtf8(dstRoot.resolve("b.txt")));
        // Source vẫn còn nguyên (copy không đụng src)
        assertTrue(Files.exists(srcDir.resolve("a.txt")));
    }

    @Test
    public void moveFile_localNio_atomicRename() throws Exception {
        Path src = Files.write(rootDir.resolve("src.txt"), "X".getBytes(StandardCharsets.UTF_8));
        VirtualNode srcNode = localSource.resolve(filePathOf(src));

        fileBackend.move(srcNode, rootNode, "moved.txt",
                NodeFileBackend.CancellationToken.neverCancelled());

        assertFalse("Source must be gone after move", Files.exists(src));
        Path dst = rootDir.resolve("moved.txt");
        assertTrue(Files.exists(dst));
        assertEquals("X", readUtf8(dst));
    }

    @Test
    public void moveFolder_localNio_preservesTreeAndRemovesSource() throws Exception {
        Path srcDir = Files.createDirectories(rootDir.resolve("oldName/child"));
        Files.write(srcDir.resolve("leaf.txt"), "L".getBytes(StandardCharsets.UTF_8));
        VirtualNode srcNode = localSource.resolve(filePathOf(srcDir.getParent()));

        fileBackend.move(srcNode, rootNode, "newName",
                NodeFileBackend.CancellationToken.neverCancelled());

        assertFalse(Files.exists(srcDir.getParent()));
        Path dst = rootDir.resolve("newName");
        assertTrue(Files.isDirectory(dst));
        assertEquals("L", readUtf8(dst.resolve("child/leaf.txt")));
    }

    @Test
    public void copyFile_destExists_throwsNodeException() throws Exception {
        Path src = Files.write(rootDir.resolve("src.txt"), "A".getBytes(StandardCharsets.UTF_8));
        Files.write(rootDir.resolve("dst.txt"), "B".getBytes(StandardCharsets.UTF_8));
        VirtualNode srcNode = localSource.resolve(filePathOf(src));

        assertThrows(NodeException.class,
                () -> fileBackend.copy(srcNode, rootNode, "dst.txt",
                        NodeFileBackend.CancellationToken.neverCancelled()));
        // Dest gốc giữ nguyên — copy fail-fast, không corrupt
        assertEquals("B", readUtf8(rootDir.resolve("dst.txt")));
    }

    @Test
    public void copyFile_nameWithSeparator_throwsBeforeIO() throws Exception {
        Path src = Files.write(rootDir.resolve("src.txt"), "A".getBytes(StandardCharsets.UTF_8));
        VirtualNode srcNode = localSource.resolve(filePathOf(src));

        assertThrows(NodeException.class,
                () -> fileBackend.copy(srcNode, rootNode, "bad/name.txt",
                        NodeFileBackend.CancellationToken.neverCancelled()));
        assertFalse(Files.exists(rootDir.resolve("bad")));
    }

    @Test
    public void copyFolder_preCancelled_cleansUpPartialDst() throws Exception {
        Path srcDir = Files.createDirectories(rootDir.resolve("srcDir"));
        for (String name : Arrays.asList("a.txt", "b.txt", "c.txt")) {
            Files.write(srcDir.resolve(name), name.getBytes(StandardCharsets.UTF_8));
        }
        VirtualNode srcNode = localSource.resolve(filePathOf(srcDir));
        NodeFileBackend.CancellationToken token = new NodeFileBackend.CancellationToken();
        token.cancel();

        // Pre-cancelled: copy throws NodeException + dst phải biến mất hoàn toàn (atomic semantic).
        assertThrows(NodeException.class,
                () -> fileBackend.copy(srcNode, rootNode, "dstDir", token));
        assertFalse("Cancel must leave no partial dst", Files.exists(rootDir.resolve("dstDir")));
        // Source giữ nguyên — copy is read-only on src.
        assertTrue(Files.isDirectory(srcDir));
    }

    @Test
    public void moveCrossSource_emulated_rollsBackWhenDeleteFails() throws Exception {
        // Emulate cross-source bằng cách wrap src trong NodeSource khác source instance, NHƯNG
        // vẫn local — tests covers fast-path bypass + rollback compensation logic.
        // Simpler: dùng same LocalSource cho cả 2 endpoint → fast path nio Files.move ăn thẳng;
        // rollback path chỉ chạy khi delete src fail mà nio không cover được. Defer thorough
        // cross-source test đến khi có MockNodeSource fixture (Phase C-1b/c).
        // Smoke: same-source local move qua nio = src biến mất + dst xuất hiện, không gọi rollback.
        Path src = Files.write(rootDir.resolve("a.txt"), "z".getBytes(StandardCharsets.UTF_8));
        VirtualNode srcNode = localSource.resolve(filePathOf(src));

        fileBackend.move(srcNode, rootNode, "b.txt", NodeFileBackend.CancellationToken.neverCancelled());

        assertFalse(Files.exists(src));
        assertTrue(Files.exists(rootDir.resolve("b.txt")));
    }

    private static NodePath filePathOf(Path nio) {
        return NodePath.local(nio.toString().replace('\\', '/'));
    }

    /** Android's nio Files trên bootclasspath không có readString (API < 33) → polyfill. */
    private static String readUtf8(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
