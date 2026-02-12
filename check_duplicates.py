#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查并删除重复的图片
"""
from pathlib import Path
from collections import defaultdict

images_dir = Path("dataset_organized/images")

# 查找新处理的图片
new_images = []
for img_path in images_dir.glob("*.jpg"):
    if "IMG_20260201" in img_path.name or "VID_20260201" in img_path.name:
        new_images.append(img_path)

print(f"找到 {len(new_images)} 张新图片")

# 按基础文件名分组（去除序号后缀）
base_names = defaultdict(list)
for img_path in new_images:
    name = img_path.stem
    # 如果文件名包含序号（如_0000, _0028），提取基础名
    if "_" in name and name.split("_")[-1].isdigit():
        base_name = "_".join(name.split("_")[:-1])
    else:
        base_name = name
    base_names[base_name].append(img_path)

# 找出重复的
duplicates = {k: v for k, v in base_names.items() if len(v) > 1}

if duplicates:
    print(f"\n发现 {len(duplicates)} 组重复图片:")
    total_duplicates = 0
    for base_name, files in duplicates.items():
        print(f"  {base_name}: {len(files)} 个文件")
        # 保留第一个，删除其他的
        for file in files[1:]:
            try:
                file.unlink()
                total_duplicates += 1
                print(f"    删除: {file.name}")
            except Exception as e:
                print(f"    删除失败 {file.name}: {e}")
    print(f"\n已删除 {total_duplicates} 张重复图片")
else:
    print("\n未发现重复图片")

# 重新统计
remaining = []
for img_path in images_dir.glob("*.jpg"):
    if "IMG_20260201" in img_path.name or "VID_20260201" in img_path.name:
        remaining.append(img_path)

print(f"\n剩余新图片: {len(remaining)} 张")
print(f"  - 原始图片: {len([f for f in remaining if 'IMG_' in f.name and '_frame_' not in f.name])} 张")
print(f"  - 视频帧: {len([f for f in remaining if '_frame_' in f.name])} 张")
