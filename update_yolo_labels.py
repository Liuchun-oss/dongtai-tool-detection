#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将VID_20260201_134806_frame_0000到VID_20260201_134806_frame_0100_0036的YOLO标签文件的类别改成17
"""
from pathlib import Path

labels_dir = Path("dataset_organized/labels")
target_class = 17

# 生成要处理的文件名列表
files_to_update = []
for i in range(101):  # 0000到0100，共101个文件
    frame_num = f"{i:04d}"  # 格式化为4位数字，如0000, 0001, ..., 0100
    filename = f"VID_20260201_134806_frame_{frame_num}_0036.txt"
    file_path = labels_dir / filename
    if file_path.exists():
        files_to_update.append(file_path)
    else:
        print(f"警告: 文件不存在 {filename}")

print(f"找到 {len(files_to_update)} 个标签文件，准备更新类别为 {target_class}...")

# 更新每个文件
updated_count = 0
for file_path in files_to_update:
    try:
        # 读取文件内容
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        # 更新每一行的类别ID
        new_lines = []
        for line in lines:
            line = line.strip()
            if line:  # 跳过空行
                parts = line.split()
                if len(parts) >= 5:  # 确保格式正确: class_id x y w h
                    # 将第一列（类别ID）改为目标类别
                    new_line = f"{target_class} {' '.join(parts[1:])}\n"
                    new_lines.append(new_line)
                else:
                    # 如果格式不对，保留原行
                    new_lines.append(line + '\n')
        
        # 写回文件
        with open(file_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        
        updated_count += 1
        if updated_count % 10 == 0:
            print(f"已更新 {updated_count} 个文件...")
    
    except Exception as e:
        print(f"更新失败 {file_path.name}: {e}")

print(f"完成！已更新 {updated_count} 个标签文件的类别为 {target_class}")
