package com.example.schedule.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Время хранится как момент (UTC millis) + зона, в которой событие создали. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val location: String?,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val zoneId: String,
    val remindMinutes: Int = 15,
    /** id события в системном календаре (Calendar Provider); null — в календаре его нет. */
    val calendarEventId: Long? = null,
    /** 0 — создано в приложении, 1 — импортировано из системного календаря. */
    @ColumnInfo(defaultValue = "0") val source: Int = 0,
    /** Уведомление-напоминание уже показано: после перезагрузки его не нужно повторять. */
    @ColumnInfo(defaultValue = "0") val notified: Boolean = false,
) {
    companion object {
        const val SOURCE_APP = 0
        const val SOURCE_CALENDAR = 1
    }
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startUtcMillis")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    /** Ещё не закончившиеся события, ближайшие первыми. */
    @Query("SELECT * FROM events WHERE endUtcMillis > :now ORDER BY startUtcMillis LIMIT :limit")
    suspend fun upcoming(now: Long, limit: Int): List<EventEntity>

    @Query("UPDATE events SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: Long)

    @Insert
    suspend fun insert(e: EventEntity): Long

    @Update
    suspend fun update(e: EventEntity)

    @Delete
    suspend fun delete(e: EventEntity)
}

/** Версия 1 -> 2: связь с системным календарём. Старые события остаются, данные не теряются. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN calendarEventId INTEGER")
        db.execSQL("ALTER TABLE events ADD COLUMN source INTEGER NOT NULL DEFAULT 0")
    }
}

/** Версия 2 -> 3: признак «напоминание показано». */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN notified INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [EventEntity::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "schedule.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
