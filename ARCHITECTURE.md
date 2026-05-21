# File Manager — Architecture

Tài liệu kiến trúc chính thức sau Phase R refactor (cập nhật 2026-05-21, R-10 done). Mọi PR/commit
refactor phải tuân thủ spec này. Khi cần thay đổi, update file này trước rồi mới sửa code.

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
       ArchiveSource                   ArchiveOpener
       TrashSource                     ExternalOpener
       BookmarkSource                  (ImageOpener Phase 2D)
       (CloudSource v2)                (VideoOpener Phase 2D)
       (SmbSource v2)                  (AudioOpener Phase 2D)
                                       (PdfOpener Phase 3)
```

- **Trục 1 — NodeSource**: ẩn cách đọc node. UI không quan tâm path là local hay archive entry —
  chỉ gọi `node.children()` / `node.openRead()`. Thêm nguồn mới = +1 NodeSource impl.
- **Trục 2 — FileOpener**: quyết định hành vi khi user click file. Mỗi loại file đứng ngang hàng
  (text/image/video/audio/archive đều là 1 FileOpener). Thêm file type mới = +1 FileOpener impl.

## Scheme hệ thống (4 hoạt động)

| Scheme | NodeSource | Opaque/Transparent | Behavior |
|--------|------------|--------------------|---------|
| `file://` | LocalSource | direct | Local filesystem qua java.nio.Files |
| `archive://{file-uri}/{path}` | ArchiveSource | opaque read-only | Children scheme=archive, click navigate vào sâu hơn |
| `trash://{entry-uuid}/{name}` | TrashSource | opaque | List từ Room TrashDao, click = select (không open) |
| `bookmark:///` | BookmarkSource | **transparent passthrough** | Root scheme=bookmark, children resolve qua LocalSource → scheme=file (click navigate target thật) |

## Cấu trúc package (11 functional + root)

```
com.vpt.filemanager
├── FileManagerApp.java                ← @HiltAndroidApp entry, Timber init
├── MainActivity.java                  ← single Activity host, drawer + dual pane
├── DrawerHost.java                    ← interface cho MainActivity expose drawer ops
├── DrawerActionHandler.java           ← Material drawer item dispatch
│
├── node/                              ← DOM ảo CORE
│   ├── VirtualNode.java               ← immutable final class, identity = FilePath
│   ├── FilePath.java                  ← value object (scheme + path + authority)
│   ├── FileCategory.java              ← enum: TEXT/IMAGE/VIDEO/AUDIO/ARCHIVE/CODE/DOC/UNKNOWN
│   ├── NodeFactory.java               ← path → VirtualNode (dispatch 4 scheme + asArchiveRoot)
│   ├── NodeException.java             ← extends AppException
│   ├── ParentSource.java              ← package-private sentinel cho `..` row
│   └── source/                        ← Strategy về NGUỒN (singleton mỗi impl)
│       ├── NodeSource.java            ← interface (resolve/list/read + write API)
│       ├── LocalSource.java           ← java.nio.Files, 1-syscall stat via BasicFileAttributes
│       ├── ArchiveSource.java         ← ZipFile cache ConcurrentHashMap, read-only writes
│       ├── TrashSource.java           ← Room TrashDao + FS blob lookup, defensive Files.exists guard
│       └── BookmarkSource.java        ← Room BookmarkDao + LocalSource passthrough, broken targets skip
│
├── opener/                            ← Strategy về HÀNH VI click
│   ├── FileOpener.java                ← interface (canOpen + onOpen)
│   ├── OpenerRegistry.java            ← match priority, @Nullable return
│   ├── OpenContext.java               ← bag: Context + FragmentManager + PaneNavigator
│   ├── PaneNavigator.java             ← interface navigateTo(FilePath)
│   ├── TextOpener.java                ← launch TextEditorActivity (TEXT + CODE category)
│   ├── ArchiveOpener.java             ← re-wrap với ArchiveSource via NodeFactory.asArchiveRoot
│   └── ExternalOpener.java            ← ACTION_VIEW + FileProvider URI fallback
│
├── operations/                        ← CRUD facade
│   ├── FileOps.java                   ← create/delete/rename — gate via supportsWrite
│   ├── TrashOps.java                  ← moveToTrash/restore/emptyAll/deleteForever (R-7b)
│   └── BookmarkOps.java               ← add/remove/removeByPath (R-7b path-only API)
│
├── browser/                           ← UI dual-pane navigate
│   ├── DualPaneHostFragment.java      ← host 2 pane + toolbar + bottom bar + bus observe
│   ├── PaneController.java            ← interface giữa Pane → Host
│   ├── PaneFragment.java              ← per-pane RecyclerView, tap-as-select trong trash scheme
│   ├── PaneViewModel.java             ← state holder: path/selection/mode/history; runActionAndEmit
│   ├── FileListAdapter.java           ← DiffUtil typed VirtualNode
│   ├── FileViewHolder.java            ← bind(VirtualNode) + isParent check
│   ├── FileIconView.java              ← icon + thumbnail composite
│   ├── IconCategory.java              ← enum file category → drawable
│   ├── IconMapper.java                ← FilePath/category → drawable resource
│   ├── NodeActionsBottomSheet.java    ← 10-action sheet (rename/share/delete/bookmark/...)
│   ├── OpenAsDialogFragment.java      ← UNKNOWN extension fallback dialog
│   ├── SortBottomSheet.java           ← sort order picker (name/size/date/type asc/desc)
│   ├── controller/
│   │   ├── ToolbarController.java         ← title + subtitle + overflow (Empty Trash R-7b)
│   │   ├── BottomBarController.java       ← 5-button non-mode bar (Back/Forward/Add/Swap/Up)
│   │   ├── SelectionBarController.java    ← 5-button mode bar context-aware (more/restore/bookmark-remove)
│   │   ├── InsetsController.java          ← edge-to-edge inset distribution
│   │   └── BackPressController.java       ← R-7a back exits selection mode first
│   ├── action/
│   │   ├── CreateAction.java              ← Create folder/file flow + conflict resolve
│   │   └── ShareAction.java               ← multi-select share via ACTION_SEND_MULTIPLE
│   └── dialog/
│       ├── ConflictDialog.java            ← name conflict (overwrite/skip/rename)
│       ├── CreateItemDialog.java          ← folder vs file type picker
│       └── NameInputDialog.java           ← single-line input với validation
│
├── editor/                            ← Text editor + TextMate syntax (R-9)
│   ├── TextEditorActivity.java        ← @AndroidEntryPoint, sora-editor wrapper, FileTreeChangeBus emit on save
│   ├── LanguageResolver.java          ← filename → TextMate scope name (case-insensitive)
│   ├── SyntaxThemeProvider.java       ← UI_MODE_NIGHT_MASK → quietlight/darcula asset path
│   └── SyntaxSetup.java               ← idempotent registry init: FileProvider + Grammars + Theme
│
├── properties/                        ← Properties dialog
│   ├── PropertiesDialogFragment.java  ← Material M3 properties + free space stats
│   └── FolderSizeCalculator.java      ← iterative DFS walk via VirtualNode tree
│
├── support/                           ← Flat utility, KHÔNG sub-package
│   ├── AppExecutors.java              ← io / main / single-thread bg pools
│   ├── MainThreadExecutor.java        ← Handler(Looper.getMainLooper()) wrapper
│   ├── Prefs.java                     ← SharedPreferences (sort order, scope mode)
│   ├── StorageScope.java              ← ROOT_PATH + canGoUp helpers
│   ├── PathUtils.java                 ← name/parent/normalize helpers
│   ├── ByteSize.java                  ← format bytes → KB/MB/GB
│   ├── MimeTypes.java                 ← extension → MIME string
│   ├── NameDeconflict.java            ← suggest non-conflicting name "name (2).ext"
│   ├── ThemeUtils.java                ← attr resolver (?colorSurface etc)
│   ├── TimberInit.java                ← lazy Timber.plant
│   ├── FileTreeChangeBus.java         ← @Singleton MutableLiveData<Long> blind counter (R-8)
│   ├── LiveEvent.java                 ← SingleLiveEvent for one-shot toast/error
│   ├── ErrorPresenter.java            ← Throwable → user message
│   ├── FileCategory? (NO — in node/)
│   ├── SortOrder.java                 ← enum comparator builder folder-first
│   └── PosixPermission.java           ← POSIX bit parser for Properties dialog
│
├── error/
│   ├── AppException.java              ← root checked exception
│   ├── ArchiveException.java          ← archive-specific (extends AppException)
│   └── FileSystemException.java       ← FS-specific
│
├── data/db/                           ← Room v2
│   ├── AppDatabase.java               ← @Database, exportSchema=true
│   ├── AppDatabaseMigrations.java     ← MIGRATION_1_2 (add bookmark_entries)
│   ├── dao/
│   │   ├── TrashDao.java
│   │   └── BookmarkDao.java
│   └── entity/
│       ├── TrashEntryEntity.java
│       └── BookmarkEntryEntity.java
│
└── di/                                ← Hilt modules
    ├── AppModule.java                 ← AppExecutors, Prefs
    └── DatabaseModule.java            ← AppDatabase + TrashDao + BookmarkDao
```

## R-8 Cross-pane reconciliation — FileTreeChangeBus

`support/FileTreeChangeBus` là **Singleton MutableLiveData<Long> counter** — phương án reconciliation
blind: bất kỳ op nào mutate FS (FileOps/TrashOps/BookmarkOps wrappers trong PaneViewModel + 
`TextEditorActivity.save`) gọi `bus.emit()`. `DualPaneHostFragment.observeChangeBus()` observe với
`getViewLifecycleOwner` → callback gọi `leftVm.refresh()` + `rightVm.refresh()`.

Skip-initial-value trick: snapshot `lastSeen` lúc attach → tránh duplicate refresh khi rotate/recreate.

Lý do: 2 pane (hoặc N pane sau này) cùng host + `TextEditorActivity` cross-Activity scope cần signal
process-wide. Bus thay thế "self-refresh" pattern — pane act không tự refresh nữa, bus là single source
of truth.

## Selection mode state machine (R-7a)

`PaneViewModel` tách 2 trục độc lập:
- `selectionMode: LiveData<Boolean>` — flag bật/tắt selection bar
- `selection: LiveData<Set<FilePath>>` — items đã chọn

Cho phép "0 selected" với bar vẫn hiện (user click Deselect xong sẵn sàng tap lại). X button = exit
mode hẳn. Range button = fill convex hull theo index khi >= 2 items.

## Drawer pane routing (R-7b)

Trash + Bookmark KHÔNG dùng Fragment riêng — chúng là NodeSource. Drawer click → active pane
`navigateTo(TRASH_ROOT)` / `(BOOKMARK_ROOT)`. SelectionBarController render context-aware 5-th
button theo `pane.currentPath.scheme()`:
- `file/archive` → ic_more → mở NodeActionsBottomSheet
- `trash` → ic_restore → `vm.restoreSelected()` direct
- `bookmark` → ic_bookmark_remove → `vm.removeBookmarksSelected()` direct

`ToolbarController` overflow menu chứa "Empty Trash" — chỉ visible khi `pane=trash` (defensive
guard trong handler check `currentPath.isTrash()`).

## Syntax highlight pipeline (R-9)

```
TextEditorActivity.onCreate
  ↓
applySyntaxStyling()
  ↓
LanguageResolver.scopeFor(fileName) → scope or null
  ↓
SyntaxSetup.ensureInitialized(ctx)
  ↓ (idempotent — load grammars once + applyTheme)
  AssetsFileResolver → GrammarRegistry.loadGrammars("editor/textmate/languages.json")
  ThemeRegistry.loadTheme(quietlight | darcula theo UI_MODE_NIGHT_MASK)
  ↓
scope != null
  → editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry))
  → editor.setEditorLanguage(TextMateLanguage.create(scope, true))
scope == null OR setup fail
  → fallback EmptyLanguage + SchemeDarcula (graceful, no crash)
```

Assets: 21 grammars + 2 themes + languages.json (~1.46 MB) trong `app/src/main/assets/editor/textmate/`.

## Mental map — đụng vấn đề gì, đi đâu

| Vấn đề | File / package |
|---|---|
| Folder không list được | `node/source/LocalSource.list()` |
| File `.zip` không mở | `node/NodeFactory` (dispatch) + `node/source/ArchiveSource` |
| Trash list rỗng | `node/source/TrashSource.list()` → `data/db/dao/TrashDao` |
| Bookmark list rỗng | `node/source/BookmarkSource.list()` → `data/db/dao/BookmarkDao` |
| Xóa file không vào Trash | `operations/TrashOps.moveToTrash()` |
| Click file mở sai app | `opener/OpenerRegistry.openerFor()` + `opener/*Opener.canOpen()` |
| Sort không hoạt động | `browser/PaneViewModel.setSort()` + `support/Prefs.sortOrder()` |
| Drawer Trash crash | `MainActivity.onTrashSelected` → `dual.navigateActivePaneTo(TRASH_ROOT)` |
| Syntax highlight sai language | `editor/LanguageResolver.scopeFor()` |
| Add bookmark | `browser/NodeActionsBottomSheet` Action.BOOKMARK → `SelectionBarController` → `PaneViewModel.addBookmarkSelected` |
| Cross-pane stale sau delete | `support/FileTreeChangeBus.emit()` chưa được gọi ở op site nào đó |
| Empty Trash menu thiếu | `browser/controller/ToolbarController.applyContextualOverflow()` |

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
| `browser/PaneViewModel` | Catch `Throwable` từ background task → post qua `events: LiveEvent<String>`. Per-item batch loop catch + count failed. |
| `browser/DualPaneHostFragment` | Observe `events` → `ErrorPresenter.present()` → Toast / Dialog. |
| `editor/TextEditorActivity` | Catch `Exception` (KHÔNG Throwable — Error bubble up cho crash report) trong applySyntaxStyling → fallback EmptyLanguage. |

## Performance — VirtualNode footprint

Mỗi VirtualNode ~48 B (header + 5 field). 10k node = 0.7-1.6 MB.

| Tối ưu | Áp dụng ở đâu | Payoff |
|---|---|---|
| Singleton `NodeSource` qua Hilt `@Singleton` | `di/AppModule` (auto by @Singleton ctor) | Zero waste — mọi node share 1 ref source |
| `FilePath` immutable | `node/FilePath` | Safe share giữa back/forward stack |
| `BasicFileAttributes` 1-syscall stat | `LocalSource.buildNode()` | 3× nhanh hơn isDirectory + length + lastModified |
| Back/forward stack hold `FilePath`, không hold `VirtualNode` | `browser/PaneViewModel` | Tránh retain children tree |
| `ArrayList(initialCapacity=64)` hint | `LocalSource.list()` | Tránh array resize 7-8 lần cho folder typical |
| Lazy folder size compute | `properties/FolderSizeCalculator` | Recursive size chỉ khi user mở Properties |
| Cancel pending IO load on rapid navigate | `PaneViewModel.load()` | Stale Content emit blocked qua path-guard |

KHÔNG làm (overengineer): SoA (Struct of Arrays), Paging 3, Flyweight pool MIME string, native heap,
String.intern blind (đã thử R-2, R-8 confirmed no-op vì không assign — removed).

## Phase R refactor — 10/10 DONE

| Phase | Mục tiêu | Status | Commit |
|---|---|---|---|
| R-1 | Skeleton `node/` (VirtualNode + NodeSource + NodeException) | DONE | `abea5a2` |
| R-2 | LocalSource + ArchiveSource + NodeFactory impl | DONE | `1b12cdc` |
| R-3 | `opener/` package + 3 FileOpener (Text/Archive/External) | DONE | `8570c8c` |
| R-4 | NodeSource write API + Bookmark schema + `operations/` | DONE | `9c31746` + `7995489` |
| R-5a | Split `DualPaneHostFragment` → 5 Controllers + 2 Actions + 3 Dialogs | DONE | `84a2b35` |
| R-5b | Migrate PaneViewModel + Adapter → VirtualNode + Opener + Ops | DONE | `7ab8f99` |
| R-6 | Drop legacy: 26 files purged | DONE | `f5d6542` |
| R-7a | Selection mode redesign (5-button + range + mode flag split) | DONE | `d836b62` + `feec3a7` |
| R-7b | Trash + Bookmark via pane (delete TrashFragment) + restore button | DONE | `b5adc49` |
| R-8 | FileTreeChangeBus + Bookmark Add wire + UiState.Roots cleanup + intern no-op removal | DONE | `7ff3e44` |
| R-9 | TextMate syntax highlight (21 grammars + 2 themes + Resolver/Provider/Setup) | DONE | `4d0d36e` |
| R-10 | Cosmetic repackage (75 file moves) + ARCHITECTURE.md sync | DONE | this commit |

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
- ❌ Không catch `Throwable` ngoại trừ outermost background task (Error subclasses phải bubble)

## Tham chiếu

- Memory `project_architecture_decisions.md` — legacy decisions trước Phase R refactor
- Memory `feedback_priorities.md` — 3 nguyên tắc tối thượng (đọc dễ / hiệu năng / KISS)
- Memory `project_decisions_v1.md` — scope v1 (minSdk 30, MANAGE_EXTERNAL_STORAGE only, no root)
- Memory `project_dom_virtual_concept.md` — concept 2-trục paradigm shift sau R-6
