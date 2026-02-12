# ONNX模型集成说明

## 概述

已成功将YOLOv8 ONNX模型集成到Android APK项目中，替换了原有的模拟随机识别功能。现在应用可以使用真实的AI模型进行工具检测。

## 完成的工作

### 1. 添加依赖项 ✅

在 `app/build.gradle.kts` 中添加了以下依赖：

```kotlin
// ONNX Runtime for AI inference
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

// ExifInterface for image rotation
implementation("androidx.exifinterface:exifinterface:1.3.7")
```

### 2. 模型文件部署 ✅

- 创建了 `app/src/main/assets/` 文件夹
- 将ONNX模型文件 `best.onnx` (44.8 MB) 复制到assets文件夹
- 模型路径: `app/src/main/assets/best.onnx`

### 3. 创建ONNX检测服务 ✅

创建了新的服务类 `OnnxToolDetectionService.kt`，包含以下功能：

#### 核心功能
- **模型初始化**: 从assets加载ONNX模型，使用NNAPI加速
- **图片预处理**: 
  - 自动处理图片旋转（基于EXIF信息）
  - 调整图片大小到640x640
  - 转换为CHW格式并归一化到[0, 1]
- **YOLOv8推理**: 运行ONNX模型进行目标检测
- **后处理**: 
  - 置信度过滤（阈值: 0.25）
  - NMS非极大值抑制（IoU阈值: 0.45）
  - 统计每个类别的数量

#### 类别映射
支持24个工具类别（A01-A24），可以根据实际工具类型修改映射：

```kotlin
private val toolNameMapping = mapOf(
    "A01" to "工具A01", "A02" to "工具A02", ..., "A24" to "工具A24"
)
```

#### 性能优化
- 使用单例模式，避免重复加载模型
- 启用ONNX Runtime的所有优化
- 使用Android NNAPI硬件加速
- 异步处理，不阻塞UI线程

### 4. 更新应用代码 ✅

将所有使用 `MockToolDetectionService` 的地方替换为 `OnnxToolDetectionService`：

- ✅ `ToolRecordViewModel.kt` - 主要的检测逻辑
- ✅ `WorkInfoScreen.kt` - 作业前拍照识别
- ✅ `AfterWorkScreen.kt` - 作业后拍照识别和对比
- ✅ `RecordDetailScreen.kt` - 历史记录查看

## 使用说明

### 模型初始化

模型会在首次使用时自动初始化：

```kotlin
val detectionService = OnnxToolDetectionService.getInstance(context)
```

### 检测工具

```kotlin
// 检测图片中的工具
val toolsJson = detectionService.detectTools(imagePath)
val detectedTools = OnnxToolDetectionService.parseToolsJson(toolsJson)
```

### 工具对比

```kotlin
// 对比作业前后的工具
val missingTools = OnnxToolDetectionService.compareTools(beforeTools, afterTools)
```

## 技术细节

### YOLOv8输出格式

模型输出格式: `[batch, num_classes+4, num_predictions]`
- 前4个值: 边界框坐标 (x, y, w, h)
- 后24个值: 类别置信度 (A01-A24)

### 图片预处理流程

1. 读取图片文件
2. 根据EXIF信息旋转图片
3. 调整大小到640x640
4. 转换为CHW格式 (Channel, Height, Width)
5. 归一化到[0, 1]范围

### NMS算法

使用IoU (Intersection over Union) 进行非极大值抑制：
- 按置信度降序排序检测结果
- 抑制与高置信度检测框重叠度高的其他检测框
- IoU阈值: 0.45

## 模型信息

- **模型类型**: YOLOv8
- **输入尺寸**: 640x640x3
- **类别数量**: 24 (A01-A24)
- **模型大小**: 44.8 MB
- **置信度阈值**: 0.25
- **IoU阈值**: 0.45

## 构建和运行

### 构建APK

```bash
# Windows
gradlew assembleDebug

# 或使用Android Studio
# Build -> Build Bundle(s) / APK(s) -> Build APK(s)
```

### 安装到设备

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 性能考虑

### 模型加载
- 首次加载需要1-2秒
- 使用单例模式，只加载一次
- 建议在应用启动时预加载

### 推理速度
- 取决于设备性能
- 使用NNAPI加速可显著提升速度
- 预计每张图片推理时间: 0.5-2秒

### 内存占用
- 模型占用约45MB存储空间
- 运行时内存占用约100-200MB
- 建议在低内存设备上监控内存使用

## 故障排除

### 模型加载失败

**问题**: 应用崩溃或提示模型加载失败

**解决方案**:
1. 确认 `best.onnx` 文件在 `app/src/main/assets/` 目录
2. 检查文件大小是否正确（约44.8MB）
3. 清理并重新构建项目

### 检测结果为空

**问题**: 检测不到任何工具

**解决方案**:
1. 检查图片质量和光照条件
2. 确保工具在图片中清晰可见
3. 可以降低置信度阈值（修改 `confidenceThreshold`）

### 推理速度慢

**问题**: 检测耗时过长

**解决方案**:
1. 确认NNAPI加速已启用
2. 在较新的设备上测试
3. 考虑使用更小的模型或量化模型

## 后续优化建议

### 1. 模型优化
- 使用量化模型减小体积
- 转换为TFLite格式（可能更快）
- 针对特定设备优化

### 2. 用户体验
- 添加检测进度指示
- 显示检测置信度
- 支持手动调整检测结果

### 3. 功能扩展
- 支持批量检测
- 添加检测结果可视化（绘制边界框）
- 导出检测详细信息

### 4. 工具名称映射
更新 `toolNameMapping` 以显示实际的工具名称：

```kotlin
private val toolNameMapping = mapOf(
    "A01" to "大扳手",
    "A02" to "活口扳手",
    "A03" to "尖嘴钳",
    // ... 根据实际工具类型修改
)
```

## 文件清单

### 新增文件
- `app/src/main/assets/best.onnx` - ONNX模型文件
- `app/src/main/java/com/android/dongtai/service/OnnxToolDetectionService.kt` - ONNX检测服务

### 修改文件
- `app/build.gradle.kts` - 添加依赖项
- `app/src/main/java/com/android/dongtai/ui/viewmodel/ToolRecordViewModel.kt` - 使用ONNX服务
- `app/src/main/java/com/android/dongtai/ui/screen/WorkInfoScreen.kt` - 使用ONNX服务
- `app/src/main/java/com/android/dongtai/ui/screen/AfterWorkScreen.kt` - 使用ONNX服务
- `app/src/main/java/com/android/dongtai/ui/screen/RecordDetailScreen.kt` - 使用ONNX服务

### 保留文件
- `app/src/main/java/com/android/dongtai/service/MockToolDetectionService.kt` - 保留作为参考

## 总结

✅ ONNX模型已成功集成到Android应用中
✅ 所有模拟识别代码已替换为真实的AI检测
✅ 支持24个工具类别的识别
✅ 使用硬件加速优化性能
✅ 代码无linter错误

现在可以构建APK并在实际设备上测试工具检测功能！
