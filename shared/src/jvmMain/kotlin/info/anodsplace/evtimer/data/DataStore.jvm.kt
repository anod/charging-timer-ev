package info.anodsplace.evtimer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import okio.Path.Companion.toPath

fun createDataStore(): DataStore<Preferences> = createDesktopDataStore(
    appDataDirectory = desktopAppDataDirectory().toPath(),
    legacyFile = File(System.getProperty("java.io.tmpdir"), dataStoreFileName).toPath()
)

internal fun createDesktopDataStore(
    appDataDirectory: Path,
    legacyFile: Path,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
): DataStore<Preferences> {
    val directory = ensurePrivateAppDataDirectory(appDataDirectory)
    val preferencesFile = directory.resolve(dataStoreFileName)
    migrateLegacyDataStore(legacyFile, preferencesFile)
    if (Files.exists(preferencesFile, LinkOption.NOFOLLOW_LINKS)) {
        check(Files.isRegularFile(preferencesFile, LinkOption.NOFOLLOW_LINKS)) {
            "Preferences path is not a regular file: $preferencesFile"
        }
        setOwnerOnlyFilePermissions(preferencesFile)
    }
    return dataStoreFactory({ preferencesFile.toAbsolutePath().toString() }, scope)
}

internal fun migrateLegacyDataStore(legacyFile: Path, preferencesFile: Path): Boolean {
    if (Files.exists(preferencesFile, LinkOption.NOFOLLOW_LINKS) ||
        !Files.exists(legacyFile, LinkOption.NOFOLLOW_LINKS)
    ) {
        return false
    }

    val before = readSafeLegacyAttributes(legacyFile) ?: return false
    val parent = requireNotNull(preferencesFile.parent)
    val temporaryCopy = Files.createTempFile(parent, ".preferences-migration-", ".tmp")
    try {
        Files.newInputStream(legacyFile, LinkOption.NOFOLLOW_LINKS).use { input ->
            Files.newOutputStream(
                temporaryCopy,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { output -> input.copyTo(output) }
        }

        val after = readSafeLegacyAttributes(legacyFile) ?: return false
        val sameFile = if (before.fileKey() != null && after.fileKey() != null) {
            before.fileKey() == after.fileKey()
        } else {
            before.creationTime() == after.creationTime()
        }
        if (!sameFile ||
            before.size() != after.size() ||
            before.lastModifiedTime() != after.lastModifiedTime()
        ) {
            return false
        }

        setOwnerOnlyFilePermissions(temporaryCopy)
        return try {
            Files.move(temporaryCopy, preferencesFile)
            true
        } catch (_: FileAlreadyExistsException) {
            false
        }
    } finally {
        Files.deleteIfExists(temporaryCopy)
    }
}

private fun readSafeLegacyAttributes(file: Path): BasicFileAttributes? {
    val attributes = Files.readAttributes(
        file,
        BasicFileAttributes::class.java,
        LinkOption.NOFOLLOW_LINKS
    )
    if (!attributes.isRegularFile || attributes.isSymbolicLink) return null

    val owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS).name
        .substringAfterLast('\\')
        .substringAfterLast('/')
    val currentUser = System.getProperty("user.name")
    val ownerMatches = if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
        owner.equals(currentUser, ignoreCase = true)
    } else {
        owner == currentUser
    }
    if (!ownerMatches) return null

    val permissions = try {
        Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
    } catch (_: UnsupportedOperationException) {
        null
    }
    if (permissions != null &&
        (PosixFilePermission.GROUP_WRITE in permissions || PosixFilePermission.OTHERS_WRITE in permissions)
    ) {
        return null
    }
    return attributes
}

internal fun desktopAppDataDirectory(
    osName: String = System.getProperty("os.name"),
    localAppData: String? = System.getenv("LOCALAPPDATA"),
    xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
    userHome: String = System.getProperty("user.home")
): File {
    val root = when {
        osName.contains("win", ignoreCase = true) -> File(localAppData ?: userHome)
        osName.contains("mac", ignoreCase = true) ->
            File(File(userHome, "Library"), "Application Support")
        else -> File(xdgDataHome ?: File(File(userHome, ".local"), "share").path)
    }
    return File(root, "charging-timer-ev")
}

internal fun ensurePrivateAppDataDirectory(directory: Path): Path {
    Files.createDirectories(directory)
    check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        "App data path is not a directory: $directory"
    }
    try {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
    } catch (_: UnsupportedOperationException) {
        // Windows profile directories use the user's inherited ACL.
    }
    return directory
}

private fun setOwnerOnlyFilePermissions(file: Path) {
    try {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
    } catch (_: UnsupportedOperationException) {
        // The containing app data directory is private on non-POSIX platforms.
    }
}
