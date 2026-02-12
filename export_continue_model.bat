@echo off
echo ================================================================================
echo 导出继续训练的ONNX模型（用于Android部署）
echo ================================================================================

rem 激活conda环境
echo.
echo ✓ 正在激活pytorch环境...
call conda activate pytorch
if %errorlevel% neq 0 (
    echo ❌ 激活pytorch环境失败！请确保conda已安装且pytorch环境存在。
    goto :eof
)
echo ✓ 已激活pytorch环境
echo.

rem 运行Python脚本导出ONNX模型
python export_continue_model.py
if %errorlevel% neq 0 (
    echo ❌ Python脚本执行失败！
    goto :eof
)

echo.
echo ================================================================================
echo ✅ 完成！
echo ================================================================================
echo.
pause
