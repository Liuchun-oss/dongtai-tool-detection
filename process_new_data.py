#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
处理new文件夹中的图片和视频
1. 提取图片到dataset_organized/images
2. 从视频导帧，也放到dataset_organized/images
3. 统一分辨率到1280*960或960*1280（保持宽高比）
"""

import os
import shutil
from pathlib import Path
from PIL import Image
import cv2
from tqdm import tqdm

def ensure_dir(path):
    """确保目录存在"""
    Path(path).mkdir(parents=True, exist_ok=True)

def resize_image_keep_aspect(image_input, target_sizes=[(1280, 960), (960, 1280)]):
    """
    调整图片分辨率，保持宽高比，适配到目标尺寸
    
    Args:
        image_input: 图片路径（字符串）或PIL Image对象
        target_sizes: 目标尺寸列表，优先使用最接近的尺寸
    Returns:
        调整后的图片对象
    """
    # 如果是字符串，打开图片；如果已经是Image对象，直接使用
    if isinstance(image_input, str) or isinstance(image_input, Path):
        img = Image.open(image_input)
    else:
        img = image_input
    original_width, original_height = img.size
    original_aspect = original_width / original_height
    
    # 选择最接近的目标尺寸
    best_size = None
    best_diff = float('inf')
    
    for target_width, target_height in target_sizes:
        target_aspect = target_width / target_height
        diff = abs(original_aspect - target_aspect)
        if diff < best_diff:
            best_diff = diff
            best_size = (target_width, target_height)
    
    target_width, target_height = best_size
    
    # 计算缩放比例（保持宽高比）
    scale = min(target_width / original_width, target_height / original_height)
    new_width = int(original_width * scale)
    new_height = int(original_height * scale)
    
    # 缩放图片
    resized_img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
    
    # 创建目标尺寸的画布（白色背景）
    canvas = Image.new('RGB', (target_width, target_height), (255, 255, 255))
    
    # 计算居中位置
    x_offset = (target_width - new_width) // 2
    y_offset = (target_height - new_height) // 2
    
    # 将缩放后的图片粘贴到画布中心
    canvas.paste(resized_img, (x_offset, y_offset))
    
    return canvas

def process_image(image_path, output_dir, counter):
    """
    处理单张图片
    
    Args:
        image_path: 源图片路径
        output_dir: 输出目录
        counter: 计数器（用于生成唯一文件名）
    Returns:
        处理后的图片路径
    """
    try:
        # 调整分辨率
        resized_img = resize_image_keep_aspect(image_path)
        
        # 生成输出文件名（保持原文件名）
        original_name = Path(image_path).stem
        output_path = output_dir / f"{original_name}.jpg"
        
        # 如果文件已存在，添加序号（避免覆盖）
        if output_path.exists():
            output_path = output_dir / f"{original_name}_{counter:04d}.jpg"
        
        # 保存图片
        resized_img.save(output_path, quality=95, optimize=True)
        
        return output_path
    except Exception as e:
        print(f"处理图片 {image_path} 失败: {e}")
        return None

def extract_frames_from_video(video_path, output_dir, frame_interval=25, counter=0):
    """
    从视频中提取帧
    
    Args:
        video_path: 视频文件路径
        output_dir: 输出目录
        frame_interval: 每隔多少帧提取一张（默认25帧）
        counter: 计数器（用于生成唯一文件名）
    Returns:
        提取的帧图片路径列表
    """
    frame_paths = []
    
    try:
        # 打开视频
        cap = cv2.VideoCapture(str(video_path))
        if not cap.isOpened():
            print(f"无法打开视频: {video_path}")
            return frame_paths
        
        # 获取视频信息
        video_fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        
        # 生成输出文件名前缀
        video_name = Path(video_path).stem
        
        frame_count = 0
        saved_count = 0
        
        print(f"  视频信息: {total_frames}帧, {video_fps:.2f}fps, 提取间隔: 每{frame_interval}帧")
        
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            # 每隔frame_interval帧提取一帧
            if frame_count % frame_interval == 0:
                # 转换为PIL Image
                frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                pil_image = Image.fromarray(frame_rgb)
                
                # 调整分辨率
                resized_img = resize_image_keep_aspect(pil_image)
                
                # 生成输出文件名
                output_path = output_dir / f"{video_name}_frame_{saved_count:04d}.jpg"
                
                # 如果文件已存在，添加计数器
                if output_path.exists():
                    output_path = output_dir / f"{video_name}_frame_{saved_count:04d}_{counter:04d}.jpg"
                
                # 保存图片
                resized_img.save(output_path, quality=95, optimize=True)
                frame_paths.append(output_path)
                saved_count += 1
            
            frame_count += 1
        
        cap.release()
        print(f"  提取了 {saved_count} 帧")
        
    except Exception as e:
        print(f"处理视频 {video_path} 失败: {e}")
    
    return frame_paths

def main():
    print("=" * 80)
    print("处理new文件夹中的图片和视频")
    print("=" * 80)
    
    # 路径设置
    new_dir = Path("new")
    output_dir = Path("dataset_organized/images")
    
    # 确保输出目录存在
    ensure_dir(output_dir)
    
    if not new_dir.exists():
        print(f"new文件夹不存在: {new_dir}")
        return
    
    # 统计信息
    image_count = 0
    video_count = 0
    frame_count = 0
    
    # 处理图片
    print("\n" + "=" * 80)
    print("步骤1: 处理图片文件")
    print("=" * 80)
    
    image_files = list(new_dir.glob("*.jpg")) + list(new_dir.glob("*.JPG")) + \
                  list(new_dir.glob("*.jpeg")) + list(new_dir.glob("*.JPEG")) + \
                  list(new_dir.glob("*.png")) + list(new_dir.glob("*.PNG"))
    
    if image_files:
        print(f"找到 {len(image_files)} 张图片")
        for i, image_path in enumerate(tqdm(image_files, desc="处理图片")):
            result = process_image(image_path, output_dir, i)
            if result:
                image_count += 1
        print(f"成功处理 {image_count} 张图片")
    else:
        print("未找到图片文件")
    
    # 处理视频
    print("\n" + "=" * 80)
    print("步骤2: 从视频提取帧")
    print("=" * 80)
    
    video_files = list(new_dir.glob("*.mp4")) + list(new_dir.glob("*.MP4")) + \
                  list(new_dir.glob("*.avi")) + list(new_dir.glob("*.AVI")) + \
                  list(new_dir.glob("*.mov")) + list(new_dir.glob("*.MOV"))
    
    if video_files:
        print(f"找到 {len(video_files)} 个视频文件")
        for i, video_path in enumerate(tqdm(video_files, desc="处理视频")):
            print(f"\n处理视频 {i+1}/{len(video_files)}: {video_path.name}")
            frames = extract_frames_from_video(video_path, output_dir, frame_interval=25, counter=i)
            frame_count += len(frames)
            video_count += 1
        print(f"\n从 {video_count} 个视频中提取了 {frame_count} 帧")
    else:
        print("未找到视频文件")
    
    # 总结
    print("\n" + "=" * 80)
    print("处理完成！")
    print("=" * 80)
    print(f"处理结果:")
    print(f"  - 图片: {image_count} 张")
    print(f"  - 视频: {video_count} 个")
    print(f"  - 提取帧: {frame_count} 张")
    print(f"  - 总计: {image_count + frame_count} 张图片")
    print(f"\n所有图片已保存到: {output_dir.absolute()}")
    print(f"分辨率: 1280x960 或 960x1280（保持宽高比，居中放置）")
    
    # 显示一些示例文件
    if image_count + frame_count > 0:
        print(f"\n示例文件（前5个）:")
        all_files = sorted(output_dir.glob("*.jpg"))[:5]
        for f in all_files:
            print(f"  - {f.name}")

if __name__ == "__main__":
    main()
