package com.zaiten.mixer.core.capture

import android.service.notification.NotificationListenerService

/**
 * Service vide : son seul rôle est de porter l'accès aux notifications accordé par l'utilisateur,
 * condition imposée par Android pour lire les sessions média des autres applis
 * (MediaSessionManager.getActiveSessions).
 */
class MediaSessionAccessService : NotificationListenerService()
