"""
继续训练脚本 - 在已有模型基础上继续训练
适用于：
- 新增了数据集
- 新增了类别
- 提升识别准确率
"""

from ultralytics import YOLO
import torch
import os
import shutil
import random
from pathlib import Path
import yaml
from collections import defaultdict


def load_classes(classes_file='dataset_organized/classes.txt'):
    """读取类别文件"""
    classes_path = Path(classes_file)
    if not classes_path.exists():
        raise FileNotFoundError(f"类别文件不存在: {classes_file}")
    
    with open(classes_path, 'r', encoding='utf-8') as f:
        classes = [line.strip() for line in f.readlines() if line.strip()]
    
    print(f"✓ 加载了 {len(classes)} 个类别")
    return classes


def find_labeled_images(images_dir='dataset_organized/images', 
                        labels_dir='dataset_organized/labels'):
    """找到所有有标注的图片（只扫描根目录，不包括子目录）"""
    images_path = Path(images_dir)
    labels_path = Path(labels_dir)
    
    if not images_path.exists():
        raise FileNotFoundError(f"图片目录不存在: {images_dir}")
    if not labels_path.exists():
        raise FileNotFoundError(f"标注目录不存在: {labels_dir}")
    
    # 获取所有图片文件（支持jpg和png，只扫描根目录，不包括子目录）
    image_files = []
    # 使用iterdir()只获取根目录的文件，不递归
    for img_file in images_path.iterdir():
        if img_file.is_file():
            # 检查文件扩展名
            ext_lower = img_file.suffix.lower()
            if ext_lower in ['.jpg', '.jpeg', '.png']:
                image_files.append(img_file)
    
    # 获取所有标注文件（只扫描根目录）
    label_files = set()
    # 使用iterdir()只获取根目录的文件，不递归
    for label_file in labels_path.iterdir():
        if label_file.is_file() and label_file.suffix.lower() == '.txt':
            label_files.add(label_file.stem)
    
    # 只保留有标注的图片
    labeled_images = []
    for img_file in image_files:
        if img_file.stem in label_files:
            labeled_images.append(img_file.stem)
    
    print(f"✓ 找到 {len(image_files)} 张图片（根目录）")
    print(f"✓ 找到 {len(label_files)} 个标注文件（根目录）")
    print(f"✓ 有标注的图片: {len(labeled_images)} 张")
    
    if len(labeled_images) == 0:
        raise ValueError("没有找到有标注的图片！")
    
    return labeled_images


def create_dataset_split(labeled_images, output_dir='dataset_organized',
                         train_ratio=0.7, val_ratio=0.2, test_ratio=0.1):
    """创建数据集划分（train/val/test）"""
    
    # 设置随机种子以确保可重复性
    random.seed(42)
    
    # 验证比例
    assert abs(train_ratio + val_ratio + test_ratio - 1.0) < 1e-6, "比例之和必须等于1"
    
    # 创建输出目录结构
    output_path = Path(output_dir)
    for split in ['train', 'val', 'test']:
        (output_path / 'images' / split).mkdir(parents=True, exist_ok=True)
        (output_path / 'labels' / split).mkdir(parents=True, exist_ok=True)
    
    # 随机打乱
    random.shuffle(labeled_images)
    
    # 计算划分点
    total = len(labeled_images)
    train_count = int(total * train_ratio)
    val_count = int(total * val_ratio)
    
    train_files = labeled_images[:train_count]
    val_files = labeled_images[train_count:train_count + val_count]
    test_files = labeled_images[train_count + val_count:]
    
    print(f"\n数据集划分:")
    print(f"  训练集: {len(train_files)} 张 ({len(train_files)/total*100:.1f}%)")
    print(f"  验证集: {len(val_files)} 张 ({len(val_files)/total*100:.1f}%)")
    print(f"  测试集: {len(test_files)} 张 ({len(test_files)/total*100:.1f}%)")
    
    # 源目录（images和labels已经在dataset_organized下）
    images_source = output_path / 'images'
    labels_source = output_path / 'labels'
    
    # 检查源目录是否存在
    if not images_source.exists():
        raise FileNotFoundError(f"图片源目录不存在: {images_source}")
    if not labels_source.exists():
        raise FileNotFoundError(f"标注源目录不存在: {labels_source}")
    
    # 统计信息
    copied_count = 0
    missing_count = 0
    
    for split, files in [('train', train_files), ('val', val_files), ('test', test_files)]:
        for file_stem in files:
            # 查找图片文件（可能的后缀）
            # 只在images根目录下查找，不在train/val/test子目录中查找
            img_file = None
            for ext in ['.jpg', '.jpeg', '.png', '.JPG', '.JPEG', '.PNG']:
                candidate = images_source / f"{file_stem}{ext}"
                if candidate.exists() and candidate.parent == images_source:
                    img_file = candidate
                    break
            
            if img_file is None:
                print(f"  警告: 找不到图片文件 {file_stem}")
                missing_count += 1
                continue
            
            # 复制图片到对应的split目录
            dst_img = output_path / 'images' / split / img_file.name
            shutil.copy2(img_file, dst_img)
            
            # 复制标注到对应的split目录
            src_label = labels_source / f"{file_stem}.txt"
            if src_label.exists() and src_label.parent == labels_source:
                dst_label = output_path / 'labels' / split / f"{file_stem}.txt"
                shutil.copy2(src_label, dst_label)
                copied_count += 1
            else:
                print(f"  警告: 找不到标注文件 {file_stem}.txt")
                missing_count += 1
    
    if missing_count > 0:
        print(f"\n⚠ 警告: {missing_count} 个文件未找到对应的图片或标注")
    
    print(f"✓ 成功复制 {copied_count} 个文件对")
    
    return len(train_files), len(val_files), len(test_files)


def create_data_yaml(output_dir='dataset_organized', classes=None):
    """创建YOLO训练所需的data.yaml配置文件"""
    
    if classes is None:
        classes = load_classes()
    
    output_path = Path(output_dir)
    yaml_path = output_path / 'data.yaml'
    
    # 使用绝对路径
    abs_path = output_path.absolute()
    
    yaml_content = f"""# 铁路工具智能清点系统 - 数据集配置文件
# 自动生成

# 数据集路径 (绝对路径)
path: {abs_path}
train: images/train
val: images/val
test: images/test

# 类别数量
nc: {len(classes)}

# 类别名称
names:
"""
    
    # 添加类别名称
    for idx, class_name in enumerate(classes):
        yaml_content += f"  {idx}: {class_name}\n"
    
    # 保存yaml文件
    with open(yaml_path, 'w', encoding='utf-8') as f:
        f.write(yaml_content)
    
    print(f"\n✓ 已生成配置文件: {yaml_path}")
    return yaml_path


def prepare_dataset(train_ratio=0.7, val_ratio=0.2, test_ratio=0.1):
    """准备数据集：找到有标注的图片并创建划分"""
    
    print("=" * 80)
    print("准备数据集...")
    print("=" * 80)
    
    # 加载类别
    classes = load_classes()
    
    # 找到有标注的图片
    labeled_images = find_labeled_images()
    
    # 创建数据集划分
    train_count, val_count, test_count = create_dataset_split(
        labeled_images, 
        train_ratio=train_ratio,
        val_ratio=val_ratio,
        test_ratio=test_ratio
    )
    
    # 创建data.yaml配置文件
    create_data_yaml(classes=classes)
    
    print("\n" + "=" * 80)
    print("数据集准备完成！")
    print("=" * 80)
    print(f"训练集: {train_count} 张")
    print(f"验证集: {val_count} 张")
    print(f"测试集: {test_count} 张")
    print(f"总计: {train_count + val_count + test_count} 张")
    
    return train_count, val_count, test_count

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
        return None
    
    if not Path('dataset_organized/labels').exists():
        print(f"✗ 标注目录不存在: dataset_organized/labels")
        return None
    
    if not Path('dataset_organized/data.yaml').exists():
        print(f"✗ 数据集配置文件不存在: dataset_organized/data.yaml")
        return None
    
    print("=" * 80)
    return device


def find_best_model():
    """查找best.pt模型文件"""
    print("\n" + "=" * 80)
    print("查找已有模型...")
    print("=" * 80)
    
    # 可能的模型路径（按优先级排序）
    possible_paths = [
        Path('runs/detect/railway_tools_continue/weights/best.pt'),  # 优先使用最新的继续训练模型
        Path('runs/detect/runs/detect/railway_tools_continue/weights/best.pt'),
        Path('runs/detect/runs/detect/railway_tools/weights/best.pt'),
        Path('runs/detect/railway_tools/weights/best.pt'),
        Path('runs/detect/railway_tools_incremental/weights/best.pt'),
    ]
    
    # 递归查找所有best.pt文件
    runs_dir = Path('runs/detect')
    model_files = []
    if runs_dir.exists():
        pt_files = list(runs_dir.rglob('best.pt'))
        if pt_files:
            model_files.extend(pt_files)
    
    # 优先使用指定路径
    for path in possible_paths:
        if path.exists():
            print(f"✓ 找到模型文件: {path}")
            return str(path)
    
    # 如果指定路径不存在，使用找到的第一个
    if model_files:
        # 优先选择railway_tools目录下的
        railway_tools_files = [f for f in model_files if 'railway_tools' in str(f)]
        if railway_tools_files:
            model_path = str(railway_tools_files[0])
        else:
            model_path = str(model_files[0])
        print(f"✓ 找到模型文件: {model_path}")
        return model_path
    
    print("✗ 未找到模型文件！")
    print("请确保best.pt文件存在于以下位置之一:")
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
    print(f"✓ 类别名称: {list(names.values())}")
    
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


def continue_train(
    model_path,
    epochs=1000,
    batch_size=32,
    img_size=640,
    device=0,
    learning_rate=0.001  # 新增类别时使用稍大的学习率，让新类别能够学习
):
    """
    继续训练函数
    
    Args:
        model_path: 已有模型路径
        epochs: 训练轮数
        batch_size: 批次大小
        img_size: 图片尺寸
        device: GPU设备ID或'cpu'
        learning_rate: 学习率（继续训练建议使用较小的学习率）
    """
    
    print("\n" + "=" * 80)
    print("开始继续训练")
    print("=" * 80)
    print(f"模型: {model_path}")
    print(f"训练轮数: {epochs}")
    print(f"批次大小: {batch_size}")
    print(f"图片尺寸: {img_size}")
    print(f"设备: {device}")
    print(f"学习率: {learning_rate} (继续训练使用较小学习率)")
    print("=" * 80)
    
    # 检查模型文件是否存在
    if not Path(model_path).exists():
        raise FileNotFoundError(f"模型文件不存在: {model_path}")
    
    # 加载已有模型
    print(f"\n加载已有模型: {model_path}")
    model = YOLO(model_path)
    
    # 检查模型类别数是否匹配
    data_yaml_path = Path('dataset_organized/data.yaml')
    new_classes_count = 0
    if data_yaml_path.exists():
        with open(data_yaml_path, 'r', encoding='utf-8') as f:
            data_config = yaml.safe_load(f)
        dataset_nc = data_config.get('nc', 0)
        model_nc = getattr(model.model, 'nc', None)
        
        if model_nc is not None:
            print(f"\n模型信息:")
            print(f"  模型类别数: {model_nc}")
            print(f"  数据集类别数: {dataset_nc}")
            
            if model_nc != dataset_nc:
                if dataset_nc > model_nc:
                    new_classes_count = dataset_nc - model_nc
                    print(f"\n⚠ 检测到新增类别: {new_classes_count} 个")
                    print("   模型会自动适配新的类别数量")
                    print(f"\n💡 学习率建议:")
                    print(f"   - 新增类别需要学习新特征，建议使用稍大的学习率")
                    print(f"   - 推荐学习率: 0.001 (比默认的0.0001大10倍)")
                    print(f"   - 这样既能学习新类别，又能保留已有类别的知识")
                else:
                    print(f"\n⚠ 警告: 数据集减少了 {model_nc - dataset_nc} 个类别")
                    print("   建议重新训练模型")
                    print("   自动继续训练...")
    
    # 根据是否有新增类别调整学习率
    if new_classes_count > 0 and learning_rate < 0.001:
        print(f"\n💡 检测到新增类别，自动调整学习率: {learning_rate} -> 0.001")
        learning_rate = 0.001
    
    # 开始继续训练
    print("\n开始继续训练...")
    print("提示: 继续训练会保留原模型的权重，在此基础上学习新数据")
    print("     使用较小的学习率，避免破坏已有学习成果")
    print(f"\n早停设置: patience=15 (如果连续15轮无改进才会停止)")
    print("         如果训练过程中指标波动，这是正常的")
    
    results = model.train(
        data='dataset_organized/data.yaml',  # 数据集配置文件
        epochs=epochs,                      # 训练轮数
        imgsz=img_size,                     # 输入图片尺寸
        batch=batch_size,                   # 批次大小
        device=device,                      # GPU设备ID或'cpu'
        workers=4,                          # 数据加载线程数
        patience=15,                       # 早停耐心值（连续15轮无改进则停止，适合20轮训练）
        save=True,                          # 保存检查点
        save_period=20,                     # 每20个epoch保存一次
        project='runs/detect',              # 项目目录
        name='railway_tools_continue',      # 实验名称（使用新名称避免覆盖）
        exist_ok=True,                      # 允许覆盖已存在的实验
        pretrained=False,                   # 不使用预训练权重（使用已有模型）
        resume=False,                       # 不从检查点继续
        
        # 优化器配置（继续训练使用较小的学习率）
        optimizer='AdamW',
        lr0=learning_rate,                  # 初始学习率（继续训练使用较小值）
        lrf=0.001,                          # 最终学习率（更小，精细调优）
        momentum=0.937,
        weight_decay=0.0005,
        warmup_epochs=5,                    # 预热轮数（继续训练可以更长）
        warmup_momentum=0.8,
        
        # 损失函数权重（可以适当调整以提高识别率）
        box=7.5,
        cls=1.0,                            # 提高分类损失权重，提升识别准确率
        dfl=1.5,
        
        # 数据增强参数（继续训练可以适当增强）
        hsv_h=0.02,                         # 稍微增强颜色变化
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
        mixup=0.1,                          # 添加MixUp增强
        copy_paste=0.1,                     # 添加复制粘贴增强
        
        # 其他优化
        amp=True,                           # 混合精度训练（加速）
        cos_lr=True,                        # 余弦学习率调度
        close_mosaic=10,                    # 最后10轮关闭mosaic增强
    )
    
    print("\n" + "=" * 80)
    print("继续训练完成！")
    print("=" * 80)
    
    # 查找保存的模型文件
    runs_dir = Path('runs/detect/railway_tools_continue')
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
    print("🚀 继续训练脚本 - 在已有模型基础上继续训练")
    print("=" * 80)
    print("\n说明:")
    print("  - 使用已有best.pt模型继续训练")
    print("  - 适用于新增数据集和新增类别")
    print("  - 保留原模型的学习成果")
    print("  - 优化参数以提升识别准确率")
    print("=" * 80)
    
    # 检查环境
    device = check_environment()
    if device is None:
        print("\n环境检查失败，请检查数据集目录结构")
        return
    
    # 查找模型文件
    model_path = find_best_model()
    if model_path is None:
        return
    
    # 准备数据集（只使用有标注的图片）
    print("\n" + "=" * 80)
    print("步骤1: 准备数据集")
    print("=" * 80)
    
    # 检查是否已经准备好数据集
    data_yaml = Path('dataset_organized/data.yaml')
    train_dir = Path('dataset_organized/images/train')
    
    if data_yaml.exists() and train_dir.exists():
        print("✓ 检测到已准备好的数据集")
        print("自动重新准备数据集以确保使用最新数据...")
        prepare_dataset()
    else:
        print("正在准备数据集（只使用有标注的图片）...")
        prepare_dataset()
    
    # 检查数据集信息
    dataset_info = check_dataset_info()
    if dataset_info is None:
        return
    
    # 配置训练参数
    print("\n" + "=" * 80)
    print("配置训练参数")
    print("=" * 80)
    
    # 检查是否有新增类别
    data_yaml_path = Path('dataset_organized/data.yaml')
    has_new_classes = False
    if data_yaml_path.exists() and model_path:
        try:
            # 加载模型检查类别数
            temp_model = YOLO(model_path)
            model_nc = getattr(temp_model.model, 'nc', None)
            with open(data_yaml_path, 'r', encoding='utf-8') as f:
                data_config = yaml.safe_load(f)
            dataset_nc = data_config.get('nc', 0)
            if model_nc is not None and dataset_nc > model_nc:
                has_new_classes = True
                new_count = dataset_nc - model_nc
                print(f"\n💡 检测到新增 {new_count} 个类别，将使用适合的学习率")
        except:
            pass
    
    print("\n推荐配置（继续训练，追求最高识别率）:")
    print("  训练轮数: 20 (快速训练)")
    print("  批次大小: 32 (根据GPU显存调整)")
    if has_new_classes:
        print("  学习率: 0.001 (新增类别，使用较大学习率)")
    else:
        print("  学习率: 0.0001 (仅新增数据，使用较小学习率)")
    print("  图片尺寸: 640 (标准)")
    
    # 自动使用推荐配置（非交互模式）
    print("\n自动使用推荐配置...")
    epochs = 20
    batch_size = 32
    learning_rate = 0.001 if has_new_classes else 0.0001
    img_size = 640
    
    # 根据设备调整批次大小
    if device == 'cpu':
        batch_size = min(batch_size, 4)
        print(f"\n⚠ 检测到使用CPU训练，批次大小调整为 {batch_size}")
    elif device == 0:
        # GPU训练，可以根据显存调整
        print(f"\n当前批次大小: {batch_size}")
        print("如果显存不足，可以减小批次大小")
    
    # 开始继续训练
    print("\n" + "=" * 80)
    print("开始继续训练...")
    print("=" * 80)
    print(f"预计训练时间: {epochs * 0.3:.0f}-{epochs * 0.5:.0f} 分钟 (RTX 5090)")
    print("=" * 80)
    
    model = continue_train(
        model_path=model_path,
        epochs=epochs,
        batch_size=batch_size,
        img_size=img_size,
        device=device,
        learning_rate=learning_rate
    )
    
    if model is None:
        print("\n训练失败或取消")
        return
    
    print("\n" + "=" * 80)
    print("✅ 继续训练完成！")
    print("=" * 80)
    print("\n模型保存在: runs/detect/railway_tools_continue/weights/")
    print("\n接下来的步骤:")
    print("1. 查看训练结果: runs/detect/railway_tools_continue/")
    print("2. 验证模型性能")
    print("3. 导出模型用于部署")


if __name__ == '__main__':
    main()
