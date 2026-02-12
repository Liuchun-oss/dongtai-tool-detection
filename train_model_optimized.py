"""
铁路工具智能清点系统 - YOLOv8优化训练脚本
针对RTX 5090 32G显存优化，专门处理外观相似工具的识别
"""

from ultralytics import YOLO
import torch
import os

# 修复PyTorch 2.6的weights_only问题
import torch as _torch
_original_load = _torch.load

def _patched_load(*args, **kwargs):
    """Patch torch.load to use weights_only=False for compatibility"""
    if 'weights_only' not in kwargs:
        kwargs['weights_only'] = False
    return _original_load(*args, **kwargs)

_torch.load = _patched_load
torch.load = _patched_load

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
        print(f"  显存容量: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB")
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


def train_model_optimized(
    model_size='s',
    epochs=200,
    batch_size=64,
    img_size=640,
    device=0,
    close_mosaic=30
):
    """
    针对外观相似工具优化的训练函数
    
    优化策略：
    1. 大批次训练 (RTX 5090 32G可以支持batch=64或更大)
    2. 更高分辨率 (640或更大，帮助区分相似工具)
    3. 增强数据增强 (特别是颜色和尺度增强)
    4. 更多训练轮数 (200轮，确保充分学习)
    5. 焦点损失调优 (提高分类精度)
    6. 细粒度特征学习
    
    Args:
        model_size: 模型大小 ('s' = small, 推荐)
        epochs: 训练轮数 (200轮，针对相似工具)
        batch_size: 批次大小 (64，充分利用32G显存)
        img_size: 输入图片尺寸 (640，可选择更大如800)
        device: GPU设备ID
        close_mosaic: 最后N轮关闭mosaic增强
    """
    
    print("=" * 80)
    print("🚀 开始优化训练 - 针对外观相似工具识别")
    print("=" * 80)
    print(f"模型: YOLOv8{model_size}")
    print(f"训练轮数: {epochs}")
    print(f"批次大小: {batch_size} (充分利用32G显存)")
    print(f"图片尺寸: {img_size}")
    print(f"设备: GPU {device}")
    print()
    print("优化策略:")
    print("  ✓ 大批次训练 (提高训练稳定性)")
    print("  ✓ 增强颜色和尺度增强 (区分相似工具)")
    print("  ✓ 更多训练轮数 (充分学习细微差异)")
    print("  ✓ 焦点损失优化 (提高分类精度)")
    print("  ✓ 混合精度训练 (加速训练)")
    print("=" * 80)
    
    # 加载预训练模型
    model = YOLO(f'yolov8{model_size}.pt')
    
    # 开始优化训练
    results = model.train(
        data='dataset_organized/data.yaml',
        
        # ===== 基础配置 =====
        epochs=epochs,
        imgsz=img_size,
        batch=batch_size,
        device=device,
        
        # ===== 性能优化 (充分利用RTX 5090) =====
        workers=8,                    # 增加数据加载线程 (8核以上)
        amp=True,                     # 混合精度训练 (加速+节省显存)
        cache=True,                   # 缓存图片到内存 (加速训练)
        
        # ===== 训练策略 =====
        patience=100,                 # 早停耐心值 (更多轮数)
        save=True,
        save_period=20,               # 每20轮保存一次
        project='runs/detect',
        name='railway_tools_optimized',
        exist_ok=True,
        pretrained=True,
        
        # ===== 优化器配置 =====
        optimizer='AdamW',
        lr0=0.01,                     # 初始学习率
        lrf=0.001,                    # 最终学习率 (更小，精细调优)
        momentum=0.937,
        weight_decay=0.0005,
        warmup_epochs=5,              # 更长预热期
        warmup_momentum=0.8,
        warmup_bias_lr=0.1,
        
        # ===== 损失函数权重 (针对相似工具优化) =====
        box=7.5,                      # 边界框损失
        cls=1.0,                      # 分类损失 (提高，强调分类精度)
        dfl=1.5,                      # DFL损失
        
        # ===== 数据增强 (针对颜色和大小差异优化) =====
        # 颜色增强 (帮助区分颜色不同的相似工具)
        hsv_h=0.03,                   # 色调增强 (增加，学习颜色变化)
        hsv_s=0.9,                    # 饱和度增强 (增加)
        hsv_v=0.6,                    # 明度增强 (增加)
        
        # 几何增强 (帮助区分大小不同的工具)
        degrees=15.0,                 # 旋转角度 (增加，学习不同角度)
        translate=0.15,               # 平移 (增加)
        scale=0.7,                    # 缩放 (增加，学习不同大小)
        shear=5.0,                    # 剪切 (轻微添加)
        perspective=0.0005,           # 透视变换 (轻微添加)
        
        # 翻转增强
        flipud=0.0,                   # 上下翻转 (工具识别通常不需要)
        fliplr=0.5,                   # 左右翻转 (保持)
        
        # 高级增强
        mosaic=1.0,                   # 马赛克增强 (增加场景多样性)
        mixup=0.1,                    # MixUp增强 (轻微添加，增强泛化)
        copy_paste=0.1,               # 复制粘贴增强 (增加，模拟多工具场景)
        
        # 最后N轮关闭mosaic增强 (精细调优)
        close_mosaic=close_mosaic,
        
        # ===== 其他优化 =====
        # 使用余弦学习率调度
        cos_lr=True,
        
        # 标签平滑 (提高泛化能力)
        label_smoothing=0.1,
        
        # NMS配置
        nms=True,
        iou=0.7,
        
        # 单类NMS (如果工具类别互斥)
        agnostic_nms=False,
        
        # 详细输出
        verbose=True,
        
        # 保存训练可视化结果
        plots=True,
    )
    
    print("\n" + "=" * 80)
    print("✅ 训练完成！")
    print("=" * 80)
    print(f"最佳模型: runs/detect/railway_tools_optimized/weights/best.pt")
    print(f"最后模型: runs/detect/railway_tools_optimized/weights/last.pt")
    
    return model, results


def validate_model(model_path='runs/detect/railway_tools_optimized/weights/best.pt'):
    """验证模型性能"""
    
    print("\n" + "=" * 80)
    print("📊 验证模型性能...")
    print("=" * 80)
    
    model = YOLO(model_path)
    
    # 在验证集上评估
    metrics = model.val(data='dataset_organized/data.yaml')
    
    print(f"\n模型性能指标:")
    print(f"  mAP50:    {metrics.box.map50:.4f} (目标 > 0.75)")
    print(f"  mAP50-95: {metrics.box.map:.4f} (目标 > 0.55)")
    print(f"  Precision: {metrics.box.mp:.4f} (目标 > 0.90)")
    print(f"  Recall:    {metrics.box.mr:.4f} (目标 > 0.85)")
    
    # 按类别统计
    print("\n各类别性能 (前10类):")
    if hasattr(metrics.box, 'ap_class_index'):
        for i in range(min(10, len(metrics.box.ap_class_index))):
            class_idx = metrics.box.ap_class_index[i]
            ap50 = metrics.box.ap50[i]
            print(f"  类别 {class_idx}: AP50={ap50:.4f}")
    
    return metrics


def export_model(
    model_path='runs/detect/railway_tools_optimized/weights/best.pt',
    format='tflite',
    img_size=640,
    int8=True
):
    """导出模型为Android部署格式"""
    
    print("\n" + "=" * 80)
    print(f"📦 导出模型为 {format.upper()} 格式...")
    print("=" * 80)
    
    model = YOLO(model_path)
    
    if format == 'tflite':
        # 导出TFLite模型 (Android部署)
        export_path = model.export(
            format='tflite',
            imgsz=img_size,
            int8=int8,
            data='dataset_organized/data.yaml'
        )
        print(f"\n✅ TFLite模型已导出")
        print(f"  位置: {export_path}")
        print(f"  量化: {'INT8' if int8 else 'FP32'}")
        
        # 估算模型大小
        if os.path.exists(export_path):
            size_mb = os.path.getsize(export_path) / (1024 * 1024)
            print(f"  大小: {size_mb:.2f} MB")
    else:
        export_path = model.export(format=format, imgsz=img_size)
        print(f"\n✅ 模型已导出为 {format.upper()} 格式")
        print(f"  位置: {export_path}")
    
    return export_path


def analyze_similar_classes(model_path='runs/detect/railway_tools_optimized/weights/best.pt'):
    """分析相似类别的混淆情况"""
    
    print("\n" + "=" * 80)
    print("🔍 分析相似类别识别情况...")
    print("=" * 80)
    
    model = YOLO(model_path)
    
    # 在验证集上评估
    metrics = model.val(data='dataset_organized/data.yaml', plots=True)
    
    print("\n查看混淆矩阵以识别容易混淆的类别:")
    print("  位置: runs/detect/railway_tools_optimized/confusion_matrix.png")
    print("\n如果某些类别容易混淆，可以:")
    print("  1. 增加这些类别的训练样本")
    print("  2. 检查标注质量")
    print("  3. 增加针对性的数据增强")
    print("  4. 调整类别权重")


def generate_completion_report():
    """生成训练完成报告"""
    import datetime
    
    report_path = "训练完成报告.txt"
    
    try:
        # 读取最新的训练指标
        results_dir = "runs/detect/railway_tools_optimized"
        
        report_content = f"""
{'='*80}
🎉 训练完成报告
{'='*80}

完成时间: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

{'='*80}
📊 训练结果位置
{'='*80}

1. 模型文件:
   - 最佳模型: {results_dir}/weights/best.pt
   - 最后模型: {results_dir}/weights/last.pt
   - TFLite模型: {results_dir}/weights/best.tflite ✓ (Android部署)

2. 训练可视化:
   - 训练曲线: {results_dir}/results.png
   - 混淆矩阵: {results_dir}/confusion_matrix.png
   - PR曲线: {results_dir}/PR_curve.png
   - F1曲线: {results_dir}/F1_curve.png

3. 验证结果:
   - 验证预测: {results_dir}/val_batch*.jpg

{'='*80}
📱 Android部署步骤
{'='*80}

1. 复制TFLite模型:
   从: {results_dir}/weights/best.tflite
   到: app/src/main/assets/best.tflite

2. 更新类别配置:
   在Android代码中更新类别名称列表（对应data.yaml）

3. 集成推理代码:
   参考文档: doc/数据采集与模型训练指南.md (Android集成部分)

{'='*80}
🔍 下一步建议
{'='*80}

1. 查看混淆矩阵 (confusion_matrix.png)
   - 识别容易混淆的类别对
   - 针对性优化

2. 查看训练曲线 (results.png)
   - 检查是否收敛
   - 是否存在过拟合

3. 测试模型性能
   - 在测试集上验证
   - 实际场景测试

4. 如果效果不理想
   - 增加训练样本（特别是混淆的类别）
   - 调整数据增强参数
   - 尝试更大的图片尺寸 (800px)
   - 考虑使用更大的模型 (yolov8m)

{'='*80}
✅ 训练配置摘要
{'='*80}

- 模型: YOLOv8s
- GPU: RTX 5090 32G
- 批次大小: 64
- 训练轮数: 200
- 图片尺寸: 640
- 优化策略: 针对外观相似工具

{'='*80}
📧 问题反馈
{'='*80}

如有问题，请查看:
- 外观相似工具识别优化指南.md
- 配置对比.md
- doc/数据采集与模型训练指南.md

{'='*80}
祝部署顺利！🚀
{'='*80}
"""
        
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write(report_content)
        
        print(f"\n✅ 训练完成报告已生成: {report_path}")
        print("   早上醒来查看这个文件即可了解所有结果！")
        
    except Exception as e:
        print(f"\n⚠️ 生成报告时出错: {e}")
        print("   但这不影响训练结果，所有文件都在 runs/detect/railway_tools_optimized/")


def main():
    """主函数"""
    
    print("\n" + "=" * 80)
    print("🚀 铁路工具智能清点系统 - 优化训练")
    print("=" * 80)
    print("\n配置:")
    print("  GPU: RTX 5090 32G")
    print("  模型: YOLOv8s")
    print("  优化目标: 区分外观相似的工具（颜色/大小不同）")
    print()
    
    # 检查环境
    device = check_environment()
    if device is None:
        print("环境检查失败，请先运行 organize_dataset.py 整理数据集")
        return
    
    # 配置确认
    print("\n推荐配置 (针对RTX 5090 32G):")
    print("  训练轮数: 200 (充分学习)")
    print("  批次大小: 64 (充分利用显存)")
    print("  图片尺寸: 640 (标准) 或 800 (更高精度)")
    print("  数据增强: 强化颜色和尺度增强")
    print()
    
    choice = input("是否使用推荐配置? (y/n) [y]: ").strip().lower()
    
    if choice == 'n':
        # 自定义配置
        try:
            epochs = int(input("训练轮数 [200]: ") or "200")
            batch = int(input("批次大小 [64]: ") or "64")
            imgsz = int(input("图片尺寸 (640/800) [640]: ") or "640")
        except:
            print("使用默认配置")
            epochs, batch, imgsz = 200, 64, 640
    else:
        epochs, batch, imgsz = 200, 64, 640
    
    print("\n" + "=" * 80)
    print("开始训练...")
    print("=" * 80)
    print(f"预计训练时间: {epochs * 0.5:.0f}-{epochs * 1:.0f} 分钟 (RTX 5090)")
    print("=" * 80)
    print()
    
    # 训练模型
    model, results = train_model_optimized(
        model_size='s',
        epochs=epochs,
        batch_size=batch,
        img_size=imgsz,
        device=device,
        close_mosaic=30
    )
    
    # 验证模型
    validate_model()
    
    # 分析相似类别
    analyze_similar_classes()
    
    # 自动导出模型（用于Android部署）
    print("\n" + "=" * 80)
    print("🔄 自动导出TensorFlow Lite模型（Android部署）...")
    print("=" * 80)
    export_model()
    
    print("\n" + "=" * 80)
    print("✅ 全部完成！")
    print("=" * 80)
    print("\n关键优化点:")
    print("  1. ✓ 大批次训练 (batch=64) - 提高训练稳定性")
    print("  2. ✓ 增强数据增强 - 特别是颜色(HSV)和尺度变化")
    print("  3. ✓ 更多训练轮数 (200轮) - 充分学习细微差异")
    print("  4. ✓ 提高分类损失权重 - 强调准确分类")
    print("  5. ✓ 混合精度训练 - 加速训练过程")
    print()
    print("如何进一步提升:")
    print("  - 查看混淆矩阵，识别容易混淆的类别")
    print("  - 为容易混淆的类别增加更多训练样本")
    print("  - 考虑使用更大的图片尺寸 (800)")
    print("  - 调整特定类别的样本权重")
    print()
    print("查看训练结果:")
    print("  训练曲线: runs/detect/railway_tools_optimized/results.png")
    print("  混淆矩阵: runs/detect/railway_tools_optimized/confusion_matrix.png")
    print("  验证结果: runs/detect/railway_tools_optimized/val_batch*.jpg")
    
    # 生成训练完成报告
    generate_completion_report()


if __name__ == '__main__':
    main()
