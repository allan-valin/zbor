package mk.zbor.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import mk.zbor.core.Lang
import java.util.Locale

val Lang.uiLocale: Locale
    get() = when (this) {
        Lang.MK -> Locale.forLanguageTag("mk")
        Lang.PT -> Locale.forLanguageTag("pt-BR")
        Lang.EN -> Locale.ENGLISH
        Lang.DE -> Locale.GERMAN
    }

/**
 * Renders [content] with app strings in [lang] instead of the phone's language (setting
 * "menus in the bottom language", for handing the phone to the other person). null = phone.
 */
@Composable
fun Localized(lang: Lang?, content: @Composable () -> Unit) {
    if (lang == null) return content()
    val base = LocalContext.current
    val config = LocalConfiguration.current
    val localized = remember(lang, config) {
        val c = Configuration(config).apply { setLocale(lang.uiLocale) }
        base.createConfigurationContext(c) to c
    }
    CompositionLocalProvider(LocalContext provides localized.first, LocalConfiguration provides localized.second) {
        content()
    }
}
