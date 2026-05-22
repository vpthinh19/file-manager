package com.vpt.filemanager.node.source.archive;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * JNI bridge tới libarchive C++ library. Phase C-2a là skeleton — chỉ {@link #nativeVersion()}
 * stub để verify toolchain end-to-end (NDK + CMake + JNI load + System.loadLibrary).
 *
 * <p>Roadmap (xem [[project-phase-roadmap]] cho concrete deliverable mỗi sub-phase):
 * <ul>
 *   <li>C-2a (this): {@code nativeVersion()} stub returning placeholder string</li>
 *   <li>C-2b: vendor libarchive source + deps (bzip2/liblzma/zstd/lz4 — zlib qua NDK system),
 *       {@code nativeVersion()} return thật {@code archive_version_string()}</li>
 *   <li>C-2c: read-only listing — {@code openArchive(path)}, {@code listEntries(handle)}</li>
 *   <li>C-2d: streaming extract qua native pipe fd → {@code FileInputStream(fd)}</li>
 *   <li>C-2e: write/update path (zip rewrite tới temp, atomic replace), replace
 *       {@code ArchiveSource} backend từ Apache Commons Compress + Zip4j → libarchive,
 *       xóa 2 lib Java đó</li>
 * </ul>
 *
 * <p><b>arm64-v8a only</b> theo v1 decision (xem [[project-decisions-v1]]). Lib name
 * {@code liblibarchive_bridge.so}, tránh trùng với upstream {@code libarchive.so} sẽ vendor ở
 * C-2b.
 *
 * <p>{@code @Singleton} qua Hilt — caller inject thay vì giữ static, để future test có thể swap
 * mock instance qua Hilt test module.
 */
@Singleton
public final class LibarchiveBridge {
    static {
        System.loadLibrary("libarchive_bridge");
    }

    @Inject
    public LibarchiveBridge() {
    }

    /**
     * Phase C-2a: trả placeholder string. C-2b sẽ thay bằng {@code archive_version_string()}
     * thật từ libarchive (vd "libarchive 3.7.2 zlib/1.2.13 liblzma/5.4.5 ...").
     */
    @NonNull
    public native String nativeVersion();
}
