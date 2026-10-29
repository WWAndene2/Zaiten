#include "MixerEngine.h"

#include <android/log.h>

#include <algorithm>
#include <ctime>

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
            t.appliedLeft = t.gainLeft.load();
            t.appliedRight = t.gainRight.load();
            t.active.store(true);
            return &t;
        }
    }
    return nullptr;  // Plus de kMaxTracks applis : la piste est ignorée.
}

void MixerEngine::write(int uid, const int16_t* samples, int count) {
    if (Track* t = findOrAdd(uid)) t->queue.write(samples, static_cast<size_t>(count));
}

void MixerEngine::setGains(int uid, int leftPercent, int rightPercent) {
    if (Track* t = findOrAdd(uid)) {
        t->gainLeft.store(dsp::clampPercent(leftPercent) / 100.0f);
        t->gainRight.store(dsp::clampPercent(rightPercent) / 100.0f);
    }
}

void MixerEngine::setEffects(const dsp::EffectsParams& params) {
    std::lock_guard<std::mutex> lock(effectsMutex_);
    pendingEffects_ = params;
    effectsVersion_.fetch_add(1, std::memory_order_release);
}

void MixerEngine::removeTrack(int uid) {
    if (Track* t = find(uid)) t->active.store(false);
}

int64_t MixerEngine::setTapEnabled(bool enabled) {
    if (!enabled) {
        tapEnabled_.store(false);
        return tapFramesWritten_.load();
    }
    // Copie coupée : le callback n'écrit pas, le compteur est figé. On vide la file (rôle de lecteur,
    // tenu par l'appelant), on relève l'index, puis on active.
    tapEnabled_.store(false);
    tap_.skip(tap_.size());
    int64_t start = tapFramesWritten_.load();
    tapEnabled_.store(true);
    return start;
}

int MixerEngine::readTap(int16_t* out, int maxSamples) {
    return static_cast<int>(tap_.read(out, static_cast<size_t>(maxSamples)));
}

void MixerEngine::tapClock(int64_t* timeNs, int64_t* framesAtTime) {
    uint32_t before, after;
    do {
        before = tapClockSeq_.load(std::memory_order_acquire);
        *timeNs = tapTimeNs_.load(std::memory_order_relaxed);
        *framesAtTime = tapFrames_.load(std::memory_order_relaxed);
        after = tapClockSeq_.load(std::memory_order_acquire);
    } while (before != after || (before & 1u));
}

void MixerEngine::setOutputDelayMs(int ms) {
    int frames = ms * kSampleRate / 1000;
    delayFrames_.store(std::clamp(frames, 0, kMaxDelayFrames - 1));
}

oboe::DataCallbackResult MixerEngine::onAudioReady(oboe::AudioStream* stream, void* audioData,
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
        const float fromLeft = t.appliedLeft;
        const float fromRight = t.appliedRight;
        const float toLeft = t.gainLeft.load(std::memory_order_relaxed);
        const float toRight = t.gainRight.load(std::memory_order_relaxed);
        const size_t frames = static_cast<size_t>(numFrames);
        size_t done = 0;
        while (done < samples) {
            size_t chunk = std::min(samples - done, scratch_.size());
            size_t read = t.queue.read(scratch_.data(), chunk);
            for (size_t i = 0; i < read; ++i) {
                size_t sample = done + i;
                bool left = sample % kChannels == 0;
                float gain = left ? dsp::rampGain(fromLeft, toLeft, sample / kChannels, frames)
                                  : dsp::rampGain(fromRight, toRight, sample / kChannels, frames);
                out[sample] += dsp::int16ToFloat(scratch_[i]) * gain;
            }
            if (read < chunk) break;  // File vide : le reste de la piste est du silence.
            done += read;
        }
        t.appliedLeft = toLeft;
        t.appliedRight = toRight;
    }

    int version = effectsVersion_.load(std::memory_order_acquire);
    if (version != appliedEffectsVersion_ && effectsMutex_.try_lock()) {
        effects_.configure(pendingEffects_, kSampleRate);
        appliedEffectsVersion_ = version;
        effectsMutex_.unlock();
    }
    effects_.process(out, numFrames);

    if (tapEnabled_.load(std::memory_order_relaxed)) {
        // Instant de sortie de cette image : maintenant + latence de sortie annoncée par Oboe, plus
        // le retard volontaire appliqué juste après. Les enceintes visent ce même instant.
        timespec ts{};
        clock_gettime(CLOCK_MONOTONIC, &ts);
        int64_t nowNs = static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
        auto latency = stream ? stream->calculateLatencyMillis() : oboe::ResultWithValue<double>(0.0);
        double latencyMs = latency ? latency.value() : 0.0;
        int64_t playNs = nowNs + static_cast<int64_t>(latencyMs * 1e6) +
                         static_cast<int64_t>(delayFrames_.load()) * 1000000000LL / kSampleRate;
        tapClockSeq_.fetch_add(1, std::memory_order_acq_rel);  // Impair : écriture en cours.
        tapTimeNs_.store(playNs, std::memory_order_relaxed);
        const int64_t framesBefore = tapFramesWritten_.load(std::memory_order_relaxed);
        tapFrames_.store(framesBefore, std::memory_order_relaxed);
        tapClockSeq_.fetch_add(1, std::memory_order_acq_rel);  // Pair : stable.
        size_t written = 0;
        for (size_t i = 0; i < samples; i += scratch_.size()) {
            size_t n = std::min(scratch_.size(), samples - i);
            for (size_t j = 0; j < n; ++j) scratch_[j] = static_cast<int16_t>(out[i + j] * 32767.0f);
            written += tap_.write(scratch_.data(), n);  // File pleine (émetteur bloqué) : excédent perdu.
        }
        tapFramesWritten_.store(framesBefore + static_cast<int64_t>(written / kChannels), std::memory_order_release);
    }

    const int delay = delayFrames_.load(std::memory_order_relaxed);
    if (delay > 0) {
        const size_t lineSamples = delayLine_.size();
        for (size_t i = 0; i < samples; ++i) {
            size_t readPos = (delayPos_ + lineSamples - static_cast<size_t>(delay) * kChannels) % lineSamples;
            float delayed = delayLine_[readPos];
            delayLine_[delayPos_] = out[i];
            out[i] = delayed;
            delayPos_ = (delayPos_ + 1) % lineSamples;
        }
    }
    return oboe::DataCallbackResult::Continue;
}
