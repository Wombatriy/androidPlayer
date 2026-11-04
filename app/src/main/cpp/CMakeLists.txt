cmake_minimum_required(VERSION 3.22.1)
project(rubberband_player LANGUAGES CXX)

# Only used in an explicitly opt-in Gradle build: -Prubberband=true.
# The user must separately fetch official Rubber Band 4.0.0 source
# into vendor/rubberband via tools/dsp/prepare-rubberband.ps1.
set(RB_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/vendor/rubberband")
set(RB_SINGLE "${RB_ROOT}/single/RubberBandSingle.cpp")
if(NOT EXISTS "${RB_SINGLE}")
    message(FATAL_ERROR
        "Rubber Band 4.0.0 source not found at ${RB_ROOT}. "
        "Run tools/dsp/prepare-rubberband.ps1 from the project root.")
endif()

add_library(rubberband_player SHARED
    rubberband_jni.cpp
    "${RB_SINGLE}")

target_include_directories(rubberband_player PRIVATE "${RB_ROOT}")
target_compile_features(rubberband_player PRIVATE cxx_std_17)
target_compile_options(rubberband_player PRIVATE -O2 -fexceptions)
# Defensively request 16 KiB page-aligned PT_LOAD segments even if a
# developer configures an older NDK. Verification of the built .so is
# mandatory before publishing, see tools/release/verify-android-release.ps1.
target_link_options(rubberband_player PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")
find_library(log_lib log)
target_link_libraries(rubberband_player PRIVATE "${log_lib}")
