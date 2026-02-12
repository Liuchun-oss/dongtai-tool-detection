@echo off
chcp 65001 >nul
echo 安装导出ONNX所需的依赖（CPU版本即可）
echo.

call conda activate pytorch
pip install onnxruntime --quiet

echo.
echo ✓ 依赖安装完成
echo.
pause
