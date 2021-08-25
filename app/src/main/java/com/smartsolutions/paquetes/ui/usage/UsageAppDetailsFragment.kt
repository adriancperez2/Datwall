package com.smartsolutions.paquetes.ui.usage

import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.smartsolutions.paquetes.databinding.FragmentUsageAppDetailsBinding
import com.smartsolutions.paquetes.helpers.NetworkUsageUtils
import com.smartsolutions.paquetes.helpers.UIHelper
import com.smartsolutions.paquetes.managers.contracts.IIconManager
import com.smartsolutions.paquetes.managers.models.DataUnitBytes
import com.smartsolutions.paquetes.managers.models.Traffic
import com.smartsolutions.paquetes.repositories.models.App
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

const val ARG_APP = "app"
const val ARG_PERIOD = "period"

@AndroidEntryPoint
class UsageAppDetailsFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var iconManager: IIconManager

    private lateinit var uiHelper: UIHelper

    private lateinit var binding: FragmentUsageAppDetailsBinding
    private val viewModel by viewModels<UsageAppDetailsViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUsageAppDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val app = arguments?.getParcelable<App>(ARG_APP)

        if (app == null) {
            this.dismiss()
            return
        }

        uiHelper = UIHelper(requireContext())

        binding.include.circleColour.visibility = View.GONE
        iconManager.getAsync(app.packageName, app.version) {
            binding.include.appIcon.setImageBitmap(it)
        }

        binding.include.textAppName.text = app.name
        val value = app.traffic!!.totalBytes.getValue()
        binding.include.textUsageValue.text = "${value.value} ${value.dataUnit}"
        configureLineChart()

        viewModel.getUsageByTime(app.uid).observe(viewLifecycleOwner, { result ->
            setLineChart(result.first, result.second)
        })
    }


    private fun configureLineChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = uiHelper.getTextColorByTheme()
            }
            axisLeft.apply {
                setDrawZeroLine(false)
                setDrawGridLinesBehindData(false)
                textColor = uiHelper.getTextColorByTheme()
                axisMinimum = 0f
            }
            setPinchZoom(true)
        }
    }

    private fun setLineChart(traffics: List<Traffic>, timeUnit: NetworkUsageUtils.TimeUnit) {
        val entries = mutableListOf<Entry>()

        traffics.forEach {
            entries.add(
                Entry(
                    it.startTime.toFloat(),
                    it.totalBytes.bytes.toFloat()
                )
            )
        }

        binding.lineChart.apply {
            data = LineData(LineDataSet(entries, "Consumo").apply {
                setDrawFilled(false)
                valueTextSize = 0f
                isHighlightPerTapEnabled = true
                circleRadius = 5f
                setDrawCircleHole(true)
                lineWidth = 2f
            })
            animateY(700)
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    val bytes = DataUnitBytes(value.toLong()).getValue()
                    return "${Math.round(bytes.value * 100) / 100.0} ${bytes.dataUnit}"
                }
            }

            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                    return when (timeUnit) {
                        NetworkUsageUtils.TimeUnit.HOUR -> {
                            SimpleDateFormat("hh aa", Locale.US).format(Date(value.toLong()))
                        }
                        NetworkUsageUtils.TimeUnit.DAY -> {
                            "Dia " + SimpleDateFormat("dd", Locale.US).format(Date(value.toLong()))
                        }
                        NetworkUsageUtils.TimeUnit.MONTH -> {
                            SimpleDateFormat("MMM", Locale.US).format(Date(value.toLong()))
                        }
                    }
                }
            }


            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {

                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val index = entries.indexOf(e)
                    if (index in 0..traffics.size) {
                        val bytes = traffics[index].totalBytes.getValue()
                        Toast.makeText(
                            context,
                            "${Math.round(bytes.value * 100) / 100.0} ${bytes.dataUnit}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onNothingSelected() {
                }

            })

        }.invalidate()
    }


    companion object {
        fun newInstance(app: App): UsageAppDetailsFragment =
            UsageAppDetailsFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_APP, app)
                }
            }

    }
}