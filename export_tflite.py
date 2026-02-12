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
print("开始导出TFLite模型...")
print("=" * 80)

# 加载最佳模型
print("\n加载模型: runs/detect/railway_tools_optimized/weights/best.pt")
model = YOLO('runs/detect/railway_tools_optimized/weights/best.pt')

# 导出为TFLite（Android部署）
print("\n导出TFLite模型（INT8量化）...")
print("这可能需要几分钟时间，请耐心等待...")

try:
    export_path = model.export(
        format='tflite',
        imgsz=640,
        int8=True,
        data='dataset_organized/data.yaml'
    )
    
    print("\n" + "=" * 80)
    print("✅ TFLite模型导出成功！")
    print("=" * 80)
    print(f"\n模型位置: {export_path}")
    
    # 检查文件大小
    if os.path.exists(export_path):
        size_mb = os.path.getsize(export_path) / (1024 * 1024)
        print(f"模型大小: {size_mb:.2f} MB")
    
    print("\n下一步:")
    print("  1. 复制模型到Android项目:")
    print(f"     从: {export_path}")
    print("     到: app/src/main/assets/best.tflite")
    print("\n  2. 在Android代码中加载模型进行推理")
    
except Exception as e:
    print(f"\n❌ 导出失败: {e}")
    print("\n可能的解决方案:")
    print("  1. 确保有足够的磁盘空间")
    print("  2. 确保data.yaml文件存在")
    print("  3. 尝试不使用INT8量化:")
    print("     model.export(format='tflite', imgsz=640, int8=False)")
