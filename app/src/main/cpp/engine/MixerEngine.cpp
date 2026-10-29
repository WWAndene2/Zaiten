#include "MixerEngine.h"

#include <android/log.h>

#include <algorithm>

#include "Dsp.h"

namespace {
constexpr const char* kTag = "MixerEngine";
// Au-delà de ce retard (en images), on jette l'excédent pour tenir la latence visée (< 40 ms, §2.1).
constexpr size_t kMaxQueuedFrames = MixerEngine::kSampleRate * 30 / 1000;
}  // namespace

bool MixerEngine::start() {
    running_.store(true);
    return openAndStart();
}

bool MixerEngine::openAndStart() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(kChannels)
        ->setSampleRate(kSampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        // Même flux que l'ancienne copie AudioTrack : non capturable et indépendant du volume
        // média coupé par OriginalMuter (voir AppCaptureTrack côté Kotlin).
        ->setUsage(oboe::Usage::VoiceCommunication)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);
    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "openStream: %s", oboe::convertToText(result));
        return false;
    }
    return stream_->requestStart() == oboe::Result::OK;
}

void MixerEngine::stop() {
    running_.store(false);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

void MixerEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result error) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "stream closed: %s", oboe::convertToText(error));
    if (error == oboe::Result::ErrorDisconnected && running_.load()) openAndStart();
}

MixerEngine::Track* MixerEngine::find(int uid) {
    for (auto& t : tracks_) {
        if (t.active.load() && t.uid.load() == uid) return &t;
    }
    return nullptr;
}

MixerEngine::Track* MixerEngine::findOrAdd(int uid) {
    if (Track* t = find(uid)) return t;
    std::lock_guard<std::mutex> lock(slotsMutex_);
    if (Track* t = find(uid)) return t;
    for (auto& t : tracks_) {
        if (!t.active.load()) {
            // Vide les restes d'une piste précédente : skip est réservé au consommateur, mais le
            // slot est inactif, le callback ne le lit donc pas pendant cette remise à zéro.
            t.queue.skip(t.queue.size());
            t.uid.store(uid);
            t.active.store(true);
            return &t;
        }
    }
    return nullptr;  // Plus de kMaxTracks applis : la piste est ignorée.
}

void MixerEngine::write(int uid, const int16_t* samples, int count) {
    if (Track* t = findOrAdd(uid)) t->queue.write(samples, static_cast<size_t>(count));
}

void MixerEngine::setGain(int uid, int percent) {
    if (Track* t = findOrAdd(uid)) t->gain.store(dsp::clampPercent(percent) / 100.0f);
}

void MixerEngine::removeTrack(int uid) {
    if (Track* t = find(uid)) t->active.store(false);
}

oboe::DataCallbackResult MixerEngine::onAudioReady(oboe::AudioStream*, void* audioData,
                                                   int32_t numFrames) {
    auto* out = static_cast<float*>(audioData);
    const size_t samples = static_cast<size_t>(numFrames) * kChannels;
    std::fill(out, out + samples, 0.0f);

    for (auto& t : tracks_) {
        if (!t.active.load(std::memory_order_acquire)) continue;
        size_t queuedFrames = t.queue.size() / kChannels;
        if (queuedFrames > kMaxQueuedFrames + numFrames) {
            t.queue.skip((queuedFrames - kMaxQueuedFrames - numFrames) * kChannels);
        }
        const float gain = t.gain.load(std::memory_order_relaxed);
        size_t done = 0;
        while (done < samples) {
            size_t chunk = std::min(samples - done, scratch_.size());
            size_t read = t.queue.read(scratch_.data(), chunk);
            for (size_t i = 0; i < read; ++i) out[done + i] += dsp::int16ToFloat(scratch_[i]) * gain;
            if (read < chunk) break;  // File vide : le reste de la piste est du silence.
            done += read;
        }
    }

    for (size_t i = 0; i < samples; ++i) out[i] = dsp::softLimit(out[i]);
    return oboe::DataCallbackResult::Continue;
}
