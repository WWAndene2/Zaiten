#pragma once

#include <array>

#include "Biquad.h"
#include "Compressor.h"
#include "Dsp.h"

namespace dsp {

constexpr int kEqBands = 10;
// Fréquences centrales ISO d'un égaliseur graphique 10 bandes (§2.4).
constexpr std::array<float, kEqBands> kEqFrequencies = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
// Q d'un égaliseur à l'octave (largeur d'une octave par bande).
constexpr float kEqQ = 1.41f;

struct EffectsParams {
    std::array<float, kEqBands> eqDb{};
    float compressorAmount = 0.0f;
    bool limiter = true;
    bool mono = false;
};

// Étapes du mix final (§7.2, point 7) : égaliseur global, compresseur, mono, limiteur.
class EffectsChain {
public:
    void configure(const EffectsParams& params, float sampleRate) {
        params_ = params;
        for (int b = 0; b < kEqBands; ++b) {
            bool active = params.eqDb[b] != 0.0f;
            // Bande réactivée : son état date d'un autre réglage, on le vide pour éviter un clic.
            if (active && !eqActive_[b]) left_[b] = right_[b] = BiquadState{};
            eqActive_[b] = active;
            coefficients_[b] = BiquadCoefficients::peaking(kEqFrequencies[b], params.eqDb[b], kEqQ, sampleRate);
        }
        compressor_.configure(params.compressorAmount, sampleRate);
    }

    // Traite des images stéréo entrelacées, en place.
    void process(float* interleaved, int frames) {
        for (int f = 0; f < frames; ++f) {
            float l = interleaved[2 * f];
            float r = interleaved[2 * f + 1];
            for (int b = 0; b < kEqBands; ++b) {
                if (!eqActive_[b]) continue;  // Bande à 0 dB : filtre neutre, on économise le calcul.
                l = left_[b].process(coefficients_[b], l);
                r = right_[b].process(coefficients_[b], r);
            }
            float g = compressor_.gainFor(l, r);
            l *= g;
            r *= g;
            if (params_.mono) l = r = 0.5f * (l + r);
            if (params_.limiter) {
                l = softLimit(l);
                r = softLimit(r);
            }
            // Sans limiteur, on borne quand même pour ne jamais envoyer hors de [-1, 1].
            interleaved[2 * f] = l < -1.0f ? -1.0f : (l > 1.0f ? 1.0f : l);
            interleaved[2 * f + 1] = r < -1.0f ? -1.0f : (r > 1.0f ? 1.0f : r);
        }
    }

private:
    EffectsParams params_;
    std::array<bool, kEqBands> eqActive_{};
    std::array<BiquadCoefficients, kEqBands> coefficients_{};
    std::array<BiquadState, kEqBands> left_{}, right_{};
    Compressor compressor_;
};

}  // namespace dsp
