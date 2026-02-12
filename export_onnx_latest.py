"""
导出最新训练的ONNX模型（用于Android部署）
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

# 忽略onnxruntime-gpu的警告（导出模型不需要GPU运行时）
warnings.filterwarnings('ignore', category=UserWarning)

print("=" * 80)
print("导出最新训练的ONNX模型（用于Android部署）")
print("=" * 80)

# 查找最新的模型文件（优先使用最新的训练结果）
model_paths = [
    'runs/detect/railway_tools/weights/best.pt',  # 最新训练的模型
    'runs/detect/runs/detect/railway_tools/weights/best.pt',  # 旧路径
]

model_path = None
for path in model_paths:
    if os.path.exists(path):
        model_path = path
        break

if model_path is None:
    print("\n❌ 未找到模型文件！")
    print("请检查以下路径:")
    for path in model_paths:
        print(f"  - {path}")
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
    if os.path.exists(classes_file):
        with open(classes_file, 'r', encoding='utf-8') as f:
            classes = [line.strip() for line in f.readlines() if line.strip()]
        print(f"\n✓ 找到类别文件: {len(classes)} 个类别")
    else:
        print("\n⚠ 未找到类别文件: dataset_organized/classes.txt")
    
    print("\n" + "=" * 80)
    print("下一步操作:")
    print("=" * 80)
    print("\n1. 复制ONNX模型到Android项目:")
    print(f"   copy \"{export_path}\" app\\src\\main\\assets\\best.onnx")
    
    if os.path.exists(classes_file):
        print("\n2. 复制类别文件到Android项目:")
        print(f"   copy \"{classes_file}\" app\\src\\main\\assets\\classes.txt")
    
    print("\n3. 重新构建APK")
    print("   .\\gradlew.bat assembleDebug")
    print("\n4. 安装并测试")
    print("   adb install -r app\\build\\outputs\\apk\\debug\\app-debug.apk")
    
except Exception as e:
    print(f"\n❌ 导出失败: {e}")
    import traceback
    traceback.print_exc()
