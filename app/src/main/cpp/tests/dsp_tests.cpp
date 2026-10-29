#include <cassert>
#include <cmath>
#include <cstdio>

#include "Dsp.h"
#include "EffectsChain.h"
#include "SpscRingBuffer.h"

static void percentIsClamped() {
    assert(dsp::clampPercent(500) == 200);
    assert(dsp::clampPercent(-3) == 0);
    assert(dsp::clampPercent(132) == 132);
}

static void limiterIsTransparentBelowThreshold() {
    assert(dsp::softLimit(0.5f) == 0.5f);
    assert(dsp::softLimit(-0.8f) == -0.8f);
}

static void limiterStaysBelowFullScale() {
    // 20000 × 200 % dépasse la pleine échelle : le limiteur doit rester sous l'écrêtage.
    float boosted = dsp::int16ToFloat(20000) * 2.0f;
    float limited = dsp::softLimit(boosted);
    assert(limited > 0.8f && limited < 1.0f);
    assert(dsp::softLimit(-boosted) == -limited);
}

static void gainRampReachesTargetWithoutJump() {
    assert(dsp::rampGain(1.0f, 0.0f, 3, 4) == 0.0f);         // Dernière image : cible atteinte.
    assert(std::fabs(dsp::rampGain(1.0f, 0.0f, 0, 4) - 0.75f) < 1e-6f);  // Première : un quart du chemin.
    assert(dsp::rampGain(0.5f, 0.5f, 0, 4) == 0.5f);         // Gain stable : inchangé.
}

static void ringBufferKeepsOrderAndBounds() {
    SpscRingBuffer<int16_t> q(4);
    int16_t in[] = {1, 2, 3, 4, 5};
    assert(q.write(in, 5) == 4);  // Capacité atteinte : le 5e est refusé.
    int16_t out[4] = {};
    assert(q.read(out, 2) == 2 && out[0] == 1 && out[1] == 2);
    q.skip(1);
    assert(q.read(out, 4) == 1 && out[0] == 4);
    assert(q.size() == 0);
}

// Amplitude de sortie d'un sinus traité par la chaîne, une fois le régime établi.
static float sineGain(const dsp::EffectsParams& params, float frequency) {
    dsp::EffectsChain chain;
    chain.configure(params, 48000);
    float peak = 0;
    for (int f = 0; f < 48000; ++f) {
        float x = 0.1f * std::sin(2.0f * static_cast<float>(M_PI) * frequency * f / 48000);
        float frame[2] = {x, x};
        chain.process(frame, 1);
        if (f > 24000) peak = std::fmax(peak, std::fabs(frame[0]));
    }
    return peak / 0.1f;
}

static void flatEqIsTransparent() {
    dsp::EffectsParams flat;
    assert(std::fabs(sineGain(flat, 1000) - 1.0f) < 0.01f);
}

static void eqBandBoostsItsFrequencyOnly() {
    dsp::EffectsParams p;
    p.eqDb[5] = 6.0f;  // 1 kHz +6 dB (×2).
    assert(std::fabs(sineGain(p, 1000) - 2.0f) < 0.1f);
    assert(std::fabs(sineGain(p, 62) - 1.0f) < 0.05f);
}

static void compressorReducesLoudSignal() {
    dsp::EffectsParams p;
    p.limiter = false;
    p.compressorAmount = 1.0f;
    dsp::EffectsChain chain;
    chain.configure(p, 48000);
    float frame[2] = {};
    for (int i = 0; i < 4800; ++i) { frame[0] = frame[1] = 0.9f; chain.process(frame, 1); }
    assert(frame[0] < 0.9f);  // -1 dBFS soutenu : nettement réduit malgré le rattrapage.
}

static void monoSumsChannels() {
    dsp::EffectsParams p;
    p.mono = true;
    dsp::EffectsChain chain;
    chain.configure(p, 48000);
    float frame[2] = {0.4f, 0.0f};
    chain.process(frame, 1);
    assert(frame[0] == 0.2f && frame[1] == 0.2f);
}

int main() {
    flatEqIsTransparent();
    eqBandBoostsItsFrequencyOnly();
    compressorReducesLoudSignal();
    monoSumsChannels();
    percentIsClamped();
    limiterIsTransparentBelowThreshold();
    limiterStaysBelowFullScale();
    gainRampReachesTargetWithoutJump();
    ringBufferKeepsOrderAndBounds();
    std::puts("dsp_tests: OK");
    return 0;
}
