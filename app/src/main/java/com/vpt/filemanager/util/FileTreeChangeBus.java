package com.vpt.filemanager.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Process-wide signal "đã có operation thay đổi cây thư mục — pane nào quan tâm thì reload".
 *
 * <p>Phase R-8: hai pane (hoặc N pane sau này) cùng host, plus {@code TextEditorActivity} ngoài
 * Fragment scope, đều có thể mutate file system. Trước R-8, mỗi {@link
 * com.vpt.filemanager.browser.PaneViewModel} tự gọi {@code refresh()} sau action → pane còn lại
 * thấy data stale (e.g. delete ở left, right vẫn show entry đã xóa nếu cả hai cùng folder).
 *
 * <p>Pattern: blind counter bus.
 * <ul>
 *   <li><b>Producer</b>: bất kỳ site nào mutate FS (FileOps/TrashOps/BookmarkOps wrappers trong
 *       PaneViewModel, plus {@code TextEditorActivity.save}) gọi {@link #emit()} sau khi success.
 *   <li><b>Consumer</b>: {@link com.vpt.filemanager.browser.DualPaneHostFragment} observe bằng
 *       {@code viewLifecycleOwner} → callback gọi {@code refresh()} trên cả 2 VM.
 * </ul>
 *
 * <p>Counter dùng để mỗi emit là 1 value MỚI (LiveData skip equal values nếu dùng setValue cùng
 * giá trị). Pane đang act vẫn refresh đúng 1 lần — vì action method giờ KHÔNG tự refresh nữa, chỉ
 * emit; observer ở host chạy 1 callback gọi cả 2 VM.
 *
 * <p>Không debounce — {@link MutableLiveData#postValue(Object)} đã coalesce burst trong cùng
 * frame; {@code PaneViewModel.load()} cancel pending IO; tốc độ refresh acceptable không cần
 * throttle. Nếu sau này có refresh storm thực sự (e.g. bulk import 1000 files), thêm 100ms
 * debounce ở observer side là 1 dòng.
 */
@Singleton
public final class FileTreeChangeBus {
    private final MutableLiveData<Long> changes = new MutableLiveData<>(0L);

    @Inject
    public FileTreeChangeBus() {
    }

    public LiveData<Long> changes() {
        return changes;
    }

    /** Notify "tree may have changed". Safe to call from any thread. */
    public void emit() {
        Long current = changes.getValue();
        changes.postValue(current == null ? 1L : current + 1L);
    }
}
