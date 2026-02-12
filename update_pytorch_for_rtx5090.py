"""
更新PyTorch以支持RTX 5090 GPU
RTX 5090需要CUDA capability sm_120，需要PyTorch 2.6+版本
"""

import subprocess
import sys

def check_pytorch_version():
    """检查当前PyTorch版本"""
    try:
        import torch
        print(f"当前PyTorch版本: {torch.__version__}")
        print(f"CUDA版本: {torch.version.cuda}")
        
        if torch.cuda.is_available():
            device_props = torch.cuda.get_device_properties(0)
            compute_capability = device_props.major * 10 + device_props.minor
            print(f"GPU: {torch.cuda.get_device_name(0)}")
            print(f"Compute Capability: sm_{compute_capability}")
            
            if compute_capability >= 120:
                print("\n⚠ RTX 5090需要PyTorch 2.6+版本")
                return False
            else:
                print("\n✓ GPU兼容")
                return True
        else:
            print("\n⚠ CUDA不可用")
            return False
    except ImportError:
        print("PyTorch未安装")
        return False

def update_pytorch():
    """更新PyTorch"""
    print("\n" + "=" * 80)
    print("更新PyTorch以支持RTX 5090")
    print("=" * 80)
    
    print("\n正在更新PyTorch...")
    print("这可能需要几分钟时间...")
    
    # 更新PyTorch（使用CUDA 12.1）
    commands = [
        [sys.executable, "-m", "pip", "install", "--upgrade", 
         "torch", "torchvision", "torchaudio", 
         "--index-url", "https://download.pytorch.org/whl/cu121"]
    ]
    
    for cmd in commands:
        print(f"\n执行: {' '.join(cmd)}")
        try:
            result = subprocess.run(cmd, check=True, capture_output=True, text=True)
            print(result.stdout)
            if result.stderr:
                print("警告:", result.stderr)
        except subprocess.CalledProcessError as e:
            print(f"错误: {e}")
            print(e.stdout)
            print(e.stderr)
            return False
    
    print("\n" + "=" * 80)
    print("更新完成！")
    print("=" * 80)
    
    # 重新检查版本
    print("\n验证安装...")
    try:
        import torch
        print(f"新版本: {torch.__version__}")
        
        if torch.cuda.is_available():
            try:
                # 测试CUDA是否可用
                test_tensor = torch.zeros(1).cuda()
                del test_tensor
                torch.cuda.empty_cache()
                print("✓ CUDA测试通过！")
                return True
            except RuntimeError as e:
                print(f"⚠ CUDA测试失败: {e}")
                print("\n可能需要:")
                print("1. 重启Python环境")
                print("2. 检查CUDA驱动版本")
                print("3. 等待PyTorch官方发布支持sm_120的版本")
                return False
        else:
            print("⚠ CUDA仍然不可用")
            return False
    except ImportError:
        print("✗ PyTorch导入失败")
        return False

def main():
    """主函数"""
    print("=" * 80)
    print("RTX 5090 PyTorch兼容性修复工具")
    print("=" * 80)
    
    # 检查当前版本
    is_compatible = check_pytorch_version()
    
    if is_compatible:
        print("\n✓ 当前PyTorch版本已兼容，无需更新")
        return
    
    print("\n需要更新PyTorch以支持RTX 5090")
    choice = input("\n是否现在更新? (y/n) [y]: ").strip().lower()
    
    if choice == 'n':
        print("\n取消更新")
        print("\n手动更新命令:")
        print("pip install --upgrade torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu121")
        return
    
    # 执行更新
    success = update_pytorch()
    
    if success:
        print("\n✅ 更新成功！现在可以运行训练脚本了")
    else:
        print("\n❌ 更新失败或仍有问题")
        print("\n备选方案:")
        print("1. 使用CPU训练（速度较慢）")
        print("2. 等待PyTorch官方发布支持sm_120的版本")
        print("3. 使用PyTorch nightly版本:")
        print("   pip install --pre torch torchvision torchaudio --index-url https://download.pytorch.org/whl/nightly/cu121")

if __name__ == '__main__':
    main()
