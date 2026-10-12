package com.example.quanlynongtrai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class HistoryFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var tvHistory: TextView
    private lateinit var tvClearHistory: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        tvHistory = view.findViewById(R.id.tvHistory)
        tvClearHistory = view.findViewById(R.id.tvClearHistory)

        tvClearHistory.setOnClickListener {
            viewModel.clearHistory()
            Toast.makeText(requireContext(), "Đã xoá lịch sử", Toast.LENGTH_SHORT).show()
        }

        setupObservers()

        return view
    }

    private fun setupObservers() {
        viewModel.historyLogs.observe(viewLifecycleOwner) { historyList ->
            if (historyList.isEmpty()) {
                tvHistory.text = "Chưa có lịch sử..."
            } else {
                val sb = StringBuilder()
                historyList.take(20).forEach { log ->
                    // Thêm 2 dấu \n để các dòng log cách nhau ra cho dễ đọc
                    sb.append("[${log.time}] ${log.type}: ${log.desc}\n\n")
                }
                tvHistory.text = sb.toString()
            }
        }
    }
}