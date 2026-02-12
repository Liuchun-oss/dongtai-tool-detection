"""快速检查数据集的脚本"""
import os
from pathlib import Path

def check_dataset():
    dataset_path = Path('dataset_organized')
    
    print("=" * 60)
    print("数据集检查报告")
    print("=" * 60)
    
    for split in ['train', 'val', 'test']:
        images_dir = dataset_path / 'images' / split
        labels_dir = dataset_path / 'labels' / split
        
        if images_dir.exists():
            image_count = len(list(images_dir.glob('*.jpg')))
            label_count = len(list(labels_dir.glob('*.txt')))
            
            print(f"\n{split.upper()} 集:")
            print(f"  图片: {image_count} 张")
            print(f"  标签: {label_count} 个")
            
            if image_count == label_count:
                print(f"  状态: ✓ 完整")
            else:
                print(f"  状态: ✗ 不匹配")
        else:
            print(f"\n{split.upper()} 集: ✗ 目录不存在")
    
    # 检查配置文件
    yaml_file = dataset_path / 'data.yaml'
    if yaml_file.exists():
        print(f"\n配置文件: ✓ 存在 ({yaml_file})")
    else:
        print(f"\n配置文件: ✗ 不存在")
    
    print("\n" + "=" * 60)

if __name__ == '__main__':
    check_dataset()
