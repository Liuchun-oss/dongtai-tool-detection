import os
import shutil
import random
from pathlib import Path
from collections import defaultdict

def organize_dataset(source_dir='data', output_dir='dataset_organized', 
                     train_ratio=0.7, val_ratio=0.2, test_ratio=0.1):
    """
    整理数据集并划分训练集、验证集、测试集
    
    Args:
        source_dir: 原始数据目录
        output_dir: 输出目录
        train_ratio: 训练集比例
        val_ratio: 验证集比例
        test_ratio: 测试集比例
    """
    
    # 设置随机种子以确保可重复性
    random.seed(42)
    
    # 创建输出目录结构
    output_path = Path(output_dir)
    for split in ['train', 'val', 'test']:
        (output_path / 'images' / split).mkdir(parents=True, exist_ok=True)
        (output_path / 'labels' / split).mkdir(parents=True, exist_ok=True)
    
    # 统计信息
    stats = {
        'total': 0,
        'train': 0,
        'val': 0,
        'test': 0,
        'categories': defaultdict(lambda: {'total': 0, 'train': 0, 'val': 0, 'test': 0})
    }
    
    # 遍历所有类别文件夹
    source_path = Path(source_dir)
    categories = sorted([d for d in source_path.iterdir() if d.is_dir()])
    
    print("=" * 80)
    print("开始整理数据集...")
    print("=" * 80)
    print(f"原始数据目录: {source_dir}")
    print(f"输出目录: {output_dir}")
    print(f"划分比例 - 训练集: {train_ratio*100}%, 验证集: {val_ratio*100}%, 测试集: {test_ratio*100}%")
    print("=" * 80)
    
    for category_dir in categories:
        category_name = category_dir.name
        print(f"\n处理类别: {category_name}")
        
        # 获取图片和标签路径
        images_dir = category_dir / category_name
        labels_dir = category_dir / 'labels'
        
        if not images_dir.exists() or not labels_dir.exists():
            print(f"  警告: {category_name} 缺少图片或标签目录，跳过")
            continue
        
        # 获取所有图片文件
        image_files = sorted([f for f in images_dir.glob('*.jpg')])
        
        # 检查每张图片是否都有对应的标签
        valid_files = []
        for img_file in image_files:
            label_file = labels_dir / f"{img_file.stem}.txt"
            if label_file.exists():
                valid_files.append(img_file.stem)
            else:
                print(f"  警告: {img_file.name} 缺少对应的标签文件")
        
        total_files = len(valid_files)
        if total_files == 0:
            print(f"  警告: {category_name} 没有有效的图片-标签对，跳过")
            continue
        
        # 随机打乱文件列表
        random.shuffle(valid_files)
        
        # 计算划分点
        train_count = int(total_files * train_ratio)
        val_count = int(total_files * val_ratio)
        
        train_files = valid_files[:train_count]
        val_files = valid_files[train_count:train_count + val_count]
        test_files = valid_files[train_count + val_count:]
        
        print(f"  总数: {total_files} 张")
        print(f"  训练集: {len(train_files)} 张, 验证集: {len(val_files)} 张, 测试集: {len(test_files)} 张")
        
        # 复制文件到对应目录
        for split, files in [('train', train_files), ('val', val_files), ('test', test_files)]:
            for file_stem in files:
                # 使用类别前缀重命名文件，避免不同类别的文件名冲突
                new_name = f"{category_name}_{file_stem}"
                
                # 复制图片
                src_img = images_dir / f"{file_stem}.jpg"
                dst_img = output_path / 'images' / split / f"{new_name}.jpg"
                shutil.copy2(src_img, dst_img)
                
                # 复制标签
                src_label = labels_dir / f"{file_stem}.txt"
                dst_label = output_path / 'labels' / split / f"{new_name}.txt"
                shutil.copy2(src_label, dst_label)
                
                stats[split] += 1
                stats['categories'][category_name][split] += 1
        
        # 更新统计信息
        stats['total'] += total_files
        stats['categories'][category_name]['total'] = total_files
    
    # 打印统计信息
    print("\n" + "=" * 80)
    print("数据集整理完成！")
    print("=" * 80)
    print(f"\n总体统计:")
    print(f"  总计: {stats['total']} 张")
    print(f"  训练集: {stats['train']} 张 ({stats['train']/stats['total']*100:.1f}%)")
    print(f"  验证集: {stats['val']} 张 ({stats['val']/stats['total']*100:.1f}%)")
    print(f"  测试集: {stats['test']} 张 ({stats['test']/stats['total']*100:.1f}%)")
    
    print(f"\n各类别统计:")
    print("-" * 80)
    print(f"{'类别':<8} {'总数':>6} {'训练集':>8} {'验证集':>8} {'测试集':>8}")
    print("-" * 80)
    for category in sorted(stats['categories'].keys()):
        cat_stats = stats['categories'][category]
        print(f"{category:<8} {cat_stats['total']:>6} "
              f"{cat_stats['train']:>8} {cat_stats['val']:>8} {cat_stats['test']:>8}")
    print("-" * 80)
    
    # 创建data.yaml配置文件
    create_data_yaml(output_dir, stats['categories'])
    
    print(f"\n数据集已保存到: {output_dir}")
    print(f"配置文件已生成: {output_dir}/data.yaml")
    print("\n现在可以开始训练模型了！")


def create_data_yaml(output_dir, categories):
    """创建YOLO训练所需的data.yaml配置文件"""
    
    yaml_content = f"""# 铁路工具智能清点系统 - 数据集配置文件
# 自动生成

# 数据集路径 (相对路径或绝对路径)
path: {os.path.abspath(output_dir)}
train: images/train
val: images/val
test: images/test

# 类别数量
nc: {len(categories)}

# 类别名称 (根据实际工具类型修改)
names:
"""
    
    # 添加类别名称（这里使用原始类别名，需要根据实际情况修改）
    for idx, category in enumerate(sorted(categories.keys())):
        yaml_content += f"  {idx}: {category}\n"
    
    yaml_content += """
# 注意：请根据实际的工具类型修改上面的类别名称
# 例如：
#   0: 大扳手
#   1: 活口扳手
#   2: 尖嘴钳
#   等等...
"""
    
    # 保存yaml文件
    yaml_path = Path(output_dir) / 'data.yaml'
    with open(yaml_path, 'w', encoding='utf-8') as f:
        f.write(yaml_content)


def verify_dataset(dataset_dir='dataset_organized'):
    """验证数据集的完整性"""
    
    print("\n" + "=" * 80)
    print("验证数据集完整性...")
    print("=" * 80)
    
    dataset_path = Path(dataset_dir)
    issues = []
    
    for split in ['train', 'val', 'test']:
        images_dir = dataset_path / 'images' / split
        labels_dir = dataset_path / 'labels' / split
        
        if not images_dir.exists():
            issues.append(f"缺少目录: {images_dir}")
            continue
        
        if not labels_dir.exists():
            issues.append(f"缺少目录: {labels_dir}")
            continue
        
        image_files = set([f.stem for f in images_dir.glob('*.jpg')])
        label_files = set([f.stem for f in labels_dir.glob('*.txt')])
        
        # 检查是否每张图片都有对应的标签
        missing_labels = image_files - label_files
        missing_images = label_files - image_files
        
        if missing_labels:
            issues.append(f"{split}集中 {len(missing_labels)} 张图片缺少标签")
        
        if missing_images:
            issues.append(f"{split}集中 {len(missing_images)} 个标签缺少对应图片")
        
        print(f"{split}集: {len(image_files)} 张图片, {len(label_files)} 个标签")
    
    if issues:
        print("\n发现问题:")
        for issue in issues:
            print(f"  - {issue}")
        return False
    else:
        print("\n✓ 数据集完整性验证通过！")
        return True


if __name__ == '__main__':
    # 整理数据集
    organize_dataset(
        source_dir='data',
        output_dir='dataset_organized',
        train_ratio=0.7,
        val_ratio=0.2,
        test_ratio=0.1
    )
    
    # 验证数据集
    verify_dataset('dataset_organized')
