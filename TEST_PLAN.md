# Test Plan

## Unit Tests

- `model/LocationTest`: roots, mounted archive serialization and parent boundary.
- `model/SortOptionTest`: ephemeral row ordering.
- `storage/LocalStorageAdapterTest`: raw `java.io.File` listing and physical mutations.
- `ui/content/editor/*Test`: TextMate catalog/assets and language selection.

## Instrumentation Tests

- `ui/pane/PaneInstrumentedTest`: dual-pane render, storage root boundary, search refresh and
  rename prefill.
- `ui/content/editor/SyntaxSetupInstrumentedTest`: packaged TextMate setup.
- `handler/archive/ArchiveHandlerInstrumentedTest`: ZIP browse, transactional rewrite, import,
  extract and editor-style save-back.

## Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon
```

## Deferred Capability

- transferring directly between two mounted archive containers with two-container rollback;
- replacing the currently bundled Android libarchive bridge with an owned JNI bridge to official
  libarchive source.
