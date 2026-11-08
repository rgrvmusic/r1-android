// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)

/* R1: the M1 engine as a standalone synth app. */

#include <cstdio>
#include <cstdlib>
#include <cstring>

#include <SDL3/SDL_main.h>
#include <SDL3/SDL_video.h>

#include "imgui.h"
#include "rsxsynth_adapter.h"
#include "rsxsynth_app.h"
#include "r1_engine.h"

#if defined(__ANDROID__)
#include <jni.h>

#include <SDL3/SDL_system.h>
#endif

namespace {

r1::Synth synth;
bool loaded = false;

/* <files>/Programs on Android, M1_ROM_DIR or ./Programs elsewhere. */
const char* programs_dir() {
    static thread_local char path[512];
#if defined(__ANDROID__)
    const char* base = SDL_GetAndroidInternalStoragePath();
    if (!base) return nullptr;
    std::snprintf(path, sizeof path, "%s/Programs", base);
#else
    const char* base = std::getenv("M1_ROM_DIR");
    std::snprintf(path, sizeof path, "%s", base ? base : "Programs");
#endif
    return path;
}

void ensure_loaded() {
    if (loaded) return;
    const char* dir = programs_dir();
    if (!dir) return;
    loaded = true;
    synth.load(dir, "m1-preload.syx");
}

void reload() {
    const char* dir = programs_dir();
    if (!dir) return;
    loaded = true;
    synth.load(dir, "m1-preload.syx");
}

/* The ROM screen in the app's Java side. */
void open_userfs() {
#if defined(__ANDROID__)
    JNIEnv* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
    jobject activity = static_cast<jobject>(SDL_GetAndroidActivity());
    if (!env || !activity) return;
    jclass cls = env->GetObjectClass(activity);
    jmethodID open = env->GetMethodID(cls, "openUserFs", "()V");
    if (open) env->CallVoidMethod(activity, open);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
    env->DeleteLocalRef(activity);
#endif
}

/* Copy a picked ALL DATA dump to <Programs>/m1-preload.syx. */
bool load_syx(RegrooveSynthAdapter*, const char* path) {
    const char* dir = programs_dir();
    if (!dir || !path) return false;

    char dst[600];
    std::snprintf(dst, sizeof dst, "%s/m1-preload.syx", dir);

    FILE* in = std::fopen(path, "rb");
    if (!in) return false;
    FILE* out = std::fopen(dst, "wb");
    if (!out) {
        std::fclose(in);
        return false;
    }
    char buf[1 << 16];
    size_t n;
    bool ok = true;
    while ((n = std::fread(buf, 1, sizeof buf, in)) > 0) {
        if (std::fwrite(buf, 1, n, out) != n) {
            ok = false;
            break;
        }
    }
    std::fclose(in);
    if (std::fclose(out) != 0) ok = false;
    if (ok) reload();
    return ok;
}

/* Preset previous/next, with the name in between, on the Main view. */
void render_main_ui(RegrooveSynthAdapter*) {
    ensure_loaded();
    if (!synth.ready()) {
        ImGui::TextDisabled("No ROMs");
        return;
    }
    if (ImGui::Button("-##preset")) synth.setProgram((synth.program() + 99) % 100);
    ImGui::SameLine();
    ImGui::Text("%s", synth.programName());
    ImGui::SameLine();
    if (ImGui::Button("+##preset")) synth.setProgram((synth.program() + 1) % 100);
}

void render_extra_ui(RegrooveSynthAdapter*) {
    ensure_loaded();

    if (!synth.ready()) ImGui::TextWrapped("ROMs: %s", synth.status());

    ImGui::Spacing();
#if defined(__ANDROID__)
    if (ImGui::Button("ROMs")) open_userfs();
#endif

    const char* dir = programs_dir();
    ImGui::TextDisabled("%s", dir ? dir : "(no storage)");
}

void note_on(void*, uint8_t key, uint8_t velocity) {
    synth.noteOn(key, velocity);
}
void note_off(void*, uint8_t key) { synth.noteOff(key); }
void all_notes_off(void*) { synth.allNotesOff(); }
void process(void*, float* left, float* right, int frames) {
    ensure_loaded();
    synth.render(left, right, static_cast<size_t>(frames));
}
void destroy(void*) {}

}  // namespace

#if defined(__ANDROID__)
/* MIDI bytes from R1MidiHandler. Note on/off and program change. */
extern "C" JNIEXPORT void JNICALL
Java_nl_rgrv_r1_R1MidiHandler_nativeMidiData(
    JNIEnv* env, jobject, jbyteArray data, jint offset, jint length)
{
    if (length < 1) return;
    jbyte* buf = env->GetByteArrayElements(data, nullptr);
    if (!buf) return;

    const uint8_t* msg = reinterpret_cast<const uint8_t*>(buf + offset);
    const uint8_t type = msg[0] & 0xf0;
    const uint8_t d1 = length > 1 ? msg[1] : 0;
    const uint8_t d2 = length > 2 ? msg[2] : 0;

    if (type == 0x90 && d2 > 0) synth.noteOn(d1, d2);
    else if (type == 0x80 || (type == 0x90 && d2 == 0)) synth.noteOff(d1);
    else if (type == 0xc0) synth.setProgram(d1);

    env->ReleaseByteArrayElements(data, buf, JNI_ABORT);
}

/* Called after the ROM screen closes, since its files may have changed. */
extern "C" JNIEXPORT void JNICALL
Java_nl_rgrv_r1_MainActivity_nativeReload(JNIEnv*, jobject)
{
    reload();
}
#endif

int main(int, char**) {
    RegrooveSynthAdapter adapter = {
        .ctx = &synth,
        .note_on = note_on,
        .note_off = note_off,
        .all_notes_off = all_notes_off,
        .process = process,
        .destroy = destroy,
        .set_morph = nullptr,
        .set_bend = nullptr,
        .set_volume = nullptr,
    };

    RsxSynthAppConfig cfg = {
        .window_title = "R1",
        .preset_filter_name = "M1 ALL DATA",
        .preset_filter_ext = "*.syx",
        .default_width = 800,
        .default_height = 500,
        .load_preset_file = load_syx,
        .render_extra_ui = render_extra_ui,
        .render_main_ui = render_main_ui,
    };

    return rsxsynth_run(&adapter, &cfg);
}
