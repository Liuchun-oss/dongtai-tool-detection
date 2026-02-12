package com.android.dongtai.data.converter

import androidx.room.TypeConverter
import com.android.dongtai.data.entity.RecordType

/**
 * Room 类型转换器
 */
class Converters {
    
    @TypeConverter
    fun fromRecordType(value: RecordType): String {
        return value.name
    }
    
    @TypeConverter
    fun toRecordType(value: String): RecordType {
        return RecordType.valueOf(value)
    }
}
