# 安装PyTorch CUDA 13.0版本
# 解决文件被占用的问题

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "安装PyTorch CUDA 13.0版本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否有Python进程在运行
Write-Host "检查是否有Python进程在运行..." -ForegroundColor Yellow
$pythonProcesses = Get-Process python -ErrorAction SilentlyContinue
if ($pythonProcesses) {
    Write-Host "发现以下Python进程:" -ForegroundColor Yellow
    $pythonProcesses | Format-Table Id, ProcessName, StartTime -AutoSize
    Write-Host ""
    $choice = Read-Host "是否关闭这些进程? (y/n) [y]"
    if ($choice -ne "n") {
        Write-Host "正在关闭Python进程..." -ForegroundColor Yellow
        $pythonProcesses | Stop-Process -Force
        Start-Sleep -Seconds 2
        Write-Host "进程已关闭" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "正在安装PyTorch CUDA 13.0..." -ForegroundColor Yellow
Write-Host "这可能需要几分钟时间..." -ForegroundColor Yellow
Write-Host ""

# 安装PyTorch CUDA 13.0
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu130

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "安装成功！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    
    # 验证安装
    Write-Host "验证安装..." -ForegroundColor Yellow
    python -c "import torch; print('PyTorch版本:', torch.__version__); print('CUDA可用:', torch.cuda.is_available()); print('CUDA版本:', torch.version.cuda if torch.cuda.is_available() else 'N/A'); print('GPU设备:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'N/A')"
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "安装失败" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "可能的解决方案:" -ForegroundColor Yellow
    Write-Host "1. 关闭所有Python进程和IDE" -ForegroundColor Yellow
    Write-Host "2. 重启终端后重试" -ForegroundColor Yellow
    Write-Host "3. 使用管理员权限运行" -ForegroundColor Yellow
}
