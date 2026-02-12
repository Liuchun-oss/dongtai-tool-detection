"""
数据集可视化脚本
可以随机展示数据集中的图片和标注框
"""

import cv2
import numpy as np
from pathlib import Path
import random
import yaml

def load_classes(yaml_file='dataset_organized/data.yaml'):
    """加载类别名称"""
    with open(yaml_file, 'r', encoding='utf-8') as f:
        data = yaml.safe_load(f)
    return data['names']

def draw_boxes(image_path, label_path, class_names):
    """在图片上绘制标注框"""
    # 读取图片
    img = cv2.imread(str(image_path))
    if img is None:
        print(f"无法读取图片: {image_path}")
        return None
    
    h, w = img.shape[:2]
    
    # 读取标注
    if not label_path.exists():
        print(f"标注文件不存在: {label_path}")
        return img
    
    with open(label_path, 'r') as f:
        lines = f.readlines()
    
    # 绘制每个标注框
    colors = {}
    for line in lines:
        parts = line.strip().split()
        if len(parts) < 5:
            continue
        
        class_id = int(parts[0])
        center_x = float(parts[1]) * w
        center_y = float(parts[2]) * h
        box_w = float(parts[3]) * w
        box_h = float(parts[4]) * h
        
        # 计算左上角和右下角坐标
        x1 = int(center_x - box_w / 2)
        y1 = int(center_y - box_h / 2)
        x2 = int(center_x + box_w / 2)
        y2 = int(center_y + box_h / 2)
        
        # 为每个类别分配颜色
        if class_id not in colors:
            colors[class_id] = (
                random.randint(0, 255),
                random.randint(0, 255),
                random.randint(0, 255)
            )
        
        color = colors[class_id]
        
        # 绘制矩形框
        cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)
        
        # 绘制类别标签
        label = class_names.get(class_id, f'Class {class_id}')
        label_size, _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
        
        # 绘制标签背景
        cv2.rectangle(img, (x1, y1 - label_size[1] - 5), 
                     (x1 + label_size[0], y1), color, -1)
        
        # 绘制标签文字
        cv2.putText(img, label, (x1, y1 - 5), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
    
    return img

def visualize_samples(split='train', num_samples=5, save_dir='visualizations'):
    """可视化数据集样本"""
    
    print("=" * 60)
    print(f"可视化 {split} 集样本")
    print("=" * 60)
    
    # 创建保存目录
    save_path = Path(save_dir)
    save_path.mkdir(exist_ok=True)
    
    # 加载类别名称
    class_names = load_classes()
    
    # 获取图片列表
    images_dir = Path('dataset_organized') / 'images' / split
    labels_dir = Path('dataset_organized') / 'labels' / split
    
    image_files = list(images_dir.glob('*.jpg'))
    
    if not image_files:
        print(f"未找到图片文件: {images_dir}")
        return
    
    # 随机选择样本
    sample_files = random.sample(image_files, min(num_samples, len(image_files)))
    
    print(f"从 {len(image_files)} 张图片中随机选择 {len(sample_files)} 张进行可视化")
    print()
    
    for idx, img_file in enumerate(sample_files, 1):
        label_file = labels_dir / f"{img_file.stem}.txt"
        
        print(f"[{idx}/{len(sample_files)}] 处理: {img_file.name}")
        
        # 绘制标注框
        img_with_boxes = draw_boxes(img_file, label_file, class_names)
        
        if img_with_boxes is not None:
            # 保存可视化结果
            output_file = save_path / f"{split}_{idx}_{img_file.name}"
            cv2.imwrite(str(output_file), img_with_boxes)
            print(f"  保存到: {output_file}")
    
    print()
    print("=" * 60)
    print(f"可视化完成！结果保存在: {save_dir}/")
    print("=" * 60)

def show_statistics():
    """显示数据集统计信息"""
    print("\n" + "=" * 60)
    print("数据集统计信息")
    print("=" * 60)
    
    class_names = load_classes()
    
    # 统计各集合的样本数
    for split in ['train', 'val', 'test']:
        images_dir = Path('dataset_organized') / 'images' / split
        labels_dir = Path('dataset_organized') / 'labels' / split
        
        if not images_dir.exists():
            continue
        
        image_files = list(images_dir.glob('*.jpg'))
        label_files = list(labels_dir.glob('*.txt'))
        
        # 统计每个类别的标注数量
        class_counts = {i: 0 for i in range(len(class_names))}
        
        for label_file in label_files:
            with open(label_file, 'r') as f:
                for line in f:
                    parts = line.strip().split()
                    if len(parts) >= 5:
                        class_id = int(parts[0])
                        class_counts[class_id] += 1
        
        print(f"\n{split.upper()} 集:")
        print(f"  图片数量: {len(image_files)}")
        print(f"  标注文件: {len(label_files)}")
        print(f"  标注框总数: {sum(class_counts.values())}")
        print(f"  平均每张图片: {sum(class_counts.values()) / len(image_files):.1f} 个标注框")

if __name__ == '__main__':
    # 设置随机种子
    random.seed(42)
    
    # 显示统计信息
    show_statistics()
    
    print("\n" + "=" * 60)
    print("开始可视化样本...")
    print("=" * 60)
    
    # 可视化各集合的样本
    for split in ['train', 'val', 'test']:
        num_samples = 5 if split == 'train' else 3
        visualize_samples(split=split, num_samples=num_samples)
    
    print("\n完成！请查看 'visualizations' 目录中的可视化结果。")
