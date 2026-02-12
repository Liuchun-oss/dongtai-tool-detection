"""
从视频中提取帧图片
每20帧提取一张图片，统一分辨率为1280*960或960*1280
"""

import cv2
import os
from pathlib import Path
import argparse


def get_video_info(video_path):
    """获取视频信息"""
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        return None
    
    fps = cap.get(cv2.CAP_PROP_FPS)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    
    cap.release()
    return {
        'fps': fps,
        'frame_count': frame_count,
        'width': width,
        'height': height
    }


def determine_target_size(original_width, original_height):
    """根据原始宽高比确定目标分辨率"""
    aspect_ratio = original_width / original_height
    
    # 如果宽大于高（横屏），使用1280*960
    # 如果高大于宽（竖屏），使用960*1280
    if aspect_ratio >= 1.0:
        # 横屏：1280*960
        return (1280, 960)
    else:
        # 竖屏：960*1280
        return (960, 1280)


def extract_frames(video_path, output_dir, frame_interval=20):
    """
    从视频中提取帧
    
    Args:
        video_path: 视频文件路径
        output_dir: 输出目录
        frame_interval: 每隔多少帧提取一张（默认20帧）
    """
    video_path = Path(video_path)
    output_dir = Path(output_dir)
    
    if not video_path.exists():
        print(f"✗ 视频文件不存在: {video_path}")
        return 0
    
    # 创建输出目录
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # 获取视频信息
    print(f"\n处理视频: {video_path.name}")
    video_info = get_video_info(video_path)
    if video_info is None:
        print(f"✗ 无法打开视频文件: {video_path}")
        return 0
    
    print(f"  视频信息:")
    print(f"    分辨率: {video_info['width']}x{video_info['height']}")
    print(f"    帧率: {video_info['fps']:.2f} fps")
    print(f"    总帧数: {video_info['frame_count']}")
    
    # 确定目标分辨率
    target_size = determine_target_size(video_info['width'], video_info['height'])
    print(f"    目标分辨率: {target_size[0]}x{target_size[1]}")
    
    # 打开视频
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        print(f"✗ 无法打开视频文件: {video_path}")
        return 0
    
    # 生成输出文件名前缀（使用视频文件名，不含扩展名）
    video_stem = video_path.stem
    
    # 检查是否已有同名视频提取的图片，避免重复
    existing_files = set()
    if output_dir.exists():
        for existing_file in output_dir.glob(f"{video_stem}_frame_*.jpg"):
            existing_files.add(existing_file.name)
    
    if existing_files:
        print(f"  警告: 发现 {len(existing_files)} 张已存在的图片（{video_stem}_frame_*.jpg）")
        choice = input(f"  是否跳过此视频? (y/n) [n]: ").strip().lower()
        if choice == 'y':
            print(f"  跳过视频: {video_path.name}")
            return 0
    
    frame_index = 0
    saved_count = 0
    frame_number = 0
    
    print(f"\n开始提取帧（每{frame_interval}帧提取一张）...")
    
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        
        # 每frame_interval帧提取一张
        if frame_number % frame_interval == 0:
            # 调整分辨率
            resized_frame = cv2.resize(frame, target_size, interpolation=cv2.INTER_LINEAR)
            
            # 生成输出文件名（使用视频文件名+时间戳避免重复）
            output_filename = f"{video_stem}_frame_{frame_number:06d}.jpg"
            output_path = output_dir / output_filename
            
            # 如果文件已存在，跳过（避免重复）
            if output_path.exists():
                print(f"  跳过已存在的文件: {output_filename}")
                frame_number += 1
                continue
            
            # 保存图片
            cv2.imwrite(str(output_path), resized_frame, [cv2.IMWRITE_JPEG_QUALITY, 95])
            saved_count += 1
            
            if saved_count % 10 == 0:
                print(f"  已保存 {saved_count} 张图片...")
        
        frame_number += 1
    
    cap.release()
    
    print(f"\n✓ 完成！共提取 {saved_count} 张图片")
    print(f"  保存位置: {output_dir}")
    
    return saved_count


def main():
    """主函数"""
    print("=" * 80)
    print("从视频中提取帧图片")
    print("=" * 80)
    print("\n说明:")
    print("  - 每20帧提取一张图片")
    print("  - 分辨率统一为1280*960（横屏）或960*1280（竖屏）")
    print("  - 保存到dataset_organized/images目录")
    print("=" * 80)
    
    # 查找new目录
    new_dir = Path('new')
    if not new_dir.exists():
        print(f"\n✗ new目录不存在: {new_dir}")
        print("请确保new目录存在，并将视频文件放在其中")
        return
    
    # 查找视频文件（使用文件名去重，避免重复处理）
    video_extensions = ['.mp4', '.avi', '.mov', '.mkv', '.flv', '.wmv', '.MP4', '.AVI', '.MOV', '.MKV']
    video_files_dict = {}  # 使用字典去重，key为文件名（小写），value为路径
    
    for ext in video_extensions:
        for video_file in new_dir.glob(f'*{ext}'):
            # 使用文件名（小写）作为key去重（避免大小写不同导致的重复）
            file_name_lower = video_file.name.lower()
            if file_name_lower not in video_files_dict:
                video_files_dict[file_name_lower] = video_file
    
    video_files = list(video_files_dict.values())
    
    if not video_files:
        print(f"\n✗ 在new目录中未找到视频文件")
        print(f"支持的格式: {', '.join(video_extensions)}")
        return
    
    print(f"\n找到 {len(video_files)} 个视频文件（已去重）:")
    for i, video_file in enumerate(video_files, 1):
        print(f"  {i}. {video_file.name}")
    
    # 输出目录
    output_dir = Path('dataset_organized/images')
    
    # 处理每个视频
    total_saved = 0
    for video_file in video_files:
        saved_count = extract_frames(
            video_path=video_file,
            output_dir=output_dir,
            frame_interval=20
        )
        total_saved += saved_count
    
    print("\n" + "=" * 80)
    print("全部完成！")
    print("=" * 80)
    print(f"共处理 {len(video_files)} 个视频")
    print(f"共提取 {total_saved} 张图片")
    print(f"保存位置: {output_dir}")
    print("\n提示:")
    print("  - 提取的图片已保存到dataset_organized/images目录")
    print("  - 现在可以对图片进行标注")
    print("  - 标注完成后，运行训练脚本会自动使用这些图片")


if __name__ == '__main__':
    main()
