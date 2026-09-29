package de.zahnputz.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.zahnputz.core.DayRecord
import java.time.LocalDate

/** One row per day. Targets are stored with the record so later plan changes don't alter history. */
class DayDatabase(context: Context) : SQLiteOpenHelper(context, "zahnputz.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE days (
                date TEXT PRIMARY KEY,
                brush_count INTEGER NOT NULL,
                brush_target INTEGER NOT NULL,
                flossed INTEGER NOT NULL,
                floss_required INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadAll(): List<DayRecord> =
        readableDatabase.rawQuery(
            "SELECT date, brush_count, brush_target, flossed, floss_required FROM days", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DayRecord(
                            date = LocalDate.parse(c.getString(0)),
                            brushCount = c.getInt(1),
                            brushTarget = c.getInt(2),
                            flossed = c.getInt(3) != 0,
                            flossRequired = c.getInt(4) != 0,
                        )
                    )
                }
            }
        }

    fun upsert(r: DayRecord) {
        val values = ContentValues().apply {
            put("date", r.date.toString())
            put("brush_count", r.brushCount)
            put("brush_target", r.brushTarget)
            put("flossed", if (r.flossed) 1 else 0)
            put("floss_required", if (r.flossRequired) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("days", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
