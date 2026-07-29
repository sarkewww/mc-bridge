@echo off
chcp 65001 >nul
echo ============================================
echo   MC Bridge Fabric Mod - 构建脚本
echo   Minecraft 1.21.1 + Fabric
echo ============================================
echo.

set "PROJECT_DIR=%~dp0"

echo [1/4] 检查 Java 21...
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%v
set JAVA_VER=%JAVA_VER:"=%
echo   检测到 Java: %JAVA_VER%

echo %JAVA_VER% | findstr /i "21\." >nul
if %ERRORLEVEL% NEQ 0 (
    echo   [WARN] 需要 Java 21! 如果编译失败请安装 JDK 21
    echo   [WARN] 下载: https://adoptium.net/download/
    echo.
)

echo [2/4] 生成 Gradle Wrapper...
set "WRAPPER_JAR=%PROJECT_DIR%gradle\wrapper\gradle-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
    echo   正在下载 gradle-wrapper.jar ...
    powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%WRAPPER_JAR%' }"
    if %ERRORLEVEL% NEQ 0 (
        echo   [FAIL] 下载 gradle-wrapper.jar 失败
        echo   请手动下载放到: gradle\wrapper\gradle-wrapper.jar
        echo   URL: https://services.gradle.org/distributions/gradle-8.10-bin.zip
        pause
        exit /b 1
    )
    echo   下载完成
) else (
    echo   gradle-wrapper.jar 已存在，跳过
)

echo [3/4] 生成 Gradle Wrapper 脚本...
set "WRAPPER_SCRIPT=%PROJECT_DIR%gradlew.bat"
if not exist "%WRAPPER_SCRIPT%" (
    echo @echo off > "%WRAPPER_SCRIPT%"
    echo setlocal >> "%WRAPPER_SCRIPT%"
    echo set DIRNAME=%%~dp0 >> "%WRAPPER_SCRIPT%"
    echo if "%%DIRNAME%%"=="" set DIRNAME=. >> "%WRAPPER_SCRIPT%"
    echo set APP_HOME=%%DIRNAME%% >> "%WRAPPER_SCRIPT%"
    echo set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m" >> "%WRAPPER_SCRIPT%"
    echo set CLASSPATH=%%APP_HOME%%\gradle\wrapper\gradle-wrapper.jar >> "%WRAPPER_SCRIPT%"
    echo java %%DEFAULT_JVM_OPTS%% %%JAVA_OPTS%% %%GRADLE_OPTS%% "-Dorg.gradle.appname=gradlew" -classpath "%%CLASSPATH%%" org.gradle.wrapper.GradleWrapperMain %%* >> "%WRAPPER_SCRIPT%"
    echo 已生成 gradlew.bat
) else (
    echo gradlew.bat 已存在，跳过
)

echo [4/4] 构建 mod...
call "%WRAPPER_SCRIPT%" build
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo   [FAIL] 构建失败!
    echo   请检查:
    echo   1. JDK 21 是否安装 (java -version 应显示 21.x)
    echo   2. 网络是否正常 (需要下载 Minecraft 依赖)
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================
echo   构建成功! JAR 文件位置:
echo   build\libs\mc-bridge-1.0.0.jar
echo.
echo   下一步:
echo   1. 复制这个 jar 到 .minecraft\mods\
echo   2. 用 Fabric 启动 Minecraft 1.21.1
echo   3. 进入世界后模组自动启动
echo ============================================
pause
