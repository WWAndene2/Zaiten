package com.zaiten.mixer.core.mixer

/** Comment le son d'origine des applis capturées est rendu silencieux (seule la copie réglée s'entend). */
enum class OriginalSoundMode {
    /** Aucun son capturé encore : la coupure n'a pas pu être testée. */
    PENDING,

    /** Volume média coupé, la capture continue : seul le son du mixeur s'entend. */
    MUTED_BY_APP,

    /**
     * Sur cet appareil la capture se tait avec le volume média : les originaux ne peuvent pas être
     * coupés (les couper dans l'appli tairait aussi la capture). Le mixeur ne peut qu'ajouter du son.
     */
    ORIGINAL_STILL_PLAYS,
}
