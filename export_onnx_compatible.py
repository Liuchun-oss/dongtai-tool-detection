"""
导出兼容Android ONNX Runtime的模型
指定opset=13以确保最大兼容性
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

print("=" * 80)
print("导出兼容Android的ONNX模型")
print("=" * 80)

# 加载最佳模型
print("\n加载模型...")
model_path = 'runs/detect/railway_tools_optimized/weights/best.pt'
if not os.path.exists(model_path):
    print(f"❌ 模型文件不存在: {model_path}")
    exit(1)

model = YOLO(model_path)

# 导出为ONNX，指定opset=13（兼容ONNX Runtime 1.19.0）
print("\n导出ONNX模型...")
print("配置:")
print("  - Opset: 13 (兼容ONNX Runtime Android)")
print("  - 图片大小: 640x640")
print("  - 简化: 是")

try:
    export_path = model.export(
        format='onnx',
        imgsz=640,
        simplify=True,
        opset=13  # 指定opset=13，确保兼容性
    )
    
    print("\n" + "=" * 80)
    print("✅ ONNX模型导出成功！")
    print("=" * 80)
    print(f"\n模型位置: {export_path}")
    
    if os.path.exists(export_path):
        size_mb = os.path.getsize(export_path) / (1024 * 1024)
        print(f"模型大小: {size_mb:.2f} MB")
    
    print("\n下一步:")
    print("1. 复制模型到Android项目:")
    print(f"   copy {export_path} app\\src\\main\\assets\\best.onnx")
    print("\n2. 重新构建APK")
    print("\n3. 安装并测试")
    
except Exception as e:
    print(f"\n❌ 导出失败: {e}")
    import traceback
    traceback.print_exc()
