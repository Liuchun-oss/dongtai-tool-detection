from ultralytics import YOLO

# 加载模型
model = YOLO('runs/detect/railway_tools_optimized/weights/best.pt')

# 导出为ONNX，指定opset=13
model.export(
    format='onnx',
    imgsz=640,
    simplify=True,
    opset=13  # 关键：指定opset=13
)