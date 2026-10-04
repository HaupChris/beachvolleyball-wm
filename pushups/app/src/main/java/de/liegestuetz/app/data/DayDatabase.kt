package de.liegestuetz.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.liegestuetz.core.DayRecord
import java.time.LocalDate

/** One row per day. The target is stored with the record so later plan changes don't alter history. */
class DayDatabase(context: Context) : SQLiteOpenHelper(context, "liegestuetz.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE days (
                date TEXT PRIMARY KEY,
                reps INTEGER NOT NULL,
                target INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadAll(): List<DayRecord> =
        readableDatabase.rawQuery("SELECT date, reps, target FROM days", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(DayRecord(date = LocalDate.parse(c.getString(0)), reps = c.getInt(1), target = c.getInt(2)))
                }
            }
        }

    fun upsert(r: DayRecord) {
        val values = ContentValues().apply {
            put("date", r.date.toString())
            put("reps", r.reps)
            put("target", r.target)
        }
        writableDatabase.insertWithOnConflict("days", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
