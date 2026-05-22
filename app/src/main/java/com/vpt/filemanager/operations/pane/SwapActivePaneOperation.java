package com.vpt.filemanager.operations.pane;

import androidx.annotation.NonNull;


/**
 * Choose the opposite pane id in a two-pane workspace.
 */
public final class SwapActivePaneOperation {
    public Output execute(Input input) {
        String next = input.leftPaneId.equals(input.activePaneId)
                ? input.rightPaneId
                : input.leftPaneId;
        return new Output(next);
    }

    public static final class Input {
        @NonNull public final String leftPaneId;
        @NonNull public final String rightPaneId;
        @NonNull public final String activePaneId;

        public Input(@NonNull String leftPaneId,
                     @NonNull String rightPaneId,
                     @NonNull String activePaneId) {
            this.leftPaneId = leftPaneId;
            this.rightPaneId = rightPaneId;
            this.activePaneId = activePaneId;
        }
    }

    public static final class Output {
        @NonNull public final String activePaneId;

        private Output(@NonNull String activePaneId) {
            this.activePaneId = activePaneId;
        }
    }
}
