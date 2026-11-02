package dev.vereda.app.ui

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class PlaceHit(val name: String, val latitude: Double, val longitude: Double)

object PlaceLookup {
    fun search(query: String): List<PlaceHit> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = get("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=$encoded")
        val array = org.json.JSONArray(body)
        val hits = ArrayList<PlaceHit>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            val name = row.optString("display_name")
            val latitude = row.optString("lat").toDoubleOrNull() ?: continue
            val longitude = row.optString("lon").toDoubleOrNull() ?: continue
            if (name.isBlank()) continue
            hits.add(PlaceHit(name, latitude, longitude))
        }
        return hits
    }

    fun label(latitude: Double, longitude: Double, precision: Int): String {
        val lat = String.format(Locale.US, "%.5f", latitude)
        val lon = String.format(Locale.US, "%.5f", longitude)
        val zoom = when (precision) {
            2 -> 3
            4 -> 5
            5 -> 10
            else -> 14
        }
        val body = get("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=$zoom")
        val json = JSONObject(body)
        val address = json.optJSONObject("address")
        val named = address?.let { shortName(it, precision) }.orEmpty()
        if (named.isNotBlank()) return named
        return json.optString("display_name").substringBefore(",").trim()
    }

    private fun shortName(address: JSONObject, precision: Int): String {
        val keys = when (precision) {
            2 -> listOf("country")
            4 -> listOf("state", "region")
            5 -> listOf("city", "town", "municipality", "village", "county")
            else -> listOf("suburb", "neighbourhood", "city_district", "quarter", "city", "town")
        }
        return keys.firstNotNullOfOrNull { key -> address.optString(key).ifBlank { null } }.orEmpty()
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "Blip/0.1 (Android)")
            connection.setRequestProperty("Accept-Language", "pt-BR")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("lookup $code")
            text
        } finally {
            connection.disconnect()
        }
    }
}
