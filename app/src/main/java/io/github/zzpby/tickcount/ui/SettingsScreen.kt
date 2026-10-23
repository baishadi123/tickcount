package io.github.zzpby.tickcount.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.AppLanguage

/**
 * The settings: everything below the pages the drawer offers, listed on one
 * screen behind the gear.
 *
 * The language leads — it is the one choice that changes what every other row
 * says — then the rows are the drawer's own rows and the groups keep the gaps
 * they always had: what you set up (reminders, appearance, widgets), what you
 * keep (data), what you read (changelog, the project). The current page is
 * lifted the same way the drawer lifts it, so the screen says where you came
 * from as well as where you can go.
 */
@Composable
fun SettingsScreen(
    current: AppScreen?,
    language: AppLanguage,
    onLanguage: () -> Unit,
    onSelect: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            DrawerRow(
                label = stringResource(R.string.drawer_language),
                trailing = stringResource(language.labelRes),
                selected = false,
                onClick = onLanguage,
            )

            Spacer(Modifier.height(GroupSpacing))

            AppScreen.entries.filter { it.inSettings }.forEach { screen ->
                if (screen.startsGroup) Spacer(Modifier.height(GroupSpacing))
                DrawerRow(
                    label = stringResource(screen.labelRes),
                    selected = screen == current,
                    onClick = { onSelect(screen) },
                )
            }
        }
    }
}
