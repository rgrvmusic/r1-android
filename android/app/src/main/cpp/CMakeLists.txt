cmake_minimum_required(VERSION 3.22.1)

project(r1)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD   11)

# r1-android, and the shared tree that holds SDL3, Dear ImGui and rsxsynth.
get_filename_component(ROOT_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
get_filename_component(RFX_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../rfx" ABSOLUTE)

set(RSXSYNTH_DIR ${RFX_DIR}/tools/rsxsynth)
set(SDL3_DIR     ${RFX_DIR}/shared/lib/SDL3)
set(IMGUI_DIR    ${RFX_DIR}/shared/lib/imgui)
set(PVSC_DIR     ${ROOT_DIR}/third_party/pvsc/instruments/PicoFaceM1)

set(SDL_SHARED ON  CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
add_subdirectory(${SDL3_DIR} SDL3 EXCLUDE_FROM_ALL)

include_directories(
    ${RSXSYNTH_DIR}/include
    ${RFX_DIR}/shared/ui
    ${ROOT_DIR}/engine
    ${PVSC_DIR}/include
    ${SDL3_DIR}/include
    ${IMGUI_DIR}
    ${IMGUI_DIR}/backends
)

set(IMGUI_SOURCES
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp
    ${IMGUI_DIR}/backends/imgui_impl_sdl3.cpp
    ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp
)

find_library(log-lib     log)
find_library(android-lib android)

add_library(main SHARED
    ${ROOT_DIR}/src/main.cpp
    ${RSXSYNTH_DIR}/src/rsxsynth_app.cpp
    ${RFX_DIR}/shared/ui/piano_keyboard.cpp
    ${IMGUI_SOURCES}
    ${ROOT_DIR}/engine/r1_engine.cc
    ${PVSC_DIR}/src/m1_engine/m1_engine.cpp
    ${PVSC_DIR}/src/m1_engine/m1_fx.cpp
)

target_compile_definitions(main PRIVATE
    IMGUI_IMPL_OPENGL_ES3
)

target_link_libraries(main
    SDL3::SDL3
    ${log-lib}
    GLESv3
    ${android-lib}
    m
)
