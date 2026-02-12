package com.android.dongtai.data.dao

import androidx.room.*
import com.android.dongtai.data.entity.RecordType
import com.android.dongtai.data.entity.ToolRecord
import kotlinx.coroutines.flow.Flow

/**
 * 工具记录数据访问对象
 */
@Dao
interface ToolRecordDao {
    
    @Query("SELECT * FROM tool_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<ToolRecord>>
    
    @Query("SELECT * FROM tool_records WHERE id = :id")
    suspend fun getRecordById(id: Long): ToolRecord?
    
    @Query("SELECT * FROM tool_records WHERE recordType = :type ORDER BY timestamp DESC")
    fun getRecordsByType(type: RecordType): Flow<List<ToolRecord>>
    
    @Query("SELECT * FROM tool_records WHERE recordType = :type ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecordByType(type: RecordType): ToolRecord?
    
    @Query("SELECT * FROM tool_records WHERE beforeRecordId = :beforeId")
    suspend fun getAfterRecordByBeforeId(beforeId: Long): ToolRecord?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ToolRecord): Long
    
    @Delete
    suspend fun deleteRecord(record: ToolRecord)
    
    @Query("DELETE FROM tool_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)
}
