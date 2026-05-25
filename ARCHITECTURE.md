# File Manager Architecture

## Principle

The app renders two independent panes from observable locations. It does not retain a virtual
filesystem tree. Opening an entry updates one pane location; resolving that location either
produces a fresh list of rows or one full-screen content component.

```text
UI component -> StateViewModel.navigate(Location)
PaneFragment -> EntryResolver.resolve(Location)
EntryResolver -> handler / storage adapter
result -> StateViewModel entries or full-screen content state
```

Components do not invoke one another. They observe and write shared state through
`StateViewModel`. Technical backends do not hold a ViewModel reference.

## Location Model

`model/Location` is the canonical pane address:

```text
storage:
storage:/Download
storage:/Download/note.txt
storage:/Download/files.zip!/
storage:/Download/files.zip!/docs/readme.txt
trash:
bookmarks:
search:?scope=...&query=...
```

Storage, Trash, Bookmarks and Search are pane roots or collection views. An archive is not a
root. It is a physical file mounted below `storage:` using `!/`, and behaves as a folder when
the archive handler successfully reads it.

`..` is a synthetic `Entry.parent()` pointing to a validated parent `Location`; it is never
passed to physical storage as a path. `storage:`, `trash:` and `bookmarks:` have no parent
row.

## Package Map

```text
com.vpt.filemanager
|-- FileManagerApp.java
|-- model/                    Location, Entry, ContentKind and SortOption
|-- state/                    StateViewModel, pane/history state and full-screen content state
|-- storage/
|   |-- LocalStorageAdapter   ordinary physical filesystem adapter returning java.io.File
|   |-- EntryOperations       guarded write operations requested by UI components
|   |-- index/                bookmark and trash collection indexes
|   `-- persistence/          Room database, records, DAOs and user preferences
|-- resolver/
|   |-- EntryResolver         resolves the current location
|   |-- ContentProbe          content/magic-byte classification at open time
|   `-- ResolveResult         directory/content/location-replacement results
|-- handler/
|   |-- FolderHandler         physical directory listing
|   |-- Text/Image/Media/...  content strategies
|   `-- archive/              libarchive container handling and rewrite transactions
|-- ui/
|   |-- main/                 Android Activity host only
|   |-- pane/                 pane fragment, RecyclerView and row icons
|   |-- topbar/               toolbar and overflow behavior
|   |-- bottombar/            normal/selection/trash/bookmark control modes
|   |-- drawer/               root navigation and theme toggle
|   |-- dialog/               input surfaces owned by commands
|   `-- content/              full-screen image, media and editor components
`-- core/                     DI, errors and executors
```

## State And Components

`StateViewModel` holds observable state only:

- active pane;
- each pane's `Location`, rows, selection, sort option and back/forward history;
- currently resolved full-screen content;
- refresh invalidation sequence.

It does not access files, call libarchive, open Android intents or decide button availability.

Each component owns the logic visible in that component:

- `PaneFragment` resolves and renders its own location, writes selection/navigation, and watches
  its visible physical directory for outside changes.
- `TopBarComponent` renders the active title/stats and owns refresh, search, sort and empty-trash.
- `BottomBarComponent` swaps normal and selection controls, computes enabled states from the
  active/inactive pane, and requests mutations.
- `DrawerComponent` changes the active pane root and owns theme toggle UI.
- `ContentHostComponent` hides the browser surface and installs one full-screen content fragment.

Internal mutations call `StateViewModel.invalidate(...)`. External physical changes are detected
by the watcher in each visible pane. If two panes show the same folder, both observe invalidation
and independently reload it.

## Storage And Resolution

`LocalStorageAdapter` is the only ordinary filesystem gateway. It resolves physical storage
locations to `java.io.File`, lists raw children, and performs direct create/copy/move/delete/read/
write operations. It does not know entries, panes, viewers or archives.

`EntryResolver` interprets a location:

```text
physical directory -> FolderHandler -> List<Entry>
physical file      -> ContentProbe -> appropriate content handler
archive file       -> ArchiveHandler -> mount storage:...!/
mounted archive dir -> ArchiveHandler -> List<Entry>
mounted archive file -> materialize cache file -> content handler
trash/bookmarks/search -> corresponding collection/index query -> List<Entry>
```

`ContentProbe` runs when a file is opened, not while each RecyclerView row is rendered. Archive
acceptance is performed by libarchive format bidding over file content. Image/audio/video/text
are recognized from content signatures or a conservative text heuristic. Extension is a display
hint and an explicit external-document preference for compound formats such as DOCX/XLSX/PPTX,
not proof that arbitrary bytes are an archive.

## Archive Handling

`handler/archive/ArchiveHandler` receives a physical container file plus an inner path. It reads
fresh entries on each resolution and never retains an archive tree. For writable formats it
applies create/rename/delete/import/editor-save operations to a temporary replacement container,
validates it, and atomically replaces the physical original when possible.

RAR and formats without a configured safe writer remain read-only. A file inside an archive is
materialized to cache for editor/image/media viewing; a successful text save writes back through
the archive transaction immediately.

## Full-Screen Content And Back

Text, image, audio and video are fragments rendered in `content_container` inside
`MainActivity`. The two pane states remain alive but are hidden while content is visible.
The viewer/editor has its own toolbar; browser controls and drawer are hidden or locked.

Back follows interaction history, not physical parent inference:

```text
bookmarks: -> storage:/Documents/report.txt -> Back -> bookmarks:
search:... -> storage:/Download/note.txt     -> Back -> search:...
```

For an edited document, Back first requests save or discard. Otherwise full-screen Back restores
the active pane's preceding location. In browser mode Back closes selection/history before
finishing the activity.

## Read The Code

| Task | Start at |
| --- | --- |
| Understand a pane address | `model/Location.java` |
| Understand shared UI state | `state/StateViewModel.java` |
| Trace tapping/opening an item | `ui/pane/PaneFragment.java`, then `resolver/EntryResolver.java` |
| Trace physical file I/O | `storage/LocalStorageAdapter.java` |
| Trace copy/delete/bookmark actions | `ui/bottombar/BottomBarComponent.java`, then `storage/EntryOperations.java` |
| Trace archive behavior | `handler/archive/ArchiveHandler.java` |
| Trace editor/viewer rendering | `ui/content/ContentHostComponent.java` |
