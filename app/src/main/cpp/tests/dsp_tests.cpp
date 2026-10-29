#include <cassert>
#include <cmath>
#include <cstdio>

#include "Dsp.h"
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

int main() {
    percentIsClamped();
    limiterIsTransparentBelowThreshold();
    limiterStaysBelowFullScale();
    gainRampReachesTargetWithoutJump();
    ringBufferKeepsOrderAndBounds();
    std::puts("dsp_tests: OK");
    return 0;
}
