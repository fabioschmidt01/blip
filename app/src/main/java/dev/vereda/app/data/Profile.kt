package dev.vereda.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Profile(context: Context) {
    private val prefs = context.getSharedPreferences("vereda", Context.MODE_PRIVATE)
    private val _nickname = MutableStateFlow(prefs.getString(KEY_NICK, "") ?: "")
    val nickname: StateFlow<String> = _nickname.asStateFlow()
    private val _ready = MutableStateFlow(prefs.getBoolean(KEY_READY, false))
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _reach = MutableStateFlow(prefs.getString(KEY_REACH, REACH_BOTH) ?: REACH_BOTH)
    val reach: StateFlow<String> = _reach.asStateFlow()
    private val _language = MutableStateFlow(prefs.getString(KEY_LANG, LANG_DEFAULT) ?: LANG_DEFAULT)
    val language: StateFlow<String> = _language.asStateFlow()
    private val _showIntro = MutableStateFlow(!prefs.getBoolean(KEY_INTRO, false))
    val showIntro: StateFlow<Boolean> = _showIntro.asStateFlow()

    fun setNickname(name: String) {
        prefs.edit().putString(KEY_NICK, name).apply()
        _nickname.value = name
    }

    fun markReady() {
        prefs.edit().putBoolean(KEY_READY, true).apply()
        _ready.value = true
    }

    fun setReach(mode: String) {
        val clean = if (mode == REACH_INTERNET) REACH_INTERNET else REACH_BOTH
        prefs.edit().putString(KEY_REACH, clean).apply()
        _reach.value = clean
    }

    fun setLanguage(tag: String) {
        prefs.edit().putString(KEY_LANG, tag).apply()
        _language.value = tag
    }

    fun markIntroSeen() {
        prefs.edit().putBoolean(KEY_INTRO, true).apply()
        _showIntro.value = false
    }

    fun wipe() {
        prefs.edit().clear().commit()
        _nickname.value = ""
        _ready.value = false
        _reach.value = REACH_BOTH
        _language.value = LANG_DEFAULT
        _showIntro.value = true
    }

    companion object {
        const val REACH_BOTH = "both"
        const val REACH_INTERNET = "internet"
        const val LANG_DEFAULT = "en"
        private const val KEY_NICK = "nickname"
        private const val KEY_READY = "ready"
        private const val KEY_REACH = "reach"
        private const val KEY_LANG = "lang"
        private const val KEY_INTRO = "intro_seen"
    }
}
