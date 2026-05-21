# Automated Test Plan

## Test Layers

### JVM Unit Tests

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Coverage:
- Pure value logic: `FilePath`, permission parsing, path/category/resolver mapping.
- Asset contracts that do not need Android runtime, especially TextMate index paths and theme includes.

Current syntax tests:
- `SyntaxAssetContractTest`: validates `languages.json` wrapper shape, grammar asset paths, and theme include paths.
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
- `SyntaxSetupInstrumentedTest`: loads bundled grammars and light/dark TextMate themes using real Android assets.
- `TextEditorActivityInstrumentedTest`: launches `TextEditorActivity` with a real local Java file.

## Next Tests To Add

### Browser Navigation

- Launch `MainActivity`, grant `MANAGE_EXTERNAL_STORAGE`, create a test tree under `/sdcard/Download/file-manager-test`.
- Verify root pane loads, folder tap navigates, Up returns, Back/Forward stacks update.
- Verify dual-pane active pane switching does not mutate the inactive pane path.

### File Operations

- Create file/folder, rename, delete to Trash, restore, empty Trash.
- Assert both pane UI and filesystem state after each operation.
- Include conflict cases: create duplicate, restore when destination exists, rename to invalid separator.

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

### Performance Gates

- Cold start: capture `am start -W` and fail if `TotalTime` exceeds an agreed threshold.
- Frame stats: capture `dumpsys gfxinfo framestats` for root folder and large-folder scroll.
- Memory: capture `dumpsys meminfo` before and after opening editor/archive; flag sustained growth.

## Practical CI Split

- Every PR: JVM unit tests.
- Android emulator smoke: editor package + browser navigation + CRUD happy path.
- Nightly: full connected tests, large folders, archive fixtures, performance snapshots.
