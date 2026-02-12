"""
更新数据集配置文件中的类别名称
根据实际的工具类型修改类别名称
"""

def update_class_names():
    """交互式更新类别名称"""
    
    print("=" * 60)
    print("数据集类别名称配置")
    print("=" * 60)
    print()
    print("当前您的数据集有24个类别（A01-A24）")
    print("请为每个类别指定实际的工具名称")
    print()
    print("提示：如果您不确定某个类别对应的工具，可以：")
    print("  1. 查看 data/A0X/A0X/ 目录下的图片")
    print("  2. 暂时保持默认名称（A01-A24），稍后再修改")
    print()
    
    choice = input("是否现在配置类别名称？(y/n) [y]: ").strip().lower()
    
    if choice == 'n':
        print("\n保持默认名称（A01-A24）")
        print("您可以稍后手动编辑 dataset_organized/data.yaml 文件")
        return
    
    print("\n" + "=" * 60)
    print("开始配置类别名称")
    print("=" * 60)
    print("提示：直接按回车保持默认名称")
    print()
    
    class_names = {}
    for i in range(24):
        default_name = f"A{i+1:02d}"
        
        # 显示对应的原始数据目录
        print(f"类别 {i}: {default_name}")
        print(f"  参考目录: data/{default_name}/{default_name}/")
        
        new_name = input(f"  请输入新名称 [默认 {default_name}]: ").strip()
        
        if new_name:
            class_names[i] = new_name
        else:
            class_names[i] = default_name
        
        print()
    
    # 生成新的data.yaml文件
    yaml_content = f"""# 铁路工具智能清点系统 - 数据集配置文件
# 更新时间: 2026-01-22

# 数据集路径 (相对路径或绝对路径)
path: D:\\bianfa_APP\\dongtai\\dongtai2\\dataset_organized
train: images/train
val: images/val
test: images/test

# 类别数量
nc: 24

# 类别名称
names:
"""
    
    for i in range(24):
        yaml_content += f"  {i}: {class_names[i]}\n"
    
    # 保存配置文件
    with open('dataset_organized/data.yaml', 'w', encoding='utf-8') as f:
        f.write(yaml_content)
    
    print("=" * 60)
    print("配置已保存到: dataset_organized/data.yaml")
    print("=" * 60)
    print("\n更新后的类别名称:")
    for i, name in class_names.items():
        print(f"  {i}: {name}")


def show_example_config():
    """显示配置示例"""
    
    print("\n" + "=" * 60)
    print("配置示例")
    print("=" * 60)
    print()
    print("如果您的数据集包含以下工具：")
    print()
    print("示例配置：")
    print("  0: 大扳手")
    print("  1: 活口扳手")
    print("  2: 尖嘴钳")
    print("  3: 钢丝钳")
    print("  4: 一字螺丝刀")
    print("  5: 十字螺丝刀")
    print("  6: 风机")
    print("  7: 防护红旗")
    print("  8: 喷壶")
    print("  9: 对讲机")
    print("  10: 万用表")
    print("  11: 内六角扳手")
    print("  12-23: 其他工具...")
    print()


if __name__ == '__main__':
    show_example_config()
    update_class_names()
    
    print("\n下一步：运行 python train_model.py 开始训练")
