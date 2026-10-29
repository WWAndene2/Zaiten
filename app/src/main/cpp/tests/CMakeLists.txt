# Tests du DSP sur la machine hôte (sans Android) :
#   cmake -S app/src/main/cpp/tests -B build/cpp-tests && cmake --build build/cpp-tests && ctest --test-dir build/cpp-tests
cmake_minimum_required(VERSION 3.16)
project(zaiten_dsp_tests CXX)
set(CMAKE_CXX_STANDARD 17)
enable_testing()
add_executable(dsp_tests dsp_tests.cpp)
target_include_directories(dsp_tests PRIVATE ../engine)
add_test(NAME dsp_tests COMMAND dsp_tests)
