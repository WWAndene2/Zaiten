package com.zaiten.mixer.features.mixer

import android.content.Context
import android.content.Intent
import com.zaiten.mixer.core.mixer.AppTrack

/**
 * Applis lançables proposées à l'ajout manuel dans le mixeur.
 *
 * Complément de la détection automatique (ActiveSessionWatcher), pour les applis qui jouent
 * du son sans publier de session média, comme beaucoup de jeux.
 */
fun loadLaunchableApps(context: Context): List<AppTrack> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.uid }
        .map { AppTrack(uid = it.uid, packageName = it.packageName, label = pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}
