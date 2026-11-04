package com.wombatriy.androidplayer.library

/**
 * Directory exclusions are keyed by SAF provider identity, never by
 * filesystem paths (document IDs are opaque per the Android SAF contract).
 * A user-selected destination within the scanned tree remains hidden,
 * even if it is not literally named "excluded".
 */
object ExcludedFolderPolicy {
    const val FOLDER_NAME = "excluded"

    data class FolderIdentity(val authority: String?, val documentId: String)

    fun shouldTraverse(
        name: String?,
        identity: FolderIdentity? = null,
        excluded: Set<FolderIdentity> = emptySet()
    ): Boolean = !name.equals(FOLDER_NAME, ignoreCase = true) &&
        (identity == null || identity !in excluded)
}
