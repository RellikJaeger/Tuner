package de.moekadu.tuner.models

import de.moekadu.tuner.temperaments.*

/** Model for detected note view. */
class DetectedNoteViewModel {
    /** Change counter, which increases which each changeSettings-call. */
    var changeId = 0
        private set

    /** Musical scale. */
    var musicalScale = MusicalScaleFactory.create(TemperamentType.EDO12)
        private set
    /** Note print options .*/
    var notePrintOptions = MusicalNotePrintOptions.None
        private set
    /** ChangeId of last change of musical scale or notePrintOptions. */
    var scaleChangeId = 0
        private set

    /** Current musical note. */
    var note: MusicalNote? = null
        private set
    /** ChangeId of last change of note. */
    var noteChangedId = 0
        private set

    /** Duration in seconds between two note updates. */
    var noteUpdateInterval = 1f
        private set
    /** ChangeId of last change of noteUpdateInterval. */
    var noteUpdateIntervalChangedId = 0
        private set

    /** Change view properties.
     * @param note A new detected note (or null if this is not changed.)
     * @param musicalScale New musical scale or null if this is not changed.
     * @param notePrintOptions New note print options.
     * @param noteUpdateInterval Rough duration in seconds between two detected notes.
     */
    fun changeSettings(
        note: MusicalNote? = null,
        musicalScale: MusicalScale? = null,
        notePrintOptions: MusicalNotePrintOptions = this.notePrintOptions,
        noteUpdateInterval: Float = this.noteUpdateInterval
    ) {
        changeId++

        if (note != null) {
            this.note = note
            noteChangedId = changeId
        }

        if (notePrintOptions != this.notePrintOptions) {
            this.notePrintOptions = notePrintOptions
            scaleChangeId = changeId
        }

        if (musicalScale != null) {
            this.musicalScale = musicalScale
            scaleChangeId = changeId
        }

        if (noteUpdateInterval != this.noteUpdateInterval) {
            this.noteUpdateInterval = noteUpdateInterval
            noteUpdateIntervalChangedId = changeId
        }
    }

}