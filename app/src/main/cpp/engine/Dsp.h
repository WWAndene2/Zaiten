#pragma once

#include <cmath>
#include <cstdint>

// Traitement du signal sans état ni dépendance à Android, testable sur la machine hôte (tests/).
namespace dsp {

constexpr int kMinPercent = 0;
constexpr int kMaxPercent = 200;
constexpr float kLimiterThreshold = 0.8f;

inline int clampPercent(int percent) {
    return percent < kMinPercent ? kMinPercent : (percent > kMaxPercent ? kMaxPercent : percent);
}

inline float int16ToFloat(int16_t sample) { return sample / 32768.0f; }

// Limiteur doux : linéaire jusqu'au seuil, puis tanh vers la pleine échelle (§2.5).
// Instantané, sans attaque ni relâchement ; sera remplacé par le limiteur de la chaîne §7.2 (étape 7).
inline float softLimit(float x) {
    float a = std::fabs(x);
    if (a <= kLimiterThreshold) return x;
    float headroom = 1.0f - kLimiterThreshold;
    float shaped = kLimiterThreshold + headroom * std::tanh((a - kLimiterThreshold) / headroom);
    return x < 0 ? -shaped : shaped;
}

}  // namespace dsp
