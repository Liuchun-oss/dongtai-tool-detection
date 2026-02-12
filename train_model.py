"""
铁路工具智能清点系统 - YOLOv8模型训练脚本
"""

from ultralytics import YOLO
import torch
import os

def check_environment():
    """检查训练环境"""
    print("=" * 80)
    print("检查训练环境...")
    print("=" * 80)
    
    # 检查CUDA
    if torch.cuda.is_available():
        print(f"✓ CUDA可用")
        print(f"  GPU设备: {torch.cuda.get_device_name(0)}")
        print(f"  GPU数量: {torch.cuda.device_count()}")
        print(f"  CUDA版本: {torch.version.cuda}")
        device = 0
    else:
        print("⚠ CUDA不可用，将使用CPU训练（速度较慢）")
        device = 'cpu'
    
    # 检查数据集
    if os.path.exists('dataset_organized/data.yaml'):
        print(f"✓ 数据集配置文件存在")
    else:
        print(f"✗ 数据集配置文件不存在！")
        return None
    
    print("=" * 80)
    return device


def train_model(model_size='s', epochs=100, batch_size=16, img_size=640, device=0):
    """
    训练YOLOv8模型
    
    Args:
        model_size: 模型大小 ('n', 's', 'm', 'l', 'x')
                   n = nano (最小最快)
                   s = small (推荐)
                   m = medium (更高精度)
        epochs: 训练轮数
        batch_size: 批次大小（根据GPU显存调整）
        img_size: 输入图片尺寸
        device: GPU设备ID或'cpu'
    """
    
    print("=" * 80)
    print("开始训练YOLOv8模型")
    print("=" * 80)
    print(f"模型: YOLOv8{model_size}")
    print(f"训练轮数: {epochs}")
    print(f"批次大小: {batch_size}")
    print(f"图片尺寸: {img_size}")
    print(f"设备: {device}")
    print("=" * 80)
    
    # 加载预训练模型
    model = YOLO(f'yolov8{model_size}.pt')
    
    # 开始训练
    results = model.train(
        data='dataset_organized/data.yaml',  # 数据集配置文件
        epochs=epochs,                        # 训练轮数
        imgsz=img_size,                       # 输入图片尺寸
        batch=batch_size,                     # 批次大小
        device=device,                        # GPU设备ID或'cpu'
        workers=4,                            # 数据加载线程数
        patience=50,                          # 早停耐心值
        save=True,                            # 保存检查点
        save_period=10,                       # 每10个epoch保存一次
        project='runs/detect',                # 项目目录
        name='railway_tools',                 # 实验名称
        exist_ok=True,                        # 允许覆盖已存在的实验
        pretrained=True,                      # 使用预训练权重
        optimizer='AdamW',                    # 优化器
        lr0=0.01,                             # 初始学习率
        lrf=0.01,                             # 最终学习率
        momentum=0.937,                       # 动量
        weight_decay=0.0005,                  # 权重衰减
        warmup_epochs=3,                      # 预热轮数
        warmup_momentum=0.8,                  # 预热动量
        box=7.5,                              # 边界框损失权重
        cls=0.5,                              # 分类损失权重
        dfl=1.5,                              # DFL损失权重
        
        # 数据增强参数
        hsv_h=0.015,                          # 色调增强
        hsv_s=0.7,                            # 饱和度增强
        hsv_v=0.4,                            # 明度增强
        degrees=0.0,                          # 旋转角度
        translate=0.1,                        # 平移
        scale=0.5,                            # 缩放
        shear=0.0,                            # 剪切
        perspective=0.0,                      # 透视变换
        flipud=0.0,                           # 上下翻转概率
        fliplr=0.5,                           # 左右翻转概率
        mosaic=1.0,                           # 马赛克增强概率
        mixup=0.0,                            # MixUp增强概率
        copy_paste=0.0,                       # 复制粘贴增强概率
    )
    
    print("\n" + "=" * 80)
    print("训练完成！")
    print("=" * 80)
    
    # 打印训练结果
    print(f"最佳模型保存在: runs/detect/railway_tools/weights/best.pt")
    print(f"最后模型保存在: runs/detect/railway_tools/weights/last.pt")
    
    return model


def validate_model(model_path='runs/detect/railway_tools/weights/best.pt'):
    """验证模型性能"""
    
    print("\n" + "=" * 80)
    print("验证模型性能...")
    print("=" * 80)
    
    # 加载训练好的模型
    model = YOLO(model_path)
    
    # 在验证集上评估
    metrics = model.val(data='dataset_organized/data.yaml')
    
    # 打印评估结果
    print(f"\n模型性能指标:")
    print(f"  mAP50: {metrics.box.map50:.4f}")
    print(f"  mAP50-95: {metrics.box.map:.4f}")
    print(f"  Precision: {metrics.box.mp:.4f}")
    print(f"  Recall: {metrics.box.mr:.4f}")
    
    return metrics


def export_model(model_path='runs/detect/railway_tools/weights/best.pt', 
                 format='tflite', img_size=640, int8=True):
    """
    导出模型为TensorFlow Lite格式（用于Android部署）
    
    Args:
        model_path: 模型路径
        format: 导出格式 ('tflite', 'onnx', 'torchscript')
        img_size: 输入图片尺寸
        int8: 是否使用INT8量化
    """
    
    print("\n" + "=" * 80)
    print(f"导出模型为 {format.upper()} 格式...")
    print("=" * 80)
    
    # 加载模型
    model = YOLO(model_path)
    
    # 导出模型
    if format == 'tflite':
        model.export(
            format='tflite',
            imgsz=img_size,
            int8=int8,  # INT8量化，减小模型大小
            data='dataset_organized/data.yaml'  # 用于量化校准
        )
        print(f"\n✓ TFLite模型已导出！")
        print(f"  位置: {model_path.replace('.pt', '.tflite')}")
    else:
        model.export(format=format, imgsz=img_size)
        print(f"\n✓ 模型已导出为 {format.upper()} 格式！")
    
    return model


def main():
    """主函数"""
    
    # 检查环境
    device = check_environment()
    if device is None:
        print("环境检查失败，请先运行 organize_dataset.py 整理数据集")
        return
    
    # 训练模型
    # 提示：可以选择不同的模型大小
    # 'n' = nano (6MB, 最快)
    # 's' = small (22MB, 推荐)
    # 'm' = medium (52MB, 更高精度)
    
    print("\n请选择模型大小:")
    print("  1. YOLOv8n (nano) - 最小最快，适合低端手机")
    print("  2. YOLOv8s (small) - 平衡精度和速度，推荐 [默认]")
    print("  3. YOLOv8m (medium) - 更高精度，需要高端手机")
    
    choice = input("\n请输入选择 (1/2/3) [默认2]: ").strip()
    
    if choice == '1':
        model_size = 'n'
        batch_size = 32
    elif choice == '3':
        model_size = 'm'
        batch_size = 8
    else:
        model_size = 's'
        batch_size = 16
    
    # 根据设备调整批次大小
    if device == 'cpu':
        batch_size = min(batch_size, 8)
        print(f"\n⚠ 检测到使用CPU训练，批次大小调整为 {batch_size}")
    
    # 训练模型
    model = train_model(
        model_size=model_size,
        epochs=100,
        batch_size=batch_size,
        img_size=640,
        device=device
    )
    
    # 验证模型
    validate_model()
    
    # 导出模型（用于Android部署）
    export_choice = input("\n是否导出TensorFlow Lite模型用于Android部署? (y/n) [y]: ").strip().lower()
    if export_choice != 'n':
        export_model()
    
    print("\n" + "=" * 80)
    print("全部完成！")
    print("=" * 80)
    print("\n接下来的步骤:")
    print("1. 查看训练结果: runs/detect/railway_tools/")
    print("2. 如果性能不满意，可以调整参数重新训练")
    print("3. 将导出的 .tflite 模型文件复制到 Android 项目的 assets 目录")
    print("4. 在 Android 应用中集成模型进行推理")


if __name__ == '__main__':
    main()
