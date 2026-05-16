# Abstract — Android File Manager

> Mô tả chức năng và cách giải quyết ở mức tổng quan.
> Đối tượng đọc: Product owner, người mới onboard, người review scope.
> Dành cho dev cần chi tiết implementation, đọc `specification.md`.

---

## 1. Vision

Một ứng dụng quản lý file Android **mạnh ngang MT Manager cho user không root**, tập trung vào ba điểm khác biệt:

1. **Properties dialog đầy đủ** — không chỉ size & date mà cả permission POSIX (rwxr-xr-x), uid/gid, hash MD5/SHA-1/SHA-256.
2. **Archive là folder ảo** — mở file nén như mở folder, xem/sửa/xóa item bên trong một cách trong suốt.
3. **Editor có syntax highlight** — đọc và sửa code, log, config ngay trong app, không cần app riêng.

Không chạy theo cloud, không AI, không subscription. Một tool dày, ổn định, dùng được offline 100%.

## 2. Triết lý thiết kế

- **Local-first, offline-first.** Không gửi gì lên server. Không cần internet.
- **Pluggable filesystem.** Local FS và Archive FS là hai backend cùng implement một interface. Network/Cloud có thể cắm vào sau mà không refactor.
- **Predictable & cancellable.** Mọi tác vụ dài (copy hàng GB, extract archive, hash file lớn) đều có progress và có thể hủy.
- **An toàn dữ liệu mặc định.** Delete đi vào trash trước; rename atomic; copy verify size sau khi xong; archive edit không phá file gốc cho đến khi re-pack thành công.
- **Không cần root.** Mọi thao tác chỉ dùng API public + `MANAGE_EXTERNAL_STORAGE`. Project loại bỏ vĩnh viễn mọi tính năng cần root để tránh phụ thuộc shell `su` và đơn giản codebase.

## 3. Phạm vi

### Trong scope v1

- Browse, copy, move, rename, delete, create file/folder
- Multi-select + batch operation
- Sort, filter, search (theo tên)
- Bookmarks / Quick Access
- Properties dialog (4 tabs: General, Permissions, Hashes, Preview)
- Trash custom (per-storage-root)
- Archive virtual filesystem (ZIP/TAR/GZ/BZ2/XZ read+write; 7Z/CPIO/AR read-only); ZIP có password
- Viewer ảnh / video / audio (format native Android)
- Text editor có syntax highlight (sora-editor)
- Dual-pane trên màn hình rộng
- Material You + Predictive Back

### Ngoài scope (có thể mở rộng v2+)

- Network FS (FTP/SFTP/SMB/WebDAV)
- Cloud (Drive/Dropbox/OneDrive)
- Snapshot / versioning
- Format media Tier 2+ (TIFF, SVG, RAR, codec hiếm qua libVLC)
- AI tagging, OCR, content search

### Không bao giờ làm

- **Root mode.** Không gọi `su`, không tích hợp Magisk/libsu. Quyết định cứng: project chỉ dành cho user không root.
- **Telemetry/analytics nội bộ.** Không thu thập dữ liệu user.
- **In-app ads / subscription.** OSS, MIT.

## 4. Tính năng và cách giải quyết

### 4.1 Browse thư mục

**Vấn đề:** liệt kê file trong folder, hiển thị icon/thumbnail, scroll mượt với folder hàng nghìn file.

**Giải pháp:** `RecyclerView` với `ListAdapter` + `DiffUtil`. Folder load qua `FileSystemProvider.list(path)` chạy trên background executor, kết quả publish qua `LiveData<List<FileNode>>`. Thumbnail ảnh/video load bất đồng bộ qua Glide với cache. Folder lớn (> 1000 entry) load theo trang để giữ ANR-free.

### 4.2 File operations (copy/move/rename/delete)

**Vấn đề:** thao tác file có thể dài (copy GB), có thể fail giữa chừng, cần progress + cancel + rollback.

**Giải pháp:**
- Mỗi operation là một **Command object** (`CopyOperation`, `MoveOperation`, `DeleteOperation`...) implement interface chung với `execute()`, `cancel()`, `progress()`.
- Operation lớn (> 5 giây dự kiến) chạy trong **foreground service** với notification có nút Cancel.
- Copy/Move giữa các provider khác nhau (Local ↔ Archive) tự động fall back sang strategy stream-based.
- Move trong cùng filesystem dùng `rename()` atomic; khác filesystem → copy + delete với verify.
- Delete đi qua **Trash** trừ khi user chọn "Delete permanently".

### 4.3 Properties dialog

**Vấn đề:** hiển thị thông tin file đầy đủ, kể cả POSIX permission/uid/gid mà Java API không expose trực tiếp.

**Giải pháp:** 4 tabs lazy-load:
- **General** — tên, path, MIME, size (human-readable + raw bytes), số file/folder con (cho folder).
- **Permissions** — `android.system.Os.stat(path)` trả về `StructStat`; format mode bits thành `rwxr-xr-x`; lookup tên user/group qua `Os.getpwuid` / `Os.getgrgid` nếu có.
- **Hashes** — MD5, SHA-1, SHA-256 tính lazy khi user mở tab (file lớn có progress).
- **Preview** — embed viewer tương ứng với MIME type (ảnh thumbnail, text snippet, audio waveform...).

File không stat được (thiếu permission, broken symlink) → hiển thị "Unavailable" thay vì crash.

### 4.4 Trash

**Vấn đề:** user xóa nhầm; cần khôi phục được; không tốn double space; tự dọn theo thời gian.

**Giải pháp:** Layout giống XDG Trash spec, **per-storage-root** (mỗi storage có trash riêng để move trong cùng partition):

```
{storageRoot}/.AppTrash/
  files/<uuid>/<originalName>      # nội dung (rename giữ unique)
  info/<uuid>.json                  # metadata: originalPath, deletedAt, sizeBytes
```

Move trong cùng partition → atomic, không tốn space. Khác partition → copy + delete. **Auto-cleanup** qua WorkManager job chạy hàng ngày, xóa entry > N ngày (cấu hình được, default 30 ngày).

Trash có UI riêng (`TrashFragment`) liệt kê item, hỗ trợ Restore (về vị trí gốc) và Delete forever.

### 4.5 Archive virtual filesystem

**Vấn đề:** mở `.zip` như mở folder, navigate sâu vào, sửa file bên trong, không phá archive gốc nếu fail.

**Giải pháp:** Implement `ArchiveFileSystemProvider extends FileSystemProvider`:
- Khi mở archive: stream → parse central directory → build cây `ArchiveNode` (Composite pattern) trong RAM.
- Browse: trả về sub-node theo path ảo (vd `archive.zip/folder/file.txt`).
- Đọc file bên trong: stream `InputStream` từ entry, decompress on-demand. Cache giải nén trong `cacheDir` cho file > 1MB.
- Sửa file: extract sang temp → user edit → trên save, đánh dấu entry dirty.
- Đóng archive: nếu có entry dirty → re-pack toàn bộ archive vào temp file → atomic move đè lên archive gốc (an toàn nếu fail giữa chừng).
- ZIP có password: hỏi password qua dialog, cache trong session (không persist).

Format hỗ trợ: ZIP, TAR, TAR.GZ, TAR.BZ2, TAR.XZ (read+write); 7Z, CPIO, AR (read-only). RAR **không** hỗ trợ trong v1.

### 4.6 Viewer ảnh / video / audio

**Vấn đề:** xem nội dung media phổ biến mà không gọi app khác.

**Giải pháp:**
- **Ảnh** — Glide cho ảnh thường, SubsamplingScaleImageView cho ảnh lớn (cần tile rendering). Format: JPEG, PNG, WebP, HEIC, AVIF, GIF (tận dụng `ImageDecoder` của Android 11+).
- **Video** — Media3 (ExoPlayer) với UI player tiêu chuẩn (PlayerView). Format: MP4/WebM/MKV và codec hệ thống support.
- **Audio** — Media3 dạng compact UI (album art + controls + seekbar). Format: MP3, AAC, Opus, Vorbis, FLAC.

Format không support → button "Open with..." chuyển sang `Intent.ACTION_VIEW` cho app khác.

### 4.7 Text editor

**Vấn đề:** xem và sửa file text-based (txt, md, source code, config, log, sql, json, xml, yaml...) với syntax highlight và hiệu năng tốt cho file lớn.

**Giải pháp:** Embed **sora-editor** (LGPL) — editor mạnh nhất hệ sinh thái Android:
- TextMate grammars (hàng trăm ngôn ngữ qua bundle phổ biến).
- Tree-sitter cho ngôn ngữ chính (Java, Python, JS, Rust, Go, C/C++, ...).
- Search/replace với regex.
- Auto-detect encoding (juniversalchardet), manual override.
- File > 10MB: mở chế độ read-only với cảnh báo (sora-editor có giới hạn).
- File không có grammar match → mở dạng plain text.

### 4.8 Search

**Vấn đề:** tìm file trong folder hiện tại hoặc recursive trong subtree.

**Giải pháp:**
- **In-folder search** — live filter `RecyclerView` bằng substring match trên tên (instant).
- **Recursive search** — chạy trên IO executor, walk cây file, match wildcard/regex, stream kết quả ra `LiveData<List<FileNode>>` (kết quả incremental hiển thị ngay, không chờ xong). Có thể cancel.

V1 chỉ search theo **tên**. Content search (grep) là v2.

### 4.9 Multi-select + batch operations

**Vấn đề:** chọn nhiều file rồi thao tác cùng lúc.

**Giải pháp:** Long-press vào item → vào "selection mode" → Toolbar đổi thành ActionMode hiển thị số đã chọn + actions (copy, move, delete, share, properties). Tap thêm/bớt item; back để thoát.

### 4.10 Dual-pane (signature MT Manager)

**Vấn đề:** trên màn hình rộng, copy giữa hai folder dễ hơn nếu nhìn được cả hai cùng lúc.

**Giải pháp:** Khi width ≥ 600dp hoặc landscape trên tablet → MainActivity show 2 `FileBrowserFragment` cạnh nhau. Mỗi pane có state độc lập (path, scroll, selection). Drag-and-drop giữa hai pane = copy/move.

Trên màn hình hẹp: single pane, swipe-to-pane2 hoặc tab.

### 4.11 Bookmarks / Quick Access

**Vấn đề:** truy cập nhanh các folder hay dùng (Downloads, DCIM, Music, custom).

**Giải pháp:** Drawer trái (Navigation Drawer) chứa:
- Storage roots auto-detect (`/sdcard`, SD card thứ hai...).
- Pre-defined shortcuts (Downloads, DCIM, Movies, Music, Documents).
- User bookmarks (long-press folder → "Add to bookmarks"). Lưu trong Room.

## 5. Đối tượng người dùng

- **Power user Android không root** muốn một file manager mạnh, ổn định, không quảng cáo.
- **Dev / modder** cần đọc-sửa file code, config, log trên thiết bị.
- **User đa phương tiện** xem nhanh ảnh/video/audio mà không cần app riêng cho từng loại.

## 6. Phi mục tiêu

- **Không** cạnh tranh với cloud storage hay đồng bộ đa thiết bị.
- **Không** thay thế gallery / music player chuyên dụng (chỉ xem nhanh).
- **Không** hỗ trợ Wear OS, Android TV, Chrome OS specific UX trong v1.
- **Không** support thiết bị < Android 11.
