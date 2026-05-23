# File Manager Architecture

This document is the current architecture contract. It intentionally describes the present package
concept only; it does not preserve the previous phase history.

## Core Idea

Everything the user can see in a pane is a `VirtualNode`.

`VirtualNode` is the single model for local files, folders, archive entries, trash entries,
bookmarks, and future sources. UI code renders nodes and sends user intent to workspace/operation
classes. It does not own filesystem policy, conflict policy, or action availability rules.

The project has five main axes:

```text
node        virtual tree model, sources, and openers
operations  user-visible features and their supporting feature backends
rules       external availability/constraint rules
workspace   retained snapshots, invalidation, sessions, and command coordination
ui          Android rendering and Android-only effects
```

## Package Map

```text
com.vpt.filemanager
├── app/                         Application entry point
├── ui/                          Android boundary
│   ├── main/                    MainActivity
│   ├── drawer/                  drawer contract and item dispatch
│   ├── pane/                    dual-pane browser UI
│   │   ├── controller/          toolbar, bottom bar, selection bar, insets, back press
│   │   ├── flow/                UI flows that show dialogs then call operations
│   │   ├── icon/                pane icon rendering
│   │   └── list/                RecyclerView adapter and view holder
│   ├── dialog/                  dialogs and bottom sheets
│   ├── editor/                  text editor activity and syntax setup
│   └── properties/              properties dialog renderer
│
├── node/                        virtual tree core
│   ├── VirtualNode.java
│   ├── NodePath.java            scheme + authority + path for virtual nodes
│   ├── NodeFactory.java         resolves NodePath to VirtualNode
│   ├── source/                  where nodes come from
│   │   ├── NodeSource.java
│   │   ├── LocalSource.java
│   │   ├── ArchiveSource.java
│   │   ├── TrashSource.java
│   │   ├── BookmarkSource.java
│   │   ├── ParentSource.java
│   │   └── archive/             native/archive backend bridge
│   └── opener/                  what happens when a node is opened
│       ├── NodeOpener.java
│       ├── OpenerRegistry.java
│       ├── TextOpener.java
│       ├── ArchiveOpener.java
│       └── ExternalOpener.java
│
├── operations/                  app features and operation support
│   ├── create/
│   ├── delete/
│   ├── rename/
│   ├── transfer/
│   ├── trash/
│   ├── bookmark/
│   ├── properties/
│   ├── sort/
│   ├── selection/
│   ├── navigation/
│   ├── pane/
│   ├── share/
│   ├── openwith/
│   ├── conflict/
│   ├── result/
│   └── support/
│
├── rules/                       external workspace action rules
│   └── storage/
├── workspace/                   live snapshots, invalidation and workspace state
├── data/                        Room and preferences
├── di/                          Hilt modules
├── event/                       app-wide event streams
├── threading/                   executors
├── format/                      formatting, MIME, theme, category helpers
└── error/                       error model and Android error presentation
```

## Node

`node` is the virtual tree. It owns the model and the two node extension axes.

`NodePath`
: A virtual path. It is not just a local filesystem path. It supports `file://`, `archive://`,
`trash://`, `bookmark://`, and `root:///`. `root:///` materializes the stable Storage, Trash, and
Bookmarks branches without loading their descendants.

`NodeSource`
: Source strategy. A source knows how to resolve, list, read, and optionally write nodes for one
kind of storage.

`NodeOpener`
: Open strategy. An opener decides how to open a file node: text editor, archive navigation, system
viewer, and future image/video/audio/pdf viewers.

`NodeFactory`
: Converts `NodePath` into `VirtualNode` by dispatching to source implementations.

## Operations

One user-visible feature should have one named operation class.

Examples:

```text
CreateNodeOperation
DeleteNodesOperation
RenameNodeOperation
TransferOperation
RestoreTrashEntriesOperation
EmptyTrashOperation
AddBookmarkOperation
RemoveBookmarksOperation
SortNodesOperation
SelectRangeOperation
NavigateToParentOperation
PrepareShareRequestOperation
PrepareOpenWithRequestOperation
PreparePropertiesModelOperation
```

Operations are UI-free. They may receive `VirtualNode`, `NodePath`, workspace snapshots, options, or
small request DTOs. They should be directly unit-testable.

### Operation Support Classes

Some classes under `operations` are not user-facing operations. They are deliberately placed in
subpackages so they do not pollute the feature list.

`operations/support/NodeFileBackend`
: Low-level backend used by create, rename, transfer, and permanent delete flows. It dispatches to
`NodeSource` write APIs and owns generic copy/move stream mechanics. It is not a user action.

`operations/trash/TrashStore`
: Trash-specific backend. It coordinates filesystem moves into `.AppTrash` and Room metadata.
User-facing trash features are `RestoreTrashEntriesOperation`, `EmptyTrashOperation`, and delete
operations that call this store.

`operations/bookmark/BookmarkStore`
: Bookmark-specific backend around Room bookmark rows. User-facing features are
`AddBookmarkOperation` and `RemoveBookmarksOperation`.

`operations/conflict/UniqueNameGenerator`
: Shared helper for "keep both" name conflict behavior. It works through the virtual node tree
instead of local-only `File.exists`.

`operations/result/BatchResult`
: Shared DTO for batch operations that intentionally continue after per-item failure.

These classes are not legacy leftovers. The old `*Ops` names were misleading; the current names
make their role explicit: backend/store/helper/result.

## Rules

Rules are external constraints applied to a workspace snapshot. UI components ask the rule layer for
action availability; they do not hardcode business rules.

Examples:

```text
SelectionShapeRule
ArchiveReadOnlyRule
NonLocalOpenWithRule
SamePanePathTransferRule
```

`RuleEngine` composes rules. `WorkspaceCommandDispatcher` owns the engine used by the live
workspace, so the same rule evaluation drives rendered availability and execution-time rejection.

Rules should answer questions like:

- Multiple selection disables rename/properties/open-with.
- Archive entries are read-only.
- Non-local nodes cannot use Android open-with.
- Same active/inactive path disables copy/move.

## Workspace

`workspace` is the control boundary for the materialized virtual tree. It does not mirror the
whole filesystem in memory. It retains only directory snapshots that a pane or a future session is
actively using; when the last owner releases a path, its snapshot is evicted and its nodes become
eligible for garbage collection.

```text
WorkspaceAction
PaneSnapshot
WorkspaceSnapshot
DirectorySnapshot
MutationResult
WorkspaceStore
WorkspaceFileWatcher
WorkspaceCommandDispatcher
```

`DirectorySnapshot`
: Immutable immediate children of one materialized `NodePath`, tagged with a workspace revision.

`MutationResult`
: The explicit invalidation scope produced after a mutation or an external change notification. It
contains changed container paths and removed subtrees. Unknown-scope operations may use the
deliberate `allLiveSnapshots` fallback; new operations should prefer precise paths.

`WorkspaceStore`
: Retains/releases live snapshots, reloads fresh data when navigating, and reconciles an
invalidated snapshot once even when two panes show the same directory.

`WorkspaceFileWatcher`
: Watches retained local directories with Android `FileObserver`. Its events are only invalidation
hints; the store always re-reads a source before publishing display state.

`WorkspaceCommandDispatcher`
: The mutation gateway. It evaluates `RuleEngine` at execution time, calls mutating operations,
and publishes each operation's `MutationResult` to `WorkspaceStore`. Browser UI does not construct
mutation scopes or call mutating operations directly.

Current transition boundary: pane navigation/selection state still lives in `PaneViewModel`, and
text-editor save currently publishes its document invalidation directly. A future
`DocumentSession` will place editor save, external-delete handling, and save conflicts behind the
same workspace-owned boundary.

### Reconciliation

```text
UI intent -> WorkspaceCommandDispatcher -> rule check -> operation -> MutationResult
         -> WorkspaceStore invalidates live snapshots
         -> PaneViewModel requests reconcile -> source re-lists affected directory
         -> RecyclerView DiffUtil renders changed rows
```

For external local-file changes:

```text
FileObserver -> MutationResult -> same reconcile path
```

Backends that cannot reliably emit events, such as future provider/cloud implementations, must
reload on navigation, foreground entry, or explicit refresh and may additionally expose their own
observation adapter.

## UI

`ui` is the Android boundary.

Allowed responsibilities:

- render panes, lists, dialogs, sheets, toolbar, drawer
- collect click/input events
- launch Android activities/intents
- observe ViewModels and LiveData
- submit commands to workspace and show their result

Forbidden responsibilities:

- own copy/move/delete/create/rename behavior
- own action availability rules
- inspect local files directly when a `VirtualNode` or operation can do it
- duplicate conflict handling policy outside operation/workflow classes

`ui/pane/flow` classes are Android flows. They may show dialogs, gather decisions, and submit
workspace commands. They are not domain operations.

### Text Editor

`ui/editor` owns the Android text editing surface and the adapter to sora-editor/TextMate:

```text
TextEditorActivity  toolbar/status/editor lifecycle and asynchronous document I/O
LanguageResolver    filename to TextMate scope selection
SyntaxCatalog       immutable scope to asset/config/dependency records
SyntaxSetup         process-wide theme/provider cache and lazy grammar loading
```

Grammar assets live under `assets/syntaxes`; the older `editor/textmate` asset directory is limited
to shared themes and the Kotlin grammar not present in the imported TM4E bundle. Grammars must not
be eagerly loaded as one application startup set. `SyntaxSetup` registers shared infrastructure
once, then loads the requested scope and required embedded scopes on a computation executor.

`TextEditorActivity` must not perform file probes, decoding, writes, or TextMate parsing on the UI
thread. It releases its `CodeEditor` in `onDestroy`; process-level registry caches remain reusable
for later editor instances.

The editor currently keeps a content savepoint after load/save, derives its dirty marker from
content equality so undo/redo can return to a clean state, and exposes in-document find through
sora `EditorSearcher`.

### Viewer And Player Features

Image, video, and audio are opener implementations under `node/opener` with Android surfaces under
`ui`, not new architecture roots:

```text
ImageOpener  -> ui/viewer/ImageViewerActivity  -> Glide full-resolution image rendering
VideoOpener  -> ui/player/MediaPlayerActivity  -> Jetpack Media3 video playback
AudioOpener  -> ui/player/MediaPlayerActivity  -> Jetpack Media3 audio playback
```

Glide and Media3 dependencies are already declared. Their UI and opener wiring remain to be
implemented. Media thumbnails in pane rows should also use Glide with recycling-safe requests and
placeholder icons for non-media nodes.

### Planned Capabilities

The following capabilities remain planned and must use the same workspace contracts:

```text
DocumentSession              editor dirty/savepoint, external-delete and conflict state
SearchNodesOperation         temporary search:// virtual result node for file/folder search
ArchiveEditSession           overlay edits for archive virtual branches
ArchiveCommitOperation       libarchive C++ temp-write, validation, and atomic replacement
Image/video/audio openers    Glide image viewer and Media3 playback
```

## Dependency Injection

The project uses Hilt.

Default rule:

- Use constructor injection for concrete stateless services and operations.
- Use Hilt modules for Android/platform objects such as Room database and DAO instances.
- Keep Android `Context`, `Intent`, `Fragment`, and `Activity` out of node and operation classes
  unless the class is explicitly in `ui`.

Current DI style:

```text
UI -> Workspace/Rules -> Operations -> Node/Store/Backend -> Data/NodeSource
```

Recommended future DI improvements:

- Use Hilt multibinding for `Set<NodeSource>` when source registration grows.
- Use Hilt multibinding for `Set<NodeOpener>` when more viewers are added.
- Use Hilt multibinding for `Set<WorkspaceRule>` when rules become configurable.

## Data

`data` owns persistence.

Room entities/DAOs stay under `data/db`. Preferences stay under `data/prefs`.

Data classes should not call UI. Store/backend classes may use DAOs, but user-facing operations
should remain the public feature surface.

## Naming Rules

```text
*Operation   user-visible feature or pure operation
*Store       feature-specific persistence/backend facade
*Backend     low-level technical backend used by operations
*Rule        external action availability rule
*Source      virtual node source
*Opener      virtual node open behavior
*Request     DTO for an Android boundary effect
*Model       render-independent data model
*Controller  UI region coordinator
*Flow/Action Android UI flow that collects input and calls operations
```

Avoid new top-level packages unless they represent one of the major architecture axes.

## Testing

Operations, rules, and node behavior should have JVM unit tests. UI behavior can remain thinner and
be covered by Android tests when flows become risky.

Build verification for structural refactors:

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
```
