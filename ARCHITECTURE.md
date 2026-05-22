# File Manager Architecture

This document is the current architecture contract. It intentionally describes the present package
concept only; it does not preserve the previous phase history.

## Core Idea

Everything the user can see in a pane is a `VirtualNode`.

`VirtualNode` is the single model for local files, folders, archive entries, trash entries,
bookmarks, and future sources. UI code renders nodes and sends user intent to workspace/operation
classes. It does not own filesystem policy, conflict policy, or action availability rules.

The project has four main axes:

```text
node        virtual tree model, sources, and openers
operations  user-visible features and their supporting feature backends
rules       external availability/constraint rules
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
├── workspace/                   conceptual workspace snapshots and action ids
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
`trash://`, and `bookmark://`.

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

`RuleEngine` composes rules. `WorkspaceRules` is the default static facade used where custom rule
sets are not needed.

Rules should answer questions like:

- Multiple selection disables rename/properties/open-with.
- Archive entries are read-only.
- Non-local nodes cannot use Android open-with.
- Same active/inactive path disables copy/move.

## Workspace

`workspace` contains concept-level state snapshots and action ids, not Android UI.

```text
WorkspaceAction
PaneSnapshot
WorkspaceSnapshot
```

The package is reserved for the future control center that coordinates panes, rules, and operations.
It should not become a dumping ground for unrelated commands.

## UI

`ui` is the Android boundary.

Allowed responsibilities:

- render panes, lists, dialogs, sheets, toolbar, drawer
- collect click/input events
- launch Android activities/intents
- observe ViewModels and LiveData
- call operations and show their result

Forbidden responsibilities:

- own copy/move/delete/create/rename behavior
- own action availability rules
- inspect local files directly when a `VirtualNode` or operation can do it
- duplicate conflict handling policy outside operation/workflow classes

`ui/pane/flow` classes are Android flows. They may show dialogs, gather decisions, and call
operations. They are not domain operations.

## Dependency Injection

The project uses Hilt.

Default rule:

- Use constructor injection for concrete stateless services and operations.
- Use Hilt modules for Android/platform objects such as Room database and DAO instances.
- Keep Android `Context`, `Intent`, `Fragment`, and `Activity` out of node and operation classes
  unless the class is explicitly in `ui`.

Current DI style:

```text
UI -> Workspace snapshots/rules -> Operations -> Node/Store/Backend -> Data/NodeSource
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
