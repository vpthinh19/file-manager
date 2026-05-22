package com.vpt.filemanager.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.vpt.filemanager.R;
import com.vpt.filemanager.workspace.WorkspaceAction;

/**
 * Long-press / "More" menu for a file node. Each action is rendered as icon + label; entries
 * supplied via {@link #setDisabledActions(Set)} are greyed out and non-clickable so the host can
 * surface why an action is unavailable for the current selection (e.g. RENAME for multi-select).
 */
public final class NodeActionsBottomSheet extends BottomSheetDialogFragment {
    public enum Action {
        COPY(R.string.action_copy, R.drawable.ic_copy, WorkspaceAction.COPY),
        MOVE(R.string.action_move, R.drawable.ic_move, WorkspaceAction.MOVE),
        DELETE(R.string.action_delete, R.drawable.ic_trash, WorkspaceAction.DELETE),
        RENAME(R.string.action_rename, R.drawable.ic_rename, WorkspaceAction.RENAME),
        TOOLS(R.string.action_tools, R.drawable.ic_tools, WorkspaceAction.TOOLS),
        COMPRESS(R.string.action_compress, R.drawable.ic_compress, WorkspaceAction.COMPRESS),
        PROPERTIES(R.string.properties, R.drawable.ic_properties, WorkspaceAction.PROPERTIES),
        SHARE(R.string.action_share, R.drawable.ic_share, WorkspaceAction.SHARE),
        OPEN_WITH(R.string.action_open_with, R.drawable.ic_open_with, WorkspaceAction.OPEN_WITH),
        BOOKMARK(R.string.action_bookmark, R.drawable.ic_bookmark, WorkspaceAction.BOOKMARK);

        @StringRes public final int labelRes;
        @DrawableRes public final int iconRes;
        public final WorkspaceAction workspaceAction;

        Action(@StringRes int labelRes, @DrawableRes int iconRes,
               WorkspaceAction workspaceAction) {
            this.labelRes = labelRes;
            this.iconRes = iconRes;
            this.workspaceAction = workspaceAction;
        }
    }

    public interface Listener {
        void onActionSelected(Action action);
    }

    private static final String ARG_TITLE = "title";
    private static final float DISABLED_ALPHA = 0.38f;

    private Listener listener;
    private EnumSet<WorkspaceAction> disabled = EnumSet.noneOf(WorkspaceAction.class);

    public static NodeActionsBottomSheet newInstance(String title) {
        NodeActionsBottomSheet sheet = new NodeActionsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        sheet.setArguments(args);
        return sheet;
    }

    public NodeActionsBottomSheet setListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    /** Mark the given workspace actions as visible-but-disabled (greyed + non-clickable). */
    public NodeActionsBottomSheet setDisabledActions(@Nullable Set<WorkspaceAction> actions) {
        this.disabled = (actions == null || actions.isEmpty())
                ? EnumSet.noneOf(WorkspaceAction.class)
                : EnumSet.copyOf(actions);
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_node_actions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView title = view.findViewById(R.id.tv_action_title);
        String text = getArguments() == null ? "" : getArguments().getString(ARG_TITLE, "");
        title.setText(text);

        RecyclerView rv = view.findViewById(R.id.rv_actions);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        List<Action> actions = new ArrayList<>();
        for (Action action : Action.values()) {
            actions.add(action);
        }
        rv.setAdapter(new ActionsAdapter(actions, disabled, action -> {
            if (listener != null) {
                listener.onActionSelected(action);
            }
            dismissAllowingStateLoss();
        }));
    }

    private static final class ActionsAdapter extends RecyclerView.Adapter<ActionViewHolder> {
        private final List<Action> actions;
        private final EnumSet<WorkspaceAction> disabled;
        private final Listener listener;

        ActionsAdapter(List<Action> actions, EnumSet<WorkspaceAction> disabled, Listener listener) {
            this.actions = actions;
            this.disabled = disabled;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ActionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_node_action, parent, false);
            return new ActionViewHolder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull ActionViewHolder holder, int position) {
            Action action = actions.get(position);
            holder.icon.setImageResource(action.iconRes);
            holder.label.setText(action.labelRes);
            boolean isDisabled = disabled.contains(action.workspaceAction);
            float alpha = isDisabled ? DISABLED_ALPHA : 1f;
            holder.icon.setAlpha(alpha);
            holder.label.setAlpha(alpha);
            holder.itemView.setEnabled(!isDisabled);
            holder.itemView.setOnClickListener(isDisabled
                    ? null
                    : v -> listener.onActionSelected(action));
        }

        @Override
        public int getItemCount() {
            return actions.size();
        }
    }

    private static final class ActionViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        ActionViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_action_icon);
            label = itemView.findViewById(R.id.tv_action_label);
        }
    }
}
