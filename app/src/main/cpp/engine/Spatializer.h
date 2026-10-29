#pragma once

#include <array>
#include <cmath>
#include <cstddef>

namespace dsp {

// Taille de pièce virtuelle (§3.2). Doit correspondre à l'enum Kotlin Room.
enum class Room { None = 0, Small = 1, LivingRoom = 2, Cinema = 3, Outdoor = 4 };

// Ligne à retard circulaire de taille fixe (pas d'allocation dans le thread audio).
template <size_t N>
class DelayLine {
public:
    void push(float x) { buffer_[pos_ = (pos_ + 1) % N] = x; }
    float tap(size_t delay) const { return buffer_[(pos_ + N - delay) % N]; }
    void clear() { buffer_.fill(0.0f); }

private:
    std::array<float, N> buffer_{};
    size_t pos_ = 0;
};

// Réverbération de type Freeverb (Jezar, domaine public) : 4 filtres en peigne amortis en
// parallèle puis 2 passe-tout en série, par canal. Version allégée de l'original (8 + 4).
class Reverb {
public:
    void configure(Room room) {
        // Durée et quantité selon la pièce ; plein air = pas de réflexions.
        switch (room) {
            case Room::Small: feedback_ = 0.70f; mix_ = 0.12f; break;
            case Room::LivingRoom: feedback_ = 0.78f; mix_ = 0.18f; break;
            case Room::Cinema: feedback_ = 0.86f; mix_ = 0.25f; break;
            default: feedback_ = 0.0f; mix_ = 0.0f; break;
        }
    }

    void process(float& l, float& r) {
        if (mix_ <= 0.0f) return;
        float input = 0.5f * (l + r) * 0.1f;  // Gain d'entrée fixe de Freeverb, évite l'emballement.
        float wetL = 0, wetR = 0;
        for (size_t i = 0; i < kCombs; ++i) {
            wetL += comb(combL_[i], combStateL_[i], kCombDelays[i], input);
            wetR += comb(combR_[i], combStateR_[i], kCombDelays[i] + kStereoSpread, input);
        }
        for (size_t i = 0; i < kAllpasses; ++i) {
            wetL = allpass(allpassL_[i], kAllpassDelays[i], wetL);
            wetR = allpass(allpassR_[i], kAllpassDelays[i] + kStereoSpread, wetR);
        }
        l += wetL * mix_;
        r += wetR * mix_;
    }

private:
    static constexpr size_t kCombs = 4, kAllpasses = 2, kStereoSpread = 23, kMaxDelay = 2048;
    // Retards de Freeverb (en échantillons à 44,1 kHz, gardés tels quels à 48 kHz : pièce ~9 % plus petite).
    static constexpr std::array<size_t, kCombs> kCombDelays = {1116, 1188, 1277, 1356};
    static constexpr std::array<size_t, kAllpasses> kAllpassDelays = {556, 441};
    static constexpr float kDamping = 0.2f;

    float comb(DelayLine<kMaxDelay>& line, float& state, size_t delay, float input) {
        float out = line.tap(delay);
        state = out * (1.0f - kDamping) + state * kDamping;
        line.push(input + state * feedback_);
        return out;
    }

    static float allpass(DelayLine<kMaxDelay>& line, size_t delay, float input) {
        float delayed = line.tap(delay);
        line.push(input + delayed * 0.5f);
        return delayed - input;
    }

    float feedback_ = 0, mix_ = 0;
    std::array<DelayLine<kMaxDelay>, kCombs> combL_{}, combR_{};
    std::array<float, kCombs> combStateL_{}, combStateR_{};
    std::array<DelayLine<kMaxDelay>, kAllpasses> allpassL_{}, allpassR_{};
};

// Virtualisation casque (§3.1) sans HRTF mesurée : modèle de tête sphérique.
// - Upmix : le stéréo donne des enceintes virtuelles avant (G/D à ±30°) et une ambiance (S = G − D)
//   placée sur les côtés (±110°).
// - Chaque enceinte atteint l'oreille opposée retardée (ITD, formule de Woodworth :
//   a/c × (θ + sin θ), a = 8,75 cm) et filtrée passe-bas (ombre de la tête), plus atténuée.
// amount : 0 = contourné ; faible = stéréo élargi ; 1 = 3D complet (§3.2, curseur d'intensité).
class Spatializer {
public:
    void configure(float amount, Room room) {
        amount_ = amount;
        reverb_.configure(room);
    }

    void process(float& l, float& r) {
        if (amount_ <= 0.0f) return;
        const float mid = 0.5f * (l + r);
        const float side = 0.5f * (l - r);

        // Stéréo élargi : renforce l'écart G/D proportionnellement à l'intensité.
        const float wideL = mid + side * (1.0f + amount_);
        const float wideR = mid - side * (1.0f + amount_);

        frontL_.push(l);
        frontR_.push(r);
        surround_.push(side);
        float farFrontL = shadowFront_[0].process(frontL_.tap(kFrontItd), kFrontShadow);
        float farFrontR = shadowFront_[1].process(frontR_.tap(kFrontItd), kFrontShadow);
        float farSide = shadowSide_.process(surround_.tap(kSideItd), kSideShadow);

        // Oreille gauche : enceinte G directe, D par le côté opposé ; ambiance à gauche directe,
        // ambiance droite (−S) atténuée et retardée.
        float virtL = l + kFrontFar * farFrontR + side * kSideNear - kSideFar * farSide;
        float virtR = r + kFrontFar * farFrontL - side * kSideNear + kSideFar * farSide;
        // Normalisation : l'ajout des chemins croisés monte le niveau d'environ 1,7 dB.
        virtL *= kVirtualGain;
        virtR *= kVirtualGain;
        reverb_.process(virtL, virtR);

        // Fondu : en dessous de la moitié, surtout l'élargissement ; au-delà, surtout la 3D.
        const float blend = amount_;
        l = (1.0f - blend) * wideL + blend * virtL;
        r = (1.0f - blend) * wideR + blend * virtR;
    }

private:
    // Passe-bas à un pôle : y += k × (x − y).
    struct OnePole {
        float y = 0;
        float process(float x, float k) { return y += k * (x - y); }
    };

    static constexpr size_t kFrontItd = 13;  // 30° : 0,26 ms à 48 kHz.
    static constexpr size_t kSideItd = 35;   // 110° : 0,73 ms.
    static constexpr float kFrontShadow = 0.5f;   // Coupure ~5 kHz.
    static constexpr float kSideShadow = 0.25f;   // Coupure ~2 kHz, tête plus masquante.
    static constexpr float kFrontFar = 0.6f;
    static constexpr float kSideNear = 0.5f;
    static constexpr float kSideFar = 0.3f;
    static constexpr float kVirtualGain = 0.82f;

    float amount_ = 0;
    DelayLine<64> frontL_, frontR_, surround_;
    std::array<OnePole, 2> shadowFront_{};
    OnePole shadowSide_;
    Reverb reverb_;
};

}  // namespace dsp
