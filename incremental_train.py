"""
增量训练脚本 - 从已有模型继续训练
使用更新后的数据集对已有模型进行增量训练
"""

from ultralytics import YOLO
import torch
import os
from pathlib import Path
import yaml

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
    if not Path('dataset_organized/images').exists():
        print(f"✗ 图片目录不存在: dataset_organized/images")
        return None, None
    
    if not Path('dataset_organized/labels').exists():
        print(f"✗ 标注目录不存在: dataset_organized/labels")
        return None, None
    
    if not Path('dataset_organized/data.yaml').exists():
        print(f"✗ 数据集配置文件不存在: dataset_organized/data.yaml")
        return None, None
    
    print("=" * 80)
    return device


def find_model_file():
    """查找模型文件"""
    print("\n" + "=" * 80)
    print("查找已有模型...")
    print("=" * 80)
    
    # 可能的模型路径
    possible_paths = [
        Path('runs/detect/runs/detect/railway_tools/weights/best.pt'),
        Path('runs/detect/railway_tools/weights/best.pt'),
        Path('runs/detect/railway_tools/weights/last.pt'),
    ]
    
    # 递归查找所有best.pt文件
    runs_dir = Path('runs/detect')
    model_files = []
    if runs_dir.exists():
        pt_files = list(runs_dir.rglob('best.pt'))
        if pt_files:
            model_files.extend(pt_files)
    
    # 优先使用用户指定的路径
    for path in possible_paths:
        if path.exists():
            print(f"✓ 找到模型文件: {path}")
            return str(path)
    
    # 如果指定路径不存在，使用找到的第一个
    if model_files:
        model_path = str(model_files[0])
        print(f"✓ 找到模型文件: {model_path}")
        return model_path
    
    print("✗ 未找到模型文件！")
    print("请确保模型文件存在于以下位置之一:")
    for path in possible_paths:
        print(f"  - {path}")
    return None


def check_dataset_info():
    """检查数据集信息"""
    print("\n" + "=" * 80)
    print("检查数据集信息...")
    print("=" * 80)
    
    data_yaml_path = Path('dataset_organized/data.yaml')
    if not data_yaml_path.exists():
        print("✗ 数据集配置文件不存在")
        return None
    
    with open(data_yaml_path, 'r', encoding='utf-8') as f:
        data_config = yaml.safe_load(f)
    
    nc = data_config.get('nc', 0)
    names = data_config.get('names', {})
    
    print(f"✓ 类别数量: {nc}")
    print(f"✓ 类别名称: {list(names.values())[:10]}..." if len(names) > 10 else f"✓ 类别名称: {list(names.values())}")
    
    # 检查训练集、验证集、测试集
    train_dir = Path('dataset_organized/images/train')
    val_dir = Path('dataset_organized/images/val')
    test_dir = Path('dataset_organized/images/test')
    
    train_count = len(list(train_dir.glob('*.jpg'))) if train_dir.exists() else 0
    val_count = len(list(val_dir.glob('*.jpg'))) if val_dir.exists() else 0
    test_count = len(list(test_dir.glob('*.jpg'))) if test_dir.exists() else 0
    
    print(f"\n数据集统计:")
    print(f"  训练集: {train_count} 张")
    print(f"  验证集: {val_count} 张")
    print(f"  测试集: {test_count} 张")
    print(f"  总计: {train_count + val_count + test_count} 张")
    
    return {
        'nc': nc,
        'names': names,
        'train_count': train_count,
        'val_count': val_count,
        'test_count': test_count
    }


def incremental_train(
    model_path,
    epochs=100,
    batch_size=32,
    img_size=640,
    device=0,
    learning_rate=0.0001,  # 增量训练使用较小的学习率
    resume=False  # 是否从检查点继续
):
    """
    增量训练函数
    
    Args:
        model_path: 已有模型路径
        epochs: 训练轮数
        batch_size: 批次大小
        img_size: 图片尺寸
        device: GPU设备ID或'cpu'
        learning_rate: 学习率（增量训练建议使用较小的学习率）
        resume: 是否从检查点继续训练
    """
    
    print("\n" + "=" * 80)
    print("开始增量训练")
    print("=" * 80)
    print(f"模型: {model_path}")
    print(f"训练轮数: {epochs}")
    print(f"批次大小: {batch_size}")
    print(f"图片尺寸: {img_size}")
    print(f"设备: {device}")
    print(f"学习率: {learning_rate} (增量训练使用较小学习率)")
    print("=" * 80)
    
    # 检查模型文件是否存在
    if not Path(model_path).exists():
        raise FileNotFoundError(f"模型文件不存在: {model_path}")
    
    # 加载已有模型
    print(f"\n加载已有模型: {model_path}")
    model = YOLO(model_path)
    
    # 检查模型类别数是否匹配
    data_yaml_path = Path('dataset_organized/data.yaml')
    if data_yaml_path.exists():
        with open(data_yaml_path, 'r', encoding='utf-8') as f:
            data_config = yaml.safe_load(f)
        dataset_nc = data_config.get('nc', 0)
        model_nc = getattr(model.model, 'nc', None)
        
        if model_nc is not None and model_nc != dataset_nc:
            print(f"\n⚠ 警告: 模型类别数 ({model_nc}) 与数据集类别数 ({dataset_nc}) 不匹配！")
            print("   增量训练可能会失败，建议重新训练模型")
            choice = input("是否继续? (y/n) [n]: ").strip().lower()
            if choice != 'y':
                print("取消训练")
                return None
    
    # 开始增量训练
    print("\n开始增量训练...")
    print("提示: 增量训练会保留原模型的权重，在此基础上继续学习新数据")
    
    results = model.train(
        data='dataset_organized/data.yaml',  # 数据集配置文件
        epochs=epochs,                        # 训练轮数
        imgsz=img_size,                      # 输入图片尺寸
        batch=batch_size,                    # 批次大小
        device=device,                       # GPU设备ID或'cpu'
        workers=4,                           # 数据加载线程数
        patience=50,                         # 早停耐心值
        save=True,                           # 保存检查点
        save_period=10,                      # 每10个epoch保存一次
        project='runs/detect',               # 项目目录
        name='railway_tools_incremental',    # 实验名称（使用新名称避免覆盖）
        exist_ok=True,                       # 允许覆盖已存在的实验
        pretrained=False,                    # 不使用预训练权重（使用已有模型）
        resume=resume,                       # 是否从检查点继续
        
        # 优化器配置（增量训练使用较小的学习率）
        optimizer='AdamW',
        lr0=learning_rate,                   # 初始学习率（增量训练使用较小值）
        lrf=0.01,                            # 最终学习率
        momentum=0.937,
        weight_decay=0.0005,
        warmup_epochs=3,                     # 预热轮数
        warmup_momentum=0.8,
        
        # 损失函数权重
        box=7.5,
        cls=0.5,
        dfl=1.5,
        
        # 数据增强参数（增量训练可以适当减少增强强度）
        hsv_h=0.015,
        hsv_s=0.7,
        hsv_v=0.4,
        degrees=0.0,
        translate=0.1,
        scale=0.5,
        shear=0.0,
        perspective=0.0,
        flipud=0.0,
        fliplr=0.5,
        mosaic=1.0,
        mixup=0.0,
        copy_paste=0.0,
    )
    
    print("\n" + "=" * 80)
    print("增量训练完成！")
    print("=" * 80)
    
    # 查找保存的模型文件
    runs_dir = Path('runs/detect/railway_tools_incremental')
    if runs_dir.exists():
        best_path = runs_dir / 'weights' / 'best.pt'
        last_path = runs_dir / 'weights' / 'last.pt'
        
        if best_path.exists():
            print(f"最佳模型保存在: {best_path}")
        if last_path.exists():
            print(f"最后模型保存在: {last_path}")
    
    return model


def main():
    """主函数"""
    
    print("\n" + "=" * 80)
    print("🚀 增量训练脚本 - 从已有模型继续训练")
    print("=" * 80)
    print("\n说明:")
    print("  - 使用已有模型进行增量训练")
    print("  - 使用更新后的数据集")
    print("  - 保留原模型的学习成果")
    print("=" * 80)
    
    # 检查环境
    device = check_environment()
    if device is None:
        print("\n环境检查失败，请检查数据集目录结构")
        return
    
    # 查找模型文件
    model_path = find_model_file()
    if model_path is None:
        return
    
    # 检查数据集信息
    dataset_info = check_dataset_info()
    if dataset_info is None:
        return
    
    # 配置训练参数
    print("\n" + "=" * 80)
    print("配置训练参数")
    print("=" * 80)
    
    print("\n推荐配置（增量训练）:")
    print("  训练轮数: 50-100 (增量训练不需要太多轮数)")
    print("  批次大小: 32 (根据GPU显存调整)")
    print("  学习率: 0.0001 (增量训练使用较小学习率)")
    
    choice = input("\n是否使用推荐配置? (y/n) [y]: ").strip().lower()
    
    if choice == 'n':
        try:
            epochs = int(input("训练轮数 [100]: ") or "100")
            batch_size = int(input("批次大小 [32]: ") or "32")
            learning_rate = float(input("学习率 [0.0001]: ") or "0.0001")
        except:
            print("使用默认配置")
            epochs, batch_size, learning_rate = 100, 32, 0.0001
    else:
        epochs = 100
        batch_size = 32
        learning_rate = 0.0001
    
    # 根据设备调整批次大小
    if device == 'cpu':
        batch_size = min(batch_size, 4)
        print(f"\n⚠ 检测到使用CPU训练，批次大小调整为 {batch_size}")
    
    # 开始增量训练
    print("\n" + "=" * 80)
    print("开始增量训练...")
    print("=" * 80)
    
    model = incremental_train(
        model_path=model_path,
        epochs=epochs,
        batch_size=batch_size,
        img_size=640,
        device=device,
        learning_rate=learning_rate,
        resume=False
    )
    
    if model is None:
        print("\n训练失败或取消")
        return
    
    print("\n" + "=" * 80)
    print("✅ 增量训练完成！")
    print("=" * 80)
    print("\n模型保存在: runs/detect/railway_tools_incremental/weights/")
    print("\n接下来的步骤:")
    print("1. 查看训练结果: runs/detect/railway_tools_incremental/")
    print("2. 验证模型性能")
    print("3. 导出模型用于部署")


if __name__ == '__main__':
    main()
