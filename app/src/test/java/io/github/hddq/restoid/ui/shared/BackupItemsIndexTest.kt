package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.data.SnapshotInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupItemsIndexTest {

    private val labelResolver: (Int) -> String = { resId ->
        BackedUpItemType.entries.first { it.labelRes == resId }.name
    }

    private fun indexOf(vararg paths: String): Map<String, Set<BackedUpItemType>> =
        buildBackedUpItemsIndex(
            SnapshotInfo(
                id = "snapshot",
                time = "2026-11-13T00:00:00Z",
                paths = paths.toList(),
                tags = emptyList()
            )
        )

    @Test
    fun `indexes every backed up item type for an app`() {
        val index = indexOf(
            "/data/app/~~abc==/com.example.app-xyz==/base.apk",
            "/data/data/com.example.app",
            "/data/user_de/0/com.example.app",
            "/storage/emulated/0/Android/data/com.example.app",
            "/storage/emulated/0/Android/obb/com.example.app",
            "/storage/emulated/0/Android/media/com.example.app"
        )

        assertEquals(
            setOf(
                BackedUpItemType.APK,
                BackedUpItemType.DATA,
                BackedUpItemType.DEVICE_PROTECTED_DATA,
                BackedUpItemType.EXTERNAL_DATA,
                BackedUpItemType.OBB,
                BackedUpItemType.MEDIA
            ),
            index["com.example.app"]
        )
    }

    @Test
    fun `does not use randomized apk directory names as package names`() {
        val index = indexOf("/data/app/~~ab-cd==/com.example.app-xyz==/base.apk")

        assertEquals(setOf(BackedUpItemType.APK), index["com.example.app"])
        assertTrue(index.keys.none { it.startsWith("~~") })
    }

    @Test
    fun `matches user profile paths as data`() {
        val index = indexOf(
            "/data/user/10/com.example.app",
            "/data/user_de/10/com.example.app"
        )

        assertEquals(
            setOf(BackedUpItemType.DATA, BackedUpItemType.DEVICE_PROTECTED_DATA),
            index["com.example.app"]
        )
    }

    @Test
    fun `ignores nested paths below the backed up roots`() {
        val index = indexOf(
            "/data/user/0/com.example.app/files/database.db",
            "/storage/emulated/0/Android/data/com.example.app/files/cache"
        )

        assertTrue(index.isEmpty())
    }

    @Test
    fun `labels append metadata-only items after path items`() {
        val labels = backedUpItemLabels(
            resolveLabel = labelResolver,
            types = setOf(BackedUpItemType.APK, BackedUpItemType.DATA),
            hasPermissionBackup = true,
            hasSpecialAccessBackup = true,
            hasAccessibilityBackup = true
        )

        assertEquals(
            listOf("APK", "DATA", "PERMISSIONS", "SPECIAL_ACCESS", "ACCESSIBILITY"),
            labels
        )
    }

    @Test
    fun `labels fall back to unknown when nothing matched`() {
        val labels = backedUpItemLabels(
            resolveLabel = labelResolver,
            types = null,
            hasPermissionBackup = false
        )

        assertEquals(listOf("UNKNOWN"), labels)
    }
}
