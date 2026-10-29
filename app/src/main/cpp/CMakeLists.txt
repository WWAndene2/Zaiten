cmake_minimum_required(VERSION 3.22.1)
project(zaiten_mixer CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(mixer_engine SHARED
    engine/MixerEngine.cpp
    jni_bridge.cpp)

target_link_libraries(mixer_engine oboe::oboe log)
