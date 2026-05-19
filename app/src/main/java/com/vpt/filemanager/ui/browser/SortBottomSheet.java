package com.vpt.filemanager.ui.browser;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.vpt.filemanager.R;
import com.vpt.filemanager.domain.model.SortOrder;

/**
 * Bottom-sheet sort picker. Mapping of {@link SortOrder} ↔ RadioButton id lives here (UI concern)
 * so the domain enum stays Android-free and JUnit-testable.
 */
public final class SortBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_CURRENT = "current";

    public interface Listener {
        void onSortPicked(@NonNull SortOrder order);
    }

    private Listener listener;

    @NonNull
    public static SortBottomSheet newInstance(@NonNull SortOrder current) {
        SortBottomSheet f = new SortBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_CURRENT, current.name());
        f.setArguments(args);
        return f;
    }

    public SortBottomSheet setListener(Listener listener) {
        this.listener = listener;
        return this;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_sort, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RadioGroup group = view.findViewById(R.id.sort_group);
        SortOrder current = SortOrder.safeValueOf(requireArguments().getString(ARG_CURRENT));
        group.check(idFor(current));
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (listener != null) {
                listener.onSortPicked(orderFor(checkedId));
            }
            dismiss();
        });
    }

    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    private static int idFor(@NonNull SortOrder order) {
        switch (order) {
            case NAME_ASC:   return R.id.sort_name_asc;
            case NAME_DESC:  return R.id.sort_name_desc;
            case SIZE_DESC:  return R.id.sort_size_desc;
            case SIZE_ASC:   return R.id.sort_size_asc;
            case DATE_DESC:  return R.id.sort_date_desc;
            case DATE_ASC:   return R.id.sort_date_asc;
            case TYPE:       return R.id.sort_type;
            default:         return R.id.sort_name_asc;
        }
    }

    @NonNull
    private static SortOrder orderFor(int id) {
        if (id == R.id.sort_name_asc)   return SortOrder.NAME_ASC;
        if (id == R.id.sort_name_desc)  return SortOrder.NAME_DESC;
        if (id == R.id.sort_size_desc)  return SortOrder.SIZE_DESC;
        if (id == R.id.sort_size_asc)   return SortOrder.SIZE_ASC;
        if (id == R.id.sort_date_desc)  return SortOrder.DATE_DESC;
        if (id == R.id.sort_date_asc)   return SortOrder.DATE_ASC;
        if (id == R.id.sort_type)       return SortOrder.TYPE;
        return SortOrder.DEFAULT;
    }
}
