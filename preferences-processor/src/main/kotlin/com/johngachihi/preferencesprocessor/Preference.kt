package com.johngachihi.preferencesprocessor

import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
annotation class Preference(
    val key: String,
    val default: String,
    val converter: KClass<out PreferenceConverter<*>> = StringConverter::class
)

/**
 * Default Converter does nothing
 */
private class StringConverter : PreferenceConverter<String> {
    override fun parse(value: String): String = value
    override fun format(value: String): String = value
}
