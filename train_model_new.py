"""
铁路工具智能清点系统 - YOLOv8模型训练脚本（新版本）
只使用有标注的图片进行训练
"""

from ultralytics import YOLO
import torch
import os
import shutil
import random
import yaml
from pathlib import Path
from collections import defaultdict

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
                         train_ratio=0.8, val_ratio=0.2):
    """创建数据集划分（train/val，不划分测试集）"""
    
    # 设置随机种子以确保可重复性
    random.seed(42)
    
    # 验证比例
    assert abs(train_ratio + val_ratio - 1.0) < 1e-6, "比例之和必须等于1"
    
    # 创建输出目录结构（只创建train和val）
    output_path = Path(output_dir)
    for split in ['train', 'val']:
        (output_path / 'images' / split).mkdir(parents=True, exist_ok=True)
        (output_path / 'labels' / split).mkdir(parents=True, exist_ok=True)
    
    # 随机打乱
    random.shuffle(labeled_images)
    
    # 计算划分点
    total = len(labeled_images)
    train_count = int(total * train_ratio)
    
    train_files = labeled_images[:train_count]
    val_files = labeled_images[train_count:]
    
    print(f"\n数据集划分:")
    print(f"  训练集: {len(train_files)} 张 ({len(train_files)/total*100:.1f}%)")
    print(f"  验证集: {len(val_files)} 张 ({len(val_files)/total*100:.1f}%)")
    
    # 源目录（images和labels已经在dataset_organized下）
    # 源文件在: dataset_organized/images/*.jpg 和 dataset_organized/labels/*.txt
    # 目标文件在: dataset_organized/images/train|val/*.jpg 和 dataset_organized/labels/train|val/*.txt
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
    
    for split, files in [('train', train_files), ('val', val_files)]:
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
    
    return len(train_files), len(val_files)


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


def check_cuda_compatibility():
    """检查CUDA兼容性 - 实际测试CUDA是否可用，而不是仅检查compute capability"""
    try:
        if not torch.cuda.is_available():
            return False, "CUDA不可用"
        
        # 获取GPU的compute capability
        device_props = torch.cuda.get_device_properties(0)
        compute_capability = device_props.major * 10 + device_props.minor
        
        # 对于sm_120（RTX 5090），虽然PyTorch可能显示警告，但实际可能可用
        # 所以直接进行实际测试，而不是仅根据compute capability判断
        if compute_capability >= 120:
            print(f"  检测到sm_{compute_capability}，进行实际CUDA测试...")
        
        # 实际测试CUDA是否可用（这是最可靠的方法）
        try:
            test_tensor = torch.zeros(100, 100).cuda()
            result = torch.matmul(test_tensor, test_tensor)
            del test_tensor, result
            torch.cuda.empty_cache()
            
            # 如果测试成功，即使有警告也认为可用
            if compute_capability >= 120:
                return True, f"CUDA可用（sm_{compute_capability}，可能有警告但实际可用）"
            else:
                return True, "CUDA兼容"
        except RuntimeError as e:
            # 如果实际测试失败，才认为不兼容
            if "no kernel image" in str(e) or "CUDA" in str(e):
                return False, f"CUDA测试失败: {str(e)}"
            raise
    except RuntimeError as e:
        if "no kernel image" in str(e) or "CUDA" in str(e):
            return False, f"CUDA兼容性错误: {str(e)}"
        return False, f"CUDA错误: {str(e)}"
    except Exception as e:
        return False, f"未知错误: {str(e)}"


def check_environment(force_cpu=False):
    """检查训练环境
    
    Args:
        force_cpu: 强制使用CPU，跳过CUDA检测
    """
    print("=" * 80)
    print("检查训练环境...")
    print("=" * 80)
    
    # 如果强制使用CPU，直接返回
    if force_cpu:
        print("⚠ 强制使用CPU训练模式")
        device = 'cpu'
    # 检查环境变量是否禁用CUDA
    elif os.environ.get('CUDA_VISIBLE_DEVICES') == '' or os.environ.get('FORCE_CPU', '').lower() == 'true':
        print("⚠ 检测到环境变量禁用CUDA，使用CPU训练")
        device = 'cpu'
    # 检查CUDA可用性
    elif torch.cuda.is_available():
        print(f"✓ CUDA可用")
        print(f"  GPU设备: {torch.cuda.get_device_name(0)}")
        print(f"  GPU数量: {torch.cuda.device_count()}")
        print(f"  显存容量: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB")
        print(f"  CUDA版本: {torch.version.cuda}")
        
        # 检查兼容性（实际测试CUDA是否可用）
        is_compatible, message = check_cuda_compatibility()
        if not is_compatible:
            print(f"\n⚠ {message}")
            print("\n解决方案:")
            print("  1. 更新PyTorch到最新版本（推荐）:")
            print("     pip install --upgrade torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124")
            print("  2. 或使用CPU训练（速度较慢，但可以使用现有环境）")
            print("\n将自动切换到CPU训练...")
            device = 'cpu'
        else:
            print(f"✓ {message}")
            if "可能有警告" in message:
                print("  注意: PyTorch可能显示警告，但会尝试使用GPU")
                print("  如果训练时遇到CUDA错误，会自动切换到CPU")
            # 即使有警告，也尝试使用GPU（脚本会在训练时捕获错误）
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
    
    if not Path('dataset_organized/classes.txt').exists():
        print(f"✗ 类别文件不存在: dataset_organized/classes.txt")
        return None
    
    print("=" * 80)
    return device


def train_model(model_size='s', epochs=100, batch_size=16, img_size=640, device=0, patience=None):
    """
    训练YOLOv8模型
    
    Args:
        model_size: 模型大小 ('n', 's', 'm', 'l', 'x')
        epochs: 训练轮数
        batch_size: 批次大小（根据GPU显存调整）
        img_size: 输入图片尺寸
        device: GPU设备ID或'cpu'
        patience: 早停耐心值（如果为None，则根据epochs自动计算）
    """
    
    # 自动计算早停耐心值（如果未指定）
    if patience is None:
        # 根据训练轮数自动计算：训练轮数的25%，最小10，最大为训练轮数的50%
        # 这样即使训练很多轮，也能给模型足够的改进机会
        calculated_patience = max(10, int(epochs * 0.25))  # 至少10，基础值是25%
        max_patience = int(epochs * 0.5)  # 最大不超过50%
        patience = min(max_patience, calculated_patience)
    
    print("=" * 80)
    print("开始训练YOLOv8模型")
    print("=" * 80)
    print(f"模型: YOLOv8{model_size}")
    print(f"训练轮数: {epochs}")
    print(f"批次大小: {batch_size}")
    print(f"图片尺寸: {img_size}")
    print(f"设备: {device}")
    print(f"早停耐心值: {patience} (连续{patience}轮无改进则停止)")
    print("=" * 80)
    
    # 检查data.yaml是否存在
    data_yaml = Path('dataset_organized/data.yaml')
    if not data_yaml.exists():
        raise FileNotFoundError(f"数据集配置文件不存在: {data_yaml}，请先运行prepare_dataset()")
    
    # 加载预训练模型
    model = YOLO(f'yolov8{model_size}.pt')
    
    # 如果使用GPU，先测试模型是否能加载到GPU
    if device != 'cpu':
        try:
            print("\n测试模型GPU加载...")
            # 尝试将模型移动到GPU
            test_model = model.model.to(f'cuda:{device}')
            del test_model
            torch.cuda.empty_cache()
            print("✓ GPU模型加载测试通过")
        except RuntimeError as e:
            if "no kernel image" in str(e) or "CUDA" in str(e):
                print(f"\n⚠ GPU模型加载失败: {e}")
                print("   自动切换到CPU训练...")
                device = 'cpu'
                batch_size = min(batch_size, 4)  # CPU训练使用更小的批次
                print(f"   批次大小调整为: {batch_size}")
            else:
                raise
    
    # 开始训练
    try:
        results = model.train(
        data=str(data_yaml),              # 数据集配置文件
        epochs=epochs,                    # 训练轮数
        imgsz=img_size,                   # 输入图片尺寸
        batch=batch_size,                 # 批次大小
        device=device,                    # GPU设备ID或'cpu'
        workers=4,                        # 数据加载线程数
        patience=patience,                # 早停耐心值（根据训练轮数自动计算）
        save=True,                        # 保存检查点
        save_period=10,                   # 每10个epoch保存一次
        project='runs/detect',            # 项目目录
        name='railway_tools',             # 实验名称
        exist_ok=True,                    # 允许覆盖已存在的实验
        pretrained=True,                  # 使用预训练权重
        optimizer='AdamW',                # 优化器
        lr0=0.01,                         # 初始学习率
        lrf=0.01,                         # 最终学习率
        momentum=0.937,                   # 动量
        weight_decay=0.0005,              # 权重衰减
        warmup_epochs=3,                  # 预热轮数
        warmup_momentum=0.8,              # 预热动量
        box=7.5,                          # 边界框损失权重
        cls=0.5,                          # 分类损失权重
        dfl=1.5,                          # DFL损失权重
        
        # 数据增强参数
        hsv_h=0.015,                      # 色调增强
        hsv_s=0.7,                        # 饱和度增强
        hsv_v=0.4,                        # 明度增强
        degrees=0.0,                      # 旋转角度
        translate=0.1,                    # 平移
        scale=0.5,                        # 缩放
        shear=0.0,                        # 剪切
        perspective=0.0,                  # 透视变换
        flipud=0.0,                       # 上下翻转概率
        fliplr=0.5,                       # 左右翻转概率
        mosaic=1.0,                       # 马赛克增强概率
        mixup=0.0,                        # MixUp增强概率
        copy_paste=0.0,                   # 复制粘贴增强概率
        )
    except RuntimeError as e:
        if "no kernel image" in str(e) or "CUDA" in str(e):
            print(f"\n⚠ 训练时GPU错误: {e}")
            print("   自动切换到CPU训练...")
            device = 'cpu'
            batch_size = min(batch_size, 4)
            print(f"   批次大小调整为: {batch_size}")
            print("   重新开始CPU训练...")
            
            # 重新加载模型并使用CPU训练
            model = YOLO(f'yolov8{model_size}.pt')
            results = model.train(
                data=str(data_yaml),
                epochs=epochs,
                imgsz=img_size,
                batch=batch_size,
                device='cpu',
                workers=2,  # CPU训练使用更少的workers
                patience=patience,
                save=True,
                save_period=10,
                project='runs/detect',
                name='railway_tools',
                exist_ok=True,
                pretrained=True,
                optimizer='AdamW',
                lr0=0.01,
                lrf=0.01,
                momentum=0.937,
                weight_decay=0.0005,
                warmup_epochs=3,
                warmup_momentum=0.8,
                box=7.5,
                cls=0.5,
                dfl=1.5,
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
        else:
            raise
    
    print("\n" + "=" * 80)
    print("训练完成！")
    print("=" * 80)
    
    # 查找实际保存的模型文件路径
    runs_dir = Path('runs/detect')
    model_files = []
    if runs_dir.exists():
        # 查找所有.pt文件
        pt_files = list(runs_dir.rglob('*.pt'))
        best_files = [f for f in pt_files if 'best' in f.name.lower()]
        last_files = [f for f in pt_files if 'last' in f.name.lower()]
        
        if best_files:
            best_path = best_files[0]
            print(f"最佳模型保存在: {best_path}")
            model_files.append(str(best_path))
        else:
            print("未找到best.pt文件")
        
        if last_files:
            last_path = last_files[0]
            print(f"最后模型保存在: {last_path}")
            model_files.append(str(last_path))
        else:
            print("未找到last.pt文件")
        
        if not model_files:
            print("警告: 未找到任何模型文件！")
            print("请检查训练日志确认训练是否成功完成")
    else:
        print("警告: runs目录不存在！")
    
    return model


def validate_model(model_path='runs/detect/railway_tools/weights/best.pt'):
    """验证模型性能"""
    
    print("\n" + "=" * 80)
    print("验证模型性能...")
    print("=" * 80)
    
    # 检查模型文件是否存在
    model_path_obj = Path(model_path)
    if not model_path_obj.exists():
        # 尝试查找模型文件
        print(f"模型文件不存在: {model_path}")
        print("正在查找模型文件...")
        
        # 查找可能的模型文件位置
        possible_paths = [
            Path('runs/detect/railway_tools/weights/best.pt'),
            Path('runs/detect/railway_tools/weights/last.pt'),
            Path('runs/detect/railway_tools/best.pt'),
            Path('runs/detect/railway_tools/last.pt'),
        ]
        
        # 递归查找runs目录下的所有.pt文件
        runs_dir = Path('runs/detect')
        if runs_dir.exists():
            pt_files = list(runs_dir.rglob('*.pt'))
            if pt_files:
                print(f"找到以下模型文件:")
                for i, f in enumerate(pt_files[:5], 1):  # 只显示前5个
                    print(f"  {i}. {f}")
                # 使用第一个找到的best.pt，否则使用last.pt
                best_files = [f for f in pt_files if 'best' in f.name.lower()]
                if best_files:
                    model_path = str(best_files[0])
                    print(f"\n使用模型文件: {model_path}")
                else:
                    model_path = str(pt_files[0])
                    print(f"\n使用模型文件: {model_path}")
            else:
                print("未找到模型文件！")
                print("训练可能未正确保存模型，请检查训练日志")
                return None
        else:
            print("runs目录不存在！")
            return None
    
    # 加载训练好的模型
    model = YOLO(str(model_path))
    
    # 检查模型类别数量是否匹配
    data_yaml_path = Path('dataset_organized/data.yaml')
    if data_yaml_path.exists():
        with open(data_yaml_path, 'r', encoding='utf-8') as f:
            data_config = yaml.safe_load(f)
        dataset_nc = data_config.get('nc', 0)
        
        # 检查模型类别数
        model_nc = getattr(model.model, 'nc', None)
        if model_nc is not None and model_nc != dataset_nc:
            print(f"\n⚠ 警告: 模型类别数 ({model_nc}) 与数据集类别数 ({dataset_nc}) 不匹配！")
            print(f"   模型路径: {model_path}")
            print(f"   数据集类别数: {dataset_nc}")
            print(f"   模型类别数: {model_nc}")
            print("\n解决方案:")
            print("  1. 使用正确类别数的模型进行验证")
            print("  2. 或重新训练模型以匹配当前数据集")
            
            # 尝试查找匹配的模型
            runs_dir = Path('runs/detect')
            if runs_dir.exists():
                pt_files = list(runs_dir.rglob('*.pt'))
                print(f"\n找到 {len(pt_files)} 个模型文件，请选择正确的模型")
                return None
    
    # 在验证集上评估
    try:
        metrics = model.val(data='dataset_organized/data.yaml')
    except (IndexError, ValueError) as e:
        if "out of bounds" in str(e) or "size" in str(e):
            print(f"\n❌ 验证失败: 类别数量不匹配")
            print(f"   错误: {e}")
            print("\n这通常是因为模型是用不同的类别数量训练的")
            print("请使用与当前数据集匹配的模型，或重新训练模型")
            return None
        else:
            raise
    
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


def prepare_dataset(train_ratio=0.8, val_ratio=0.2):
    """准备数据集：找到有标注的图片并创建划分（只划分训练集和验证集）"""
    
    print("=" * 80)
    print("准备数据集...")
    print("=" * 80)
    
    # 加载类别
    classes = load_classes()
    
    # 找到有标注的图片
    labeled_images = find_labeled_images()
    
    # 创建数据集划分（只划分train和val）
    train_count, val_count = create_dataset_split(
        labeled_images, 
        train_ratio=train_ratio,
        val_ratio=val_ratio
    )
    
    # 创建data.yaml配置文件
    create_data_yaml(classes=classes)
    
    print("\n" + "=" * 80)
    print("数据集准备完成！")
    print("=" * 80)
    print(f"训练集: {train_count} 张")
    print(f"验证集: {val_count} 张")
    print(f"总计: {train_count + val_count} 张")
    
    return train_count, val_count


def main():
    """主函数"""
    
    print("\n" + "=" * 80)
    print("🚀 铁路工具智能清点系统 - 模型训练")
    print("=" * 80)
    print("\n说明:")
    print("  - 只使用有标注的图片进行训练")
    print("  - 自动划分训练集、验证集（不划分测试集，更多数据用于训练）")
    print("  - 自动生成data.yaml配置文件")
    print("=" * 80)
    
    # 检查是否强制使用CPU
    force_cpu = os.environ.get('FORCE_CPU', '').lower() == 'true'
    
    # 检查环境
    device = check_environment(force_cpu=force_cpu)
    if device is None:
        print("\n环境检查失败，请检查数据集目录结构")
        return
    
    # 如果使用CPU，给出提示
    if device == 'cpu':
        print("\n" + "=" * 80)
        print("⚠ 使用CPU训练")
        print("=" * 80)
        print("✓ 使用现有PyTorch环境进行CPU训练")
        print("\n提示:")
        print("  - CPU训练速度较慢，但可以使用现有环境")
        print("  - 训练时间会比GPU慢10-50倍")
        print("  - 建议减少训练轮数或使用较小的模型")
        print("\n继续使用CPU训练...")
        print("=" * 80)
    
    # 准备数据集
    print("\n" + "=" * 80)
    print("步骤1: 准备数据集")
    print("=" * 80)
    
    # 检查是否已经准备好数据集
    data_yaml = Path('dataset_organized/data.yaml')
    train_dir = Path('dataset_organized/images/train')
    
    if data_yaml.exists() and train_dir.exists():
        print("✓ 检测到已准备好的数据集")
        choice = input("是否重新准备数据集? (y/n) [n]: ").strip().lower()
        if choice == 'y':
            prepare_dataset()
    else:
        print("正在准备数据集...")
        prepare_dataset()
    
    # 选择模型大小
    print("\n" + "=" * 80)
    print("步骤2: 选择模型配置")
    print("=" * 80)
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
        batch_size = 32
    
    # 根据设备调整批次大小
    if device == 'cpu':
        batch_size = min(batch_size, 4)  # CPU训练使用更小的批次
        print(f"\n⚠ 检测到使用CPU训练，批次大小调整为 {batch_size}")
        print("   提示: CPU训练速度很慢，建议更新PyTorch以使用GPU")
    
    # 输入训练轮数
    print("\n" + "=" * 80)
    print("步骤3: 设置训练参数")
    print("=" * 80)
    
    while True:
        try:
            epochs_input = input("请输入训练轮数 [默认100]: ").strip()
            if epochs_input == "":
                epochs = 100
            else:
                epochs = int(epochs_input)
                if epochs <= 0:
                    print("⚠ 训练轮数必须大于0，请重新输入")
                    continue
            break
        except ValueError:
            print("⚠ 请输入有效的数字")
    
    # 自动计算早停耐心值（训练轮数的25%，最小10，最大为训练轮数的50%）
    # 这样即使训练很多轮，也能给模型足够的改进机会
    calculated_patience = max(10, int(epochs * 0.25))  # 至少10，基础值是25%
    max_patience = int(epochs * 0.5)  # 最大不超过50%
    patience = min(max_patience, calculated_patience)
    
    print(f"\n训练配置:")
    print(f"  模型: YOLOv8{model_size}")
    print(f"  训练轮数: {epochs}")
    print(f"  批次大小: {batch_size}")
    print(f"  图片尺寸: 640")
    print(f"  早停耐心值: {patience} (连续{patience}轮无改进则停止)")
    
    # 训练模型
    print("\n" + "=" * 80)
    print("步骤4: 开始训练")
    print("=" * 80)
    
    model = train_model(
        model_size=model_size,
        epochs=epochs,
        batch_size=batch_size,
        img_size=640,
        device=device,
        patience=patience
    )
    
    # 验证模型
    print("\n" + "=" * 80)
    print("步骤5: 验证模型")
    print("=" * 80)
    
    # 查找模型文件
    runs_dir = Path('runs/detect')
    model_path = None
    if runs_dir.exists():
        pt_files = list(runs_dir.rglob('best.pt'))
        if pt_files:
            model_path = str(pt_files[0])
        else:
            # 如果没有best.pt，尝试找last.pt
            pt_files = list(runs_dir.rglob('last.pt'))
            if pt_files:
                model_path = str(pt_files[0])
    
    if model_path:
        validate_model(model_path)
    else:
        print("未找到模型文件，跳过验证")
        print("模型文件可能保存在其他位置，请手动检查runs目录")
    
    # 导出模型（用于Android部署）
    print("\n" + "=" * 80)
    print("步骤5: 导出模型")
    print("=" * 80)
    export_choice = input("\n是否导出TensorFlow Lite模型用于Android部署? (y/n) [y]: ").strip().lower()
    if export_choice != 'n':
        export_model()
    
    print("\n" + "=" * 80)
    print("✅ 全部完成！")
    print("=" * 80)
    print("\n接下来的步骤:")
    print("1. 查看训练结果: runs/detect/railway_tools/")
    print("2. 如果性能不满意，可以调整参数重新训练")
    print("3. 将导出的 .tflite 模型文件复制到 Android 项目的 assets 目录")
    print("4. 在 Android 应用中集成模型进行推理")


if __name__ == '__main__':
    main()
