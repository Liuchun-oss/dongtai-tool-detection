import torch
import os

# 修复PyTorch 2.6的weights_only问题
_original_load = torch.load

def _patched_load(*args, **kwargs):
    if 'weights_only' not in kwargs:
        kwargs['weights_only'] = False
    return _original_load(*args, **kwargs)

torch.load = _patched_load

from ultralytics import YOLO

print("=" * 80)
print("开始导出TFLite模型（FP16，不使用INT8量化）")
print("=" * 80)

# 加载最佳模型
print("\n加载模型...")
model = YOLO('runs/detect/railway_tools_optimized/weights/best.pt')

# 导出为TFLite（不使用INT8量化，避免内存问题）
print("\n导出TFLite模型...")

try:
    export_path = model.export(
        format='tflite',
        imgsz=640,
        half=True  # 使用FP16而不是INT8
    )
    
    print("\n" + "=" * 80)
    print("✅ TFLite模型导出成功！")
    print("=" * 80)
    print(f"\n模型位置: {export_path}")
    
    if os.path.exists(export_path):
        size_mb = os.path.getsize(export_path) / (1024 * 1024)
        print(f"模型大小: {size_mb:.2f} MB")
    
except Exception as e:
    print(f"\n❌ 导出失败: {e}")
    import traceback
    traceback.print_exc()
