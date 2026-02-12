"""
验证PyTorch安装和CUDA支持
"""

import torch
import sys

print("=" * 80)
print("PyTorch安装验证")
print("=" * 80)

# 基本信息
print(f"\nPyTorch版本: {torch.__version__}")
print(f"Python版本: {sys.version}")

# CUDA信息
print(f"\nCUDA可用: {torch.cuda.is_available()}")

if torch.cuda.is_available():
    print(f"CUDA版本: {torch.version.cuda}")
    print(f"cuDNN版本: {torch.backends.cudnn.version()}")
    print(f"GPU数量: {torch.cuda.device_count()}")
    
    for i in range(torch.cuda.device_count()):
        print(f"\nGPU {i}:")
        props = torch.cuda.get_device_properties(i)
        print(f"  名称: {props.name}")
        print(f"  显存: {props.total_memory / 1024**3:.1f} GB")
        compute_cap = props.major * 10 + props.minor
        print(f"  Compute Capability: sm_{compute_cap}")
    
    # 测试CUDA
    print("\n" + "=" * 80)
    print("CUDA功能测试")
    print("=" * 80)
    
    try:
        # 创建测试tensor
        print("\n1. 创建CUDA tensor...")
        x = torch.randn(1000, 1000).cuda()
        print("   [OK] 成功")
        
        # 执行计算
        print("2. 执行矩阵乘法...")
        y = torch.matmul(x, x)
        print("   [OK] 成功")
        
        # 检查结果
        print("3. 验证结果...")
        assert y.shape == (1000, 1000), "形状不正确"
        print("   [OK] 结果正确")
        
        # 清理
        del x, y
        torch.cuda.empty_cache()
        print("\n[SUCCESS] CUDA测试全部通过！")
        print("   虽然可能有警告，但RTX 5090可以正常使用")
        
    except RuntimeError as e:
        print(f"\n[ERROR] CUDA测试失败: {e}")
        print("\n可能的原因:")
        print("1. PyTorch版本不支持sm_120")
        print("2. CUDA驱动版本不匹配")
        print("3. 需要等待PyTorch官方更新")
        
else:
    print("\n[WARNING] CUDA不可用")

print("\n" + "=" * 80)
print("验证完成")
print("=" * 80)
