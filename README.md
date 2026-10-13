# 🔧 动态工具清点识别系统

通辽车辆段工具清点App —— 基于 YOLO11s + Android Kotlin，拍照自动识别蓝色垫布上的工具，作业前后对比检查是否缺失。

## 功能特性

- 📸 拍照自动识别工具（YOLO11s ONNX 模型，28类工具）
- 🔍 蓝色垫布区域智能检测（HSV色彩空间）
- 📊 作业前后工具对比（支持总数量/具体工具两种模式）
- 🔄 TTA重新识别（原图+翻转+旋转多次推理合并，提升召回率）
- 📤 一键生成对比报告图片并分享
- 🌙 深色/浅色主题切换
- ⚙️ 可调节置信度、IoU等检测参数

## 技术栈

| 模块 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI框架 | Jetpack Compose + Material3 |
| AI推理 | ONNX Runtime (YOLO11s) |
| 数据库 | Room |
| 图片加载 | Coil |
| 相机 | CameraX |

## 项目结构

```
app/src/main/java/com/android/dongtai/
├── data/           # Room数据库、Entity、DAO
├── navigation/     # NavGraph导航
├── service/
│   ├── OnnxToolDetectionService.kt  # ONNX推理核心
│   ├── MockToolDetectionService.kt  # 对比逻辑
│   └── ShareImageGenerator.kt      # 分享图片生成
├── ui/
│   ├── screen/     # 页面（Home/WorkInfo/AfterWork/History/Settings等）
│   ├── theme/      # Material3主题
│   └── viewmodel/  # ViewModel
└── utils/
    ├── BlueRegionDetector.kt   # 蓝色区域HSV检测
    ├── DetectionHelper.kt      # 识别+反馈公共逻辑
    ├── DetectionLogger.kt      # 日志工具
    └── SettingsManager.kt      # 设置管理
```

## 模型信息

- 模型：YOLO11s（best.onnx，76.8MB）
- 输入：640×640 RGB
- 类别：28种工具（扳手、钳子、螺丝刀、套筒、内六角等）
- 训练：1500 epochs

## 截图

> TODO: 添加App截图

## 编译

```bash
# 需要 Android Studio 或配置 JAVA_HOME
./gradlew assembleRelease
```

## 下载

前往 [Releases](https://github.com/Liuchun-oss/dongtai-tool-detection/releases) 下载最新APK。

## License

Private - 仅供内部使用
