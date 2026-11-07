cmake_minimum_required(VERSION 3.22)
project(purism_probe LANGUAGES C CXX)
include(FetchContent)
set(PURISM_CORE_ABI v5 CACHE STRING "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
FetchContent_Declare(purism
    GIT_REPOSITORY https://github.com/SakuraMotion/PurismCore.git
    GIT_TAG 1069334965522df5d0b791e97f01e26b19c45456)
FetchContent_MakeAvailable(purism)
add_executable(core-probe probe.cpp)
target_compile_features(core-probe PRIVATE cxx_std_17)
target_compile_definitions(core-probe PRIVATE PSM_COMPAT_VERSION=0x05010000L)
target_include_directories(core-probe PRIVATE ${purism_SOURCE_DIR}/include)
target_link_libraries(core-probe PRIVATE dl)
