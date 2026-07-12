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
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

# Dobby is pinned to a known commit so QA builds are reproducible.
set(DOBBY_DEBUG OFF CACHE BOOL "" FORCE)
set(DOBBY_BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(DOBBY_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(Plugin.SymbolResolver ON CACHE BOOL "" FORCE)
set(Plugin.ImportTableReplace OFF CACHE BOOL "" FORCE)
set(Plugin.Android.BionicLinkerUtil OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
        dobby_source
        GIT_REPOSITORY https://github.com/jmpews/Dobby.git
        GIT_TAG 5dfc8546954ce3b3198132ab13fddb89ee92cdd7
        GIT_PROGRESS TRUE
)

FetchContent_MakeAvailable(dobby_source)

add_library(
        NetworkQA
        SHARED
        NetworkEmulation.cpp
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
