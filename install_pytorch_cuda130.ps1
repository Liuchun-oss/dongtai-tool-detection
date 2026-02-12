# 安装支持CUDA的最新PyTorch版本（RTX 5090支持）
# 注意：PyTorch官方可能还没有CUDA 13.0的预编译版本，但CUDA 12.4/12.1通常向后兼容

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "安装支持CUDA的最新PyTorch版本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 激活conda环境
Write-Host "激活conda pytorch环境..." -ForegroundColor Yellow
conda activate pytorch

Write-Host ""
Write-Host "检查当前PyTorch版本..." -ForegroundColor Yellow
python -c "import torch; print(f'当前PyTorch版本: {torch.__version__}'); print(f'CUDA可用: {torch.cuda.is_available()}'); print(f'CUDA版本: {torch.version.cuda if torch.cuda.is_available() else \"N/A\"}')"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "步骤1: 卸载旧版本PyTorch" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 卸载旧版本
Write-Host "正在卸载旧版本..." -ForegroundColor Yellow
conda remove pytorch torchvision torchaudio pytorch-cuda -y
pip uninstall torch torchvision torchaudio -y

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "步骤2: 安装最新版本PyTorch (支持CUDA 12.4)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "注意: PyTorch官方最新稳定版支持CUDA 12.1/12.4" -ForegroundColor Yellow
Write-Host "RTX 5090需要PyTorch 2.6+版本才能完全支持sm_120" -ForegroundColor Yellow
Write-Host ""

# 方法1: 使用conda安装（推荐）
Write-Host "方法1: 使用conda安装..." -ForegroundColor Yellow
Write-Host "执行命令: conda install pytorch torchvision torchaudio pytorch-cuda=12.4 -c pytorch -c nvidia -y" -ForegroundColor Gray
$choice1 = Read-Host "是否使用conda安装? (y/n) [y]"
if ($choice1 -ne "n") {
    conda install pytorch torchvision torchaudio pytorch-cuda=12.4 -c pytorch -c nvidia -y
}

# 如果conda安装失败，使用pip安装
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "conda安装失败，尝试使用pip安装..." -ForegroundColor Yellow
    Write-Host "执行命令: pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124" -ForegroundColor Gray
    $choice2 = Read-Host "是否使用pip安装? (y/n) [y]"
    if ($choice2 -ne "n") {
        pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "步骤3: 验证安装" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 验证安装
Write-Host "验证PyTorch安装..." -ForegroundColor Yellow
python -c @"
import torch
print('=' * 60)
print('PyTorch安装信息:')
print('=' * 60)
print(f'PyTorch版本: {torch.__version__}')
print(f'CUDA可用: {torch.cuda.is_available()}')
if torch.cuda.is_available():
    print(f'CUDA版本: {torch.version.cuda}')
    print(f'GPU设备: {torch.cuda.get_device_name(0)}')
    print(f'GPU数量: {torch.cuda.device_count()}')
    print(f'显存容量: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB')
    
    # 测试CUDA是否可用
    try:
        test_tensor = torch.zeros(1).cuda()
        del test_tensor
        torch.cuda.empty_cache()
        print('✓ CUDA测试通过！')
    except RuntimeError as e:
        print(f'✗ CUDA测试失败: {e}')
else:
    print('⚠ CUDA不可用')
print('=' * 60)
"@

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "安装完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "如果CUDA测试失败，可能需要:" -ForegroundColor Yellow
Write-Host "1. 重启Python环境" -ForegroundColor Yellow
Write-Host "2. 检查CUDA驱动版本 (nvidia-smi)" -ForegroundColor Yellow
Write-Host "3. 等待PyTorch官方发布支持sm_120的版本" -ForegroundColor Yellow
Write-Host ""
Write-Host "现在可以运行训练脚本: python train_model_new.py" -ForegroundColor Green
