"""
批量修改YOLO标签文件中的类别
将指定范围内的标签文件中的所有类别改为指定类别ID
"""

from pathlib import Path
import re


def update_label_class(file_path, new_class_id=3):
    """
    修改标签文件中的类别ID
    
    Args:
        file_path: 标签文件路径
        new_class_id: 新的类别ID
    """
    file_path = Path(file_path)
    
    if not file_path.exists():
        print(f"[ERROR] 文件不存在: {file_path}")
        return False
    
    # 读取文件内容
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    except Exception as e:
        print(f"[ERROR] 读取文件失败 {file_path}: {e}")
        return False
    
    # 修改每行的类别ID
    modified_lines = []
    modified_count = 0
    
    for line in lines:
        line = line.strip()
        if not line:
            modified_lines.append('')
            continue
        
        # YOLO格式: class_id x y w h
        parts = line.split()
        if len(parts) >= 5:
            old_class_id = parts[0]
            # 替换类别ID
            new_line = f"{new_class_id} {' '.join(parts[1:])}"
            modified_lines.append(new_line)
            if old_class_id != str(new_class_id):
                modified_count += 1
        else:
            # 如果格式不对，保持原样
            modified_lines.append(line)
    
    # 写回文件
    try:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(modified_lines))
            if modified_lines and modified_lines[-1]:  # 如果最后一行不是空的，添加换行
                f.write('\n')
        
        if modified_count > 0:
            print(f"[OK] {file_path.name}: 修改了 {modified_count} 个类别ID")
        else:
            print(f"[SKIP] {file_path.name}: 无需修改（已经是类别{new_class_id}）")
        return True
    except Exception as e:
        print(f"[ERROR] 写入文件失败 {file_path}: {e}")
        return False


def find_label_files(base_name, start_num, end_num, labels_dir='dataset_organized/labels'):
    """
    查找指定范围内的标签文件
    
    Args:
        base_name: 文件名基础部分（如：VID_20260202_095859_frame_）
        start_num: 起始编号（如：0）
        end_num: 结束编号（如：4380）
        labels_dir: 标签目录
    """
    labels_path = Path(labels_dir)
    if not labels_path.exists():
        print(f"[ERROR] 标签目录不存在: {labels_dir}")
        return []
    
    found_files = []
    
    # 先查找所有匹配基础名称的文件
    all_matching = list(labels_path.glob(f"{base_name}*.txt"))
    
    # 提取文件中的编号并过滤
    for file_path in all_matching:
        # 从文件名中提取编号
        # 格式1：VID_20260201_141412_frame_0017_0054.txt -> 提取0017
        # 格式2：VID_20260202_095859_frame_000000.txt -> 提取000000
        # 匹配 base_name 后面的数字（可能是3-6位）
        match = re.search(rf"{re.escape(base_name)}(\d{{3,6}})", file_path.name)
        if match:
            file_num = int(match.group(1))
            if start_num <= file_num <= end_num:
                found_files.append(file_path)
    
    return sorted(found_files)


def main():
    """主函数"""
    print("=" * 80)
    print("批量修改YOLO标签文件中的类别")
    print("=" * 80)
    
    # 从文件名提取信息
    # VID_20260202_095859_frame_000000.txt 到 VID_20260202_095859_frame_004380.txt
    base_name = "VID_20260202_095859_frame_"
    start_num = 0
    end_num = 4380
    new_class_id = 21
    
    print(f"\n配置:")
    print(f"  文件名基础: {base_name}")
    print(f"  起始编号: {start_num:04d}")
    print(f"  结束编号: {end_num:04d}")
    print(f"  新类别ID: {new_class_id}")
    print(f"  标签目录: dataset_organized/labels")
    
    # 查找文件
    print(f"\n查找标签文件...")
    label_files = find_label_files(base_name, start_num, end_num)
    
    if not label_files:
        print(f"\n[ERROR] 未找到匹配的标签文件")
        print(f"  查找模式: {base_name}{start_num:04d}_*.txt 到 {base_name}{end_num:04d}_*.txt")
        return
    
    print(f"\n找到 {len(label_files)} 个标签文件:")
    for i, file_path in enumerate(label_files[:10], 1):  # 只显示前10个
        print(f"  {i}. {file_path.name}")
    if len(label_files) > 10:
        print(f"  ... 还有 {len(label_files) - 10} 个文件")
    
    # 确认（非交互模式，直接执行）
    print(f"\n将把所有文件中的类别ID改为 {new_class_id}")
    print("开始修改...")
    
    # 批量修改
    print(f"\n开始修改...")
    success_count = 0
    for file_path in label_files:
        if update_label_class(file_path, new_class_id):
            success_count += 1
    
    print("\n" + "=" * 80)
    print("完成！")
    print("=" * 80)
    print(f"成功修改: {success_count}/{len(label_files)} 个文件")
    print(f"所有类别ID已改为: {new_class_id}")


if __name__ == '__main__':
    main()
