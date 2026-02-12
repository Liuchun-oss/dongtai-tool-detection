@echo off
chcp 65001 >nul
echo ================================================================================
echo 重新导出并部署兼容的ONNX模型
echo ================================================================================
echo.

echo [1/3] 导出兼容的ONNX模型...
python export_onnx_compatible.py
if errorlevel 1 (
    echo.
    echo ❌ 模型导出失败！
    echo 请确保已安装Python环境和ultralytics库
    pause
    exit /b 1
)

echo.
echo [2/3] 复制模型到Android项目...
copy /Y runs\detect\railway_tools_optimized\weights\best.onnx app\src\main\assets\best.onnx
if errorlevel 1 (
    echo.
    echo ❌ 复制模型失败！
    pause
    exit /b 1
)

echo.
echo [3/3] 验证模型文件...
if exist app\src\main\assets\best.onnx (
    for %%A in (app\src\main\assets\best.onnx) do (
        echo ✅ 模型文件已更新: %%~zA 字节
    )
) else (
    echo ❌ 模型文件不存在！
    pause
    exit /b 1
)

echo.
echo ================================================================================
echo ✅ 模型导出和部署完成！
echo ================================================================================
echo.
echo 下一步：
echo 1. 在Android Studio中重新构建APK
echo 2. 安装到设备测试
echo.
pause
