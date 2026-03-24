@echo off
REM ========================================
REM   AIGC GameFlow - 启动脚本 (Windows)
REM ========================================

REM 检查 .env 文件是否存在
if not exist ".env" (
    echo.
    echo [错误] 未找到 .env 文件！
    echo.
    echo 请先创建 .env 文件并配置 API Key:
    echo 1. 复制 .env.template 为 .env
    echo 2. 编辑 .env 文件，填入你的 DeepSeek API Key
    echo 3. 重新运行此脚本
    echo.
    pause
    exit /b 1
)

REM 从 .env 文件读取环境变量
for /f "delims=" %%a in ('findstr /R "^DEEPSEEK_API_KEY=" .env') do set "%%a"

echo ========================================
echo   AIGC GameFlow 启动中...
echo ========================================
echo.

REM 启动 Spring Boot 应用
mvnw.cmd spring-boot:run

pause
