# Specification — Android File Manager

> Đặc tả triển khai chi tiết. Đọc cùng `abstract.md`.
> Mục tiêu: dev khác đọc xong có thể bắt đầu code mà không cần hỏi lại quyết định kiến trúc.

---

## Mục lục

1. [Overview](#1-overview)
2. [Tech stack & build configuration](#2-tech-stack--build-configuration)
3. [Project structure](#3-project-structure)
4. [Core abstractions](#4-core-abstractions)
5. [Filesystem Provider SPI](#5-filesystem-provider-spi)
6. [Domain layer](#6-domain-layer)
7. [Data layer](#7-data-layer)
8. [Presentation layer](#8-presentation-layer)
9. [Concurrency model](#9-concurrency-model)
10. [Permission & storage strategy](#10-permission--storage-strategy)
11. [Error handling](#11-error-handling)
12. [Feature design (deep dive)](#12-feature-design-deep-dive)
13. [UI / UX specification](#13-ui--ux-specification)
14. [Dependencies & versions](#14-dependencies--versions)
15. [Testing strategy](#15-testing-strategy)
16. [Build, signing & release](#16-build-signing--release)
17. [Coding conventions](#17-coding-conventions)
18. [Extension points (v2+)](#18-extension-points-v2)
19. [Glossary](#19-glossary)

---

## 1. Overview

### 1.1 Tóm tắt kiến trúc

App theo **MVVM + Clean Architecture lite**, single-module ở v1, ba layer rõ ràng:

```
Presentation (UI)
       │      observe LiveData / submit Intent
       ▼
   ViewModel  ────────────►   UseCase
                                  │
                                  ▼
                              Repository
                                  │
                  ┌───────────────┼──────────────────┐
                  ▼               ▼                  ▼
           FileSystemProvider   RoomDB           Settings/Prefs
              (SPI)
                  │
        ┌─────────┴────────────┐
        ▼                      ▼
  LocalFileSystem        ArchiveFileSystem
                         (ZIP/TAR/7Z/…)
```

**Quy tắc dependency:** `presentation → domain → data`. Domain không biết Android, không biết Room, không biết Hilt module nào. Data layer biết Android nhưng KHÔNG biết Fragment/Activity.

### 1.2 Nguyên tắc thiết kế cứng

| Nguyên tắc | Ý nghĩa cụ thể |
|------------|----------------|
| **No root, ever** | KHÔNG có `Runtime.exec("su")`, không libsu, không Magisk integration. Bất kỳ PR nào thêm cũng phải reject. |
| **Provider-agnostic ViewModel** | ViewModel chỉ nói chuyện với `FileSystemProvider` interface, không biết Local hay Archive. |
| **Cancellable by default** | Mọi async operation phải nhận `CancellationSignal` hoặc trả về `Future` cancel được. |
| **Fail loud in debug, gracefully in release** | Dùng `Timber` + `BuildConfig.DEBUG` để decide có throw hay log. |
| **Java 17 source target** | Không dùng record/pattern matching trên runtime path nóng (ART desugaring limit), an toàn cho mọi feature ngôn ngữ. |
| **Single source of truth** | Trạng thái UI sống trong ViewModel, không sync hai chiều giữa Fragment và ViewModel. |

## 2. Tech stack & build configuration

### 2.1 JDK & AGP

- **JDK build:** OpenJDK 26 — set qua `org.gradle.java.home` hoặc auto via Gradle toolchain (`java { toolchain { languageVersion.set(JavaLanguageVersion.of(26)) } }`).
- **AGP:** 8.7.x trở lên (phiên bản đầu tiên ổn định cho `compileSdk = 36`). Pin chính xác trong `libs.versions.toml`.
- **Gradle wrapper:** 8.10+ (đi kèm AGP 8.7+).
- **Java source/target:** 17 (an toàn nhất cho desugaring trên Android Runtime, kể cả khi build bằng JDK 26).

> **Tại sao source = 17 mà build = 26?** JDK 26 có thể build code Java 17 hoàn toàn (`--release 17`). Không dùng feature Java 21+ ở runtime vì ART chưa hỗ trợ virtual thread / sealed pattern matching / scoped values. Build bằng JDK mới chỉ để có toolchain ổn định và tốc độ compile tốt.

### 2.2 Gradle Kotlin DSL — bố cục build

```
settings.gradle.kts        # plugin management, dependency resolution mgmt, version catalog
build.gradle.kts           # root: classpath cho plugin (nếu có)
app/
  build.gradle.kts         # module config: android{}, dependencies{}
gradle/
  libs.versions.toml       # SINGLE SOURCE: versions, libraries, plugins, bundles
```

**`libs.versions.toml`** là bắt buộc. Mọi version đều khai báo ở đây — không hard-code version trong `build.gradle.kts`.

### 2.3 `app/build.gradle.kts` template

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vpt.filemanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vpt.filemanager"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0", "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work)
    implementation(libs.material)

    // Hilt
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    annotationProcessor(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // Media
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    implementation(libs.subsampling.image)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    // Editor
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)

    // Archive
    implementation(libs.commons.compress)
    implementation(libs.zip4j)

    // Utilities
    implementation(libs.timber)
    implementation(libs.juniversalchardet)

    // Desugaring
    coreLibraryDesugaring(libs.desugar)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

## 3. Project structure

```
app/src/main/
├── java/io/github/phucthinh/filemanager/
│   ├── FileManagerApp.java                   # @HiltAndroidApp Application
│   │
│   ├── core/                                 # Cross-cutting utilities
│   │   ├── concurrent/
│   │   │   ├── AppExecutors.java             # @Singleton pool definitions
│   │   │   ├── CancellationSignal.java       # Cooperative cancellation
│   │   │   └── ProgressReporter.java         # Callback interface
│   │   ├── di/
│   │   │   ├── AppModule.java
│   │   │   ├── ProviderModule.java
│   │   │   └── DatabaseModule.java
│   │   ├── error/
│   │   │   ├── AppException.java             # Sealed-ish exception base
│   │   │   ├── FileSystemException.java
│   │   │   └── ArchiveException.java
│   │   ├── logging/
│   │   │   └── TimberInitializer.java
│   │   └── util/
│   │       ├── ByteSize.java                 # Human-readable size
│   │       ├── MimeTypes.java
│   │       └── PathUtils.java
│   │
│   ├── domain/                               # Business logic, no Android
│   │   ├── model/
│   │   │   ├── FileNode.java                 # Abstract Composite node
│   │   │   ├── FilePath.java                 # Scheme + path value object
│   │   │   ├── FileMetadata.java
│   │   │   ├── PosixPermission.java
│   │   │   ├── MimeCategory.java             # enum: IMAGE/VIDEO/AUDIO/TEXT/ARCHIVE/OTHER
│   │   │   ├── HashAlgorithm.java
│   │   │   └── operation/
│   │   │       ├── FileOperation.java        # Interface
│   │   │       ├── OperationProgress.java
│   │   │       └── OperationResult.java
│   │   ├── repository/                       # Interface only (impl ở data/)
│   │   │   ├── FileRepository.java
│   │   │   ├── TrashRepository.java
│   │   │   ├── BookmarkRepository.java
│   │   │   └── HashRepository.java
│   │   └── usecase/
│   │       ├── ListDirectoryUseCase.java
│   │       ├── CopyFilesUseCase.java
│   │       ├── MoveFilesUseCase.java
│   │       ├── DeleteFilesUseCase.java
│   │       ├── RenameFileUseCase.java
│   │       ├── CreateFolderUseCase.java
│   │       ├── ExtractArchiveUseCase.java
│   │       ├── ComputeHashUseCase.java
│   │       ├── SearchFilesUseCase.java
│   │       ├── RestoreFromTrashUseCase.java
│   │       └── EmptyTrashUseCase.java
│   │
│   ├── data/                                 # Implementations
│   │   ├── fs/
│   │   │   ├── FileSystemProvider.java       # SPI interface
│   │   │   ├── FileSystemRegistry.java       # Scheme → Provider lookup
│   │   │   ├── local/
│   │   │   │   ├── LocalFileSystemProvider.java
│   │   │   │   └── LocalFileNode.java
│   │   │   └── archive/
│   │   │       ├── ArchiveFileSystemProvider.java
│   │   │       ├── ArchiveSession.java       # Open archive + cache
│   │   │       ├── ArchiveNode.java
│   │   │       ├── ZipBackend.java           # Commons Compress + Zip4j
│   │   │       ├── TarBackend.java
│   │   │       ├── SevenZBackend.java
│   │   │       └── ArchiveBackend.java       # Interface
│   │   ├── repository/
│   │   │   ├── FileRepositoryImpl.java
│   │   │   ├── TrashRepositoryImpl.java
│   │   │   ├── BookmarkRepositoryImpl.java
│   │   │   └── HashRepositoryImpl.java
│   │   ├── db/
│   │   │   ├── AppDatabase.java
│   │   │   ├── dao/
│   │   │   │   ├── BookmarkDao.java
│   │   │   │   ├── TrashDao.java
│   │   │   │   └── RecentDao.java
│   │   │   └── entity/
│   │   │       ├── BookmarkEntity.java
│   │   │       ├── TrashEntryEntity.java
│   │   │       └── RecentPathEntity.java
│   │   ├── prefs/
│   │   │   └── UserPreferences.java          # DataStore wrapper hoặc SharedPreferences
│   │   └── worker/
│   │       └── TrashCleanupWorker.java       # WorkManager
│   │
│   └── ui/
│       ├── MainActivity.java
│       ├── browser/
│       │   ├── FileBrowserFragment.java
│       │   ├── FileBrowserViewModel.java
│       │   ├── FileListAdapter.java
│       │   ├── FileViewHolder.java
│       │   └── SelectionTracker.java
│       ├── dualpane/
│       │   ├── DualPaneFragment.java         # Hosts two FileBrowserFragment
│       │   └── DualPaneViewModel.java
│       ├── properties/
│       │   ├── PropertiesDialogFragment.java
│       │   ├── PropertiesViewModel.java
│       │   ├── GeneralTabFragment.java
│       │   ├── PermissionsTabFragment.java
│       │   ├── HashesTabFragment.java
│       │   └── PreviewTabFragment.java
│       ├── viewer/
│       │   ├── ImageViewerActivity.java
│       │   ├── VideoPlayerActivity.java
│       │   ├── AudioPlayerActivity.java
│       │   └── TextEditorActivity.java
│       ├── trash/
│       │   ├── TrashFragment.java
│       │   └── TrashViewModel.java
│       ├── bookmark/
│       │   └── BookmarkDrawerFragment.java
│       ├── search/
│       │   ├── SearchFragment.java
│       │   └── SearchViewModel.java
│       ├── operations/
│       │   ├── OperationService.java         # Foreground service
│       │   ├── OperationNotification.java
│       │   └── OperationManager.java         # Singleton, queue + dispatch
│       ├── permission/
│       │   ├── PermissionGateActivity.java   # Bootstrap: ensure MANAGE_EXTERNAL_STORAGE
│       │   └── PermissionRationaleDialog.java
│       └── common/
│           ├── BaseFragment.java
│           ├── BaseDialogFragment.java
│           └── LiveEvent.java                # Single-shot LiveData (SingleLiveEvent variant)
│
├── res/
│   ├── drawable/                             # Icon, vector
│   ├── layout/                               # XML layouts
│   ├── menu/
│   ├── navigation/
│   │   └── nav_graph.xml
│   ├── values/
│   │   ├── strings.xml
│   │   ├── colors.xml
│   │   ├── themes.xml
│   │   ├── dimens.xml
│   │   └── attrs.xml
│   ├── values-night/
│   ├── values-w600dp/                        # Dual-pane breakpoint
│   ├── xml/
│   │   └── file_paths.xml                    # FileProvider config
│   └── mipmap-anydpi-v26/                    # Adaptive icon
│
└── AndroidManifest.xml
```

## 4. Core abstractions

### 4.1 `FilePath` — value object thay vì raw String

```java
public final class FilePath {
    private final String scheme;   // "file", "archive"
    private final String authority; // "" cho file; archive URI cho archive
    private final String path;     // Absolute path within authority

    public FilePath(String scheme, String authority, String path) { ... }

    public static FilePath local(String absolutePath) {
        return new FilePath("file", "", normalize(absolutePath));
    }

    public static FilePath inArchive(FilePath archivePath, String innerPath) {
        return new FilePath("archive", archivePath.toString(), normalize(innerPath));
    }

    public boolean isLocal()   { return "file".equals(scheme); }
    public boolean isArchive() { return "archive".equals(scheme); }

    public FilePath parent() { ... }
    public FilePath child(String name) { ... }
    public String name() { ... }
    public String extension() { ... }

    @Override public String toString() { ... }   // canonical URI form
    @Override public boolean equals(Object o) { ... }
    @Override public int hashCode() { ... }
}
```

**Lý do:** Tách scheme khỏi path tránh string-parsing rải rác. Khi v2 thêm `ftp://`, `smb://`, chỉ cần thêm scheme mới — không ảnh hưởng code consumer.

### 4.2 `FileNode` — Composite

```java
public abstract class FileNode {
    public abstract FilePath path();
    public abstract String name();
    public abstract boolean isDirectory();
    public abstract boolean isSymbolicLink();
    public abstract long sizeBytes();          // -1 nếu directory hoặc unknown
    public abstract long lastModifiedMillis();
    public abstract FileMetadata metadata();   // Lazy compute, có cache

    // Composite operations — chỉ valid khi isDirectory()
    public List<FileNode> children() {
        throw new UnsupportedOperationException("Not a directory");
    }

    public FileNode findChild(String name) {
        for (FileNode c : children()) if (c.name().equals(name)) return c;
        return null;
    }

    @Override public boolean equals(Object o) {
        return o instanceof FileNode && path().equals(((FileNode) o).path());
    }
    @Override public int hashCode() { return path().hashCode(); }
}
```

Implementation cụ thể:
- `LocalFileNode` — wrap `java.io.File` + cache lazy stat.
- `ArchiveNode` — wrap entry trong archive, children chỉ build khi gọi `children()`.

**Quan trọng:** `FileNode` chỉ là **metadata view**, KHÔNG chứa `InputStream`. Đọc nội dung qua `FileSystemProvider.openRead(node.path())`. Lý do: archive entry có thể đại diện file 10GB — nếu node giữ stream, GC sẽ chết.

### 4.3 `FileMetadata`

```java
public final class FileMetadata {
    private final long sizeBytes;
    private final long createdAtMillis;        // -1 nếu không có
    private final long lastModifiedMillis;
    private final long lastAccessAtMillis;     // -1 nếu không có
    private final PosixPermission permission;  // null nếu không stat được
    private final int uid;                     // -1 nếu không có
    private final int gid;                     // -1 nếu không có
    private final String ownerName;            // null nếu lookup fail
    private final String groupName;            // null nếu lookup fail
    private final String mimeType;             // detect bằng extension + magic
    private final String selinuxContext;       // null nếu không có (luôn null khi không root — OK)

    // Builder pattern...
}
```

### 4.4 `PosixPermission`

```java
public final class PosixPermission {
    private final int mode;  // st_mode từ stat

    public PosixPermission(int mode) { this.mode = mode; }

    public boolean isReadable(Who who)   { ... }
    public boolean isWritable(Who who)   { ... }
    public boolean isExecutable(Who who) { ... }
    public boolean isSetuid()  { return (mode & 04000) != 0; }
    public boolean isSetgid()  { return (mode & 02000) != 0; }
    public boolean isSticky()  { return (mode & 01000) != 0; }

    public String toRwxString() { ... }  // "rwxr-xr-x"
    public String toOctalString() { ... } // "0755"

    public enum Who { USER, GROUP, OTHER }
}
```

## 5. Filesystem Provider SPI

### 5.1 Interface

```java
public interface FileSystemProvider {
    String scheme();

    /** Resolve node tại path. Throw NotFound nếu không tồn tại. */
    FileNode resolve(FilePath path) throws FileSystemException;

    /** List trực tiếp một thư mục. KHÔNG recursive. */
    List<FileNode> list(FilePath dir, ListOptions opts) throws FileSystemException;

    InputStream openRead(FilePath path) throws FileSystemException;

    /** Mode: CREATE / TRUNCATE / APPEND */
    OutputStream openWrite(FilePath path, WriteMode mode) throws FileSystemException;

    FileNode createFile(FilePath path) throws FileSystemException;
    FileNode createDirectory(FilePath path) throws FileSystemException;

    /** Atomic nếu cùng filesystem; nếu không, thực hiện copy+delete và caller phải accept không-atomic. */
    void rename(FilePath src, FilePath dst) throws FileSystemException;

    void delete(FilePath path, DeleteOptions opts) throws FileSystemException;

    /** True nếu hai path nằm trên cùng "atomic unit" (mount point). */
    boolean isSameVolume(FilePath a, FilePath b);

    boolean exists(FilePath path);
    boolean supportsWrite();        // Archive read-only như 7Z trả false
    long freeSpaceBytes(FilePath path);   // -1 nếu không xác định

    /** Watch thay đổi — optional, return null nếu không support. */
    Closeable watch(FilePath dir, WatchListener listener);
}
```

### 5.2 Registry

```java
@Singleton
public class FileSystemRegistry {
    private final Map<String, FileSystemProvider> byScheme;

    @Inject
    public FileSystemRegistry(Set<FileSystemProvider> providers) {
        Map<String, FileSystemProvider> m = new HashMap<>();
        for (FileSystemProvider p : providers) m.put(p.scheme(), p);
        byScheme = Collections.unmodifiableMap(m);
    }

    public FileSystemProvider providerFor(FilePath path) {
        FileSystemProvider p = byScheme.get(path.scheme());
        if (p == null) throw new IllegalStateException("No provider for scheme: " + path.scheme());
        return p;
    }
}
```

Hilt multibinding: `@IntoSet` cho mỗi `FileSystemProvider` trong `ProviderModule`. Thêm provider mới = thêm 1 binding, không sửa registry.

### 5.3 `LocalFileSystemProvider`

- Wrap `java.nio.file.Files` cho operation; fallback `java.io.File` cho code cũ.
- `metadata()` dùng `android.system.Os.stat(absolutePath)` → `StructStat`.
- `watch()` dùng `FileObserver` (per-directory).
- `isSameVolume()` so sánh `Os.statvfs(path).f_fsid` của hai path.
- `freeSpaceBytes()` dùng `StatFs(path).getAvailableBytes()`.

### 5.4 `ArchiveFileSystemProvider`

Trừu tượng hơn, vì archive có concept "session":

```java
@Singleton
public class ArchiveFileSystemProvider implements FileSystemProvider {
    private final Map<FilePath, ArchiveSession> openSessions = new ConcurrentHashMap<>();

    @Override public String scheme() { return "archive"; }

    @Override public FileNode resolve(FilePath path) {
        ArchiveSession session = openSession(extractArchivePath(path));
        return session.resolveInner(path.path());
    }
    // …
}
```

`ArchiveSession`:
- Mở archive một lần, parse central directory.
- Build cây `ArchiveNode` lazy (chỉ inflate children khi cần).
- Cache decompressed file > 1MB trong `cacheDir/archives/<hash>/`.
- Track dirty entry để re-pack on close.
- LRU policy: max 8 archive open cùng lúc; oldest bị close + flush dirty (hoặc prompt user nếu có dirty).

`ArchiveBackend` interface:

```java
public interface ArchiveBackend {
    boolean canRead(String mimeOrExtension);
    boolean canWrite(String mimeOrExtension);
    Iterator<ArchiveEntry> entries(InputStream archive) throws IOException;
    InputStream openEntry(ArchiveEntry entry) throws IOException;
    void writeArchive(OutputStream out, List<ArchiveEntry> entries) throws IOException;
}
```

Implementations:
- `ZipBackend` — Commons Compress cho ZIP thường; Zip4j nếu encrypted. Detect bằng peek vào header.
- `TarBackend` — Commons Compress (TAR + GZ/BZ2/XZ wrapping).
- `SevenZBackend` — Commons Compress 7z (read-only trong v1).
- `CpioBackend`, `ArBackend` — Commons Compress (read-only).

## 6. Domain layer

### 6.1 UseCase pattern

Mỗi UseCase = **một verb**, một method `execute(...)`. Trả về `Result<T>` (sealed wrapper) hoặc `Future<Result<T>>` cho async.

```java
public final class CopyFilesUseCase {
    private final FileRepository fileRepo;
    private final AppExecutors executors;

    @Inject
    public CopyFilesUseCase(FileRepository fileRepo, AppExecutors executors) {
        this.fileRepo = fileRepo;
        this.executors = executors;
    }

    public Future<Result<Void>> execute(
            List<FilePath> sources,
            FilePath dstDir,
            ConflictPolicy policy,
            ProgressReporter progress,
            CancellationSignal cancel) {
        return executors.io().submit(() -> {
            try {
                fileRepo.copyAll(sources, dstDir, policy, progress, cancel);
                return Result.success(null);
            } catch (FileSystemException e) {
                return Result.failure(e);
            }
        });
    }
}
```

### 6.2 `Result<T>` wrapper

```java
public abstract class Result<T> {
    public static <T> Result<T> success(T value) { return new Success<>(value); }
    public static <T> Result<T> failure(Throwable error) { return new Failure<>(error); }

    public abstract boolean isSuccess();
    public abstract T getOrThrow() throws Throwable;
    public abstract T getOrNull();
    public abstract Throwable errorOrNull();

    public static final class Success<T> extends Result<T> { ... }
    public static final class Failure<T> extends Result<T> { ... }
}
```

### 6.3 `ConflictPolicy`

```java
public enum ConflictPolicy {
    SKIP,
    OVERWRITE,
    RENAME,        // file.txt → file (1).txt
    ASK            // ViewModel sẽ prompt user, repository gọi callback
}
```

## 7. Data layer

### 7.1 Repository implementations

`FileRepositoryImpl` orchestrate giữa các provider:

```java
@Singleton
public class FileRepositoryImpl implements FileRepository {
    private final FileSystemRegistry registry;
    private final TrashRepository trash;
    private final UserPreferences prefs;

    @Override
    public void copyAll(List<FilePath> sources, FilePath dstDir,
                        ConflictPolicy policy,
                        ProgressReporter progress,
                        CancellationSignal cancel) throws FileSystemException {
        long totalBytes = estimateSize(sources);
        long copiedBytes = 0;
        for (FilePath src : sources) {
            cancel.throwIfCancelled();
            copiedBytes += copyOne(src, dstDir, policy, progress, copiedBytes, totalBytes, cancel);
        }
    }

    private long copyOne(...) throws FileSystemException {
        FileSystemProvider srcFs = registry.providerFor(src);
        FileSystemProvider dstFs = registry.providerFor(dstDir);
        // Same FS + same volume → use native rename hoặc OS copy
        // Khác → stream copy với 64KB buffer
    }
}
```

### 7.2 Room schema

`AppDatabase` version 1:

```java
@Database(
    version = 1,
    entities = {
        BookmarkEntity.class,
        TrashEntryEntity.class,
        RecentPathEntity.class
    },
    exportSchema = true
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract BookmarkDao bookmarkDao();
    public abstract TrashDao trashDao();
    public abstract RecentDao recentDao();
}
```

**`BookmarkEntity`:**

| Column | Type | Note |
|--------|------|------|
| `id` | INTEGER PK autoincrement | |
| `display_name` | TEXT | User-editable name |
| `path` | TEXT | `FilePath.toString()` canonical |
| `icon_key` | TEXT | Optional icon hint |
| `sort_order` | INTEGER | Manual ordering |
| `created_at` | INTEGER | Epoch millis |

**`TrashEntryEntity`:**

| Column | Type | Note |
|--------|------|------|
| `id` | TEXT PK (UUID) | |
| `original_path` | TEXT | Canonical FilePath |
| `trash_path` | TEXT | Where it's stored now |
| `storage_root` | TEXT | Which trash root contains it |
| `display_name` | TEXT | Original name |
| `size_bytes` | INTEGER | |
| `is_directory` | INTEGER (boolean) | |
| `deleted_at` | INTEGER | Epoch millis |
| `expires_at` | INTEGER | Epoch millis; nullable |

**`RecentPathEntity`:**

| Column | Type | Note |
|--------|------|------|
| `path` | TEXT PK | |
| `last_visited_at` | INTEGER | |
| `visit_count` | INTEGER | |

> Index trên `last_visited_at DESC` để query recent nhanh.

### 7.3 DataStore vs SharedPreferences

Dùng **`androidx.datastore.preferences`** (Java compat OK qua RxDataStore hoặc Future API). Lưu:
- View mode (list/grid)
- Sort order
- Hidden files visible
- Theme (system/light/dark)
- Trash auto-cleanup ngày

## 8. Presentation layer

### 8.1 ViewModel pattern

```java
@HiltViewModel
public class FileBrowserViewModel extends ViewModel {
    private final ListDirectoryUseCase listDir;
    private final CopyFilesUseCase copyUseCase;
    // ...

    private final MutableLiveData<FilePath> currentPath = new MutableLiveData<>();
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>();
    private final LiveEvent<UiEvent> events = new LiveEvent<>();

    private final MediatorLiveData<List<FileNode>> children = new MediatorLiveData<>();

    @Inject
    public FileBrowserViewModel(ListDirectoryUseCase listDir, CopyFilesUseCase copyUseCase) { ... }

    public LiveData<UiState> uiState() { return uiState; }
    public LiveData<List<FileNode>> children() { return children; }
    public LiveData<UiEvent> events() { return events; }

    public void navigateTo(FilePath path) {
        currentPath.setValue(path);
        loadChildren(path);
    }

    public void onItemClicked(FileNode node) { ... }
    public void onItemLongPressed(FileNode node) { ... }
    public void onCopyClicked(List<FileNode> selection, FilePath dest) { ... }
    // …
}
```

`UiState` là sealed-ish:

```java
public abstract sealed class UiState
    permits UiState.Loading, UiState.Content, UiState.Empty, UiState.Error {
    // Java 17 sealed classes — desugaring không cần (ART runtime check không kiểm tra sealed)
}
```

> Lưu ý: `sealed` ở compile time chỉ giúp Java compiler enforce, runtime ART không có khái niệm này — không thành vấn đề. Nếu chưa quen, dùng base class abstract bình thường cũng được.

### 8.2 LiveEvent (single-shot)

LiveData broadcast lại event sau config change → cần wrapper `LiveEvent` chỉ deliver một lần. Sample implementation chuẩn (Jose Alcerreca's `SingleLiveEvent` pattern).

### 8.3 Activity / Fragment trách nhiệm

| Layer | Trách nhiệm | Không được làm |
|-------|------------|----------------|
| Activity | Host fragment, navigation root, permission gate | Business logic, gọi UseCase trực tiếp |
| Fragment | Bind UI, observe LiveData, gửi intent → ViewModel | Hold reference data ngoài lifecycle |
| ViewModel | State holder, gọi UseCase, transform data → UiState | Reference Context/Activity/Fragment/View |
| UseCase | Business operation, thread-aware | Reference Android API |
| Repository | Coordinate data sources | Hold UI state |
| Provider | I/O thuần | Bất kỳ Android UI |

## 9. Concurrency model

### 9.1 Executor topology

```java
@Singleton
public class AppExecutors {
    private final ExecutorService io;            // newFixedThreadPool(8)
    private final ExecutorService computation;   // newWorkStealingPool(Runtime.getRuntime().availableProcessors())
    private final ScheduledExecutorService scheduled; // newScheduledThreadPool(2)
    private final Executor main;                 // new Handler(Looper.getMainLooper())::post

    @Inject
    public AppExecutors() {
        this.io = Executors.newFixedThreadPool(8, named("io"));
        this.computation = Executors.newWorkStealingPool();
        this.scheduled = Executors.newScheduledThreadPool(2, named("sched"));
        this.main = command -> mainHandler.post(command);
    }
    // getters
}
```

**Phân chia:**
- `io` — file read/write, archive parse, network (v2). 8 threads cố định để tránh thrash khi user copy nhiều file song song.
- `computation` — hash, decompress trong RAM, image decode size pre-check. Work-stealing tận dụng CPU.
- `scheduled` — định kỳ trash cleanup heartbeat (chính là WorkManager nhưng có in-memory ticker để UI).
- `main` — post kết quả về UI.

**KHÔNG dùng:** `Executors.newCachedThreadPool()` (có thể spawn vô số thread khi user mở 100 archive). `AsyncTask` (deprecated).

### 9.2 `CancellationSignal`

```java
public final class CancellationSignal {
    private volatile boolean cancelled = false;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public void cancel() {
        cancelled = true;
        for (Runnable l : listeners) l.run();
    }

    public boolean isCancelled() { return cancelled; }

    public void throwIfCancelled() throws InterruptedException {
        if (cancelled) throw new InterruptedException("Cancelled by user");
    }

    public void addListener(Runnable listener) { listeners.add(listener); }
}
```

**Quy ước:** mọi loop trong copy/extract/hash phải check `cancel.throwIfCancelled()` mỗi iteration; mọi `InputStream` đọc trong loop dùng buffer ≤ 64KB để kiểm tra cancel nhanh.

### 9.3 `ProgressReporter`

```java
public interface ProgressReporter {
    void onProgress(OperationProgress progress);

    final class OperationProgress {
        public final long doneBytes;
        public final long totalBytes;
        public final int doneFiles;
        public final int totalFiles;
        public final String currentFileName;
        public final long bytesPerSecond;  // EWMA
    }
}
```

Throttle gọi `onProgress` xuống tối đa 10Hz để tránh main thread overload.

### 9.4 Foreground service cho operation dài

`OperationService` extends `Service`:
- `onStartCommand` lấy `OperationRequest` từ Intent extras.
- Tạo notification (channel `operations`, importance LOW).
- Submit vào `OperationManager` queue (single-thread executor để serialize show notification).
- Mỗi operation update notification progress + Cancel action.
- Khi xong: notification self-cancel (hoặc giữ với "Operation complete" nếu user setting bật).
- Sử dụng `ServiceCompat.startForeground(this, id, notif, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` — Android 14+ bắt buộc khai báo type.

`AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<service
    android:name=".ui.operations.OperationService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

## 10. Permission & storage strategy

### 10.1 Manifest

```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
                 tools:ignore="ScopedStorage" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<queries>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:mimeType="*/*" />
    </intent>
</queries>
```

### 10.2 Permission gate flow

```
App start
   │
   ▼
PermissionGateActivity (no-history, transparent)
   │
   ├─ Environment.isExternalStorageManager() == true? ──► MainActivity (finish gate)
   │
   └─ false ──► Show RationaleDialog
                  │
                  └─ User chọn "Cấp quyền"
                       │
                       └─ startActivityForResult(MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                             │
                             └─ onResume re-check → MainActivity hoặc giữ gate
```

Code skeleton:

```java
private void requestAllFilesAccess() {
    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
    intent.setData(Uri.fromParts("package", getPackageName(), null));
    permissionLauncher.launch(intent);
}
```

> Nếu user denied → hiển thị màn hình "App cần quyền để hoạt động" với button "Mở Cài đặt". Không có "limited mode" trong v1.

### 10.3 Storage roots discovery

```java
@Singleton
public class StorageRootsProvider {
    public List<StorageRoot> discover(Context ctx) {
        List<StorageRoot> roots = new ArrayList<>();
        // Primary
        roots.add(StorageRoot.primary(Environment.getExternalStorageDirectory()));
        // SD/USB qua StorageManager
        StorageManager sm = ctx.getSystemService(StorageManager.class);
        for (StorageVolume v : sm.getStorageVolumes()) {
            if (v.isRemovable() || !v.isPrimary()) {
                File path = v.getDirectory();
                if (path != null) roots.add(StorageRoot.secondary(path, v.getDescription(ctx)));
            }
        }
        return roots;
    }
}
```

## 11. Error handling

### 11.1 Hierarchy

```java
public abstract class AppException extends RuntimeException {
    public AppException(String msg, Throwable cause) { super(msg, cause); }
    public abstract int errorCode();
}

public class FileSystemException extends AppException {
    public enum Kind { NOT_FOUND, ACCESS_DENIED, ALREADY_EXISTS,
                       INSUFFICIENT_SPACE, CANCELLED, IO_ERROR, UNSUPPORTED }
    private final Kind kind;
    // …
}

public class ArchiveException extends FileSystemException {
    public enum ArchiveKind { CORRUPT, ENCRYPTED, UNSUPPORTED_FORMAT, PASSWORD_REQUIRED, WRONG_PASSWORD }
    private final ArchiveKind archiveKind;
    // …
}
```

### 11.2 Strategy

- **Provider layer:** catch checked `IOException`, wrap thành `FileSystemException` với `Kind` tương ứng.
- **UseCase:** không catch, trả về `Result.failure(e)`.
- **ViewModel:** map exception → `UiEvent.ShowError(messageRes, args)`.
- **Fragment:** show Snackbar/Dialog tương ứng.

### 11.3 Logging

`Timber.plant(new Timber.DebugTree())` ở debug; release dùng `ReleaseTree` lọc log level (no PII):

```java
public class ReleaseTree extends Timber.Tree {
    @Override protected boolean isLoggable(String tag, int priority) {
        return priority >= Log.WARN;
    }
    @Override protected void log(int priority, String tag, String message, Throwable t) {
        // log to local rolling file in cacheDir/logs/
    }
}
```

KHÔNG log path đầy đủ ở release (path có thể chứa thông tin user). Hash path nếu cần correlation.

## 12. Feature design (deep dive)

### 12.1 File browsing & list rendering

**Adapter:**

```java
public class FileListAdapter extends ListAdapter<FileNode, FileViewHolder> {
    public FileListAdapter() { super(DIFF); }

    private static final DiffUtil.ItemCallback<FileNode> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(FileNode a, FileNode b) {
            return a.path().equals(b.path());
        }
        @Override public boolean areContentsTheSame(FileNode a, FileNode b) {
            return a.sizeBytes() == b.sizeBytes()
                && a.lastModifiedMillis() == b.lastModifiedMillis();
        }
    };
}
```

**ViewHolder:**
- ImageView icon (MIME-based default + Glide overlay nếu thumbnail có)
- TextView name + size + date
- CheckBox cho selection mode (visibility GONE khi không trong selection)

**View modes:** List (default) | Grid (chỉ folder ảnh — toggle qua menu).

**Lazy thumbnail:**

```java
Glide.with(holder.itemView)
    .load(localFileForNode(node))
    .signature(new ObjectKey(node.lastModifiedMillis()))
    .placeholder(iconForMime(node))
    .into(holder.icon);
```

Cho archive entry: stream từ ArchiveSession qua custom Glide ModelLoader. Cache thumbnail vào `cacheDir/thumbs/<contentHash>`.

### 12.2 Properties dialog

`PropertiesDialogFragment` extends `BottomSheetDialogFragment`, host 4 tab qua `ViewPager2` + `TabLayout`.

**Permissions tab algorithm:**

```java
StructStat st = Os.stat(absolutePath);
PosixPermission perm = new PosixPermission(st.st_mode);
String owner = lookupUserName(st.st_uid);   // Os.getpwuid
String group = lookupGroupName(st.st_gid);  // Os.getgrgid

// Hiển thị:
//   Mode:   rwxr-xr-x (0755)
//   Owner:  {owner} (uid {st_uid})
//   Group:  {group} (gid {st_gid})
//   Atime:  {fmt(st.st_atime)}
//   Mtime:  {fmt(st.st_mtime)}
//   Ctime:  {fmt(st.st_ctime)}
//   Inode:  {st.st_ino}
//   Device: {st.st_dev}
//   Links:  {st.st_nlink}
```

Lookup user/group có thể fail trên một số path → fallback show số uid/gid trần.

**Hashes tab:** Khi user chuyển sang tab → submit `ComputeHashUseCase` → progress hiển thị %; có nút Copy beside mỗi hash.

### 12.3 Trash

**Layout per-storage-root:**

```
{storageRoot}/.AppTrash/
  files/
    {uuid}/{originalName}    ← rename giữ unique
  info/
    {uuid}.json              ← metadata
```

**Schema info JSON:**

```json
{
  "id": "9c2…",
  "originalPath": "file:///sdcard/Documents/note.txt",
  "displayName": "note.txt",
  "sizeBytes": 1234,
  "isDirectory": false,
  "deletedAt": 1716000000000,
  "expiresAt": 1718592000000
}
```

**Delete flow:**

1. Determine target storage root (path nằm trên storage nào).
2. Generate UUID; tạo `info/{uuid}.json` và `files/{uuid}/`.
3. `Files.move(src, files/{uuid}/{name}, REPLACE_EXISTING)` — atomic vì cùng partition.
4. Insert `TrashEntryEntity` vào Room.

Nếu file ở **archive** → trash KHÔNG áp dụng (xóa = xóa entry). UI cảnh báo "File trong archive sẽ bị xóa vĩnh viễn — không có Trash."

**Restore:** ngược lại — move từ trash về `originalPath`. Nếu `originalPath` đã có file khác → prompt rename/overwrite/skip.

**Cleanup:** `TrashCleanupWorker` (WorkManager, PeriodicWorkRequest 24h):

```java
@HiltWorker
public class TrashCleanupWorker extends Worker {
    @Override public Result doWork() {
        long now = System.currentTimeMillis();
        List<TrashEntryEntity> expired = trashDao.findExpired(now);
        for (TrashEntryEntity e : expired) {
            deleteFiles(e.trash_path);
            trashDao.delete(e);
        }
        return Result.success();
    }
}
```

### 12.4 Archive virtual filesystem (chi tiết)

**Open flow:**

```
User tap file.zip
   ↓
MimeDetector → "application/zip"
   ↓
ArchiveFileSystemProvider.resolve(archive://file%3A%2F%2F.../file.zip/)
   ↓
ArchiveSession.open(file.zip)
   ├─ Read header (8KB) để detect compression + encryption
   ├─ Chọn ArchiveBackend (ZipBackend)
   ├─ Iterator<ArchiveEntry> entries → build root ArchiveNode
   └─ Return root
   ↓
FileBrowserFragment hiển thị children
```

**Edit flow:**

1. User tap `entry.txt` bên trong → resolved thành `archive://.../entry.txt`.
2. Open editor → editor cần ghi → provider call `archiveSession.markDirty(entry)`.
3. Provider extract entry sang `cacheDir/archives/{archiveHash}/edits/entry.txt`.
4. Editor write vào file extracted; provider tự sync vào ArchiveSession dirty map.
5. Khi user back ra khỏi archive (rời browser, hoặc đóng app, hoặc LRU evict):
   - Nếu có dirty entry → confirm dialog "Lưu thay đổi vào archive?"
   - Nếu Yes → `repackArchive(session, dirtyEntries)`:
     1. Tạo `temp/{archiveHash}.repack`.
     2. Stream lại từng entry (dirty thì dùng version mới, không thì copy nguyên byte qua không decompress nếu cùng compression — optimization).
     3. Verify size + sample read.
     4. Atomic `Files.move(temp, originalArchive)`.

**ZIP password:**

- Detect bằng general purpose bit 0 trong ZIP local header.
- Prompt password dialog (`PasswordRequiredException` → catch ở ViewModel).
- Cache password trong `ArchiveSession` (memory only); KHÔNG persist.
- 3 lần sai → fail open.

**Cache management:**

- Cache root: `context.getCacheDir() + "/archives/"`.
- Size limit: 500MB hoặc 10% free space (chọn nhỏ hơn).
- Eviction: LRU theo last access; khi session close → xóa toàn bộ folder của session đó.
- WorkManager job dọn orphan cache (sessions đã close nhưng cache còn lại) hàng tuần.

### 12.5 Search (tên file)

**In-folder:** RecyclerView `Filter` qua adapter — `setFilter(query)` re-filter list hiện tại.

**Recursive:**

```java
public final class SearchFilesUseCase {
    public LiveData<List<FileNode>> execute(
            FilePath root, SearchQuery query, CancellationSignal cancel) {
        MutableLiveData<List<FileNode>> results = new MutableLiveData<>(new ArrayList<>());
        executors.io().submit(() -> {
            walk(root, query, results, cancel, new ArrayList<>());
        });
        return results;
    }

    private void walk(FilePath dir, SearchQuery q, MutableLiveData<List<FileNode>> sink,
                      CancellationSignal cancel, List<FileNode> acc) {
        if (cancel.isCancelled()) return;
        FileSystemProvider fs = registry.providerFor(dir);
        for (FileNode child : fs.list(dir, ListOptions.DEFAULT)) {
            if (q.matches(child)) {
                acc.add(child);
                if (acc.size() % 20 == 0) {
                    sink.postValue(new ArrayList<>(acc));
                }
            }
            if (child.isDirectory()) walk(child.path(), q, sink, cancel, acc);
        }
        sink.postValue(new ArrayList<>(acc));
    }
}
```

**`SearchQuery`:**

```java
public final class SearchQuery {
    public enum Mode { SUBSTRING, GLOB, REGEX }
    private final String pattern;
    private final Mode mode;
    private final boolean caseSensitive;
    private final EnumSet<MimeCategory> categories;  // optional filter
}
```

### 12.6 Dual-pane

**Layout:**

`activity_main.xml` qualified `values-w600dp/`:

```xml
<LinearLayout android:orientation="horizontal">
    <FrameLayout android:id="@+id/pane_left" android:layout_weight="1" />
    <View android:layout_width="1dp" android:background="?android:dividerHorizontal" />
    <FrameLayout android:id="@+id/pane_right" android:layout_weight="1" />
</LinearLayout>
```

`MainActivity` decide:
- Default layout (single pane) → host nav graph trong `R.id.nav_host`.
- w600dp layout → attach 2 `FileBrowserFragment` vào 2 frame, mỗi cái có ViewModel scoped riêng (key bằng "left"/"right").

**Drag-and-drop:** Long-press item → start `View.startDragAndDrop` với `ClipData` chứa `FilePath`. Drop target ở pane kia → MainActivity nhận → prompt "Copy / Move / Cancel".

### 12.7 Viewers

**Image viewer activity:**

```
ImageViewerActivity (FullScreen, no ActionBar)
  └─ ViewPager2 (cho swipe giữa các ảnh trong cùng folder)
       └─ SubsamplingScaleImageView (tiled) hoặc Glide ImageView (cho ảnh nhỏ)
```

Decision: kích thước file > 4MB hoặc resolution > 4000px → SubsamplingScaleImageView.

**Video / Audio:** Media3 PlayerView + MediaSession để OS media controls. Background playback cho audio (foreground service `mediaPlayback` type).

**Text editor:**

```
TextEditorActivity
  ├─ Toolbar (file name, save state)
  ├─ CodeEditor (sora-editor view)
  ├─ Bottom toolbar (encoding selector, line/col indicator, undo/redo)
  └─ Search bar (slide-down)
```

Save = atomic write: ghi vào `{name}.tmp` → fsync → rename đè. Cho archive: gọi `markDirty()`.

### 12.8 Multi-select & batch

`SelectionTracker` wraps `androidx.recyclerview.selection`:

```java
SelectionTracker<String> tracker = new SelectionTracker.Builder<>(
    "browser-selection",
    recyclerView,
    new FilePathKeyProvider(adapter),
    new FilePathDetailsLookup(recyclerView),
    StorageStrategy.createStringStorage())
    .withSelectionPredicate(SelectionPredicates.createSelectAnything())
    .build();
```

Khi selection > 0 → ViewModel emit `UiState.Selecting(count)` → Fragment start ActionMode với menu copy/move/delete/properties/share.

### 12.9 Bookmarks

`BookmarkDrawerFragment` trong Navigation Drawer:
- Section 1: Storage roots (auto, không xóa được).
- Section 2: Quick links (Downloads, DCIM, ...).
- Section 3: User bookmarks (drag-to-reorder, swipe-to-delete).

ViewModel observe `bookmarkDao.observeAll()` → render.

## 13. UI / UX specification

### 13.1 Theming

```
themes.xml
  Theme.FileManager (parent Theme.Material3.DynamicColors.DayNight)
    ├─ Theme.FileManager.Splash
    ├─ Theme.FileManager.NoActionBar
    └─ Theme.FileManager.Fullscreen   (viewers)
```

Material You: dùng `DynamicColors.applyToActivitiesIfAvailable(this)` trong `Application.onCreate()`.

### 13.2 Navigation graph

```
nav_graph.xml
  ├─ FileBrowserFragment (start destination)
  │    actions: → PropertiesDialog, → SearchFragment, → TrashFragment
  ├─ TrashFragment
  ├─ SearchFragment
  ├─ BookmarkManagerFragment
  └─ SettingsFragment
```

Viewer là `Activity` riêng (không trong nav graph) — vì cần fullscreen, không cần shared transition với browser hợp lý.

### 13.3 Breakpoint

| Width | Layout |
|-------|--------|
| < 600dp | Single pane + drawer (overlay) |
| 600–839dp | Single pane + drawer (rail) |
| ≥ 840dp | Dual pane + drawer (permanent) |

### 13.4 Predictive Back

- `OnBackPressedDispatcher` với callback đăng ký ở mỗi Fragment.
- `android:enableOnBackInvokedCallback="true"` trong `<application>`.
- Selection mode active → back exit selection (KHÔNG navigate up).
- Trong archive → back navigate lên parent ảo trong archive trước khi exit archive.

## 14. Dependencies & versions

> Pinned trong `gradle/libs.versions.toml`. Các version dưới là baseline tại thời điểm khởi tạo project; bump theo schedule (xem Section 17).

```toml
[versions]
agp = "8.7.3"
hilt = "2.51.1"
room = "2.6.1"
lifecycle = "2.8.7"
navigation = "2.8.5"
work = "2.10.0"
material = "1.12.0"
media3 = "1.5.1"
glide = "4.16.0"
subsampling = "3.10.0"
sora = "0.23.4"
sora-textmate = "0.23.4"
commons-compress = "1.27.1"
zip4j = "2.11.5"
juniversalchardet = "2.5.0"
timber = "5.0.1"
desugar = "2.1.4"
junit = "4.13.2"
mockito = "5.12.0"
robolectric = "4.13"
androidx-test = "1.6.1"
espresso = "3.6.1"
datastore = "1.1.1"

[libraries]
androidx-core = { module = "androidx.core:core", version = "1.15.0" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version = "1.7.0" }
androidx-activity = { module = "androidx.activity:activity", version = "1.9.3" }
androidx-fragment = { module = "androidx.fragment:fragment", version = "1.8.5" }
androidx-lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
androidx-lifecycle-livedata = { module = "androidx.lifecycle:lifecycle-livedata", version.ref = "lifecycle" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime", version.ref = "lifecycle" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
androidx-navigation-fragment = { module = "androidx.navigation:navigation-fragment", version.ref = "navigation" }
androidx-navigation-ui = { module = "androidx.navigation:navigation-ui", version.ref = "navigation" }
androidx-documentfile = { module = "androidx.documentfile:documentfile", version = "1.0.1" }
androidx-work = { module = "androidx.work:work-runtime", version.ref = "work" }
material = { module = "com.google.android.material:material", version.ref = "material" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version = "1.2.0" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version = "1.2.0" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
glide = { module = "com.github.bumptech.glide:glide", version.ref = "glide" }
glide-compiler = { module = "com.github.bumptech.glide:compiler", version.ref = "glide" }
subsampling-image = { module = "com.davemorrissey.labs:subsampling-scale-image-view-androidx", version.ref = "subsampling" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
media3-session = { module = "androidx.media3:media3-session", version.ref = "media3" }
sora-editor = { module = "io.github.Rosemoe.sora-editor:editor", version.ref = "sora" }
sora-language-textmate = { module = "io.github.Rosemoe.sora-editor:language-textmate", version.ref = "sora-textmate" }
commons-compress = { module = "org.apache.commons:commons-compress", version.ref = "commons-compress" }
zip4j = { module = "net.lingala.zip4j:zip4j", version.ref = "zip4j" }
juniversalchardet = { module = "com.github.albfernandez:juniversalchardet", version.ref = "juniversalchardet" }
timber = { module = "com.jakewharton.timber:timber", version.ref = "timber" }
desugar = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugar" }
junit = { module = "junit:junit", version.ref = "junit" }
mockito-core = { module = "org.mockito:mockito-core", version.ref = "mockito" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test" }
androidx-test-rules = { module = "androidx.test:rules", version.ref = "androidx-test" }
androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

> Kiểm tra version mới nhất tại thời điểm tạo project trước khi commit `libs.versions.toml`. Các version trên là baseline tham khảo cuối 2025 / đầu 2026.

## 15. Testing strategy

### 15.1 Pyramid

| Layer | Loại | Count target | Tool |
|-------|------|--------------|------|
| Domain | Pure unit | ~70% test | JUnit 4 + Mockito |
| Repository / Provider | Integration unit | ~20% test | Robolectric (cho `Os`, `Environment`) hoặc thư mục thật trong `tmp_dir` |
| ViewModel | Unit với LiveData test rule | ~5% test | JUnit + ArchUnit cho LiveData |
| UI critical paths | Instrumentation | ~5% test | Espresso, chỉ smoke flow chính |

### 15.2 Test scope bắt buộc

**Phải có test cho:**
- `FilePath` (URI parse, normalize, parent/child, equals/hash)
- `PosixPermission` (mode bits → rwx + octal)
- `LocalFileSystemProvider` (create/list/rename/delete trong `tmpDir`)
- `ArchiveSession` (open ZIP/TAR mẫu, list entries, edit + repack, verify hash)
- `CopyFilesUseCase` (cancel mid-copy, conflict policies)
- `TrashRepositoryImpl` (delete → restore → empty)
- `SearchFilesUseCase` (cancel, incremental result)

**Smoke instrumentation:**
- Mở app → grant permission → browse `/sdcard` → tap file → properties dialog hiển thị.
- Multi-select 2 file → copy sang folder khác.
- Mở ZIP nhỏ → navigate → mở text entry → edit → save → re-open verify content.

## 16. Build, signing & release

### 16.1 Build types & flavors

- `debug` — `applicationIdSuffix = ".debug"`, logging max, không minify.
- `release` — minify + shrink + Proguard, signed.

Không cần flavor ở v1.

### 16.2 ProGuard / R8

Rules tối thiểu (sora-editor, Hilt, Room đa số đã có consumer rules). Thêm:

```
# sora-editor reflection
-keep class io.github.rosemoe.sora.** { *; }

# Commons Compress 7z entries reflection
-keep class org.apache.commons.compress.archivers.** { *; }

# Zip4j
-keep class net.lingala.zip4j.** { *; }

# Hilt generated
-keep class hilt_aggregated_deps.** { *; }
```

### 16.3 Release checklist

- [ ] `versionCode++`, `versionName` semver.
- [ ] CHANGELOG.md cập nhật.
- [ ] App Bundle build (`./gradlew :app:bundleRelease`).
- [ ] Test smoke trên Android 11, 13, 14, 16 (emulator + device thực).
- [ ] Privacy Policy URL trong Play Console.
- [ ] Permission Declaration Form điền — lý do MANAGE_EXTERNAL_STORAGE: "Core file management — read/write across user storage to provide a general-purpose file browser."

## 17. Coding conventions

### 17.1 Style

- Indent 4 space, no tab.
- Line length 120.
- Brace K&R style.
- Import order: java.*, javax.*, android.*, androidx.*, third-party, project.
- Định dạng qua `google-java-format` (script `format.sh`).

### 17.2 Naming

- Class: `PascalCase`.
- Method/field: `camelCase`.
- Constant: `UPPER_SNAKE`.
- Test class: `{TargetClass}Test`.
- Resource id: `snake_case` với prefix theo type (`btn_*`, `tv_*`, `rv_*`, `frag_*`).

### 17.3 Java practices

- Prefer immutable: dùng `final` cho field, `List.copyOf()` thay vì expose raw collection.
- Optional chỉ ở return type của API public; KHÔNG dùng cho field hay parameter.
- `@Nullable` / `@NonNull` annotation cho mọi public API (dùng `androidx.annotation`).
- `try-with-resources` cho mọi stream.

### 17.4 Architecture guard

Mọi PR phải pass:
- Không có `import android.*` trong package `domain/`.
- Không có `import org.apache.commons.compress.*` trong package `presentation/`.
- Không có `Runtime.exec`, `ProcessBuilder` với arg "su" (regex test trong CI).

Có thể enforce qua ArchUnit hoặc custom Gradle task.

### 17.5 Dependency update cadence

- Patch (x.y.**Z**): tự động bump qua Renovate / Dependabot, merge sau khi CI green.
- Minor (x.**Y**.z): review monthly.
- Major (**X**.y.z): plan riêng, có testing matrix.

## 18. Extension points (v2+)

Spec design sẵn cho mở rộng — KHÔNG implement v1 nhưng giữ chỗ:

### 18.1 Network filesystem

Thêm `FtpFileSystemProvider`, `SftpFileSystemProvider`, `SmbFileSystemProvider`, `WebDavFileSystemProvider`. Đăng ký vào `FileSystemRegistry` qua Hilt multibinding. Không sửa ViewModel/UseCase/UI nào.

Credential management: thêm `data/credential/` với encrypted-shared-prefs hoặc Tink. UI: dialog request credential khi resolve fail với `AccessDeniedException`.

### 18.2 Cloud

Cloud provider implement cùng interface (`GoogleDriveFileSystemProvider`, etc.). Khác biệt: auth flow (OAuth) → cần extension `AuthenticationProvider` interface song song.

### 18.3 Snapshot

Plan-able:
- Level 1: thêm `SnapshotRepository` chỉ ghi metadata (path + sha + mtime) vào Room. Job định kỳ snapshot folder bookmarked.
- Level 2+: deferred.

Khi thêm KHÔNG cần sửa `FileSystemProvider` (snapshot là consumer, không phải provider).

### 18.4 Content search (grep)

Thêm `ContentSearchUseCase` song song `SearchFilesUseCase`. Sử dụng existing executors. Implement trên `LocalFileSystemProvider` trước; archive sau (cần extract qua stream).

### 18.5 Plugin format (lâu dài)

Có thể expose `ArchiveBackend` interface ở public package + ServiceLoader để third-party app cài codec/format plugin. Cần plan kỹ về security, signature verification — không trong scope v1/v2 gần.

## 19. Glossary

| Term | Definition |
|------|------------|
| **FilePath** | Value object kết hợp scheme + authority + path. Thay thế raw String. |
| **FileNode** | Composite node đại diện file/folder, metadata-only. |
| **FileSystemProvider** | SPI cho backend filesystem (Local, Archive, future Network). |
| **ArchiveSession** | Stateful object đại diện một archive đang mở, quản lý cache + dirty. |
| **PosixPermission** | Mode bits POSIX (rwxr-xr-x + setuid/gid/sticky). |
| **Operation** | Verb có thể chạy dài, có progress, có cancel (Copy/Move/Extract...). |
| **Provider scheme** | Chuỗi định danh provider trong URI: `"file"`, `"archive"`. |
| **Trash root** | Folder `.AppTrash/` trên một storage root, chỉ chứa file deleted của storage đó. |
| **Dual-pane** | Layout hai panel song song trên màn hình ≥ 600dp / landscape. |

---

## Phụ lục A — Quy ước AndroidManifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
                     tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <queries>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:mimeType="*/*" />
        </intent>
    </queries>

    <application
        android:name=".FileManagerApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@style/Theme.FileManager"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:enableOnBackInvokedCallback="true"
        android:requestLegacyExternalStorage="false"
        tools:targetApi="36">

        <activity
            android:name=".ui.permission.PermissionGateActivity"
            android:exported="true"
            android:theme="@style/Theme.FileManager.Translucent"
            android:noHistory="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name=".ui.MainActivity"
                  android:exported="false"
                  android:theme="@style/Theme.FileManager.NoActionBar" />

        <activity android:name=".ui.viewer.ImageViewerActivity"
                  android:theme="@style/Theme.FileManager.Fullscreen" />
        <activity android:name=".ui.viewer.VideoPlayerActivity"
                  android:theme="@style/Theme.FileManager.Fullscreen"
                  android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden" />
        <activity android:name=".ui.viewer.AudioPlayerActivity" />
        <activity android:name=".ui.viewer.TextEditorActivity" />

        <service
            android:name=".ui.operations.OperationService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
    </application>
</manifest>
```

## Phụ lục B — Sequence diagram (copy operation)

```
User           Fragment          ViewModel        UseCase         Repository    Provider
 │  tap copy     │                  │                │                │              │
 ├──────────────►│                  │                │                │              │
 │               │ onCopyClicked()  │                │                │              │
 │               ├─────────────────►│                │                │              │
 │               │                  │ startService() │                │              │
 │               │                  ├──────► OperationService         │              │
 │               │                  │                │ execute()      │              │
 │               │                  │                ├───────────────►│              │
 │               │                  │                │                │ copyAll()    │
 │               │                  │                │                ├─────────────►│
 │               │                  │                │                │              │ openRead(src)
 │               │                  │                │                │              │ openWrite(dst)
 │               │                  │                │                │              │ stream + progress
 │               │                  │ ◄────── notification update ────┤              │
 │               │ ◄── LiveData snackbar "Done" ────┤                │              │
```

## Phụ lục C — Hệ quả của quyết định "no root"

| Capability | Có | Không |
|------------|-----|-------|
| Browse `/sdcard`, SD card | ✓ | |
| Truy cập `Android/data/*` của app khác | | ✗ |
| Truy cập `/system`, `/data` | | ✗ |
| Đọc POSIX permission của file truy cập được | ✓ | |
| Lookup owner/group name | ✓ (qua `Os.getpwuid`) | Một số path fail |
| Đổi owner/group/permission | | ✗ |
| Đọc SELinux context | | ✗ |
| Mount/unmount partition | | ✗ |
| Chỉnh `/etc/hosts`, system apps | | ✗ |
| Trash atomic move cùng partition | ✓ | |
| Truy cập Trash của hệ thống (MediaStore) | | ✗ (chủ động bỏ — confusing) |

> Quyết định này có nghĩa: app KHÔNG có chế độ "advanced root mode" và sẽ không bao giờ có. Người dùng cần root features nên dùng app khác (MT Manager, Root Explorer). Đây là **một product position rõ ràng**, không phải limitation.
