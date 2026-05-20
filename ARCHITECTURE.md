# File Manager — Architecture

Tài liệu kiến trúc chính thức sau Phase R refactor (2026-05-20). Mọi PR/commit refactor phải tuân
thủ spec này. Khi cần thay đổi, update file này trước rồi mới sửa code.

## Concept trung tâm: DOM ảo

**Mọi thứ user nhìn thấy là một `VirtualNode` trong cây ảo. Browser chỉ làm 1 việc: chọn node →
render children của node đó.**

Không có `FileSystemProvider`, không có `Repository`, không có `Registry`, không có `domain/port`.
Chỉ có cây node với 2 trục thiết kế **orthogonal**:

```
                       VirtualNode (final class, immutable)
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
       NodeSource (where)              FileOpener (what)
       ───────────────────             ───────────────────
       LocalSource                     TextOpener
       ArchiveSource                   ImageOpener
       TrashSource                     VideoOpener
       BookmarkSource                  AudioOpener
       (CloudSource v2)                ArchiveOpener
       (SmbSource v2)                  ExternalOpener
                                       (PdfOpener Phase 3)
```

- **Trục 1 — NodeSource**: ẩn cách đọc node. UI không quan tâm path là local hay archive entry —
  chỉ gọi `node.children()` / `node.openRead()`. Thêm nguồn mới = +1 NodeSource impl.
- **Trục 2 — FileOpener**: quyết định hành vi khi user click file. Mỗi loại file đứng ngang hàng
  (text/image/video/audio/archive đều là 1 FileOpener). Thêm file type mới = +1 FileOpener impl.

## Cấu trúc package (11 top-level)

```
com.vpt.filemanager
├── FileManagerApp.java                ← Hilt @HiltAndroidApp entry
├── MainActivity.java                  ← single Activity, drawer + content host
│
├── node/                              ← DOM ảo CORE
│   ├── VirtualNode.java               ← immutable final class
│   ├── FilePath.java                  ← value object (scheme + path + authority)
│   ├── FileCategory.java              ← enum: TEXT/IMAGE/VIDEO/AUDIO/ARCHIVE/DOC/UNKNOWN
│   ├── NodeFactory.java               ← path → VirtualNode (intern prefix, chọn source)
│   ├── NodeException.java
│   └── source/                        ← Strategy về NGUỒN (singleton mỗi impl)
│       ├── NodeSource.java            ← interface
│       ├── LocalSource.java
│       ├── ArchiveSource.java
│       ├── TrashSource.java
│       └── BookmarkSource.java
│
├── opener/                            ← Strategy về HÀNH VI click
│   ├── FileOpener.java                ← interface
│   ├── OpenerRegistry.java            ← match canOpen() theo priority
│   ├── OpenContext.java               ← bag: pane reference + activity + fragmentManager
│   ├── TextOpener.java                ← launch TextEditorActivity
│   ├── ArchiveOpener.java             ← re-wrap node với ArchiveSource → navigate
│   ├── ImageOpener.java               ← Phase 2D Glide preview
│   ├── VideoOpener.java               ← Phase 2D Media3
│   ├── AudioOpener.java               ← Phase 2D Media3
│   └── ExternalOpener.java            ← ACTION_VIEW fallback
│
├── operations/                        ← CRUD trên node
│   ├── FileOps.java                   ← create/delete/rename/copy/move
│   ├── TrashOps.java                  ← moveToTrash/restore/empty
│   └── BookmarkOps.java               ← add/remove/reorder
│
├── browser/                           ← UI navigate (dual-pane)
│   ├── BrowserFragment.java           ← host 2 pane + toolbar + bottom bar (~150 LOC)
│   ├── controller/
│   │   ├── ToolbarController.java
│   │   ├── BottomBarController.java
│   │   ├── SelectionBarController.java
│   │   ├── InsetsController.java
│   │   └── BackPressController.java
│   ├── pane/
│   │   ├── PaneFragment.java
│   │   └── PaneViewModel.java         ← state holder: currentNode + selection + history
│   ├── adapter/
│   │   ├── NodeListAdapter.java
│   │   └── NodeViewHolder.java
│   ├── icon/
│   │   ├── NodeIconResolver.java
│   │   └── IconCategory.java
│   ├── dialog/
│   │   ├── CreateItemDialog.java
│   │   ├── NameInputDialog.java
│   │   ├── ConflictDialog.java
│   │   ├── OpenAsDialog.java
│   │   ├── NodeActionsBottomSheet.java
│   │   └── SortBottomSheet.java
│   └── action/
│       ├── CreateAction.java
│       └── ShareAction.java
│
├── editor/                            ← Text editor + syntax highlight
│   ├── TextEditorActivity.java
│   ├── LanguageResolver.java          ← extension → TextMate grammar name
│   └── SyntaxThemeProvider.java
│
├── properties/                        ← Properties dialog
│   ├── PropertiesDialogFragment.java
│   └── FolderSizeCalculator.java
│
├── preview/                           ← (Phase 2D)
│   ├── ImagePreviewFragment.java      ← Glide
│   └── MediaPreviewFragment.java      ← Media3
│
├── theme/
│   └── ThemeUtils.java
│
├── support/                           ← Flat utility, KHÔNG sub-package
│   ├── AppExecutors.java
│   ├── MainThreadExecutor.java
│   ├── Prefs.java
│   ├── StorageScope.java
│   ├── PathUtils.java
│   ├── ByteSize.java
│   ├── MimeTypes.java
│   ├── NameDeconflict.java
│   ├── LiveEvent.java
│   └── ErrorPresenter.java
│
├── error/
│   ├── AppException.java
│   ├── FileSystemException.java
│   └── ArchiveException.java
│
├── data/                              ← Room ONLY
│   ├── AppDatabase.java
│   ├── TrashDao.java
│   ├── TrashEntryEntity.java
│   ├── BookmarkDao.java
│   └── BookmarkEntryEntity.java
│
└── di/                                ← Hilt modules
    ├── AppModule.java                 ← AppExecutors, Prefs
    ├── DataModule.java                ← AppDatabase + DAOs
    ├── NodeModule.java                ← NodeSource impls (singletons)
    └── OpenerModule.java              ← FileOpener registrations
```

## Mental map — đụng vấn đề gì, đi đâu

| Vấn đề | File / package |
|---|---|
| Folder không list được | `node/source/LocalSource.list()` |
| File `.zip` không mở | `node/NodeFactory` (rule) + `node/source/ArchiveSource` |
| Trash list rỗng | `node/source/TrashSource.list()` → `data/TrashDao` |
| Bookmark list rỗng | `node/source/BookmarkSource.list()` → `data/BookmarkDao` |
| Xóa file không vào Trash | `operations/TrashOps.moveToTrash()` |
| Thumbnail không hiện | `browser/adapter/NodeViewHolder.bind()` (Glide call) |
| Click file mở sai app | `opener/OpenerRegistry.openerFor()` + `opener/*Opener.canOpen()` |
| Sort không hoạt động | `browser/pane/PaneViewModel.setSort()` + `support/Prefs` |
| Drawer Trash crash | `MainActivity` route → `pane.navigateTo(trashRoot)` |
| Syntax highlight sai language | `editor/LanguageResolver.forExtension()` |
| Add bookmark | `browser/dialog/NodeActionsBottomSheet` → `operations/BookmarkOps.add()` |

## Naming convention

| Suffix | Ý nghĩa |
|---|---|
| `*Source` | NodeSource impl (where data lives) |
| `*Opener` | FileOpener impl (what to do on click) |
| `*Ops` | Operations layer — CRUD logic |
| `*Controller` | UI region orchestrator, plain Java + manual release |
| `*Action` | Command pattern — Fragment-scoped user action |
| `*Dialog` / `*BottomSheet` | DialogFragment / BottomSheetDialogFragment |
| `*Fragment` / `*Activity` | Android UI components |
| `*ViewModel` | Lifecycle-aware UI state holder |
| `*Resolver` | Strategy selector (LanguageResolver, ...) |
| `*Dao` | Room DAO |
| `*Entity` | Room Entity |

Package naming: `lower-case singular` (`opener` không `openers`, `operation` thì `operations`
plural vì gom nhiều ops).

## Exception handling — quy tắc đi xuyên suốt

| Layer | Quy tắc |
|---|---|
| `node/source/*` | Catch low-level `IOException` / `ZipException` / Room exception → wrap `NodeException` với message user-friendly. |
| `operations/*` | Validate precondition (scheme matches, target writable, source exists) → throw `NodeException`. KHÔNG swallow. |
| `opener/*` | Catch `ActivityNotFoundException` (Intent fail) → bubble lên `OpenContext` callback. |
| `browser/pane/PaneViewModel` | Catch `Throwable` từ background task → post qua `events: LiveEvent<String>`. |
| `browser/BrowserFragment` | Observe `events` → `ErrorPresenter.present()` → Toast / Dialog. |
| **Edge case must pass** | Folder permission denied, zip corrupt, name conflict (create/rename), restore conflict, empty selection action, parent navigate beyond root, archive write attempt, file >5MB editor warn. |

## Performance — VirtualNode footprint

Mỗi VirtualNode ~48 B (header + 5 field). 10k node = 0.7-1.6 MB.

| Tối ưu | Áp dụng ở đâu | Payoff |
|---|---|---|
| Singleton `NodeSource` qua Hilt `@Singleton` | `di/NodeModule` | Zero waste — mọi node share 1 ref source |
| `String.intern()` cho path prefix | `LocalSource.list()` | ~50% giảm string dup trong folder lớn |
| `FilePath` immutable | `node/FilePath` | Safe share giữa back/forward stack |
| Back/forward stack hold `FilePath`, không hold `VirtualNode` | `browser/pane/PaneViewModel` | Tránh retain children tree |
| `ArrayList(initialCapacity)` hint | `node/source/*` list impl | Tránh array resize 7-8 lần |

KHÔNG làm (overengineer): SoA (Struct of Arrays), Paging 3, Flyweight pool, native heap.

## 8 Phase refactor plan

| Phase | Mục tiêu | Status |
|---|---|---|
| R-1 | Skeleton `node/` (VirtualNode + NodeSource + NodeException) — không xóa code cũ | **DONE** |
| R-2 | LocalSource + ArchiveSource + NodeFactory impl (UI wire deferred to R-5) | **DONE** |
| R-3 | `opener/` package + 3 FileOpener (Text/Archive/External); Image/Video/Audio defer Phase 2D | **DONE** |
| R-4 | NodeSource write API + Bookmark schema + `operations/` (FileOps/TrashOps/BookmarkOps) | **DONE** |
| R-5 | Split `DualPaneHostFragment` → BrowserFragment + Controllers + Actions | pending |
| R-6 | Drop legacy FileSystemProvider / Registry / Repository / use cases | pending |
| R-7 | Feature-first repackage + flatten `support/` | pending |
| R-8 | Bookmark UI + Syntax highlight wire + node-aware polish | pending |

Mỗi phase = 1 commit, BUILD SUCCESSFUL + smoke test bắt buộc. Bisectable.

## Quy tắc KHÔNG làm (vĩnh viễn)

- ❌ Không root mode / `libsu` / `Runtime.exec("su")`
- ❌ Không Coroutines / Flow (Java native)
- ❌ Không Jetpack Compose
- ❌ Không `Result<T>` wrapper (sync throwing)
- ❌ Không `layoutAnimation` cho RecyclerView path-change
- ❌ Không `recreate()` cho permission round-trip
- ❌ Không `FileSystemProvider` abstraction (replaced by NodeSource Strategy)
- ❌ Không `Repository` pass-through (replaced by direct NodeSource/Ops call)
- ❌ Không Use Case layer cho action 1-step pass-through
- ❌ Không hardcode color trong layout/Java — dùng `@color/...`
- ❌ Không `new Thread()` / AsyncTask — dùng `AppExecutors`

## Tham chiếu

- Memory `project_architecture_decisions.md` — legacy decisions trước Phase R refactor này
- Memory `feedback_priorities.md` — 3 nguyên tắc tối thượng (đọc dễ / hiệu năng / KISS)
- Memory `project_decisions_v1.md` — scope v1 (minSdk 30, MANAGE_EXTERNAL_STORAGE only, no root)
