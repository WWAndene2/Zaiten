#pragma once

#include <atomic>
#include <cstddef>
#include <vector>

// File circulaire un producteur / un consommateur, sans verrou : le thread de capture écrit,
// le callback Oboe lit. Aucune allocation ni attente côté lecture (contrainte temps réel).
template <typename T>
class SpscRingBuffer {
public:
    explicit SpscRingBuffer(size_t capacity) : buffer_(capacity + 1) {}

    size_t size() const {
        size_t w = write_.load(std::memory_order_acquire);
        size_t r = read_.load(std::memory_order_acquire);
        return (w + buffer_.size() - r) % buffer_.size();
    }

    // Écrit ce qui tient ; renvoie le nombre d'éléments écrits.
    size_t write(const T* data, size_t count) {
        size_t w = write_.load(std::memory_order_relaxed);
        size_t r = read_.load(std::memory_order_acquire);
        size_t free = (r + buffer_.size() - w - 1) % buffer_.size();
        size_t n = count < free ? count : free;
        for (size_t i = 0; i < n; ++i) buffer_[(w + i) % buffer_.size()] = data[i];
        write_.store((w + n) % buffer_.size(), std::memory_order_release);
        return n;
    }

    // Lit au plus count éléments ; renvoie le nombre d'éléments lus.
    size_t read(T* out, size_t count) {
        size_t r = read_.load(std::memory_order_relaxed);
        size_t w = write_.load(std::memory_order_acquire);
        size_t available = (w + buffer_.size() - r) % buffer_.size();
        size_t n = count < available ? count : available;
        for (size_t i = 0; i < n; ++i) out[i] = buffer_[(r + i) % buffer_.size()];
        read_.store((r + n) % buffer_.size(), std::memory_order_release);
        return n;
    }

    // Côté consommateur uniquement : jette les éléments les plus anciens.
    void skip(size_t count) {
        size_t r = read_.load(std::memory_order_relaxed);
        read_.store((r + count) % buffer_.size(), std::memory_order_release);
    }

private:
    std::vector<T> buffer_;
    std::atomic<size_t> write_{0};
    std::atomic<size_t> read_{0};
};
