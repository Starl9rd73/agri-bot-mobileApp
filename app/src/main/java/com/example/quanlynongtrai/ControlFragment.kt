package com.example.quanlynongtrai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.materialswitch.MaterialSwitch

class ControlFragment : Fragment() {

    // Vẫn dùng chung ViewModel để kết nối MQTT
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tvPumpStatus: TextView
    private lateinit var switchPump: MaterialSwitch
    private lateinit var etQuickDuration: EditText
    private lateinit var btnQuickIrrigate: Button

    private lateinit var switchAuto: MaterialSwitch
    private lateinit var etThreshold: EditText
    private lateinit var etDuration: EditText
    private lateinit var btnSaveAuto: Button


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_control, container, false)
        initUI(view)
        setupObservers()
        return view
    }

    private fun initUI(view: View) {
        tvPumpStatus = view.findViewById(R.id.tvPumpStatus)
        switchPump = view.findViewById(R.id.switchPump)
        etQuickDuration = view.findViewById(R.id.etQuickDuration)
        btnQuickIrrigate = view.findViewById(R.id.btnQuickIrrigate)
        switchAuto = view.findViewById(R.id.switchAuto)
        etThreshold = view.findViewById(R.id.etThreshold)
        etDuration = view.findViewById(R.id.etDuration)
        btnSaveAuto = view.findViewById(R.id.btnSaveAuto)

        switchPump.setOnCheckedChangeListener { _, isChecked ->
            viewModel.sendPumpCommand(if (isChecked) "turn_on" else "turn_off")
        }

        updateAutoInputsEnabled(switchAuto.isChecked)
        switchAuto.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) { // Tránh lỗi gửi lệnh liên tục khi MQTT tự động cập nhật
                // Khóa/mở ngay lập tức giao diện khi người dùng gạt
                updateAutoInputsEnabled(isChecked)
                val threshold = etThreshold.text.toString().toFloatOrNull() ?: 30f
                val duration = etDuration.text.toString().toIntOrNull() ?: 30
                viewModel.sendAutoConfigCommand(isChecked, threshold, duration)
                Toast.makeText(requireContext(), if(isChecked) "Đã BẬT tự động" else "Đã TẮT tự động", Toast.LENGTH_SHORT).show()
            }
        }

        btnQuickIrrigate.setOnClickListener {
            val duration = etQuickDuration.text.toString().toIntOrNull() ?: 10
            viewModel.sendIrrigateCommand(duration)
            Toast.makeText(requireContext(), "Khởi động tưới: $duration s", Toast.LENGTH_SHORT).show()
        }

        btnSaveAuto.setOnClickListener {
            val isAuto = switchAuto.isChecked
            val threshold = etThreshold.text.toString().toFloatOrNull() ?: 30f
            val duration = etDuration.text.toString().toIntOrNull() ?: 30
            viewModel.sendAutoConfigCommand(isAuto, threshold, duration)
            Toast.makeText(requireContext(), "Đã cập nhật tự động", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAutoInputsEnabled(isEnabled: Boolean) {
        etThreshold.isEnabled = isEnabled
        etDuration.isEnabled = isEnabled
        btnSaveAuto.isEnabled = isEnabled

        // Tùy chỉnh độ mờ (alpha) để người dùng dễ nhận biết trạng thái bị khóa
        val alphaValue = if (isEnabled) 1.0f else 0.4f
        etThreshold.alpha = alphaValue
        etDuration.alpha = alphaValue
        btnSaveAuto.alpha = alphaValue
    }
    private fun setupObservers() {
        viewModel.deviceStatus.observe(viewLifecycleOwner) { status ->
            tvPumpStatus.text = "Trạng thái: ${if (status.pumpOn) "Đang bật" else "Tắt"}"

            switchPump.isChecked = status.pumpOn // Không cần setOnCheckedChangeListener(null) nữa nhờ có isPressed

            status.autoConfig?.let { cfg ->
                switchAuto.isChecked = cfg.enabled
                etThreshold.setText(cfg.threshold.toString())
                etDuration.setText(cfg.duration.toString())
                // ĐÃ XOÁ phần gán listener ở đây cho gọn
            }
        }
    }
}