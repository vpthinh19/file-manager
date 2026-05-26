package com.vpt.filemanager.component.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.vpt.filemanager.R;

import java.util.function.IntConsumer;

/** Two-column action sheet for selected entries. */
public final class SelectionActionsDialogComponent {
    private SelectionActionsDialogComponent() {
    }

    public static void show(Context context, String title, String[] labels, boolean[] enabled,
                            IntConsumer selected) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View sheet = LayoutInflater.from(context).inflate(
                R.layout.bottom_sheet_selection_actions, null, false);
        ((TextView) sheet.findViewById(R.id.selection_actions_title)).setText(title);
        RecyclerView actions = sheet.findViewById(R.id.selection_actions_grid);
        actions.setLayoutManager(new GridLayoutManager(context, 2));
        actions.setAdapter(new ActionAdapter(labels, enabled, index -> {
            dialog.dismiss();
            selected.accept(index);
        }));
        dialog.setContentView(sheet);
        dialog.show();
    }

    private static final class ActionAdapter extends RecyclerView.Adapter<ActionHolder> {
        private final String[] labels;
        private final boolean[] enabled;
        private final IntConsumer selected;

        ActionAdapter(String[] labels, boolean[] enabled, IntConsumer selected) {
            this.labels = labels;
            this.enabled = enabled;
            this.selected = selected;
        }

        @NonNull
        @Override
        public ActionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ActionHolder(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.row_selection_action, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ActionHolder holder, int position) {
            holder.bind(labels[position], enabled[position], () -> selected.accept(position));
        }

        @Override
        public int getItemCount() {
            return labels.length;
        }
    }

    private static final class ActionHolder extends RecyclerView.ViewHolder {
        private final MaterialButton action;

        ActionHolder(View view) {
            super(view);
            action = (MaterialButton) view;
        }

        void bind(String label, boolean enabled, Runnable selected) {
            action.setText(label);
            action.setEnabled(enabled);
            action.setOnClickListener(enabled ? ignored -> selected.run() : null);
        }
    }
}
