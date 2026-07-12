cmake_minimum_required(VERSION 3.22.1)

project(
        NetworkQA
        VERSION 1.0.0
        LANGUAGES C CXX ASM
)

set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# Dobby uses GNU extensions such as typeof in its internal list macros.
set(CMAKE_CXX_EXTENSIONS ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

# This maintained Dobby fork avoids the Apple-only ARM64 assembly path that
# fails with modern Android NDK toolchains. The revision is pinned so CI and
# Android Studio resolve exactly the same native dependency.
set(DOBBY_GENERATE_SHARED ON CACHE BOOL "" FORCE)
set(DOBBY_DEBUG OFF CACHE BOOL "" FORCE)
set(DynamicBinaryInstrument OFF CACHE BOOL "" FORCE)
set(NearBranch ON CACHE BOOL "" FORCE)
set(FullFloatingPointRegisterPack OFF CACHE BOOL "" FORCE)
set(Plugin.SymbolResolver ON CACHE BOOL "" FORCE)
set(Plugin.GlobalOffsetTableHook OFF CACHE BOOL "" FORCE)
set(Plugin.LinkerLoadCallback OFF CACHE BOOL "" FORCE)
set(Plugin.ApplicationEventMonitor OFF CACHE BOOL "" FORCE)
set(Plugin.Android.BionicLinkerRestriction OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
        dobby_source
        GIT_REPOSITORY https://github.com/BepInEx/Dobby.git
        GIT_TAG 9d271803881f467aabfc24593c7dcaeb710fe861
        GIT_PROGRESS TRUE
)

FetchContent_MakeAvailable(dobby_source)

add_library(
        NetworkQA
        SHARED
        NetworkEmulation.cpp
)

set_target_properties(
        NetworkQA
        PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
        CXX_EXTENSIONS OFF
)

target_compile_options(
        NetworkQA
        PRIVATE
        -Wall
        -Wextra
        -Wpedantic
        -fvisibility=hidden
        -fvisibility-inlines-hidden
)

target_link_options(
        NetworkQA
        PRIVATE
        "-Wl,--no-undefined"
)

find_library(ANDROID_LOG_LIBRARY log REQUIRED)
find_library(ANDROID_NATIVE_LIBRARY android REQUIRED)
find_library(ANDROID_DL_LIBRARY dl REQUIRED)

target_link_libraries(
        NetworkQA
        PRIVATE
        dobby
        ${ANDROID_LOG_LIBRARY}
        ${ANDROID_NATIVE_LIBRARY}
        ${ANDROID_DL_LIBRARY}
)
