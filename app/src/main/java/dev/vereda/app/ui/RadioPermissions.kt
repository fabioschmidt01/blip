package dev.vereda.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object RadioPermissions {
    fun ask(): List<String> {
        val items = mutableListOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        if (Build.VERSION.SDK_INT <= 32) {
            items += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) {
            items += Manifest.permission.NEARBY_WIFI_DEVICES
            items += Manifest.permission.POST_NOTIFICATIONS
        }
        return items
    }

    fun missingRadio(context: Context): List<String> {
        return ask().filter { permission ->
            permission != Manifest.permission.POST_NOTIFICATIONS &&
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}
