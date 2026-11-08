cmake_minimum_required(VERSION 3.22)
project(r1 CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD   11)

get_filename_component(RFX_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../rfx" ABSOLUTE)
set(PVSC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/pvsc/instruments/PicoFaceM1")

include(${RFX_DIR}/tools/rsxsynth/cmake/RegrooveSynth.cmake)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/engine
    ${PVSC_DIR}/include
)

rsxsynth_add_app(r1
    SOURCES
        src/main.cpp
        engine/r1_engine.cc
    ENGINE
        ${PVSC_DIR}/src/m1_engine/m1_engine.cpp
        ${PVSC_DIR}/src/m1_engine/m1_fx.cpp
)

install(TARGETS r1 DESTINATION bin)
