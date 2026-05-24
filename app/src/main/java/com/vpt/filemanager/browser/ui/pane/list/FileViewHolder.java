package com.vpt.filemanager.browser.ui.pane.list;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import com.vpt.filemanager.R;
import com.vpt.filemanager.browser.item.Item;
import com.vpt.filemanager.browser.item.ItemType;
import com.vpt.filemanager.browser.ui.format.ByteSize;
import com.vpt.filemanager.browser.ui.pane.icon.FileIconView;
import com.vpt.filemanager.browser.ui.pane.icon.IconCategory;
import com.bumptech.glide.Glide;

public final class FileViewHolder extends RecyclerView.ViewHolder {
    private static final int DATE_CACHE_SIZE = 128;
    private static final DateFormat DATE_FORMAT =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final Map<Long, String> FORMATTED_DATES =
            new LinkedHashMap<>(DATE_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                    return size() > DATE_CACHE_SIZE;
                }
            };

    private final FileIconView icon;
    private final ImageView mediaThumbnail;
    private final TextView name;
    private final TextView meta;
    private Item current;

    public FileViewHolder(@NonNull View itemView, @NonNull FileListAdapter.Listener listener) {
        super(itemView);
        icon = itemView.findViewById(R.id.file_icon);
        mediaThumbnail = itemView.findViewById(R.id.media_thumbnail);
        name = itemView.findViewById(R.id.tv_name);
        meta = itemView.findViewById(R.id.tv_meta);
        itemView.setOnClickListener(view -> {
            if (current != null) {
                listener.onFileClicked(current);
            }
        });
        itemView.setOnLongClickListener(view -> {
            if (current == null) {
                return false;
            }
            listener.onFileLongClicked(current);
            return true;
        });
    }

    public void bind(@NonNull Item entry, boolean selected) {
        current = entry;
        Glide.with(itemView).clear(mediaThumbnail);
        mediaThumbnail.setVisibility(View.GONE);
        icon.setVisibility(View.VISIBLE);
        if (isThumbnailEntry(entry)) {
            icon.bindCategory(IconCategory.ofFileName(entry.name()));
            mediaThumbnail.setVisibility(View.VISIBLE);
            Glide.with(itemView)
                    .load(new File(entry.localPath()))
                    .centerCrop()
                    .thumbnail(0.25f)
                    .dontAnimate()
                    .into(mediaThumbnail);
        } else if (entry.isFolder()) {
            icon.bindFolder();
        } else {
            icon.bindCategory(IconCategory.ofFileName(entry.name()));
        }
        name.setText(entry.name());
        meta.setText(formatMeta(entry));
        bindSelection(selected);
    }

    public void clear() {
        current = null;
        Glide.with(itemView).clear(mediaThumbnail);
    }

    public void bindSelection(boolean selected) {
        itemView.setSelected(selected);
    }

    private static String formatMeta(Item entry) {
        String size = entry.isFolder() ? "Folder" : ByteSize.format(entry.size());
        if (entry.modifiedAt() <= 0) {
            return size;
        }
        long minute = entry.modifiedAt() / 60000L;
        String date = FORMATTED_DATES.get(minute);
        if (date == null) {
            date = DATE_FORMAT.format(new java.util.Date(minute * 60000L));
            FORMATTED_DATES.put(minute, date);
        }
        return size + " / " + date;
    }

    private static boolean isThumbnailEntry(Item entry) {
        return entry.localPathOrNull() != null
                && (entry.behavior() == ItemType.IMAGE || entry.behavior() == ItemType.VIDEO);
    }
}
