package com.example.quanlynongtrai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "UserDB", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        // Tạo bảng user
        val createTable = "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT, password TEXT)"
        db.execSQL(createTable)

        // Tạo bảng lịch sử hoạt động
        val createHistoryTable = """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_type TEXT,
                description TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        db.execSQL(createHistoryTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }

    fun addActivity(type: String, desc: String) {
        val db = this.writableDatabase
        val values = android.content.ContentValues().apply {
            put("event_type", type)
            put("description", desc)
        }
        db.insert("history", null, values)
        db.close()
    }

    fun getHistory(): List<ActivityLog> {
        val list = mutableListOf<ActivityLog>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM history ORDER BY id DESC LIMIT 50", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(ActivityLog(
                    cursor.getString(cursor.getColumnIndexOrThrow("event_type")),
                    cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    data class ActivityLog(val type: String, val desc: String, val time: String)

    fun registerUser(email: String, pass: String): Long {
        val db = this.writableDatabase
        val values = android.content.ContentValues().apply {
            put("email", email)
            put("password", pass)
        }
        val success = db.insert("users", null, values)
        db.close()
        return success
    }

    fun checkUser(email: String, pass: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE email = ? AND password = ?", arrayOf(email, pass))
        val count = cursor.count
        cursor.close()
        db.close()
        return count > 0
    }

    fun isEmailExists(email: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM users WHERE email = ?", arrayOf(email))
        val count = cursor.count
        cursor.close()
        db.close()
        return count > 0
    }
}