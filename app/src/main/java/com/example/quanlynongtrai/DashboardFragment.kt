package com.example.quanlynongtrai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class DashboardFragment : Fragment() {

    // Dùng chung MainViewModel với MainActivity
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tvStatus: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvSoil: TextView
    private lateinit var tvLight: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)

        tvStatus = view.findViewById(R.id.tvStatus)
        tvTemp = view.findViewById(R.id.tvTemp)
        tvHum = view.findViewById(R.id.tvHum)
        tvSoil = view.findViewById(R.id.tvSoil)
        tvLight = view.findViewById(R.id.tvLight)

        setupObservers()

        return view
    }

    private fun setupObservers() {
        // Lắng nghe trạng thái MQTT
        viewModel.connectionStatus.observe(viewLifecycleOwner) { statusMsg ->
            tvStatus.text = statusMsg
        }

        // Lắng nghe dữ liệu cảm biến thay đổi
        viewModel.sensorData.observe(viewLifecycleOwner) { data ->
            tvTemp.text = "${data.temperature} °C"
            tvHum.text = "${data.humidity} %"
            tvSoil.text = "${data.soilMoisture} %"
            tvLight.text = "${data.lightLevel} Lux"
        }
    }
}