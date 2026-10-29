#pragma once

#include <cmath>

namespace dsp {

// Compresseur à suivi de crête, stéréo lié (même gain sur les deux canaux pour garder l'image).
// amount 0 = contourné ; 1 = seuil -30 dBFS, taux 8:1. Le gain de rattrapage compense la moitié
// de la réduction maximale, pour que l'activation ne fasse pas chuter le volume perçu (§7.7).
class Compressor {
public:
    void configure(float amount, float sampleRate) {
        amount_ = amount;
        thresholdDb_ = -30.0f * amount;
        ratio_ = 1.0f + 7.0f * amount;
        makeupDb_ = -thresholdDb_ * (1.0f - 1.0f / ratio_) * 0.5f;
        attack_ = std::exp(-1.0f / (0.005f * sampleRate));   // 5 ms
        release_ = std::exp(-1.0f / (0.150f * sampleRate));  // 150 ms
    }

    // Renvoie le gain linéaire à appliquer à l'image courante.
    float gainFor(float left, float right) {
        if (amount_ <= 0.0f) return 1.0f;
        float peak = std::fmax(std::fabs(left), std::fabs(right));
        float coeff = peak > envelope_ ? attack_ : release_;
        envelope_ = coeff * envelope_ + (1.0f - coeff) * peak;
        float levelDb = 20.0f * std::log10(std::fmax(envelope_, 1e-6f));
        float overDb = levelDb - thresholdDb_;
        float reductionDb = overDb > 0.0f ? overDb * (1.0f - 1.0f / ratio_) : 0.0f;
        return std::pow(10.0f, (makeupDb_ - reductionDb) / 20.0f);
    }

private:
    float amount_ = 0, thresholdDb_ = 0, ratio_ = 1, makeupDb_ = 0;
    float attack_ = 0, release_ = 0, envelope_ = 0;
};

}  // namespace dsp
