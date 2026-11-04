package com.wombatriy.androidplayer.library

import org.junit.Assert.*
import org.junit.Test

class ExcludedFolderPolicyTest {
    @Test fun excludedDirectoryIsSkippedAtEveryDepth() {
        assertFalse(ExcludedFolderPolicy.shouldTraverse("excluded"))
        assertFalse(ExcludedFolderPolicy.shouldTraverse("EXCLUDED"))
        assertFalse(ExcludedFolderPolicy.shouldTraverse("Excluded"))
    }

    @Test fun explicitlySelectedDestinationIsSkippedByDocumentIdentity() {
        val target = ExcludedFolderPolicy.FolderIdentity(
            "com.android.externalstorage.documents", "primary:Music/Archive"
        )
        assertFalse(ExcludedFolderPolicy.shouldTraverse(
            "Archive", target, setOf(target)
        ))
        assertTrue(ExcludedFolderPolicy.shouldTraverse(
            "Archive", ExcludedFolderPolicy.FolderIdentity(
                "other.provider", "primary:Music/Archive"
            ), setOf(target)
        ))
    }

    @Test fun otherDirectoriesStayTraversable() {
        assertTrue(ExcludedFolderPolicy.shouldTraverse("jazz"))
        assertTrue(ExcludedFolderPolicy.shouldTraverse("excluded-backup"))
        assertTrue(ExcludedFolderPolicy.shouldTraverse(null))
    }
}
