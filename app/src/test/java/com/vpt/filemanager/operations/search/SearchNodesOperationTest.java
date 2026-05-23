package com.vpt.filemanager.operations.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import com.vpt.filemanager.node.NodeException;
import com.vpt.filemanager.node.NodePath;

public final class SearchNodesOperationTest {
    @Test
    public void execute_buildsSearchResultLocationWithinScope() throws Exception {
        NodePath scope = NodePath.local("/sdcard/Documents");

        SearchNodesOperation.Output result = new SearchNodesOperation().execute(
                new SearchNodesOperation.Input(scope, "  report  "));

        assertEquals(scope, result.resultPath.searchScope());
        assertEquals("report", result.resultPath.searchQuery());
    }

    @Test
    public void searchAgain_reusesOriginalScopeInsteadOfSearchProjection() throws Exception {
        NodePath scope = NodePath.local("/sdcard/Documents");
        NodePath existing = NodePath.search(scope, "old");

        SearchNodesOperation.Output result = new SearchNodesOperation().execute(
                new SearchNodesOperation.Input(existing, "new"));

        assertEquals(scope, result.resultPath.searchScope());
        assertEquals("new", result.resultPath.searchQuery());
    }

    @Test
    public void emptyQuery_isRejected() {
        assertThrows(NodeException.class, () -> new SearchNodesOperation().execute(
                new SearchNodesOperation.Input(NodePath.local("/sdcard"), "  ")));
    }
}
