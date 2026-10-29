package com.zaiten.mixer.core.effects

/**
 * Presets fournis (§2.4), réglés d'après le §7.4. Bandes : 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz.
 * Ce qui relève d'étapes ultérieures n'est pas encore appliqué : stéréo élargi et 3D (étape 8),
 * mode basse latence du preset Jeu.
 */
object BuiltInPresets {
    val all: List<Preset> = listOf(
        // Égaliseur en V léger : basses et aigus +3 dB, compression faible.
        preset("Musique", listOf(3f, 3f, 2f, 0f, 0f, 0f, 0f, 2f, 3f, 3f), compressor = 0.2f),
        // Voix renforcées de 1 à 4 kHz (+4 dB), compression moyenne.
        preset("Film", listOf(0f, 0f, 0f, 0f, 0f, 4f, 4f, 4f, 0f, 0f), compressor = 0.5f),
        // Coupure sous 100 Hz, voix +5 dB, compression forte, mono.
        preset("Voix / Podcast", listOf(-12f, -12f, -3f, 0f, 2f, 5f, 5f, 3f, 0f, 0f), compressor = 0.8f, mono = true),
        // Aigus renforcés pour les bruits de pas, pas de compression (garde les attaques).
        preset("Jeu", listOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 4f, 2f), compressor = 0f),
        // Basses +6 dB sous 120 Hz, limiteur actif.
        preset("Basses+", listOf(6f, 6f, 4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), compressor = 0.2f),
        // Compression très forte, volume maximum plafonné à 100 %, basses réduites.
        preset("Nuit", listOf(-6f, -6f, -3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), compressor = 1f, masterCap = 100),
    )

    private fun preset(
        name: String,
        eq: List<Float>,
        compressor: Float,
        mono: Boolean = false,
        masterCap: Int = 200,
    ) = Preset(name, EffectSettings(eq, compressor, limiter = true, mono = mono, masterCapPercent = masterCap), builtIn = true)
}
