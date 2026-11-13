package io.github.hddq.restoid.ui.shared

import io.github.hddq.restoid.R
import io.github.hddq.restoid.data.SnapshotInfo

/**
 * The kinds of items restoid can back up for an application.
 */
enum class BackedUpItemType(val labelRes: Int) {
    APK(R.string.backup_type_apk),
    DATA(R.string.backup_type_data),
    DEVICE_PROTECTED_DATA(R.string.backup_type_device_protected_data),
    EXTERNAL_DATA(R.string.backup_type_external_data),
    OBB(R.string.backup_item_obb),
    MEDIA(R.string.backup_item_media),
    PERMISSIONS(R.string.backup_item_permissions),
    SPECIAL_ACCESS(R.string.backup_item_special_access),
    ACCESSIBILITY(R.string.backup_item_accessibility),
    UNKNOWN(R.string.backup_item_unknown)
}

/**
 * Groups the backed up item types found in [snapshot] by package name.
 *
 * Restic stores every backed up target (one per app directory) in [SnapshotInfo.paths], so a
 * snapshot can contain thousands of paths. Scanning all of them for every app is
 * O(apps × paths) and used to compile several [Regex]es per candidate path, which blocked the
 * main thread long enough to freeze the UI. This walks the paths exactly once instead.
 */
fun buildBackedUpItemsIndex(snapshot: SnapshotInfo): Map<String, Set<BackedUpItemType>> {
    val index = mutableMapOf<String, MutableSet<BackedUpItemType>>()
    snapshot.paths.forEach { path ->
        val match = classifySnapshotPath(path) ?: return@forEach
        index.getOrPut(match.first) { linkedSetOf() }.add(match.second)
    }
    return index
}

/**
 * Resolves the localized labels for the items backed up for a single app, in the order in which
 * they appear in the snapshot. Apps with no matching paths always expose an "unknown" item.
 */
fun backedUpItemLabels(
    resolveLabel: (Int) -> String,
    types: Set<BackedUpItemType>?,
    hasPermissionBackup: Boolean,
    hasSpecialAccessBackup: Boolean = false,
    hasAccessibilityBackup: Boolean = false
): List<String> {
    val items = linkedSetOf<BackedUpItemType>()
    types?.let(items::addAll)
    if (hasPermissionBackup) {
        items.add(BackedUpItemType.PERMISSIONS)
    }
    if (hasSpecialAccessBackup) {
        items.add(BackedUpItemType.SPECIAL_ACCESS)
    }
    if (hasAccessibilityBackup) {
        items.add(BackedUpItemType.ACCESSIBILITY)
    }
    return if (items.isEmpty()) {
        listOf(resolveLabel(BackedUpItemType.UNKNOWN.labelRes))
    } else {
        items.map { resolveLabel(it.labelRes) }
    }
}

private fun classifySnapshotPath(path: String): Pair<String, BackedUpItemType>? {
    return when {
        path.startsWith("/data/data/") -> {
            val packageName = path.removePrefix("/data/data/")
            if (packageName.isNotEmpty() && '/' !in packageName) {
                packageName to BackedUpItemType.DATA
            } else {
                null
            }
        }

        path.startsWith("/data/user/") -> {
            val segments = path.removePrefix("/data/user/").split('/')
            if (segments.size == 2 && segments[0].isUserId() && segments[1].isNotEmpty()) {
                segments[1] to BackedUpItemType.DATA
            } else {
                null
            }
        }

        path.startsWith("/data/user_de/") -> {
            val segments = path.removePrefix("/data/user_de/").split('/')
            if (segments.size == 2 && segments[0].isUserId() && segments[1].isNotEmpty()) {
                segments[1] to BackedUpItemType.DEVICE_PROTECTED_DATA
            } else {
                null
            }
        }

        path.startsWith("/storage/emulated/") -> {
            val segments = path.removePrefix("/storage/emulated/").split('/')
            if (segments.size == 4 && segments[0].isUserId() && segments[1] == "Android") {
                val type = when (segments[2]) {
                    "data" -> BackedUpItemType.EXTERNAL_DATA
                    "obb" -> BackedUpItemType.OBB
                    "media" -> BackedUpItemType.MEDIA
                    else -> null
                }
                if (type != null && segments[3].isNotEmpty()) segments[3] to type else null
            } else {
                null
            }
        }

        path.startsWith("/data/app/") -> classifyApkPath(path)

        else -> null
    }
}

/**
 * APKs live in `/data/app/<pkg>-<suffix>/...` or, on newer Android versions, in
 * `/data/app/~~<random>==/<pkg>-<suffix>/...`. The randomized directory name can itself contain
 * dashes, so the first segment that does not start with `~~` and contains a `-` is used.
 */
private fun classifyApkPath(path: String): Pair<String, BackedUpItemType>? {
    for (segment in path.removePrefix("/data/app/").split('/')) {
        if (segment.startsWith("~~")) continue
        val separator = segment.indexOf('-')
        if (separator > 0) {
            return segment.substring(0, separator) to BackedUpItemType.APK
        }
    }
    return null
}

private fun String.isUserId(): Boolean = isNotEmpty() && all(Char::isDigit)
