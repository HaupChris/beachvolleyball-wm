package de.habits.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.habits.core.Habit
import java.time.LocalDate

class HabitDatabase(context: Context) : SQLiteOpenHelper(context, "habits.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT NOT NULL, sort INTEGER NOT NULL DEFAULT 0)")
        db.execSQL(
            "CREATE TABLE entries (habit_id INTEGER NOT NULL, date TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY (habit_id, date))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadHabits(): List<Habit> =
        readableDatabase.rawQuery("SELECT id, data FROM habits ORDER BY sort, id", null).use { c ->
            buildList { while (c.moveToNext()) add(HabitJson.decode(c.getLong(0), c.getString(1))) }
        }

    fun loadEntries(): Map<Long, Map<LocalDate, Int>> =
        readableDatabase.rawQuery("SELECT habit_id, date, amount FROM entries", null).use { c ->
            val result = mutableMapOf<Long, MutableMap<LocalDate, Int>>()
            while (c.moveToNext()) result.getOrPut(c.getLong(0)) { mutableMapOf() }[LocalDate.parse(c.getString(1))] = c.getInt(2)
            result
        }

    /** Inserts (id == 0) or updates a habit, returns its id. */
    fun saveHabit(h: Habit): Long {
        val values = ContentValues().apply { put("data", HabitJson.encode(h)) }
        return if (h.id == 0L) {
            writableDatabase.insertOrThrow("habits", null, values)
        } else {
            writableDatabase.update("habits", values, "id = ?", arrayOf(h.id.toString()))
            h.id
        }
    }

    fun deleteHabit(id: Long) {
        writableDatabase.delete("entries", "habit_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("habits", "id = ?", arrayOf(id.toString()))
    }

    fun setAmount(habitId: Long, date: LocalDate, amount: Int) {
        if (amount <= 0) {
            writableDatabase.delete("entries", "habit_id = ? AND date = ?", arrayOf(habitId.toString(), date.toString()))
        } else {
            val values = ContentValues().apply {
                put("habit_id", habitId)
                put("date", date.toString())
                put("amount", amount)
            }
            writableDatabase.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}
