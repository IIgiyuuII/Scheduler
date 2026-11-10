package com.example.schedule.data

import android.content.Context
import androidx.room.*
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
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startUtcMillis")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    @Insert
    suspend fun insert(e: EventEntity): Long

    @Update
    suspend fun update(e: EventEntity)

    @Delete
    suspend fun delete(e: EventEntity)
}

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "schedule.db")
                .build().also { instance = it }
        }
    }
}
