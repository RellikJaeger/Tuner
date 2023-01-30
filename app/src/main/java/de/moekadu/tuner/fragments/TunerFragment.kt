/*
 * Copyright 2020 Michael Moessner
 *
 * This file is part of Tuner.
 *
 * Tuner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tuner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tuner.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.tuner.fragments

import android.Manifest
import android.graphics.Paint
import android.os.Bundle
import android.text.TextPaint
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import de.moekadu.tuner.MainActivity
import de.moekadu.tuner.R
import de.moekadu.tuner.misc.WaveFileWriterIntent
import de.moekadu.tuner.models.PitchHistoryModel
import de.moekadu.tuner.preferenceResources
import de.moekadu.tuner.viewmodels.TunerViewModel
import de.moekadu.tuner.views.*
import kotlinx.coroutines.launch

class TunerFragment : Fragment() {
    val viewModel: TunerViewModel by viewModels {
        TunerViewModel.Factory(requireActivity().preferenceResources, null)
    }

    private val waveFileWriterIntent = WaveFileWriterIntent(this)

    private var spectrumPlot: PlotView? = null
    private var spectrumPlotChangeId = -1
    private var correlationPlot: PlotView? = null
    private var correlationPlotChangeId = -1
    private var pitchPlot: PlotView? = null
    private var pitchPlotChangeId = -1
    private var volumeMeter: VolumeMeter? = null
    private var recordFab: FloatingActionButton? = null

    /** Instance for requesting audio recording permission.
     * This will create the sourceJob as soon as the permissions are granted.
     */
    private val askForPermissionAndNotifyViewModel = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        if (result) {
            viewModel.startSampling()
        } else {
            Toast.makeText(activity, getString(R.string.no_audio_recording_permission), Toast.LENGTH_LONG)
                .show()
            Log.v(
                "Tuner",
                "TunerFragment.askForPermissionAnNotifyViewModel: No audio recording permission is granted."
            )
        }
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.toolbar, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    // User chose the "Settings" item, show the app settings UI...
                    (activity as MainActivity?)?.loadSettingsFragment()
                    return true
                }
            }
            return false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
//        Log.v("Tuner", "TunerFragment.onCreateView")
        val view = inflater.inflate(R.layout.diagrams, container, false)

        activity?.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        pitchPlot = view.findViewById(R.id.pitch_plot)
        spectrumPlot = view.findViewById(R.id.spectrum_plot)
        correlationPlot = view.findViewById(R.id.correlation_plot)
        volumeMeter = view.findViewById(R.id.volume_meter)
        recordFab = view.findViewById(R.id.record)

        spectrumPlot?.setXTicks(
            floatArrayOf(0f, 250f, 500f, 750f, 1000f, 1250f, 1500f, 1750f, 2000f, 2250f, 2500f,
                2750f, 3000f, 3250f, 3500f, 3750f, 4000f, 4500f, 5000f, 5500f, 6000f,
                6500f, 7000f, 7500f, 8000f, 8500f, 9000f, 9500f, 10000f, 11000f, 12000f, 13000f, 14000f,
                15000f, 16000f, 17000f, 18000f, 19000f, 20000f, 25000f, 30000f, 35000f, 40000f)
        ) { _, i ->
            getString(R.string.hertz, i)
        }
        spectrumPlot?.setYTouchLimits(0f, Float.POSITIVE_INFINITY)

        correlationPlot?.setXTicks(
            floatArrayOf(
                1 / 1600f,
                1 / 150f,
                1 / 80f,
                1 / 50f,
                1 / 38f,
                1 / 30f,
                1 / 25f,
                1 / 20f,
                1 / 17f,
                1 / 15f,
                1 / 13f,
                1 / 11f,
                1 / 10f,
                1 / 9f,
                1 / 8f,
                1 / 7f,
                1 / 6f,
                1 / 5f,
                1 / 4f,
                1 / 3f,
                1 / 2f,
                1 / 1f,
            )
        ) { _, i ->
            getString(R.string.hertz, 1 / i)
        }
        correlationPlot?.setYTicks(floatArrayOf(0f)) { _, _ -> "" }

        viewModel.spectrumPlotModel.observe(viewLifecycleOwner) { model ->
//            Log.v("Tuner", "TunerFragment: spectrumModel.observe: changeId=${model.changeId}, noteDetectionId=${model.noteDetectionChangeId}, targetId=${model.targetChangeId}")
            if (model.changeId < spectrumPlotChangeId)
                spectrumPlotChangeId = -1
            if (model.noteDetectionChangeId > spectrumPlotChangeId) {
                spectrumPlot?.plot(model.frequencies, model.squaredAmplitudes)
                spectrumPlot?.setMarks(model.harmonicsFrequencies, null, HARMONIC_ID,
                    indexEnd = model.numHarmonics)
                val label = getString(R.string.hertz, model.detectedFrequency)
                if (model.detectedFrequency > 0f) {
                    spectrumPlot?.setXMark(
                        model.detectedFrequency, label, MARK_ID_FREQUENCY, LabelAnchor.SouthWest,
                        placeLabelsOutsideBoundsIfPossible = false,
                    )
                } else {
                    spectrumPlot?.removePlotMarks(MARK_ID_FREQUENCY)
                }
                if (model.frequencies.isNotEmpty())
                    spectrumPlot?.setXTouchLimits(0f, model.frequencies.last())
            }

            if (model.targetChangeId > spectrumPlotChangeId) {
                spectrumPlot?.xRange(model.frequencyRange[0], model.frequencyRange[1], 300L)
            }
            spectrumPlot?.enableExtraPadding = model.useExtraPadding
            spectrumPlotChangeId = model.changeId
        }

        viewModel.correlationPlotModel.observe(viewLifecycleOwner) { model ->
//            Log.v("Tuner", "TunerFragment: spectrumModel.observe: changeId=${model.changeId}, noteDetectionId=${model.noteDetectionChangeId}, targetId=${model.targetChangeId}")
            if (model.changeId < correlationPlotChangeId)
                correlationPlotChangeId = -1

            if (model.noteDetectionChangeId > correlationPlotChangeId) {
                correlationPlot?.plot(model.timeShifts, model.correlationValues)
                val label = getString(R.string.hertz, model.detectedFrequency)
                if (model.detectedFrequency > 0f) {
                    correlationPlot?.setXMark(
                        1.0f / model.detectedFrequency,
                        label,
                        MARK_ID_FREQUENCY,
                        LabelAnchor.SouthWest,
                        placeLabelsOutsideBoundsIfPossible = false
                    )
                } else {
                    correlationPlot?.removePlotMarks(MARK_ID_FREQUENCY)
                }
                if (model.timeShifts.isNotEmpty())
                    correlationPlot?.setXTouchLimits(0f, model.timeShifts.last())
            }

            if (model.targetChangeId > correlationPlotChangeId) {
                correlationPlot?.xRange(model.timeShiftRange[0], model.timeShiftRange[1], 300L)
            }

            correlationPlot?.enableExtraPadding = model.useExtraPadding
            correlationPlotChangeId = model.changeId
        }

        viewModel.pitchHistoryModel.observe(viewLifecycleOwner) { model ->
            if (model.changeId < pitchPlotChangeId)
                pitchPlotChangeId = -1

            if (model.musicalScaleChangeId > pitchPlotChangeId || model.notePrintOptionsChangeId > pitchPlotChangeId) {
                pitchPlot?.setYTicks(model.musicalScaleFrequencies,
                    noteNameScale = model.musicalScale.noteNameScale,
                    noteIndexBegin = model.musicalScale.noteIndexBegin,
                    notePrintOptions = model.notePrintOptions
                )

                pitchPlot?.setYTouchLimits(model.musicalScaleFrequencies[0], model.musicalScaleFrequencies.last(), 0L)
                pitchPlot?.enableExtraPadding = model.useExtraPadding
            }
            if (model.historyValuesChangeId > pitchPlotChangeId) {
                if (model.numHistoryValues == 0 || model.currentFrequency <= 0f) {
                    pitchPlot?.removePlotPoints(PitchHistoryModel.CURRENT_FREQUENCY_POINT_TAG)
                    pitchPlot?.removePlotPoints(PitchHistoryModel.TUNING_DIRECTION_POINT_TAG)
                } else {
                    val point = floatArrayOf(model.numHistoryValues - 1f, model.currentFrequency)
                    pitchPlot?.setPoints(point, tag = PitchHistoryModel.CURRENT_FREQUENCY_POINT_TAG)
                    pitchPlot?.setPoints(point, tag = PitchHistoryModel.TUNING_DIRECTION_POINT_TAG)
                }
                pitchPlot?.plot(
                    model.historyValues, PitchHistoryModel.HISTORY_LINE_TAG,
                    indexBegin = 0, indexEnd = model.numHistoryValues
                )
                pitchPlot?.xRange(0f, 1.08f * model.historyValues.size)
            }

            if (model.yRangeChangeId > pitchPlotChangeId) {
                pitchPlot?.yRange(model.yRangeAuto[0], model.yRangeAuto[1], 600L)
            }

            if (model.targetNoteChangeId > pitchPlotChangeId || model.notePrintOptionsChangeId > pitchPlotChangeId) {
                val targetNote = model.targetNote
                if (model.targetNoteFrequency > 0f && targetNote != null) {
                    pitchPlot?.setYMark(
                        model.targetNoteFrequency,
                        targetNote,
                        model.notePrintOptions,
                        PitchHistoryModel.TARGET_NOTE_MARK_TAG,
                        LabelAnchor.East,
                        model.targetNoteMarkStyle,
                        placeLabelsOutsideBoundsIfPossible = true
                    )
                } else {
                    pitchPlot?.removePlotMarks(PitchHistoryModel.TARGET_NOTE_MARK_TAG)
                }
            }

            if (model.toleranceChangeId > pitchPlotChangeId) {
                if (model.lowerToleranceFrequency > 0f && model.upperToleranceFrequency > 0f) {
//                    Log.v("Tuner","TunerFragment: setting tolerance in pitchhistory: ${model.lowerToleranceFrequency} -- ${model.upperToleranceFrequency}, plotrange=${model.yRangeAuto[0]} -- ${model.yRangeAuto[1]}, currentFreq=${model.currentFrequency}")
                    pitchPlot?.setMarks(
                        null,
                        floatArrayOf(
                            model.lowerToleranceFrequency,
                            model.upperToleranceFrequency
                        ),
                        PitchHistoryModel.TOLERANCE_MARK_TAG,
                        styleIndex = PitchHistoryModel.TOLERANCE_STYLE,
                        anchors = arrayOf(LabelAnchor.NorthWest, LabelAnchor.SouthWest),
                        backgroundSizeType = MarkLabelBackgroundSize.FitLargest,
                        placeLabelsOutsideBoundsIfPossible = false,
                        maxLabelBounds = null
                    ) { index: Int, _: Float?, _: Float?, textPaint: TextPaint, backgroundPaint: Paint?, gravity: LabelGravity, paddingHorizontal: Float, paddingVertical: Float, cornerRadius: Float ->
                        val s = when (index) {
                            0 -> getString(R.string.cent, -model.toleranceInCents)
                            1 -> getString(R.string.cent, model.toleranceInCents)
                            else -> ""
                        }
                        StringLabel(
                            s,
                            textPaint,
                            backgroundPaint,
                            cornerRadius,
                            gravity,
                            paddingHorizontal,
                            paddingHorizontal,
                            paddingVertical,
                            paddingVertical
                        )
                    }
                } else {
                    pitchPlot?.removePlotMarks(PitchHistoryModel.TOLERANCE_MARK_TAG)
                }
            }

            pitchPlot?.setLineStyle(model.historyLineStyle, PitchHistoryModel.HISTORY_LINE_TAG)
            pitchPlot?.setPointStyle(model.currentFrequencyPointStyle, PitchHistoryModel.CURRENT_FREQUENCY_POINT_TAG)
            pitchPlot?.setPointStyle(model.tuningDirectionPointStyle, PitchHistoryModel.TUNING_DIRECTION_POINT_TAG)
            val pointSize = pitchPlot?.pointSizes?.get(model.currentFrequencyPointStyle) ?: 1f
            pitchPlot?.setPointOffset(
                0f, pointSize * model.tuningDirectionPointRelativeOffset,
                PitchHistoryModel.TUNING_DIRECTION_POINT_TAG
            )
//            Log.v("Tuner", "TunerFragment: tuningDirectionPointVisible = ${model.tuningDirectionPointVisible}, offset=${pointSize * model.tuningDirectionPointRelativeOffset}")
            pitchPlot?.setPointVisible(model.tuningDirectionPointVisible, PitchHistoryModel.TUNING_DIRECTION_POINT_TAG)
            pitchPlot?.setMarkStyle(model.targetNoteMarkStyle, PitchHistoryModel.TARGET_NOTE_MARK_TAG)
            pitchPlotChangeId = model.changeId
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.showWaveWriterFab.collect {
                    recordFab?.visibility = if (it) View.VISIBLE else View.GONE
                }
            }
        }

        recordFab?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.waveWriter.storeSnapshot()
                waveFileWriterIntent.launch()
            }
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        askForPermissionAndNotifyViewModel.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onResume() {
        super.onResume()
        activity?.let {
            it.setTitle(R.string.app_name)
            if (it is MainActivity) {
                it.setStatusAndNavigationBarColors()
                it.setPreferenceBarVisibilty(View.VISIBLE)
            }
        }
    }

    override fun onStop() {
        viewModel.stopSampling()
        super.onStop()
    }

    companion object{
        private const val MARK_ID_FREQUENCY = 11L
        private const val HARMONIC_ID = 12L
    }
}
