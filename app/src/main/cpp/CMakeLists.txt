# Phase C-2a: NDK + CMake skeleton cho libarchive bridge.
#
# Stub này build 1 shared lib (libarchive_bridge.so) chứa duy nhất nativeVersion() —
# verify toolchain (NDK + CMake + JNI load) hoạt động trước khi vendor libarchive source
# thật ở Phase C-2b. Lib name `libarchive_bridge` (không phải `libarchive` để tránh đụng
# tên với upstream libarchive ở C-2b sau).

cmake_minimum_required(VERSION 3.22.1)
project(libarchive_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

add_library(libarchive_bridge SHARED
    libarchive_bridge.cpp
)

# Android log API (__android_log_print) — built-in NDK system library.
find_library(log-lib log)

target_link_libraries(libarchive_bridge PRIVATE ${log-lib})
