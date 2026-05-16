package com.vpt.filemanager.ui.browser;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

import com.vpt.filemanager.R;

public final class NodeActionsBottomSheet extends BottomSheetDialogFragment {
    public enum Action {
        COPY(R.string.action_copy, "📋"),
        MOVE(R.string.action_move, "✂"),
        DELETE(R.string.action_delete, "🗑"),
        RENAME(R.string.action_rename, "✎"),
        TOOLS(R.string.action_tools, "🔧"),
        COMPRESS(R.string.action_compress, "⤵"),
        PROPERTIES(R.string.properties, "ⓘ"),
        SHARE(R.string.action_share, "⤳"),
        OPEN_WITH(R.string.action_open_with, "✓"),
        BOOKMARK(R.string.action_bookmark, "☆");

        final int labelRes;
        final String iconText;

        Action(int labelRes, String iconText) {
            this.labelRes = labelRes;
            this.iconText = iconText;
        }
    }

    public interface Listener {
        void onActionSelected(Action action);
    }

    private static final String ARG_TITLE = "title";

    private Listener listener;

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
        rv.setAdapter(new ActionsAdapter(actions, action -> {
            if (listener != null) {
                listener.onActionSelected(action);
            }
            dismissAllowingStateLoss();
        }));
    }

    private static final class ActionsAdapter extends RecyclerView.Adapter<ActionViewHolder> {
        private final List<Action> actions;
        private final Listener listener;

        ActionsAdapter(List<Action> actions, Listener listener) {
            this.actions = actions;
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
            holder.icon.setText(action.iconText);
            holder.label.setText(action.labelRes);
            holder.itemView.setOnClickListener(v -> listener.onActionSelected(action));
        }

        @Override
        public int getItemCount() {
            return actions.size();
        }
    }

    private static final class ActionViewHolder extends RecyclerView.ViewHolder {
        final TextView icon;
        final TextView label;

        ActionViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.tv_action_icon);
            label = itemView.findViewById(R.id.tv_action_label);
        }
    }
}
