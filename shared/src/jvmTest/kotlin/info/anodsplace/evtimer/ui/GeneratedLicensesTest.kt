package info.anodsplace.evtimer.ui

import com.mikepenz.aboutlibraries.Libs
import info.anodsplace.evtimer.generated.resources.Res
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratedLicensesTest {
    @Test
    fun bundledMetadataIncludesSharedAndPlatformAppDependenciesWithLicenses() = runTest {
        val libraries = Libs.Builder()
            .withJson(Res.readBytes("files/aboutlibraries.json").decodeToString())
            .build()
        for (artifact in listOf("core-splashscreen", "coroutines-swing", "sqldelight")) {
            assertTrue(
                libraries.libraries.any { artifact in it.uniqueId && it.licenses.isNotEmpty() },
                "Missing generated license for $artifact"
            )
        }
        assertTrue(libraries.licenses.any { !it.licenseContent.isNullOrBlank() })
    }
}
