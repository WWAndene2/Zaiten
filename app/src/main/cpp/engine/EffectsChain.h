#pragma once

#include <array>
#include <cmath>

#include "Biquad.h"
#include "Compressor.h"
#include "Dsp.h"
#include "Spatializer.h"

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
    float spatialAmount = 0.0f;
    Room room = Room::None;
    float preampDb = 0.0f;
    bool crossfeed = false;
};

// Crossfeed (§4.3) : chaque oreille reçoit un peu de l'autre canal, filtré passe-bas (~700 Hz),
// comme avec des enceintes ; réduit la fatigue d'une stéréo très séparée au casque.
class Crossfeed {
public:
    void process(float& l, float& r) {
        lowL_ += kCoeff * (l - lowL_);
        lowR_ += kCoeff * (r - lowR_);
        float outL = (l + kAmount * lowR_) * kNorm;
        float outR = (r + kAmount * lowL_) * kNorm;
        l = outL;
        r = outR;
    }

private:
    static constexpr float kCoeff = 0.0874f;  // 1 − exp(−2π × 700 / 48 000).
    static constexpr float kAmount = 0.3f;
    static constexpr float kNorm = 1.0f / (1.0f + kAmount);  // Pas de hausse de niveau au centre.
    float lowL_ = 0, lowR_ = 0;
};

// Étapes du mix final (§7.2) : préampli, égaliseur global, crossfeed, spatialisation, compresseur,
// mono, limiteur.
// La spatialisation est appliquée au mix et non par appli comme le prévoit le §7.2 (point 5) :
// placer chaque appli dans l'espace (§3.2, 3D par appli) demande un spatialiseur par piste.
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
        spatializer_.configure(params.spatialAmount, params.room);
        preampGain_ = std::pow(10.0f, params.preampDb / 20.0f);
    }

    // Traite des images stéréo entrelacées, en place.
    void process(float* interleaved, int frames) {
        for (int f = 0; f < frames; ++f) {
            float l = interleaved[2 * f] * preampGain_;
            float r = interleaved[2 * f + 1] * preampGain_;
            for (int b = 0; b < kEqBands; ++b) {
                if (!eqActive_[b]) continue;  // Bande à 0 dB : filtre neutre, on économise le calcul.
                l = left_[b].process(coefficients_[b], l);
                r = right_[b].process(coefficients_[b], r);
            }
            if (params_.crossfeed) crossfeed_.process(l, r);
            spatializer_.process(l, r);
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
    Spatializer spatializer_;
    Crossfeed crossfeed_;
    float preampGain_ = 1.0f;
};

}  // namespace dsp
