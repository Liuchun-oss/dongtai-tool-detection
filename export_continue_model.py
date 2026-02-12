"""
导出继续训练的ONNX模型（用于Android部署）
"""
import torch

# 修复PyTorch 2.6的weights_only问题
_original_load = torch.load

def _patched_load(*args, **kwargs):
    if 'weights_only' not in kwargs:
        kwargs['weights_only'] = False
    return _original_load(*args, **kwargs)

torch.load = _patched_load

from ultralytics import YOLO
import os
from pathlib import Path
import warnings
import shutil

# 忽略onnxruntime-gpu的警告（导出模型不需要GPU运行时）
warnings.filterwarnings('ignore', category=UserWarning)

print("=" * 80)
print("导出继续训练的ONNX模型（用于Android部署）")
print("=" * 80)

# 指定的模型路径
model_path = 'runs/detect/runs/detect/railway_tools_continue/weights/best.pt'

if not os.path.exists(model_path):
    print(f"\n❌ 未找到模型文件: {model_path}")
    exit(1)

print(f"\n✓ 找到模型文件: {model_path}")

# 检查文件大小
if os.path.exists(model_path):
    size_mb = os.path.getsize(model_path) / (1024 * 1024)
    print(f"  模型大小: {size_mb:.2f} MB")

# 加载模型
print("\n加载模型...")
model = YOLO(model_path)

# 导出为ONNX，指定opset=13（兼容ONNX Runtime Android）
print("\n导出ONNX模型...")
print("配置:")
print("  - Opset: 13 (兼容ONNX Runtime Android)")
print("  - 图片大小: 640x640")
print("  - 简化: 是")

try:
    # 设置环境变量，跳过onnxruntime-gpu检查（导出模型不需要）
    os.environ['YOLO_VERBOSE'] = 'False'
    
    export_path = model.export(
        format='onnx',
        imgsz=640,
        simplify=True,
        opset=13,  # 指定opset=13，确保兼容性
        dynamic=False,  # 固定输入尺寸，提高兼容性
        half=False  # 使用FP32精度，确保兼容性
    )
    
    print("\n" + "=" * 80)
    print("✅ ONNX模型导出成功！")
    print("=" * 80)
    print(f"\n模型位置: {export_path}")
    
    if os.path.exists(export_path):
        size_mb = os.path.getsize(export_path) / (1024 * 1024)
        print(f"模型大小: {size_mb:.2f} MB")
    
    # 检查类别文件
    classes_file = 'dataset_organized/classes.txt'
    if not os.path.exists(classes_file):
        print("\n⚠ 未找到类别文件: dataset_organized/classes.txt")
        exit(1)
    
    with open(classes_file, 'r', encoding='utf-8') as f:
        classes = [line.strip() for line in f.readlines() if line.strip()]
    print(f"\n✓ 找到类别文件: {len(classes)} 个类别")
    
    # 自动复制文件到Android项目
    print("\n" + "=" * 80)
    print("自动复制文件到Android项目...")
    print("=" * 80)
    
    # 复制ONNX模型
    android_model_path = 'app/src/main/assets/best.onnx'
    android_assets_dir = Path('app/src/main/assets')
    android_assets_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"\n复制ONNX模型: {export_path} -> {android_model_path}")
    shutil.copy2(export_path, android_model_path)
    print("✓ ONNX模型已复制")
    
    # 复制类别文件
    android_classes_path = 'app/src/main/assets/classes.txt'
    print(f"\n复制类别文件: {classes_file} -> {android_classes_path}")
    shutil.copy2(classes_file, android_classes_path)
    print("✓ 类别文件已复制")
    
    print("\n" + "=" * 80)
    print("✅ 所有文件已更新到Android项目！")
    print("=" * 80)
    print("\n下一步操作:")
    print("1. 重新构建APK: .\\gradlew.bat assembleDebug")
    print("2. 安装并测试: adb install -r app\\build\\outputs\\apk\\debug\\app-debug.apk")
    
except Exception as e:
    print(f"\n❌ 导出失败: {e}")
    import traceback
    traceback.print_exc()
