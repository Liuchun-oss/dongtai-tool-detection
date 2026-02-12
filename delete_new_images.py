#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
删除刚刚新增的图片
"""
from pathlib import Path

images_dir = Path("dataset_organized/images")

# 查找新处理的图片
new_images = []
for img_path in images_dir.glob("*.jpg"):
    if "IMG_20260201" in img_path.name or "VID_20260201" in img_path.name:
        new_images.append(img_path)

print(f"找到 {len(new_images)} 张新图片，准备删除...")

# 删除
deleted_count = 0
for img_path in new_images:
    try:
        img_path.unlink()
        deleted_count += 1
    except Exception as e:
        print(f"删除失败 {img_path.name}: {e}")

print(f"已删除 {deleted_count} 张图片")
