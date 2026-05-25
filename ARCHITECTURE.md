# File Manager Architecture

## Core Flow

The application has no retained virtual filesystem tree and no central command/rule engine.
Each pane observes shared Android UI state and opens exactly one `Location` at a time:

```text
tap an Entry
  -> StateViewModel changes that pane Location
  -> PaneFragment observes it
  -> LocationResolver opens it on a worker thread
  -> pane renders Entries or ContentHost renders an OpenedContent
```

Components share state through `ui.state.StateViewModel`; they do not invoke each other.
Storage and navigation code never holds a ViewModel.

## Read The Source

```text
com.vpt.filemanager
|-- entry/                    visible pane items and their sorting
|-- navigation/               Location, content detection and LocationResolver.open()
|-- operation/                user-requested file mutations across backends
|-- settings/                 persisted UI preferences only
|-- storage/
|   |-- LocalStorageAdapter   direct java.io.File operations
|   |-- archive/              archive container access and transactions
|   |-- bookmarks/            persisted bookmark collection
|   |-- trash/                persisted trash collection and payload moves
|   `-- persistence/          Room schema/DAO/entity implementation
|-- ui/
|   |-- state/                shared LiveData StateViewModel
|   |-- pane/                 pane state, RecyclerView and PaneFragment
|   |-- content/              full-screen opened content and editor/media/image components
|   |-- topbar/               active-pane toolbar behavior
|   |-- bottombar/            browsing/selection behavior and button validity
|   |-- drawer/               roots and theme control
|   `-- dialog/               input prompts
`-- core/                     DI, exceptions and executors
```

Code-reading entry points:

| Question | Open |
| --- | --- |
| What can be shown in a list? | `entry/Entry.java`, `entry/EntryType.java` |
| What does a path mean? | `navigation/Location.java` |
| What happens after tapping an item? | `ui/pane/PaneFragment.java`, `navigation/LocationResolver.java` |
| What state connects independent components? | `ui/state/StateViewModel.java` |
| Where does physical I/O happen? | `storage/LocalStorageAdapter.java` |
| Where are copy/delete/rename implemented? | `operation/FileOperations.java` |
| How are archives accessed? | `storage/archive/ArchiveAccess.java` |
| Why is an action disabled? | `ui/bottombar/BottomBarComponent.java` or `ui/topbar/TopBarComponent.java` |

## Entries And Locations

`Entry` is an ephemeral visible item, not a node retained in a tree. It exists only in the list
currently rendered by a pane. `EntryType` contains both source and directory/file shape, so
folder state is not duplicated in a separate field.

`Location` is the only navigation address:

```text
storage:
storage:/Download
storage:/Download/readme.txt
storage:/Download/files.zip!/
storage:/Download/files.zip!/docs/readme.txt
trash:
bookmarks:
search:?scope=...&query=...
```

Storage, Trash, Bookmarks and Search are pane locations. Archive content is mounted beneath the
physical archive file and is never a separate root. A `..` row is an `Entry.parent(...)` built
from validated `Location.parent()`; no component can navigate above `storage:`, `trash:` or
`bookmarks:`.

## Navigation

`LocationResolver` is the complete read/open route:

```text
storage directory       -> raw File children -> List<Entry>
storage file            -> ContentDetector -> OpenedContent or archive mount redirect
mounted archive folder  -> ArchiveAccess.list() -> List<Entry>
mounted archive file    -> ArchiveAccess materializes cache file -> OpenedContent
bookmarks / trash       -> their collection backend -> List<Entry>
search                  -> raw storage scan -> List<Entry>
```

`ContentDetector` examines content on open, rather than relying on a filename extension or
probing every row while scrolling. Known ZIP-based document formats are left to external apps
instead of being mounted as folders.

## Mutation And Action Validity

`operation.FileOperations` contains the cross-backend writes required by user commands:
create, rename, delete, restore, bookmark, transfer and materialization for sharing/open-with.
It delegates physical writes to `LocalStorageAdapter`, archive transactions to `ArchiveAccess`,
and collection changes to bookmark/trash storage.

The UI component displaying an action owns its validity policy. For example,
`BottomBarComponent` disables bookmark for files and copy/move when the inactive pane cannot be
a destination. Backends still reject invalid or unsafe writes so a stale UI state cannot damage
data.

## Full-Screen Content

Text, image, video and audio content replaces the browser surface inside `MainActivity`; the two
pane states remain alive in `StateViewModel`. `OpenedContent` describes the currently visible
full-screen file. Back returns through the originating pane history, so opening from Bookmarks or
Search returns there rather than inferring a physical parent.

## Archive Boundary

`storage.archive.ArchiveAccess` is a specialized access layer for a physical archive container.
It reads entries fresh on navigation and applies supported mutations by writing and validating a
replacement archive before replacing the original file. RAR remains read-only. The current
Android libarchive Java bridge is retained until an owned official-libarchive JNI bridge is
implemented.
