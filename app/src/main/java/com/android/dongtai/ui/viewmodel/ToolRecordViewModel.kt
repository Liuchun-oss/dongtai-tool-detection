package com.android.dongtai.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.dongtai.data.database.AppDatabase
import com.android.dongtai.data.entity.RecordType
import com.android.dongtai.data.entity.ToolRecord
import com.android.dongtai.data.entity.WorkRecord
import com.android.dongtai.service.OnnxToolDetectionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 工具记录ViewModel
 */
class ToolRecordViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.toolRecordDao()
    private val detectionService = OnnxToolDetectionService.getInstance(application)
    
    val allRecords: Flow<List<ToolRecord>> = dao.getAllRecords()
    
    /**
     * 获取所有工作记录（组合记录）
     */
    val allWorkRecords: Flow<List<WorkRecord>> = allRecords.map { records ->
        val workRecords = mutableListOf<WorkRecord>()
        val beforeRecords = records.filter { it.recordType == RecordType.BEFORE_WORK }
        
        beforeRecords.forEach { beforeRecord ->
            // 查找对应的作业后记录
            val afterRecord = records.firstOrNull { 
                it.recordType == RecordType.AFTER_WORK && it.beforeRecordId == beforeRecord.id 
            }
            workRecords.add(WorkRecord(beforeRecord, afterRecord))
        }
        
        // 按时间倒序排列
        workRecords.sortedByDescending { it.beforeRecord.timestamp }
    }
    
    /**
     * 是否有未完成的作业前记录（Flow版本，用于UI监听）
     */
    val hasUncompletedBeforeRecord: Flow<Boolean> = allRecords.map { records ->
        val latestBefore = records
            .filter { it.recordType == RecordType.BEFORE_WORK }
            .maxByOrNull { it.timestamp }
        
        if (latestBefore == null) {
            false
        } else {
            // 检查是否有对应的作业后记录
            val hasAfterRecord = records.any { 
                it.recordType == RecordType.AFTER_WORK && it.beforeRecordId == latestBefore.id 
            }
            !hasAfterRecord
        }
    }
    
    /**
     * 获取所有未完成的工作记录（Flow版本）
     */
    val uncompletedWorkRecords: Flow<List<WorkRecord>> = allWorkRecords.map { workRecords ->
        workRecords.filter { !it.isCompleted() }
    }
    
    /**
     * 插入记录并自动识别工具
     */
    fun insertRecordWithDetection(record: ToolRecord, onDetectionComplete: (ToolRecord) -> Unit = {}) {
        viewModelScope.launch {
            // 如果是作业前记录，直接识别并保存
            if (record.recordType == RecordType.BEFORE_WORK) {
                val toolsJson = detectionService.detectTools(record.imagePath)
                val updatedRecord = record.copy(toolsJson = toolsJson)
                dao.insertRecord(updatedRecord)
                onDetectionComplete(updatedRecord)
            } else {
                // 如果是作业后记录，需要找到对应的作业前记录进行对比
                val beforeRecord = getLatestBeforeRecord()
                if (beforeRecord != null) {
                    // 识别作业后的工具（纯识别，不传beforeToolsJson，对比逻辑统一在compareToolsByMode里做）
                    // 继承作业前记录的作业组和作业地点
                    val afterToolsJson = detectionService.detectTools(
                        record.imagePath,
                        null
                    )
                    val updatedRecord = record.copy(
                        toolsJson = afterToolsJson,
                        beforeRecordId = beforeRecord.id,
                        workGroup = beforeRecord.workGroup, // 继承作业组
                        workLocation = beforeRecord.workLocation // 继承作业地点
                    )
                    dao.insertRecord(updatedRecord)
                    onDetectionComplete(updatedRecord)
                } else {
                    // 没有找到作业前记录，只识别不对比
                    val toolsJson = detectionService.detectTools(record.imagePath)
                    val updatedRecord = record.copy(toolsJson = toolsJson)
                    dao.insertRecord(updatedRecord)
                    onDetectionComplete(updatedRecord)
                }
            }
        }
    }
    
    /**
     * 获取对比结果（缺失的工具列表）
     */
    suspend fun getComparisonResult(afterRecord: ToolRecord): List<String> {
        if (afterRecord.beforeRecordId == null) {
            return emptyList()
        }
        
        val beforeRecord = dao.getRecordById(afterRecord.beforeRecordId)
        if (beforeRecord == null) {
            return emptyList()
        }
        
        val beforeCount = OnnxToolDetectionService.parseToolCount(beforeRecord.toolsJson)
        val afterCount = OnnxToolDetectionService.parseToolCount(afterRecord.toolsJson)
        
        return OnnxToolDetectionService.compareToolCounts(beforeCount, afterCount)
    }
    
    fun insertRecord(record: ToolRecord) {
        viewModelScope.launch {
            dao.insertRecord(record)
        }
    }
    
    /**
     * 插入作业后记录（自动关联作业前记录）
     */
    fun insertAfterWorkRecord(
        imagePath: String,
        toolsJson: String,
        workGroup: String,
        workLocation: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val beforeRecord = getLatestBeforeRecord()
            if (beforeRecord != null) {
                val record = ToolRecord(
                    recordType = RecordType.AFTER_WORK,
                    imagePath = imagePath,
                    toolsJson = toolsJson,
                    beforeRecordId = beforeRecord.id,
                    workGroup = workGroup,
                    workLocation = workLocation
                )
                dao.insertRecord(record)
                onComplete()
            } else {
                // 如果没有作业前记录，仍然保存（但不会有对比）
                val record = ToolRecord(
                    recordType = RecordType.AFTER_WORK,
                    imagePath = imagePath,
                    toolsJson = toolsJson,
                    workGroup = workGroup,
                    workLocation = workLocation
                )
                dao.insertRecord(record)
                onComplete()
            }
        }
    }
    
    fun deleteRecord(record: ToolRecord) {
        viewModelScope.launch {
            dao.deleteRecord(record)
        }
    }
    
    suspend fun getRecordByIdSync(id: Long): ToolRecord? {
        return dao.getRecordById(id)
    }
    
    /**
     * 获取最新的作业前记录
     */
    suspend fun getLatestBeforeRecord(): ToolRecord? {
        return dao.getLatestRecordByType(RecordType.BEFORE_WORK)
    }
    
    /**
     * 根据作业前记录ID获取工作记录
     */
    suspend fun getWorkRecordById(beforeRecordId: Long): WorkRecord? {
        val beforeRecord = dao.getRecordById(beforeRecordId) ?: return null
        val afterRecord = dao.getAfterRecordByBeforeId(beforeRecordId)
        return WorkRecord(beforeRecord, afterRecord)
    }
    
    /**
     * 删除工作记录（删除作业前和作业后）
     */
    fun deleteWorkRecord(workRecord: WorkRecord) {
        viewModelScope.launch {
            if (workRecord.afterRecord != null) {
                dao.deleteRecord(workRecord.afterRecord)
            }
            dao.deleteRecord(workRecord.beforeRecord)
        }
    }
    
    /**
     * 检查是否有未完成的作业前记录（没有对应的作业后记录）
     */
    suspend fun hasUncompletedBeforeRecord(): Boolean {
        val latestBefore = getLatestBeforeRecord() ?: return false
        val afterRecord = dao.getAfterRecordByBeforeId(latestBefore.id)
        return afterRecord == null
    }
    
    /**
     * 获取未完成的作业前记录
     */
    suspend fun getUncompletedBeforeRecord(): ToolRecord? {
        val latestBefore = getLatestBeforeRecord() ?: return null
        val afterRecord = dao.getAfterRecordByBeforeId(latestBefore.id)
        return if (afterRecord == null) latestBefore else null
    }
}
