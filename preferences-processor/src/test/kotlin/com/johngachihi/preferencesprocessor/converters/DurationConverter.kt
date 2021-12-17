package com.johngachihi.preferencesprocessor.converters

import com.johngachihi.preferencesprocessor.PreferenceConverter
import java.time.Duration

class DurationConverter : PreferenceConverter<Duration> {
    override fun parse(value: String): Duration {
        return Duration.ofMinutes(value.toLong())
    }

    override fun format(value: Duration): String {
        return value.toMinutes().toString()
    }
}