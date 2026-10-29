#pragma once

#include <cmath>

namespace dsp {

// Filtre biquad en forme directe I transposée, coefficients RBJ (Audio EQ Cookbook, R. Bristow-Johnson).
struct BiquadCoefficients {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;

    // Filtre en cloche : +/- gainDb autour de frequency, largeur q.
    static BiquadCoefficients peaking(float frequency, float gainDb, float q, float sampleRate) {
        const float a = std::pow(10.0f, gainDb / 40.0f);
        const float w0 = 2.0f * static_cast<float>(M_PI) * frequency / sampleRate;
        const float alpha = std::sin(w0) / (2.0f * q);
        const float cosw = std::cos(w0);
        const float a0 = 1.0f + alpha / a;
        BiquadCoefficients c;
        c.b0 = (1.0f + alpha * a) / a0;
        c.b1 = (-2.0f * cosw) / a0;
        c.b2 = (1.0f - alpha * a) / a0;
        c.a1 = (-2.0f * cosw) / a0;
        c.a2 = (1.0f - alpha / a) / a0;
        return c;
    }
};

struct BiquadState {
    float z1 = 0, z2 = 0;

    float process(const BiquadCoefficients& c, float x) {
        float y = c.b0 * x + z1;
        z1 = c.b1 * x - c.a1 * y + z2;
        z2 = c.b2 * x - c.a2 * y;
        return y;
    }
};

}  // namespace dsp
