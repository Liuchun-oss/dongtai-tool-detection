@echo off
chcp 65001 >nul
echo ================================================================================
echo 铁路工具智能清点系统 - 一键启动训练
echo ================================================================================
echo.
echo 配置信息:
echo   - GPU: RTX 5090 32G
echo   - 模型: YOLOv8s
echo   - 批次大小: 64
echo   - 训练轮数: 200
echo   - 预计时间: 1.5-3小时
echo.
echo 优化策略:
echo   - 针对外观相似工具优化
echo   - 增强颜色和尺度数据增强
echo   - 自动导出TFLite模型（Android部署）
echo   - 训练完成自动生成报告
echo.
echo ================================================================================
echo 提示:
echo   - 训练过程中请勿关闭此窗口
echo   - 训练完成后会自动生成 "训练完成报告.txt"
echo   - TFLite模型会保存在 runs/detect/railway_tools_optimized/weights/
echo ================================================================================
echo.
echo 正在激活conda pytorch环境...
echo.

call conda activate pytorch

echo.
echo 正在启动训练...
echo.

python train_model_optimized.py

echo.
echo ================================================================================
echo 训练流程已完成！
echo ================================================================================
echo.
echo 查看结果:
echo   - 训练完成报告.txt
echo   - runs/detect/railway_tools_optimized/
echo.
echo Android部署模型:
echo   - runs/detect/railway_tools_optimized/weights/best.tflite
echo.
pause
