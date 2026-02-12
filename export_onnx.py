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
print("导出ONNX模型（更稳定，可以后续转换为TFLite）")
print("=" * 80)

# 加载最佳模型
print("\n加载模型...")
model = YOLO('runs/detect/railway_tools_optimized/weights/best.pt')

# 导出为ONNX
print("\n导出ONNX模型...")

try:
    export_path = model.export(
        format='onnx',
        imgsz=640,
        simplify=True,
        opset=12  # 指定ONNX opset版本为12（兼容IR版本9）
    )
    
    print("\n" + "=" * 80)
    print("✅ ONNX模型导出成功！")
    print("=" * 80)
    print(f"\n模型位置: {export_path}")
    
    if os.path.exists(export_path):
        size_mb = os.path.getsize(export_path) / (1024 * 1024)
        print(f"模型大小: {size_mb:.2f} MB")
    
    print("\n说明:")
    print("  ONNX模型可以:")
    print("  1. 直接在Android上使用（需要ONNX Runtime）")
    print("  2. 使用onnx2tf工具转换为TFLite")
    print("  3. 使用其他转换工具")
    
    print("\n转换为TFLite的命令（需要安装onnx2tf）:")
    print(f"  pip install onnx2tf")
    print(f"  onnx2tf -i {export_path} -o tflite_model")
    
except Exception as e:
    print(f"\n❌ 导出失败: {e}")
    import traceback
    traceback.print_exc()
