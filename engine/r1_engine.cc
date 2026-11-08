// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Gerard Braad (Regroove | RGRV)

#include "r1_engine.h"

#include <cmath>
#include <cstdio>
#include <cstring>

namespace r1 {
namespace {

constexpr int kChannel = 0;
constexpr unsigned kVoices = 16;
constexpr float kGain = 1.0f;
constexpr float kLimitLevel = 0.7f;
constexpr float kLimitAttack = 0.02f;       /* ~1 ms at 48 kHz */
constexpr float kLimitRelease = 0.00042f;   /* ~50 ms at 48 kHz */
constexpr float kBendRange = 12.f;

/* The held peak spans the waveform's own peaks, so the gain settles at one
 * value: a chord loses level without its peaks being flattened. */
void limitPair(float& l, float& r, float& gain, float& peak) {
    const float al = l < 0.f ? -l : l;
    const float ar = r < 0.f ? -r : r;
    const float p = al > ar ? al : ar;
    peak -= kLimitRelease * peak;
    if (p > peak) peak = p;
    const float target = peak > kLimitLevel ? kLimitLevel / peak : 1.f;
    const float coeff = target < gain ? kLimitAttack : kLimitRelease;
    gain += (target - gain) * coeff;
    if (gain > 1.f) gain = 1.f;
    l *= gain;
    r *= gain;
    if (l > 1.f) l = 1.f;
    else if (l < -1.f) l = -1.f;
    if (r > 1.f) r = 1.f;
    else if (r < -1.f) r = -1.f;
}

/* The effect block is 25 bytes at 38; both effects are off. */
void disableEffects(uint8_t* program) {
    std::memset(program + 38, 0, 25);
    program[38] = 33;
    program[39] = 33;
}

/* A plain single-oscillator program: the split's own sample and level, an
 * open filter and a VDA that holds until release. */
void buildProgram(uint8_t* program, const m1unit::M1Rom& rom, int multisound) {
    std::memset(program, 0, m1::kProgLen);
    std::snprintf(reinterpret_cast<char*>(program), 11, "%-10.10s",
                  rom.multisoundName(multisound));
    program[10] = 0;   /* OSC mode: Single */
    program[11] = 0;   /* no mono, no hold */
    program[12] = static_cast<uint8_t>(multisound);
    program[32] = static_cast<uint8_t>(static_cast<int8_t>(kBendRange));
    disableEffects(program);

    uint8_t* osc = program + 63;
    osc[8] = 99;    /* VDF cutoff: open */
    osc[9] = 60;    /* VDF key centre */
    osc[11] = 0;    /* VDF EG intensity */
    osc[23] = 99;   /* VDA level */
    osc[24] = 60;   /* VDA key centre */
    osc[30] = 99;   /* VDA attack level */
    osc[31] = 0;    /* VDA decay time */
    osc[32] = 99;   /* VDA break point */
    osc[33] = 0;    /* VDA slope time */
    osc[34] = 99;   /* VDA sustain level */
    osc[35] = 10;   /* VDA release time */
}

}  // namespace

bool Synth::load(const char* dir, const char* preload) {
    std::lock_guard<std::mutex> guard(lock_);

    ready_ = false;
    status_[0] = 0;
    if (!dir || !rom_.load(dir, preload)) {
        std::snprintf(status_, sizeof status_, "%s", rom_.failure());
        return false;
    }
    if (!engine_.init(rom_.tables(), rom_.fxMem())) {
        std::snprintf(status_, sizeof status_, "engine init failed");
        return false;
    }
    engine_.setVoiceLimit(kVoices);
    res_.init(m1::kSampleRate, 48000);
    limGain_ = 1.f;
    limPeak_ = 0.f;
    program_ = 0;
    ready_ = true;
    applyProgram();
    return true;
}

void Synth::noteOn(uint8_t key, uint8_t velocity) {
    std::lock_guard<std::mutex> guard(lock_);
    if (ready_) engine_.noteOn(kChannel, key, velocity);
}

void Synth::noteOff(uint8_t key) {
    std::lock_guard<std::mutex> guard(lock_);
    if (ready_) engine_.noteOff(kChannel, key);
}

void Synth::allNotesOff() {
    std::lock_guard<std::mutex> guard(lock_);
    if (ready_) engine_.allSoundOff();
}

void Synth::setProgram(int index) {
    if (index < 0) index = 0;
    if (index > 99) index = 99;

    std::lock_guard<std::mutex> guard(lock_);
    if (!ready_ || index == program_) return;
    program_ = index;
    applyProgram();
}

const char* Synth::programName() {
    std::lock_guard<std::mutex> guard(lock_);
    const uint8_t* source = rom_.program(program_);
    if (source) {
        std::memcpy(name_, source, 10);
    } else {
        std::snprintf(name_, sizeof name_, "%s", rom_.multisoundName(program_));
    }
    name_[10] = 0;
    for (int i = 9; i >= 0 && (name_[i] == ' ' || name_[i] == 0); --i) name_[i] = 0;
    return name_;
}

void Synth::applyProgram() {
    const uint8_t* source = rom_.program(program_);
    if (source) {
        std::memcpy(programBuf_, source, m1::kProgLen);
        disableEffects(programBuf_);
        programBuf_[32] = static_cast<uint8_t>(static_cast<int8_t>(kBendRange));
    } else {
        buildProgram(programBuf_, rom_, program_);
    }
    engine_.allSoundOff();
    engine_.playProgram(programBuf_);
}

void Synth::render(float* left, float* right, size_t frames) {
    if (!left || !right) return;

    std::lock_guard<std::mutex> guard(lock_);
    if (!ready_) {
        std::memset(left, 0, frames * sizeof(float));
        std::memset(right, 0, frames * sizeof(float));
        return;
    }

    size_t done = 0;
    while (done < frames) {
        size_t n = frames - done;
        if (n > m1::kRenderBlock) n = m1::kRenderBlock;
        res_.render(engine_, mix_, n, kGain);
        for (size_t i = 0; i < n; ++i) {
            float l = mix_[2 * i];
            float r = mix_[2 * i + 1];
            limitPair(l, r, limGain_, limPeak_);
            left[done + i] = l;
            right[done + i] = r;
        }
        done += n;
    }
}

}  // namespace r1
