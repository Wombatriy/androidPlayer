package com.wombatriy.androidplayer.player

import android.content.Context
import java.util.UUID

/**
 * MediaSessionService is exported for system/remote MediaControllers, but its
 * same-process Binder gives full access to the internal playback model.
 * The Binder must NOT be exposed to arbitrary third-party applications.
 *
 * A random per-install token stored in app-private preferences gates only
 * the explicit local bind action. Normal Media3 session controllers are
 * authenticated and handled by MediaSessionService itself.
 */
internal object LocalBindAuth {
    private const val PREFERENCES = "playback-local-bind"
    private const val KEY_TOKEN = "token"

    fun token(context: Context): String {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val existing = preferences.getString(KEY_TOKEN, null)
        if (existing != null) return existing
        return UUID.randomUUID().toString().also {
            // A new install creates the token only once, before bindService.
            preferences.edit().putString(KEY_TOKEN, it).commit()
        }
    }

    fun matches(context: Context, candidate: String?): Boolean =
        candidate != null && token(context) == candidate
}
