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

    fun setNickname(name: String) {
        prefs.edit().putString(KEY_NICK, name).apply()
        _nickname.value = name
    }

    fun markReady() {
        prefs.edit().putBoolean(KEY_READY, true).apply()
        _ready.value = true
    }

    fun wipe() {
        prefs.edit().clear().commit()
        _nickname.value = ""
        _ready.value = false
    }

    private companion object {
        const val KEY_NICK = "nickname"
        const val KEY_READY = "ready"
    }
}
