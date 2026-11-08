// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)

#pragma once

#include <cstddef>
#include <cstdint>
#include <mutex>

#include "m1_engine/m1_engine.h"
#include "m1_resampler.h"
#include "m1_rom.h"

namespace r1 {

/* The M1 engine on the user's ROM set, rendered at 48 kHz. */
class Synth {
public:
    /* Reads <dir>/m1-*.bin; the preload may be absent. */
    bool load(const char* dir, const char* preload);

    bool ready() const { return ready_; }
    const char* status() const { return status_; }

    void noteOn(uint8_t key, uint8_t velocity);
    void noteOff(uint8_t key);
    void allNotesOff();

    void setProgram(int index);
    int program() const { return program_; }
    const char* programName();

    void render(float* left, float* right, size_t frames);

private:
    void applyProgram();

    m1unit::M1Rom rom_;
    m1::Engine engine_;
    m1unit::Resampler res_;
    std::mutex lock_;
    uint8_t programBuf_[m1::kProgLen];
    char name_[12];
    char status_[96];
    int program_ = 0;
    bool ready_ = false;
    float limGain_ = 1.f;
    float limPeak_ = 0.f;
    float mix_[2 * m1::kRenderBlock];
};

}  // namespace r1
