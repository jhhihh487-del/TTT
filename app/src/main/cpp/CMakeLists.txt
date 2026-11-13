cmake_minimum_required(VERSION 3.22.1)
project(local_image_studio LANGUAGES CXX)

add_library(local_image_engine SHARED native-lib.cpp)
find_library(log-lib log)
target_link_libraries(local_image_engine ${log-lib})

# Phase 3: add a pinned, Android-NDK-compatible stable-diffusion.cpp source tree.
# Do not add INTERNET permission; model files are selected from local storage.
