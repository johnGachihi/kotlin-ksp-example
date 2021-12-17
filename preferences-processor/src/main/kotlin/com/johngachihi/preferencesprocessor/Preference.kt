package com.johngachihi.preferencesprocessor

import java.time.Duration
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
annotation class Preference (
    val key: String,
    val default: String,
    val converter: KClass<out PreferenceConverter<*>>
)


// Remove below

class DurationConverter : PreferenceConverter<Duration> {
    override fun parse(value: String): Duration {
        return Duration.ofMinutes(5)
    }

    override fun format(value: Duration): String {
        return value.toMinutes().toString()
    }
}

class StringConverter : PreferenceConverter<String> {
    override fun parse(value: String): String {
        return ""
    }

    override fun format(value: String): String {
        return ""
    }

}

class A {
    @Preference(
        key = "",
        default = "",
        converter = StringConverter::class
    )
    val a: String = ""
}

