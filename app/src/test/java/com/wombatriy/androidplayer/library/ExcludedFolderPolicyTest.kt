package com.wombatriy.androidplayer.library

import org.junit.Assert.*
import org.junit.Test

class ExcludedFolderPolicyTest {
    @Test fun excludedDirectoryIsSkippedAtEveryDepth() {
        assertFalse(ExcludedFolderPolicy.shouldTraverse("excluded"))
        assertFalse(ExcludedFolderPolicy.shouldTraverse("EXCLUDED"))
        assertFalse(ExcludedFolderPolicy.shouldTraverse("Excluded"))
    }

    @Test fun otherDirectoriesStayTraversable() {
        assertTrue(ExcludedFolderPolicy.shouldTraverse("jazz"))
        assertTrue(ExcludedFolderPolicy.shouldTraverse("excluded-backup"))
        assertTrue(ExcludedFolderPolicy.shouldTraverse(null))
    }
}
