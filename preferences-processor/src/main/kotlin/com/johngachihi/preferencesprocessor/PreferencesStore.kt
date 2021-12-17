package com.johngachihi.preferencesprocessor

interface PreferencesStore {
    fun write(key: String, value: String)
    fun read(key: String): String
}