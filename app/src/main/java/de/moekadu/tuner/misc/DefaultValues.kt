package de.moekadu.tuner.misc

import de.moekadu.tuner.temperaments.TemperamentType

class DefaultValues {
    companion object {
        val TEMPERAMENT = TemperamentType.EDO12
        const val REFERENCE_FREQUENCY = 440f
        const val REFERENCE_FREQUENCY_STRING = "440"
        const val FREQUENCY_MIN = 16f
        const val FREQUENCY_MAX = 16000f
        const val SAMPLE_RATE = 44100
    }
}