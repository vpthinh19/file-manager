# Test Plan

## Unit Tests

- `navigation/LocationTest`: location serialization, archive mount and root boundary.
- `entry/SortOptionTest`: visible entry ordering.
- `storage/LocalStorageAdapterTest`: primitive physical file mutations.
- `ui/state/StateViewModelTest`: independent pane history and full-screen back behavior.
- `ui/content/editor/*Test`: TextMate catalog/assets and language selection.

## Instrumentation Tests

- `ui/pane/PaneInstrumentedTest`: dual-pane render, root navigation boundary, search refresh,
  rename prefill, full-screen editor opening and disabled action policy.
- `ui/content/editor/SyntaxSetupInstrumentedTest`: packaged TextMate grammar setup.
- `storage/archive/ArchiveAccessInstrumentedTest`: archive browse, transactional mutation,
  extraction/import, archive-to-archive folder transfer and editor save-back.

## Verification Command

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest --no-daemon
```

## Deferred Capability

- Provide two-container rollback if a move between mounted archives succeeds on import but its
  source deletion later fails.
- Replace the bundled Android archive bridge with an owned JNI bridge built from official
  libarchive source.
