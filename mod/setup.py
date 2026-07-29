#!/usr/bin/env python3
"""MC Bridge Fabric Mod — 构建脚本 (Minecraft 1.21.1 + Fabric)"""
import os
import sys
import subprocess
import urllib.request
import re

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
WRAPPER_DIR = os.path.join(PROJECT_DIR, "gradle", "wrapper")
WRAPPER_JAR = os.path.join(WRAPPER_DIR, "gradle-wrapper.jar")
GRADLEW_BAT = os.path.join(PROJECT_DIR, "gradlew.bat")
GRADLE_VERSION = "8.11"


def info(msg):
    print(f"  [*] {msg}")


def success(msg):
    print(f"  [OK] {msg}")


def fail(msg):
    print(f"  [FAIL] {msg}")
    sys.exit(1)


def find_best_java():
    """Find a Java 21+ installation, preferring Minecraft's bundled JDK."""
    candidates = []

    minecraft_runtimes = [
        os.path.expandvars(r"%APPDATA%\.minecraft\runtime"),
    ]
    for rt in minecraft_runtimes:
        if os.path.isdir(rt):
            for name in os.listdir(rt):
                java_exe = os.path.join(rt, name, "bin", "java.exe")
                if os.path.exists(java_exe):
                    candidates.append(("Minecraft bundled", java_exe))

    java_home = os.environ.get("JAVA_HOME", "")
    if java_home:
        java_exe = os.path.join(java_home, "bin", "java.exe")
        if os.path.exists(java_exe):
            candidates.append(("JAVA_HOME", java_exe))

    for base in [
        r"C:\Program Files\Eclipse Adoptium",
        r"C:\Program Files\Java",
        r"C:\Program Files\Microsoft",
    ]:
        if os.path.isdir(base):
            for name in os.listdir(base):
                java_exe = os.path.join(base, name, "bin", "java.exe")
                if os.path.exists(java_exe):
                    candidates.append(("System install", java_exe))

    candidates.append(("PATH", "java"))

    for source, java_path in candidates:
        try:
            result = subprocess.run(
                [java_path, "-version"],
                capture_output=True, text=True, timeout=10,
            )
            ver_out = result.stderr + result.stdout
            m = re.search(r'version\s+"(\d+)', ver_out)
            if m:
                major = int(m.group(1))
                if major >= 21:
                    return java_path, major, source
        except Exception:
            continue

    return None, 0, ""


def download_gradle_wrapper():
    """Download gradle-wrapper.jar (small ~55KB file)."""
    os.makedirs(WRAPPER_DIR, exist_ok=True)

    if os.path.exists(WRAPPER_JAR) and os.path.getsize(WRAPPER_JAR) > 20000:
        success(f"gradle-wrapper.jar 已存在 ({os.path.getsize(WRAPPER_JAR):,} bytes)")
        return

    urls = [
        f"https://cdn.jsdelivr.net/gh/gradle/gradle@v{GRADLE_VERSION}.0/gradle/wrapper/gradle-wrapper.jar",
        f"https://raw.fastgit.org/gradle/gradle/v{GRADLE_VERSION}.0/gradle/wrapper/gradle-wrapper.jar",
        f"https://raw.githubusercontent.com/gradle/gradle/v{GRADLE_VERSION}.0/gradle/wrapper/gradle-wrapper.jar",
    ]

    for url in urls:
        host = url.split("/")[2]
        try:
            info(f"下载 gradle-wrapper.jar ({host}) ...")
            req = urllib.request.Request(url, headers={
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
            })
            with urllib.request.urlopen(req, timeout=30) as resp:
                with open(WRAPPER_JAR, "wb") as f:
                    f.write(resp.read())
            size = os.path.getsize(WRAPPER_JAR)
            if size < 20000:
                os.remove(WRAPPER_JAR)
                info(f"  文件太小 ({size} bytes)，重试...")
                continue
            success(f"下载完成 ({size:,} bytes)")
            return
        except Exception as e:
            info(f"  {host}: {e}")

    fail("所有源下载均失败。请手动下载放到:\n  " + WRAPPER_JAR)


# ── Main ────────────────────────────────────────────────────────
print("=" * 55)
print("  MC Bridge Fabric Mod — 构建脚本")
print(f"  项目: {PROJECT_DIR}")
print("=" * 55)
print()

info("查找 Java 21+ ...")
java_bin, java_major, java_source = find_best_java()

if not java_bin or java_major < 21:
    if java_major > 0:
        info(f"当前 Java {java_major} 不满足要求 (需 21+)")
    info("安装 JDK 21: https://adoptium.net/download/")
    info("或安装 Minecraft 官方启动器 (自带 JDK)")
    fail("未找到 Java 21+")

java_home_dir = os.path.dirname(os.path.dirname(os.path.abspath(java_bin)))
os.environ["JAVA_HOME"] = java_home_dir
success(f"Java {java_major} ({java_source}) — {java_home_dir}")

# ── Gradle Wrapper ──────────────────────────────────────────────
print()
info("准备 Gradle Wrapper ...")
download_gradle_wrapper()

# Update properties file
props_path = os.path.join(WRAPPER_DIR, "gradle-wrapper.properties")
with open(props_path, "w") as f:
    f.write("distributionBase=GRADLE_USER_HOME\n")
    f.write("distributionPath=wrapper/dists\n")
    f.write(f"distributionUrl=https\\://services.gradle.org/distributions/gradle-{GRADLE_VERSION}-bin.zip\n")
    f.write("networkTimeout=10000\n")
    f.write("validateDistributionUrl=true\n")
    f.write("zipStoreBase=GRADLE_USER_HOME\n")
    f.write("zipStorePath=wrapper/dists\n")

# Write gradlew.bat
gradlew_content = (
    '@@rem Gradle startup script for Windows\r\n'
    '@if "%DEBUG%"=="" @echo off\r\n'
    '@rem Set local scope for the variables with windows NT shell\r\n'
    'if "%OS%"=="Windows_NT" setlocal\r\n'
    'set DIRNAME=%~dp0\r\n'
    'if "%DIRNAME%"=="" set DIRNAME=.\r\n'
    'set APP_BASE_NAME=%~n0\r\n'
    'set APP_HOME=%DIRNAME%\r\n'
    'set APP_HOME=%APP_HOME:"=%\r\n'
    'set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"\r\n'
    'set CLASSPATH=%APP_HOME%\\gradle\\wrapper\\gradle-wrapper.jar\r\n'
    'java %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% '
    '"-Dorg.gradle.appname=gradlew" '
    '-classpath "%CLASSPATH%" '
    'org.gradle.wrapper.GradleWrapperMain %*\r\n'
)

with open(GRADLEW_BAT, "wb") as f:
    f.write(gradlew_content.encode("ascii"))
success("gradlew.bat 已就绪")

# ── Build ───────────────────────────────────────────────────────
print()
print("=== 编译 mod ===")
info(f"JAVA_HOME = {java_home_dir}")
info("gradlew build (首次约 3-10 分钟，需下载 Minecraft 依赖)")

try:
    subprocess.run(
        [GRADLEW_BAT, "build"],
        cwd=PROJECT_DIR,
        check=True,
        timeout=900,
        env={**os.environ, "JAVA_HOME": java_home_dir},
    )
except subprocess.TimeoutExpired:
    fail("构建超时 (15 分钟)，请检查网络")
except subprocess.CalledProcessError as e:
    fail(f"构建失败 (exit {e.returncode})，查看上方日志")

# ── Done ────────────────────────────────────────────────────────
print()
jar_path = os.path.join(PROJECT_DIR, "build", "libs", "mc-bridge-1.0.0.jar")
if os.path.exists(jar_path):
    size = os.path.getsize(jar_path)
    mods_dir = os.path.expandvars(r"%APPDATA%\.minecraft\mods")
    print("=" * 55)
    print(f"  BUILD SUCCESS")
    print(f"  {jar_path}")
    print(f"  {size:,} bytes")
    print()
    print("  下一步:")
    print(f"  1. 复制 JAR 到 {mods_dir}")
    print(f"  2. 用 Fabric 1.21.1 启动 Minecraft")
    print(f"  3. 进世界后桥自动启动在 ws://127.0.0.1:25575")
    print("=" * 55)
else:
    fail("未找到构建产物 JAR")
