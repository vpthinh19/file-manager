package com.vpt.filemanager.browser.constraint;

import java.util.EnumSet;

import com.vpt.filemanager.browser.NodeActionsBottomSheet.Action;
import com.vpt.filemanager.node.FilePath;

/**
 * Pure compute function: given a {@link WorkspaceState} snapshot, returns the set of
 * {@link Action} that should be disabled in the More bottom sheet for the current selection.
 *
 * <p>Stateless, JVM-testable, no Android deps on this path. Single source of truth for which
 * actions are unavailable in which workspace context — replaces the imperative if/else block that
 * lived inside {@code SelectionBarController.computeDisabledActions} (R-7a..R-7b).
 *
 * <p>Rules (evaluation order):
 * <ol>
 *   <li><b>Multi-select</b> ({@code selection.size() > 1}) — disable single-target actions:
 *       RENAME, PROPERTIES, OPEN_WITH, BOOKMARK.</li>
 *   <li><b>Single folder</b> — disable OPEN_WITH (no external viewer for directories).</li>
 *   <li><b>Single file</b> — disable BOOKMARK (v1 decision: bookmarks are folders only).</li>
 *   <li><b>Any archive entry in selection</b> — read-only source: disable RENAME, DELETE, MOVE,
 *       COMPRESS, BOOKMARK.</li>
 *   <li><b>Any non-local entry in selection</b> — OPEN_WITH requires {@code file://} URI via
 *       FileProvider. Archive/trash/bookmark entries cannot resolve, so the handler silently
 *       no-ops (see {@code SelectionBarController.handleAction} OPEN_WITH branch). Disable in UI
 *       to give the user a clear signal instead of a dead tap.</li>
 *   <li><b>Same path in both panes</b> — COPY/MOVE would be a no-op transfer (active==inactive
 *       parent). Disabled in UI so user doesn't tap and receive a "Same folder" toast.</li>
 * </ol>
 *
 * <p>Adding a new rule = adding a new {@code if} block here + a unit test. Do not scatter
 * enforcement across controllers/fragments.
 */
public final class ActionConstraints {
    private ActionConstraints() {
    }

    public static EnumSet<Action> compute(WorkspaceState state) {
        EnumSet<Action> disabled = EnumSet.noneOf(Action.class);

        if (state.selection.size() > 1) {
            disabled.add(Action.RENAME);
            disabled.add(Action.PROPERTIES);
            disabled.add(Action.OPEN_WITH);
            disabled.add(Action.BOOKMARK);
        } else if (state.singleIsFolder != null) {
            if (state.singleIsFolder) {
                disabled.add(Action.OPEN_WITH);
            } else {
                disabled.add(Action.BOOKMARK);
            }
        }

        boolean nonLocalFound = false;
        for (FilePath p : state.selection) {
            if (p.isArchive()) {
                disabled.add(Action.RENAME);
                disabled.add(Action.DELETE);
                disabled.add(Action.MOVE);
                disabled.add(Action.COMPRESS);
                disabled.add(Action.BOOKMARK);
            }
            if (!p.isLocal()) {
                nonLocalFound = true;
            }
        }
        if (nonLocalFound) {
            // Rule 6 (R-11a Codex review): OPEN_WITH requires file:// URI via FileProvider.
            // Archive/trash/bookmark entries cannot resolve, so handler silently no-ops without
            // this rule.
            disabled.add(Action.OPEN_WITH);
        }

        // Rule 5 (R-11a NEW — user request from architecture discussion 2026-05-22):
        // 2 panes on the same path → COPY/MOVE is a no-op transfer. Disable in UI rather than
        // letting the user tap and receive a "Same folder" toast (which is what
        // TransferAction.execute does at runtime). Environment-driven constraint, no special
        // wiring at action site.
        if (state.activePath != null && state.activePath.equals(state.inactivePath)) {
            disabled.add(Action.COPY);
            disabled.add(Action.MOVE);
        }

        return disabled;
    }
}
