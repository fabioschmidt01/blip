package dev.vereda.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import dev.vereda.app.data.Profile
import java.util.Locale

object AppLocales {
    fun wrap(base: Context): Context {
        val prefs = base.getSharedPreferences("vereda", Context.MODE_PRIVATE)
        if (!prefs.contains("lang")) {
            prefs.edit().putString("lang", Profile.LANG_DEFAULT).commit()
        }
        val tag = prefs.getString("lang", Profile.LANG_DEFAULT) ?: Profile.LANG_DEFAULT
        return applyConfig(base, tag)
    }

    fun apply(context: Context, tag: String) {
        context.getSharedPreferences("vereda", Context.MODE_PRIVATE)
            .edit()
            .putString("lang", tag)
            .commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.applicationContext.getSystemService(LocaleManager::class.java)
            manager?.applicationLocales = LocaleList.forLanguageTags(tag)
        }
        (context as? Activity)?.recreate()
    }

    private fun applyConfig(base: Context, tag: String): Context {
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
