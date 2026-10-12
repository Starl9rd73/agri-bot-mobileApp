package com.example.quanlynongtrai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        val dashboardFragment = DashboardFragment()
        val controlFragment = ControlFragment()
        val historyFragment = HistoryFragment()

        // Đặt Tab Tổng quan làm màn hình mặc định khi mở app
        setCurrentFragment(dashboardFragment)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    setCurrentFragment(dashboardFragment)
                    true
                }
                R.id.nav_control -> {
                    setCurrentFragment(controlFragment)
                    true
                }
                R.id.nav_history -> {
                    setCurrentFragment(historyFragment)
                    true
                }
                else -> false
            }
        }

    }

    private fun setCurrentFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fragmentContainer, fragment)
            commit()
        }
    }
}