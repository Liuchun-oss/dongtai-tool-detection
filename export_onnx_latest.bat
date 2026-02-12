@echo off
chcp 65001 >nul
echo ========================================================================
echo 导出最新训练的ONNX模型（用于Android部署）
echo ========================================================================
echo.

REM 激活pytorch conda环境
call conda activate pytorch
if errorlevel 1 (
    echo ❌ 无法激活pytorch环境，请检查conda环境
    pause
    exit /b 1
)

echo ✓ 已激活pytorch环境
echo.

REM 运行Python脚本
python export_onnx_latest.py

if errorlevel 1 (
    echo.
    echo ❌ 导出失败，请检查错误信息
    pause
    exit /b 1
)

echo.
echo ========================================================================
echo 自动复制文件到Android项目...
echo ========================================================================

REM 复制ONNX模型
if exist "runs\detect\runs\detect\railway_tools\weights\best.onnx" (
    copy /Y "runs\detect\runs\detect\railway_tools\weights\best.onnx" "app\src\main\assets\best.onnx"
    echo ✓ 已复制ONNX模型到 app\src\main\assets\best.onnx
) else if exist "runs\detect\railway_tools\weights\best.onnx" (
    copy /Y "runs\detect\railway_tools\weights\best.onnx" "app\src\main\assets\best.onnx"
    echo ✓ 已复制ONNX模型到 app\src\main\assets\best.onnx
) else (
    echo ⚠ 未找到ONNX模型文件，请手动复制
)

REM 复制类别文件
if exist "dataset_organized\classes.txt" (
    copy /Y "dataset_organized\classes.txt" "app\src\main\assets\classes.txt"
    echo ✓ 已复制类别文件到 app\src\main\assets\classes.txt
) else (
    echo ⚠ 未找到类别文件 dataset_organized\classes.txt
)

echo.
echo ========================================================================
echo ✅ 完成！
echo ========================================================================
echo.
echo 下一步：
echo 1. 重新构建APK: .\gradlew.bat assembleDebug
echo 2. 安装APK: adb install -r app\build\outputs\apk\debug\app-debug.apk
echo.
pause
