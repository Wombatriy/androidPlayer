package com.wombatriy.androidplayer.library

/**
 * The 'excluded' directory is deliberately omitted from recursive scans,
 * even when a user later selects its parent as the new Library root.
 */
object ExcludedFolderPolicy {
    const val FOLDER_NAME = "excluded"

    fun shouldTraverse(name: String?): Boolean =
        !name.equals(FOLDER_NAME, ignoreCase = true)
}
