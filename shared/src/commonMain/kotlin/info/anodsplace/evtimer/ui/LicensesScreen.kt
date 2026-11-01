package info.anodsplace.evtimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.style.LibraryActionBadges
import info.anodsplace.evtimer.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }
    Column(modifier.fillMaxSize()) {
        SettingsHeader(stringResource(Res.string.settings_oss_licenses), onBack)
        LibrariesContainer(
            libraries, Modifier.weight(1f),
            actionLabels = LibraryActionBadges(
                source = stringResource(Res.string.settings_source),
                website = stringResource(Res.string.oss_website),
                sponsorEnabled = false,
                viewLicense = stringResource(Res.string.oss_view_license)
            ),
            licenseDialogConfirmText = stringResource(Res.string.oss_close)
        )
    }
}
