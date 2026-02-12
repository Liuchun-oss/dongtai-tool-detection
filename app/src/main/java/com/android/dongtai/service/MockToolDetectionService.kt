package com.android.dongtai.service

import com.android.dongtai.utils.DetectionLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.random.Random

/**
 * 模拟工具检测服务
 * 在没有模型文件的情况下，生成随机的工具列表用于测试
 */
object MockToolDetectionService {
    
    // 铁路常用工具列表（根据项目报告）
    private val availableTools = listOf(
        "大扳手",
        "活口扳手",
        "尖嘴钳",
        "钢丝钳",
        "一字螺丝刀",
        "十字螺丝刀",
        "风机",
        "防护红旗",
        "喷壶",
        "对讲机",
        "万用表",
        "内六角"
    )
    
    /**
     * 模拟识别工具
     * @param imagePath 图片路径（暂时不使用，用于模拟延迟）
     * @param beforeToolsJson 作业前的工具列表JSON（可选），如果提供，则作业后的结果会基于此生成，确保不超过作业前的数量
     * @return 工具列表的JSON字符串（格式：工具名*数量）
     */
    suspend fun detectTools(imagePath: String, beforeToolsJson: String? = null): String {
        // 模拟识别延迟（1-2秒）
        kotlinx.coroutines.delay(Random.nextLong(1000, 2000))
        
        val detectedTools: List<String>
        
        // 如果提供了作业前的工具列表，则基于它生成作业后的结果
        if (beforeToolsJson != null && beforeToolsJson.isNotBlank()) {
            val beforeTools = parseToolsJson(beforeToolsJson)
            if (beforeTools.isNotEmpty()) {
                // 解析作业前的工具，统计每种工具的数量
                fun parseTool(toolStr: String): Pair<String, Int> {
                    val parts = toolStr.split("*")
                    return if (parts.size == 2) {
                        Pair(parts[0], parts[1].toIntOrNull() ?: 1)
                    } else {
                        Pair(toolStr, 1)
                    }
                }
                
                val beforeMap = beforeTools.map { parseTool(it) }
                    .groupBy { it.first }
                    .mapValues { it.value.sumOf { pair -> pair.second } }
                
                val selectedTools = mutableListOf<String>()
                
                // 随机概率：70%完全一样，30%缺失一个工具
                val randomValue = Random.nextInt(0, 100)
                
                if (randomValue < 70) {
                    // 70%的概率：作业后的工具列表和作业前完全一样
                    beforeMap.forEach { (toolName, count) ->
                        selectedTools.add("$toolName*$count")
                    }
                } else {
                    // 30%的概率：缺失一个工具
                    val toolNames = beforeMap.keys.toList()
                    
                    if (toolNames.size == 1) {
                        // 如果只有一种工具，则减少数量（如果数量>1）
                        val toolName = toolNames[0]
                        val beforeCount = beforeMap[toolName] ?: 1
                        if (beforeCount > 1) {
                            val afterCount = beforeCount - 1
                            selectedTools.add("$toolName*$afterCount")
                        } else {
                            // 如果只有1个，则完全移除（作业后为空）
                            // selectedTools保持为空
                        }
                    } else {
                        // 有多种工具时，随机选择一种方式：
                        // 1. 完全移除一种工具种类
                        // 2. 减少某个工具的数量（如果数量>1）
                        val hasToolsWithMultipleCounts = beforeMap.values.any { it > 1 }
                        
                        if (hasToolsWithMultipleCounts && Random.nextBoolean()) {
                            // 方式2：减少某个工具的数量
                            val toolsWithMultipleCounts = beforeMap.filter { it.value > 1 }
                            val toolToReduce = toolsWithMultipleCounts.keys.random()
                            val reducedCount = beforeMap[toolToReduce]!! - 1
                            
                            // 复制所有工具，但减少选中的工具数量
                            beforeMap.forEach { (toolName, count) ->
                                if (toolName == toolToReduce) {
                                    selectedTools.add("$toolName*$reducedCount")
                                } else {
                                    selectedTools.add("$toolName*$count")
                                }
                            }
                        } else {
                            // 方式1：完全移除一种工具种类
                            val toolToRemove = toolNames.random()
                            beforeMap.forEach { (toolName, count) ->
                                if (toolName != toolToRemove) {
                                    selectedTools.add("$toolName*$count")
                                }
                            }
                        }
                    }
                }
                
                detectedTools = selectedTools
            } else {
                // 如果作业前工具列表为空，则随机生成
                val selectedTools = availableTools.shuffled().take(Random.nextInt(3, 9))
                detectedTools = selectedTools.map { tool ->
                    val count = Random.nextInt(1, 4) // 1-3个
                    "$tool*$count"
                }
            }
        } else {
            // 没有提供作业前工具列表，随机生成3-8个不同的工具
            val selectedTools = availableTools.shuffled().take(Random.nextInt(3, 9))
            detectedTools = selectedTools.map { tool ->
                val count = Random.nextInt(1, 4) // 1-3个
                "$tool*$count"
            }
        }
        
        // 转换为JSON格式
        val gson = Gson()
        return gson.toJson(detectedTools)
    }
    
    /**
     * 解析工具数量JSON
     * 支持两种格式：
     * 1. 新格式：{"count": 5}
     * 2. 旧格式：["A01*1", "A02*2"] (向后兼容)
     */
    fun parseToolCount(toolsJson: String): Int {
        return try {
            val gson = Gson()
            // 先尝试解析为新格式 {"count": 5}
            val countObj = gson.fromJson(toolsJson, Map::class.java)
            if (countObj != null && countObj.containsKey("count")) {
                val count = countObj["count"]
                when (count) {
                    is Number -> count.toInt()
                    is String -> count.toIntOrNull() ?: 0
                    else -> 0
                }
            } else {
                // 尝试解析为旧格式（向后兼容）
                val type = object : TypeToken<List<String>>() {}.type
                val tools = gson.fromJson<List<String>>(toolsJson, type) ?: emptyList()
                // 计算总数（支持状态信息，如"扳手*1 已存在"）
                tools.sumOf { tool ->
                    val parts = tool.split("*")
                    if (parts.size == 2) {
                        // 移除状态信息（"已存在"、"缺失X件"等）
                        val countPart = parts[1].trim().split(" ")[0]
                        countPart.toIntOrNull() ?: 0
                    } else {
                        0
                    }
                }
            }
        } catch (e: Exception) {
            // 如果解析失败，尝试作为数字字符串解析
            toolsJson.toIntOrNull() ?: 0
        }
    }
    
    /**
     * 解析工具列表JSON（已废弃，保留用于向后兼容）
     */
    @Deprecated("使用 parseToolCount 代替")
    fun parseToolsJson(toolsJson: String): List<String> {
        return try {
            val gson = Gson()
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(toolsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 将工具数量转换为JSON
     */
    fun toolCountToJson(count: Int): String {
        val gson = Gson()
        return gson.toJson(mapOf("count" to count))
    }
    
    /**
     * 对比两个工具数量，找出缺失的工具
     * 
     * @param beforeCount 作业前的工具总数
     * @param afterCount 作业后的工具总数
     * @return 缺失的工具信息列表（格式：["发现缺失X个工具"]）
     */
    fun compareToolCounts(beforeCount: Int, afterCount: Int): List<String> {
        DetectionLogger.log(DetectionLogger.Level.INFO, "工具对比：作业前总数=$beforeCount，作业后总数=$afterCount")
        
        // 策略：只对比总数，如果总数不一致，返回缺失数量
        if (beforeCount == afterCount) {
            DetectionLogger.log(DetectionLogger.Level.INFO, "✓ 总数一致（${beforeCount}件），不报告缺失")
            return emptyList()  // 总数一致，不报告缺失
        }
        
        // 如果总数不一致，计算缺失数量
        val missingCount = beforeCount - afterCount
        if (missingCount > 0) {
            DetectionLogger.log(DetectionLogger.Level.WARN, "⚠ 总数不一致：作业前${beforeCount}件，作业后${afterCount}件，缺失${missingCount}件")
            // 返回一个特殊的格式，表示缺失X个工具
            return listOf("发现缺失${missingCount}个工具")
        } else {
            // 如果作业后数量反而更多，不报告缺失（可能是识别误差）
            DetectionLogger.log(DetectionLogger.Level.INFO, "✓ 作业后数量(${afterCount}) >= 作业前数量(${beforeCount})，不报告缺失")
            return emptyList()
        }
    }
    
    /**
     * 对比两个工具列表（具体工具数量对比）
     * 
     * @param beforeTools 作业前的工具列表（格式：["扳手*2", "螺丝刀*1"]）
     * @param afterTools 作业后的工具列表（格式：["扳手*1", "螺丝刀*1"]）
     * @return 缺失的工具信息列表（格式：["扳手*1"]）
     */
    fun compareSpecificTools(beforeTools: List<String>, afterTools: List<String>): List<String> {
        // 解析工具名称和数量（支持新格式："工具名*数量 已存在" 或 "工具名*数量 未找到"）
        fun parseTool(toolStr: String): Pair<String, Int> {
            val parts = toolStr.split("*")
            return if (parts.size == 2) {
                // 移除状态信息（"已存在"或"未找到"）
                val countPart = parts[1].trim().split(" ")[0]
                Pair(parts[0], countPart.toIntOrNull() ?: 1)
            } else {
                Pair(toolStr, 1)
            }
        }
        
        // 检查作业后工具是否包含状态信息（新格式）
        val hasStatusInfo = afterTools.any { it.contains("已存在") || it.contains("未找到") }
        
        if (hasStatusInfo) {
            // 新格式：作业后工具包含状态信息（"工具名*数量 已存在" 或 "工具名*数量 未找到"）
            DetectionLogger.log(DetectionLogger.Level.INFO, "使用新格式对比逻辑（包含状态信息）")
            
            val missingTools = mutableListOf<String>()
            
            // 统计作业前工具数量
            val beforeMap = beforeTools.map { parseTool(it) }
                .groupBy { it.first }
                .mapValues { it.value.sumOf { pair -> pair.second } }
            
            // 解析作业后工具（提取状态信息和匹配数量）
            afterTools.forEach { afterTool ->
                val parts = afterTool.split("*")
                if (parts.size == 2) {
                    val toolName = parts[0]
                    val countAndStatus = parts[1].trim()
                    val countPart = countAndStatus.split(" ")[0]
                    val beforeCount = beforeMap[toolName] ?: 0
                    val afterCount = countPart.toIntOrNull() ?: 0
                    
                    // 检查状态：可能是"已存在"、"未找到"或"缺失X件"
                    if (countAndStatus.contains("已存在")) {
                        // "已存在"：匹配数量 = 作业前数量
                        DetectionLogger.log(DetectionLogger.Level.INFO, "✓ 工具 $toolName 已存在：作业前${beforeCount}件，匹配到${beforeCount}件")
                    } else if (countAndStatus.contains("缺失")) {
                        // 提取缺失数量（格式："缺失X件"）
                        val missingMatch = Regex("缺失(\\d+)件").find(countAndStatus)
                        val missingCount = missingMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        if (missingCount > 0) {
                            // 匹配数量 = 作业前数量 - 缺失数量
                            val matchedCount = beforeCount - missingCount
                            missingTools.add("$toolName*$missingCount")
                            DetectionLogger.log(DetectionLogger.Level.WARN, "⚠ 工具 $toolName 缺失：作业前${beforeCount}件，匹配到${matchedCount}件，缺失${missingCount}件")
                        }
                    } else if (countAndStatus.contains("未找到")) {
                        // "未找到"：匹配数量 = 0，缺失全部
                        if (beforeCount > 0) {
                            missingTools.add("$toolName*$beforeCount")
                            DetectionLogger.log(DetectionLogger.Level.WARN, "⚠ 工具 $toolName 未找到：作业前${beforeCount}件，匹配到0件，缺失${beforeCount}件")
                        }
                    }
                }
            }
            
            // 检查是否有作业前有但作业后没有的工具（可能检测失败）
            beforeMap.forEach { (toolName, beforeCount) ->
                val foundInAfter = afterTools.any { it.startsWith("$toolName*") }
                if (!foundInAfter) {
                    missingTools.add("$toolName*$beforeCount")
                    DetectionLogger.log(DetectionLogger.Level.WARN, "⚠ 工具 $toolName 未在作业后检测结果中找到：作业前${beforeCount}件")
                }
            }
            
            if (missingTools.isEmpty()) {
                DetectionLogger.log(DetectionLogger.Level.INFO, "✓ 所有工具均已找到，无缺失")
            }
            
            return missingTools
        } else {
            // 旧格式：作业后工具不包含状态信息（向后兼容）
            DetectionLogger.log(DetectionLogger.Level.INFO, "使用旧格式对比逻辑（不包含状态信息）")
            
            // 统计作业前工具数量
            val beforeMap = beforeTools.map { parseTool(it) }
                .groupBy { it.first }
                .mapValues { it.value.sumOf { pair -> pair.second } }
            
            // 统计作业后工具数量
            val afterMap = afterTools.map { parseTool(it) }
                .groupBy { it.first }
                .mapValues { it.value.sumOf { pair -> pair.second } }
            
            DetectionLogger.log(DetectionLogger.Level.INFO, "具体工具对比：作业前=${beforeMap}, 作业后=${afterMap}")
            
            val missingTools = mutableListOf<String>()
            
            // 对比每个工具的数量
            beforeMap.forEach { (toolName, beforeCount) ->
                val afterCount = afterMap[toolName] ?: 0
                val missingCount = beforeCount - afterCount
                if (missingCount > 0) {
                    missingTools.add("$toolName*$missingCount")
                    DetectionLogger.log(DetectionLogger.Level.WARN, "⚠ 工具 $toolName 缺失：作业前${beforeCount}件，作业后${afterCount}件，缺失${missingCount}件")
                }
            }
            
            // 检查是否有作业前没有但作业后有的工具（可能是识别错误，不报告）
            afterMap.forEach { (toolName, afterCount) ->
                if (!beforeMap.containsKey(toolName)) {
                    DetectionLogger.log(DetectionLogger.Level.INFO, "ℹ 工具 $toolName 在作业后出现但作业前没有（可能是识别误差），不报告")
                }
            }
            
            if (missingTools.isEmpty()) {
                DetectionLogger.log(DetectionLogger.Level.INFO, "✓ 所有工具数量一致，无缺失")
            }
            
            return missingTools
        }
    }
    
    /**
     * 对比两个工具列表（已废弃，保留用于向后兼容）
     */
    @Deprecated("使用 compareToolCounts 或 compareSpecificTools 代替")
    fun compareTools(beforeTools: List<String>, afterTools: List<String>): List<String> {
        // 解析工具名称和数量
        fun parseTool(toolStr: String): Pair<String, Int> {
            val parts = toolStr.split("*")
            return if (parts.size == 2) {
                Pair(parts[0], parts[1].toIntOrNull() ?: 1)
            } else {
                Pair(toolStr, 1)
            }
        }
        
        // 统计作业前工具数量
        val beforeMap = beforeTools.map { parseTool(it) }
            .groupBy { it.first }
            .mapValues { it.value.sumOf { pair -> pair.second } }
        
        // 统计作业后工具数量
        val afterMap = afterTools.map { parseTool(it) }
            .groupBy { it.first }
            .mapValues { it.value.sumOf { pair -> pair.second } }
        
        // 计算总数
        val beforeTotal = beforeMap.values.sum()
        val afterTotal = afterMap.values.sum()
        
        return compareToolCounts(beforeTotal, afterTotal)
    }
    
    /**
     * 将工具列表转换为JSON
     */
    fun toolsToJson(tools: List<String>): String {
        val gson = Gson()
        return gson.toJson(tools)
    }
}
