package com.johngachihi.preferencesprocessor

interface PreferenceConverter<T> {
    fun parse(value: String): T
    fun format(value: T): String
}