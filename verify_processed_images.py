#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验证处理后的图片
"""
from pathlib import Path
from PIL import Image

images_dir = Path("dataset_organized/images")

# 统计所有图片
all_images = list(images_dir.glob("*.jpg"))
print(f"总图片数: {len(all_images)}")

# 统计新处理的图片
new_images = [img for img in all_images if "IMG_20260201" in img.name or "VID_20260201" in img.name]
print(f"新处理的图片数: {len(new_images)}")

# 检查分辨率
if new_images:
    print("\n检查前5张新图片的分辨率:")
    for img_path in new_images[:5]:
        try:
            img = Image.open(img_path)
            print(f"  {img_path.name}: {img.width}x{img.height}")
            img.close()
        except Exception as e:
            print(f"  {img_path.name}: 错误 - {e}")

# 统计视频帧
video_frames = [img for img in new_images if "_frame_" in img.name]
print(f"\n从视频提取的帧数: {len(video_frames)}")

# 统计原始图片
original_images = [img for img in new_images if "IMG_" in img.name and "_frame_" not in img.name]
print(f"原始图片数: {len(original_images)}")
