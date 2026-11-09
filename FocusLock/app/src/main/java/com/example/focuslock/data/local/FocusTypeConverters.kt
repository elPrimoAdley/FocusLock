package com.example.focuslock.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object FocusTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromPackageList(packages: List<String>): String {
        return json.encodeToString(packages)
    }

    @TypeConverter
    fun toPackageList(rawJson: String): List<String> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<String>>(rawJson) }
            .getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromPackageSet(packages: Set<String>): String {
        // Trie les packages pour garantir un JSON déterministe lors des tests unitaires
        return json.encodeToString(packages.sorted())
    }

    @TypeConverter
    fun toPackageSet(rawJson: String): Set<String> {
        if (rawJson.isBlank()) return emptySet()
        return runCatching { json.decodeFromString<List<String>>(rawJson).toSet() }
            .getOrDefault(emptySet())
    }
}