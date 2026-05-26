# Refactor Plan — file-manager

> Approved by user on branch `refactor/architecture`. All naming and scope
> decisions in this document are confirmed.

## ⚑ STATUS (read this first)

This is the **current** roadmap — not a stale doc. The old `ARCHITECTURE.md` and
`TEST_PLAN.md` were deleted because they described the pre-refactor
`Location`/`Redirect`/`ui` design and would mislead.

| Phase | What | Commit | State |
| --- | --- | --- | --- |
| 0 | This plan | `6a295f1` | ✅ done |
| 1 | `navigation/Location` → `core/path/Path` (+ method renames) | `7fa17cf` | ✅ done |
| 2 | `Storage` interface + `StorageRegistry` + 5 impls + `StorageModule` | `0f43d7d` | ✅ done |
| 3 | `Handler` + `HandlerRegistry` + sealed `HandlerResult` + 5 handlers | `554d12f` | ✅ done |
| 4 | Slim `PathResolver`; delete `NavigationResult`/`Redirect`/`replaceResolvedLocation` | `12342b8` | ✅ done |
| 5 | move `content/` → `core/detect/`; drop archive sniffing; `EXTERNAL`→`OTHER` | `e94ab52` | ✅ done |
| 6 | `Entry`/`EntryType` → `core/entry/`; type = PARENT/FOLDER/FILE only | — | ⏳ pending |
| 7 | `ui/` → `component/`; `settings/`+`ui/format/` → `core/`; `FileOperations` → `Operations` | — | ⏳ pending |
| 8 | `Operations` facade uses `StorageRegistry` polymorphically | — | ⏳ pending |
| 9 | verify `PaneFragment`/`ContentHostComponent` on new flow (mostly done in P4) | — | ⏳ pending |
| 10 | update tests + `assembleDebug` + `testDebugUnitTest` clean | — | ⏳ pending |
| 11 | fix runtime so app launches (missing mipmap icon, Hilt, theme — see §12) | — | ⏳ pending |

Build compiles clean (`./gradlew :app:compileDebugJavaWithJavac --no-daemon`)
through Phase 5. Runtime is **not** yet validated. Old adapters
(`LocalStorageAdapter`, `ArchiveAccess`, `BookmarkCollection`, `TrashCollection`,
`FileOperations`) still exist; the new `Storage` impls wrap them. Do not push;
the user pushes.

## 1. Why we refactor

The current code was implemented by Codex around a single resolver
(`navigation/LocationResolver`) that knows about every backend, and a single
ad-hoc detector (`content/ContentDetector`) that has side effects on archive
inspection. Naming is also off: the user's mental model uses **Path**, **Storage**,
and per-content-type **Handlers**, while Codex chose **Location**, **Adapter**,
and inline `if/else` inside one resolver.

Symptoms:

1. Reading `LocationResolver.open(...)` requires holding the entire flow in your
   head — folder branch, archive-mount branch, search branch, trash branch,
   content-detect branch, plus the `Redirect` hack that lets the resolver tell
   the ViewModel "actually re-navigate me to this other path." Five
   responsibilities in 127 lines.
2. `NavigationResult.Redirect` is a code smell. It exists only because there is
   no dedicated archive entry point that can transparently return entries when
   an archive file is opened.
3. Adding any new backend (e.g., FTP, WebDAV, Drive) currently means editing
   `LocationResolver`, `LocalStorageAdapter`, `Operations`, `ContentDetector`,
   and every component that switches on `isArchiveEntry()`.
4. The package `ui/` is misleading — every class in there is an **active
   component** that decides what to do, not a passive view.
5. The runtime is broken at the most basic level: even the launcher icon does
   not load (no `mipmap-*/`, no adaptive icon). This suggests Codex skipped
   several Android boilerplate parts that need verification.

## 2. Target principles

- **Component-driven architecture** unchanged from current intent: each
  component is independent, observes `StateViewModel`, and pushes actions back.
- **Strategy pattern for backends**: every backend (local FS, archive,
  trash, bookmarks, search, future remote) implements one `Storage` interface.
- **Strategy pattern for content**: every renderable kind of file
  (text, image, audio, video, fallback intent) is one `Handler` class.
- **Registry pattern**: `StorageRegistry.storageFor(Path)` and
  `HandlerRegistry.handlerFor(ContentType)` replace `if/else` chains.
- **Facade pattern**: `operation/Operations` stays as the single entry point
  for component-issued mutations. Internally it asks the registry, not the
  backend directly.
- **Observer pattern**: keep `LiveData` in `StateViewModel`. No change.

## 3. Target package layout

Three top-level packages: `core/` (domain + infrastructure), `storage/` and
`handler/` (Strategy implementations), `operation/` (Facade), `component/`
(active UI actors).

```
com.vpt.filemanager
│
├── core/                                ← domain types + infrastructure
│   ├── path/
│   │   ├── Path.java                    ← virtual address (was navigation/Location)
│   │   └── PathResolver.java            ← thin resolver
│   ├── entry/
│   │   ├── Entry.java                   ← ephemeral pane row
│   │   ├── EntryType.java               ← PARENT, FOLDER, FILE only
│   │   └── SortOption.java
│   ├── detect/
│   │   ├── ContentType.java             ← TEXT, IMAGE, AUDIO, VIDEO, OTHER
│   │   └── ContentDetector.java         ← pure byte-magic (no archive sniffing)
│   ├── settings/
│   │   └── UserPreferences.java
│   ├── format/
│   │   ├── ByteSize.java
│   │   └── MimeTypes.java
│   ├── di/
│   │   ├── DatabaseModule.java
│   │   ├── StorageModule.java           ← NEW: multibinds Storage implementations
│   │   └── HandlerModule.java           ← NEW: multibinds Handler implementations
│   ├── error/
│   │   ├── ArchiveOperationException.java
│   │   ├── DocumentConflictException.java
│   │   ├── FileOperationException.java
│   │   └── NameConflictException.java
│   └── threading/
│       ├── AppExecutors.java
│       └── MainThreadExecutor.java
│
├── storage/                             ← Strategy: every backend implements Storage
│   ├── Storage.java                     ← interface
│   ├── StorageRegistry.java             ← picks backend by Path
│   ├── local/
│   │   └── LocalStorage.java            ← java.io.File (was LocalStorageAdapter)
│   ├── archive/
│   │   ├── ArchiveStorage.java          ← libarchive (was ArchiveAccess)
│   │   └── ArchiveFormat.java
│   ├── trash/
│   │   ├── TrashStorage.java            ← virtual; persisted via Room
│   │   ├── TrashRecord.java
│   │   └── TrashDao.java
│   ├── bookmarks/
│   │   ├── BookmarkStorage.java         ← virtual
│   │   ├── BookmarkRecord.java
│   │   └── BookmarkDao.java
│   ├── search/
│   │   └── SearchStorage.java           ← virtual scan over LocalStorage
│   └── persistence/
│       └── AppDatabase.java
│
├── handler/                             ← Strategy: one class per ContentType
│   ├── Handler.java                     ← HandlerResult handle(File, Path)
│   ├── HandlerRegistry.java
│   ├── HandlerResult.java               ← sealed: Entries | OpenContent | LaunchIntent
│   ├── TextHandler.java
│   ├── ImageHandler.java
│   ├── AudioHandler.java
│   ├── VideoHandler.java
│   └── OtherHandler.java
│
├── operation/
│   └── Operations.java                  ← Facade (was FileOperations)
│
├── component/                           ← renamed from ui/ — active actors
│   ├── state/StateViewModel.java
│   ├── pane/
│   │   ├── PaneFragment.java
│   │   ├── PaneState.java
│   │   ├── PaneId.java
│   │   ├── EntryAdapter.java
│   │   ├── EntryViewHolder.java
│   │   └── icon/FileIconView.java + IconCategory.java + IconMapper.java
│   ├── topbar/TopBarComponent.java
│   ├── bottombar/BottomBarComponent.java
│   ├── drawer/DrawerComponent.java
│   ├── dialog/InputDialogs.java
│   ├── content/                         ← full-screen content host
│   │   ├── ContentHostComponent.java
│   │   ├── OpenedContent.java
│   │   ├── FullScreenContent.java
│   │   ├── ImageContentFragment.java
│   │   ├── MediaContentFragment.java
│   │   └── editor/TextEditorFragment.java + DocumentSession + DocumentService + Syntax*
│   └── main/MainActivity.java
│
└── FileManagerApp.java
```

## 4. Core interfaces

### 4.1 Path (was Location)

Same shape, renamed methods:

```java
package com.vpt.filemanager.core.path;

public final class Path {
  enum Scheme { STORAGE, TRASH, BOOKMARKS, SEARCH }

  static Path storageRoot();
  static Path storage(String virtualPath);
  static Path archive(String container, String inner);    // storage:/a.zip!/b
  static Path trash();
  static Path bookmarks();
  static Path search(String scope, String query);

  Scheme scheme();
  boolean isStorage();
  boolean isTrash();
  boolean isBookmarks();
  boolean isSearch();
  boolean isInsideArchive();          // was isArchiveEntry()
  boolean isStorageRoot();

  String storagePath();
  String archiveInnerPath();
  String query();

  Path parent();
  Path child(String name);

  String serialize();
  static Path parse(String);
}
```

`isArchiveMount()` is dropped — the `StorageRegistry` decides routing.

### 4.2 Storage

```java
package com.vpt.filemanager.storage;

public interface Storage {
  /** Quick check the registry uses to pick the right backend for a path. */
  boolean handles(Path path);

  /** A path that lists children (folder, archive folder, mounted archive root,
   *  trash, bookmarks, or a search result). */
  boolean isContainer(Path path) throws FileOperationException;

  /** Children visible inside a container. */
  List<Entry> list(Path path) throws FileOperationException;

  /** Produce a real java.io.File the handler can read.
   *  Local → returns the file as-is.
   *  Archive → extracts to cache.
   *  Future remote → downloads to cache. */
  File materialize(Path path) throws FileOperationException;

  boolean canWrite(Path path);
  void create(Path parent, String name, boolean folder) throws FileOperationException;
  void rename(Entry entry, String newName) throws FileOperationException;
  void delete(List<Entry> entries) throws FileOperationException;

  /** Copy/move WITHIN the same storage. Cross-storage transfer is orchestrated
   *  by Operations using materialize + create. */
  void copyInternal(Entry source, Path destinationParent, String name) throws FileOperationException;
  void moveInternal(Entry source, Path destinationParent, String name) throws FileOperationException;

  InputStream openRead(Entry entry) throws FileOperationException;
  OutputStream openWrite(Entry entry) throws FileOperationException;
}
```

Concrete implementations:

| Class                | `handles()` returns true when                  | Notes |
|----------------------|-----------------------------------------------|-------|
| `LocalStorage`       | `p.isStorage() && !p.isInsideArchive()`        | wraps current `LocalStorageAdapter` |
| `ArchiveStorage`     | `p.isInsideArchive() || isContainerFile(p)`    | handles both physical archive file and mounted view |
| `TrashStorage`       | `p.isTrash()`                                  | wraps `TrashCollection` |
| `BookmarkStorage`    | `p.isBookmarks()`                              | wraps `BookmarkCollection` |
| `SearchStorage`      | `p.isSearch()`                                 | wraps current search scan |

### 4.3 Handler

```java
package com.vpt.filemanager.handler;

public interface Handler {
  HandlerResult handle(File materialized, Path source) throws FileOperationException;
}

public sealed interface HandlerResult
    permits HandlerResult.Entries, HandlerResult.OpenContent, HandlerResult.LaunchIntent {

  record Entries(List<Entry> entries) implements HandlerResult {}
  record OpenContent(Path source, String localPath, String displayName,
                     ContentType type, boolean readOnly) implements HandlerResult {}
  record LaunchIntent(Path source, String localPath, String mimeType) implements HandlerResult {}
}
```

`FolderHandler` and `ArchiveHandler` are **not** separate classes — because
`Storage.isContainer()` + `Storage.list()` already cover those cases before
the handler step is reached.

### 4.4 PathResolver — the thin core

```java
package com.vpt.filemanager.core.path;

public final class PathResolver {
  private final StorageRegistry storages;
  private final HandlerRegistry handlers;
  private final ContentDetector detector;

  public HandlerResult open(Path path) throws FileOperationException {
    Storage storage = storages.storageFor(path);
    if (storage.isContainer(path)) {
      return new HandlerResult.Entries(storage.list(path));
    }
    File materialized = storage.materialize(path);
    ContentType type = detector.detect(materialized);
    return handlers.handlerFor(type).handle(materialized, path);
  }
}
```

~10 lines. No `Redirect`. No content-specific branches.

## 5. Flow comparison

### Today

```
tap zip file
  → state.navigate(pane, file path)
  → PaneFragment.load()
  → LocationResolver.open()
       reads file, ContentDetector.isArchive() → opens archive once
       returns Redirect to storage:/file.zip!/
  → state.replaceResolvedLocation(...)
  → PaneFragment observes change → load() again
  → LocationResolver.open()
       sees isArchiveEntry(), archives.list() → opens archive a second time
  → state.showEntries()
```

Two opens of the archive, two passes through resolver, special state-mutation
method to swap path without history.

### After

```
tap zip file
  → state.navigate(pane, file path)
  → PaneFragment.load()
  → PathResolver.open()
       storage = ArchiveStorage (registry chose it because Path is a zip)
       storage.isContainer(path) → true
       storage.list(path) → entries of the archive root
       returns Entries(...)
  → state.showEntries()
```

One archive open. No redirect. No state hack. Component still drives.

## 6. Entry simplification

Today's `EntryType` enum encodes storage backend (LOCAL_*, ARCHIVE_*,
BOOKMARK_FOLDER, TRASH_*) in addition to folder/file shape. That information
is duplicated with the Path scheme. After refactor:

```java
enum EntryType { PARENT, FOLDER, FILE }
```

The backend a row belongs to is determined by `entry.path()` and looked up
via `StorageRegistry`. Iconography logic stays in `IconMapper` based on file
name and `entry.isFolder()`.

Factories:

```java
Entry parent(Path parent);
Entry folder(Path target, String displayName, long modifiedAt);
Entry folder(Path target, String displayName, String localPath, long modifiedAt);
Entry file(Path target, String displayName, long size, long modifiedAt);
Entry file(Path target, String displayName, String localPath, long size, long modifiedAt);
Entry trashed(Path target, String recordId, String displayName, String storedPath,
              boolean folder, long size, long deletedAt);
```

`localPath` and `recordId` remain optional fields used by storages that need
to round-trip back to a physical file or DB row.

## 7. Operations becomes thin

```java
package com.vpt.filemanager.operation;

class Operations {
  Operations(StorageRegistry registry) { ... }

  boolean canWrite(Path p)               { return registry.storageFor(p).canWrite(p); }
  void create(Path parent, String name, boolean folder) { registry.storageFor(parent).create(...); }
  void rename(Entry e, String name)      { registry.storageFor(e.path()).rename(e, name); }
  void delete(List<Entry> entries)       { /* group by storage, delegate */ }

  void transfer(List<Entry> entries, Path destination, boolean move) {
    Storage destinationStorage = registry.storageFor(destination);
    if (!destinationStorage.canWrite(destination)) throw new ...;
    for (Entry source : entries) {
      Storage sourceStorage = registry.storageFor(source.path());
      String uniqueName = unique(destinationStorage, destination, source.name());
      if (sourceStorage == destinationStorage) {
        if (move) sourceStorage.moveInternal(source, destination, uniqueName);
        else      sourceStorage.copyInternal(source, destination, uniqueName);
      } else {
        File staged = sourceStorage.materialize(source.path());
        destinationStorage.create(...);   // write staged content
        if (move) sourceStorage.delete(List.of(source));
      }
    }
  }

  void emptyTrash() / bookmark() / removeBookmarks() / restore() {
    // delegate to TrashStorage / BookmarkStorage
  }
}
```

No `isArchiveEntry()` switches. No knowledge of which backend is which.

## 8. Component layer renames

Behavior is identical; only package paths change:

| Was                                              | Becomes                                                |
|--------------------------------------------------|--------------------------------------------------------|
| `com.vpt.filemanager.ui.state.StateViewModel`    | `com.vpt.filemanager.component.state.StateViewModel`   |
| `com.vpt.filemanager.ui.pane.*`                  | `com.vpt.filemanager.component.pane.*`                 |
| `com.vpt.filemanager.ui.pane.icon.*`             | `com.vpt.filemanager.component.pane.icon.*`            |
| `com.vpt.filemanager.ui.topbar.*`                | `com.vpt.filemanager.component.topbar.*`               |
| `com.vpt.filemanager.ui.bottombar.*`             | `com.vpt.filemanager.component.bottombar.*`            |
| `com.vpt.filemanager.ui.drawer.*`                | `com.vpt.filemanager.component.drawer.*`               |
| `com.vpt.filemanager.ui.content.*`               | `com.vpt.filemanager.component.content.*`              |
| `com.vpt.filemanager.ui.dialog.*`                | `com.vpt.filemanager.component.dialog.*`               |
| `com.vpt.filemanager.ui.main.MainActivity`       | `com.vpt.filemanager.component.main.MainActivity`      |
| `com.vpt.filemanager.ui.format.*`                | `com.vpt.filemanager.core.format.*`                    |

`AndroidManifest.xml`'s `.ui.main.MainActivity` becomes
`.component.main.MainActivity`.

## 9. State methods cleaned up

`StateViewModel.replaceResolvedLocation(...)` is **removed**. It existed only
for the Redirect mechanism. Everything else in `StateViewModel` keeps its
current shape; only `Location` types change to `Path`.

## 10. Migration order (Phases)

Each phase compiles before starting the next. Tests run after phases 2, 4, 7, 10.

| Phase | Scope                                                                                  |
|-------|----------------------------------------------------------------------------------------|
| 0     | This plan written ✅                                                                    |
| 1     | Move `navigation/Location` → `core/path/Path` with method renames                       |
| 2     | `storage/Storage` interface + `StorageRegistry` + 5 implementations                     |
| 3     | `handler/Handler` interface + registry + 5 handlers + sealed `HandlerResult`            |
| 4     | `core/path/PathResolver` rewritten + `Redirect` removed                                 |
| 5     | Move `content/ContentDetector` → `core/detect/`, drop archive sniffing                  |
| 6     | `core/entry/Entry` + `EntryType` simplified to PARENT/FOLDER/FILE                       |
| 7     | Rename `ui/` → `component/`, `FileOperations` → `Operations`, move format → `core/format/` |
| 8     | `Operations` refactored onto StorageRegistry polymorphically                            |
| 9     | `PaneFragment` & `ContentHostComponent` on new flow                                     |
| 10    | Tests updated + `./gradlew :app:assembleDebug` clean                                    |
| 11    | Diagnose runtime: launcher icon, Hilt init, manifest, permission flow                   |

## 11. Files / symbols that disappear

- `app/src/main/java/com/vpt/filemanager/navigation/` (whole package)
- `app/src/main/java/com/vpt/filemanager/content/` (moved to `core/detect/`)
- `app/src/main/java/com/vpt/filemanager/ui/` (renamed to `component/`)
- `app/src/main/java/com/vpt/filemanager/entry/` (moved to `core/entry/`)
- `app/src/main/java/com/vpt/filemanager/settings/` (moved to `core/settings/`)
- `NavigationResult.Redirect` record
- `StateViewModel.replaceResolvedLocation(...)`
- `ContentDetector.isArchive(File)` method (sniffing moves into `ArchiveStorage`)
- Old prefixed `EntryType` values: `LOCAL_FOLDER`, `LOCAL_FILE`,
  `ARCHIVE_FOLDER`, `ARCHIVE_FILE`, `BOOKMARK_FOLDER`, `TRASH_FOLDER`,
  `TRASH_FILE` (collapsed to `PARENT`, `FOLDER`, `FILE`)

## 12. Suspected current-runtime bugs to fix in Phase 11

These caused the user's "app doesn't even launch / icon doesn't load" symptom.

1. **No launcher icon (mipmap / adaptive)** — `AndroidManifest.xml` sets
   `android:icon="@drawable/ic_launcher"`, but `ic_launcher.xml` is a plain
   108dp 2-path vector with no adaptive-icon foreground/background layers, and
   no `mipmap-*/` folder exists. On Android 8+ this means launchers either fall
   back to a default icon or skip rendering entirely. **Fix**: add
   `res/mipmap-anydpi-v26/ic_launcher.xml` (adaptive-icon XML) plus
   `res/drawable/ic_launcher_foreground.xml` and `res/values/colors.xml`
   `ic_launcher_background`; or at minimum a `mipmap-mdpi…xxxhdpi` raster set.
2. **`PaneFragment.onViewCreated` references `binding.paneRoot`** — verify
   that view id exists in `res/layout/fragment_pane.xml`. If absent, add it or
   substitute `binding.getRoot()`.
3. **`StateViewModel` reads `UserPreferences` synchronously in constructor** —
   confirm `UserPreferences` is `@Singleton` in DI and SharedPreferences load
   is non-blocking enough for the main thread.
4. **`MANAGE_EXTERNAL_STORAGE` permission flow** — `MainActivity.install(...)`
   is only called when permission already granted; on first-grant the
   `settingsLauncher` callback may race with `onResume`. Verify a single
   install path.
5. **`FileObserver` lifecycle** — on a non-storage path (trash/bookmarks),
   `installObserver(...)` returns without nulling old observer if already
   `null`; reverify after rename.
6. **`FileManagerApp` / Hilt init** — verify it has `@HiltAndroidApp` and that
   Hilt graph actually compiles after package renames (annotation processor
   refresh).
7. **Theme `Theme.FileManager.NoActionBar`** — verify it exists in
   `res/values/themes.xml` (referenced from manifest `activity` element).

## 13. Tests

| Test                                              | After                                                   |
|---------------------------------------------------|---------------------------------------------------------|
| `navigation/LocationTest`                         | `core/path/PathTest`                                    |
| `storage/LocalStorageAdapterTest`                 | `storage/local/LocalStorageTest`                        |
| `ui/state/StateViewModelTest`                     | `component/state/StateViewModelTest`                    |
| `entry/SortOptionTest`                            | `core/entry/SortOptionTest`                             |
| `androidTest/.../ArchiveAccessInstrumentedTest`   | `androidTest/storage/archive/ArchiveStorageInstrumentedTest` |
| `androidTest/.../PaneInstrumentedTest`            | `androidTest/component/pane/PaneInstrumentedTest`       |
| `ui/content/editor/LanguageResolverTest`          | `component/content/editor/LanguageResolverTest`         |
| `ui/content/editor/SyntaxAssetContractTest`      | `component/content/editor/SyntaxAssetContractTest`      |
| `androidTest/.../SyntaxSetupInstrumentedTest`    | `androidTest/component/content/editor/SyntaxSetupInstrumentedTest` |

New tests to add:

- `StorageRegistryTest` — verifies the right Storage is picked for each Path scheme.
- `HandlerRegistryTest` — verifies the right Handler is picked for each ContentType.
- `PathResolverTest` — fake StorageRegistry + HandlerRegistry, asserts
  Entries / OpenContent / LaunchIntent results without touching the filesystem.

## 14. Open follow-ups (not in this refactor)

- A real remote `Storage` implementation (FTP/WebDAV/cloud) — proves the
  abstraction is correct.
- Background transfer service (WorkManager) for long copies.
- Plugin-style `Handler` registration (so adding a `PdfHandler` is one class).
- Replace third-party libarchive Java bridge with an owned JNI binding
  (note already in current `ARCHITECTURE.md`).
- Proper app icon design (vector or raster set, with adaptive layers).

---

Last updated: 2026-05-25 — refactor in progress on branch `refactor/architecture`.
