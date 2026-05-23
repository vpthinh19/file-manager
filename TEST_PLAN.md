# Automated Test Plan

## Test Layers

### JVM Unit Tests

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Coverage:
- Pure value logic: `FilePath`, permission parsing, path/category/resolver mapping.
- Asset contracts that do not need Android runtime, especially lazy TextMate catalog grammar/configuration paths and dependency scopes.
- Operation mutation contracts: mutating operations report precise `MutationResult` branches.
- Workspace command dispatch: rule enforcement at execution time and mutation publication.

Current syntax tests:
- `SyntaxAssetContractTest`: validates catalog grammar/configuration paths, dependency scopes, and expanded language coverage.
- `LanguageResolverTest`: validates filename to TextMate scope mapping.

### Android Instrumentation Tests

Run all connected tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Run editor-only tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.package=com.vpt.filemanager.editor" --console=plain
```

Coverage:
- Android-only contracts: `AssetManager`, Hilt wiring, Activity launch, Room, FileProvider, and runtime permissions.
- UI smoke flows that used to require manual client exploration.

Current syntax tests:
- `SyntaxSetupInstrumentedTest`: lazily loads selected catalog grammars and light/dark TextMate themes using real Android assets.
- `TextEditorActivityInstrumentedTest`: launches `TextEditorActivity` with a real local Java file.
- `DualPaneHostInstrumentedTest`: launches `MainActivity` with all-files access and verifies both panes plus the command bar instantiate through Hilt.

## Next Tests To Add

### Browser Navigation

- Extend the existing launch smoke test by creating a test tree under `/sdcard/Download/file-manager-test`.
- Verify root pane loads, folder tap navigates, Up returns, Back/Forward stacks update.
- Verify dual-pane active pane switching does not mutate the inactive pane path.
- Verify `root:///` renders Storage, Trash, and Bookmarks and Up from a top-level branch reaches it.
- Open the same directory in both panes, mutate it once, and verify both render the reconciled snapshot.

### File Operations

- Create file/folder, rename, delete to Trash, restore, empty Trash.
- Assert both pane UI and filesystem state after each operation.
- Include conflict cases: create duplicate, restore when destination exists, rename to invalid separator.
- Change a visible local folder from outside the app and verify `FileObserver` invalidation refreshes it.

### Source Contracts

- `LocalSource`: list/stat/read/write with temporary folders.
- `ArchiveSource`: open zip, list nested folders, read text entry, reject write.
- `BookmarkSource`: skip broken targets, passthrough children as `file://`.
- `TrashSource`: missing blob handling, restore metadata.

### Editor

- Open UTF-8 file, save, assert file bytes changed.
- Open read-only or large file, assert Save disabled/read-only.
- Open binary-looking file, assert confirmation dialog appears.
- Open `.java`, `.json`, `.xml`, `.md`, `.kt`, assert TextMate registry can create expected language.
- Verify undo/redo enabled states and dirty marker return to the last savepoint.
- Existing smoke coverage verifies the find-in-file surface opens; extend it to verify result navigation and count using sora `EditorSearcher`.

### Media Viewer And Player

- Load image thumbnails and a full image through Glide; verify row recycling does not display a stale thumbnail.
- Open local video and audio nodes through their openers; verify Media3 play, pause, seek, release, and lifecycle restore.
- Verify unsupported/archive-backed media is disabled or materialized through an explicit operation before playback.

### Search And Archive Editing

- Search file/folder results render through a temporary `search://` virtual node and refresh after mutation.
- Stage archive add/rename/delete changes in an overlay, commit through libarchive, and verify the original file is replaced only after successful validation.
- Modify the archive file externally during a staged edit and verify commit enters conflict handling rather than overwriting.

### Performance Gates

- Cold start: capture `am start -W` and fail if `TotalTime` exceeds an agreed threshold.
- Frame stats: capture `dumpsys gfxinfo framestats` for root folder and large-folder scroll.
- Memory: capture `dumpsys meminfo` before and after opening editor/archive; flag sustained growth.

## Practical CI Split

- Every PR: JVM unit tests.
- Android emulator smoke: editor package + browser navigation + CRUD happy path.
- Nightly: full connected tests, large folders, archive fixtures, performance snapshots.
