package info.anodsplace.evtimer.data

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopDataStoreTest {
    @Test
    fun legacyFileSafetyDecisionIsPlatformIndependent() {
        val ownerOnly = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        val groupWritable = ownerOnly + PosixFilePermission.GROUP_WRITE
        val othersWritable = ownerOnly + PosixFilePermission.OTHERS_WRITE
        fun safe(
            regular: Boolean = true,
            symlink: Boolean = false,
            owner: String = "alice",
            currentUser: String = "alice",
            windows: Boolean = false,
            permissions: Set<PosixFilePermission>? = ownerOnly
        ) = isSafeLegacyFile(regular, symlink, owner, currentUser, windows, permissions)

        assertFalse(safe(symlink = true))
        assertFalse(safe(regular = false))
        assertFalse(safe(owner = "mallory"))
        assertFalse(safe(permissions = groupWritable))
        assertFalse(safe(permissions = othersWritable))
        assertTrue(safe())
        assertTrue(safe(owner = "DOMAIN\\Alice", currentUser = "alice", windows = true))
        assertFalse(safe(owner = "DOMAIN\\ALICE", currentUser = "alice"))
    }

    @Test
    fun appDataDirectoryUsesPerUserPlatformLocation() {
        assertEquals(
            File("C:\\Users\\driver\\AppData\\Local", "charging-timer-ev"),
            desktopAppDataDirectory("Windows 11", "C:\\Users\\driver\\AppData\\Local", null, "C:\\Users\\driver")
        )
        assertEquals(
            File("/Users/driver/Library/Application Support", "charging-timer-ev"),
            desktopAppDataDirectory("Mac OS X", null, null, "/Users/driver")
        )
        assertEquals(
            File("/home/driver/data", "charging-timer-ev"),
            desktopAppDataDirectory("Linux", null, "/home/driver/data", "/home/driver")
        )
        assertEquals(
            File("/home/driver/.local/share", "charging-timer-ev"),
            desktopAppDataDirectory("Linux", null, null, "/home/driver")
        )
    }

    @Test
    fun legacyPreferencesMigrateOnceAndRemainPersistent() = runBlocking {
        val root = Files.createTempDirectory("charging-timer-desktop-test")
        val appData = root.resolve("app-data")
        val legacy = root.resolve("legacy").resolve(dataStoreFileName)
        val legacyJob = Job()
        val firstJob = Job()
        val restartJob = Job()
        try {
            Files.createDirectories(legacy.parent)
            val oldStore = dataStoreFactory(
                { legacy.toAbsolutePath().toString() },
                CoroutineScope(Dispatchers.IO + legacyJob)
            )
            oldStore.edit {
                it[SessionPreferences.batteryCapacity] = 60f
                it[SessionPreferences.chargingPower] = 12f
                it[SessionPreferences.startPercent] = 20f
                it[SessionPreferences.maxPercent] = 80f
                it[SessionPreferences.notifyAt80] = false
                it[SessionPreferences.selectedPreset] = "ev"
                it[SessionPreferences.isRunning] = true
                it[SessionPreferences.startTime] = 1_800_000_000_123L
            }
            legacyJob.cancelAndJoin()

            ensurePrivateAppDataDirectory(appData)
            val destination = appData.resolve(dataStoreFileName)
            assertTrue(migrateLegacyDataStore(legacy, destination))
            assertFalse(migrateLegacyDataStore(legacy, destination))
            assertTrue(Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS))

            val first = createDesktopDataStore(
                appData,
                legacy,
                CoroutineScope(Dispatchers.IO + firstJob)
            )
            val persisted = first.data.first()
            assertEquals(60f, SessionPreferences.readSettings(persisted).batteryCapacity)
            assertEquals(12f, SessionPreferences.readSettings(persisted).chargingPower)
            assertEquals(false, persisted[SessionPreferences.notifyAt80])
            assertEquals("ev", persisted[SessionPreferences.selectedPreset])
            assertTrue(persisted[SessionPreferences.isRunning] == true)
            assertEquals(1_800_000_000_123L, persisted[SessionPreferences.startTime])
            assertNotNull(SessionPreferences.readSnapshot(persisted))
            if (Files.getFileStore(appData).supportsFileAttributeView("posix")) {
                assertEquals(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(destination)
                )
                assertEquals(
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                    ),
                    Files.getPosixFilePermissions(appData)
                )
            }
            firstJob.cancelAndJoin()

            val restarted = createDesktopDataStore(
                appData,
                legacy,
                CoroutineScope(Dispatchers.IO + restartJob)
            )
            val restored = restarted.data.first()
            assertEquals(60f, SessionPreferences.readSettings(restored).batteryCapacity)
            assertEquals(false, restored[SessionPreferences.notifyAt80])
            assertEquals("ev", restored[SessionPreferences.selectedPreset])
            assertTrue(restored[SessionPreferences.isRunning] == true)
            assertEquals(1_800_000_000_123L, restored[SessionPreferences.startTime])
            assertTrue(Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))
        } finally {
            legacyJob.cancelAndJoin()
            firstJob.cancelAndJoin()
            restartJob.cancelAndJoin()
            deleteTree(root)
        }
    }

    @Test
    fun existingDestinationPreferencesAreNeverReplaced() {
        val root = Files.createTempDirectory("charging-timer-desktop-test")
        try {
            val appData = ensurePrivateAppDataDirectory(root.resolve("app-data"))
            val legacy = root.resolve("legacy.preferences_pb")
            val destination = appData.resolve(dataStoreFileName)
            val legacyBytes = byteArrayOf(1, 2, 3)
            val destinationBytes = byteArrayOf(4, 5, 6)
            Files.write(legacy, legacyBytes)
            Files.write(destination, destinationBytes)

            assertFalse(migrateLegacyDataStore(legacy, destination))
            assertContentEquals(legacyBytes, Files.readAllBytes(legacy))
            assertContentEquals(destinationBytes, Files.readAllBytes(destination))
        } finally {
            deleteTree(root)
        }
    }

    @Test
    fun unsafeSymlinkAndWorldWritableLegacyFilesAreNotMigrated() {
        val root = Files.createTempDirectory("charging-timer-desktop-test")
        try {
            val appData = ensurePrivateAppDataDirectory(root.resolve("app-data"))
            val target = Files.write(root.resolve("target.preferences_pb"), byteArrayOf(1, 2, 3))
            val symlink = root.resolve("legacy-symlink.preferences_pb")
            if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
                Files.createSymbolicLink(symlink, target)
                assertFalse(migrateLegacyDataStore(symlink, appData.resolve(dataStoreFileName)))

                val writableLegacy = Files.write(root.resolve("world-writable.preferences_pb"), byteArrayOf(4))
                Files.setPosixFilePermissions(
                    writableLegacy,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_WRITE)
                )
                assertFalse(migrateLegacyDataStore(writableLegacy, appData.resolve(dataStoreFileName)))
            }
        } finally {
            deleteTree(root)
        }
    }

    private fun deleteTree(root: Path) {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
