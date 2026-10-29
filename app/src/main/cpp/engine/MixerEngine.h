#pragma once

#include <oboe/Oboe.h>

#include <array>
#include <atomic>
#include <memory>
#include <mutex>

#include "EffectsChain.h"
#include "SpscRingBuffer.h"

// Moteur de mixage : chaque piste (une appli capturée) alimente sa file, le callback Oboe
// additionne les pistes avec leurs gains gauche/droite puis applique la chaîne du mix final
// (égaliseur, compresseur, mono, limiteur), sur un seul flux.
class MixerEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int kSampleRate = 48000;
    static constexpr int kChannels = 2;
    static constexpr int kMaxTracks = 16;

    bool start();
    void stop();

    // Appelés depuis le thread de capture de la piste.
    void write(int uid, const int16_t* samples, int count);
    // Gains effectifs par canal, déjà calculés côté Kotlin (muet, solo, maître, canal et balance).
    void setGains(int uid, int leftPercent, int rightPercent);
    // Réglages du mix final ; appelé hors du thread audio.
    void setEffects(const dsp::EffectsParams& params);
    void removeTrack(int uid);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream, void* audioData,
                                          int32_t numFrames) override;
    // Changement de sortie (casque branché, Bluetooth…) : Oboe ferme le flux, on le rouvre.
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    bool openAndStart();

    struct Track {
        std::atomic<int> uid{-1};
        std::atomic<bool> active{false};
        std::atomic<float> gainLeft{1.0f};
        std::atomic<float> gainRight{1.0f};
        // Derniers gains réellement appliqués, départ de la rampe ; lus et écrits par le seul
        // callback, sauf à l'attribution du slot, quand la piste est encore inactive.
        float appliedLeft = 1.0f;
        float appliedRight = 1.0f;
        // ~170 ms : marge contre les à-coups du thread de capture.
        SpscRingBuffer<int16_t> queue{8192 * kChannels};
    };

    Track* find(int uid);
    Track* findOrAdd(int uid);

    std::array<Track, kMaxTracks> tracks_;
    std::mutex slotsMutex_;  // Attribution des pistes uniquement, jamais pris par le callback.
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> running_{false};
    std::array<int16_t, 4096 * kChannels> scratch_{};

    // Passage des réglages d'effets vers le callback : l'écrivain prend le verrou, le callback ne
    // fait qu'un try_lock quand la version a changé, il n'attend donc jamais.
    std::mutex effectsMutex_;
    dsp::EffectsParams pendingEffects_;
    std::atomic<int> effectsVersion_{0};
    int appliedEffectsVersion_ = -1;
    dsp::EffectsChain effects_;
};
