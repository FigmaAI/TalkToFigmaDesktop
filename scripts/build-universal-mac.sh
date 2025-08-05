#!/bin/bash

# =================================================================
# 유니버설 macOS DMG 빌드 스크립트
# Apple Silicon(arm64)과 인텔(x86_64) 맥 모두에서 실행 가능한 범용 DMG 생성
# 
# Usage: ./scripts/build-universal-mac.sh
# =================================================================

set -e # Exit immediately if a command exits with a non-zero status.

# 이 스크립트는 Apple Silicon Mac에서 실행되어야 합니다
ARCH=$(uname -m)
if [ "$ARCH" != "arm64" ]; then
  echo "❌ 이 스크립트는 Apple Silicon Mac에서 실행되어야 합니다."
  exit 1
fi

# --- Configuration ---
APP_NAME="Cursor Talk to Figma desktop"
APP_VERSION=$(cd app && ../gradlew -q printVersion)
NATIVE_BUILD_DIR="app/build/compose/binaries/main"
INTEL_BUILD_DIR="app/build/compose/binaries/intel"
UNIVERSAL_BUILD_DIR="app/build/compose/binaries/universal"

echo "🖥️  유니버설 macOS DMG 빌드 시작"
echo "📋 Configuration:"
echo "   - App Version: $APP_VERSION"
echo "   - App Name: $APP_NAME"

# Rosetta 2 설치 확인
echo "🔍 Rosetta 2 설치 확인 중..."
if ! /usr/bin/pgrep -q oahd; then
    echo "⚠️ Rosetta 2가 설치되어 있지 않습니다. 설치를 진행합니다..."
    softwareupdate --install-rosetta --agree-to-license
else
    echo "✅ Rosetta 2가 정상적으로 설치되어 있습니다."
fi

# 인텔 JDK 다운로드 및 설정
echo "📥 인텔 JDK 준비 중..."
JDK_INSTALL_DIR="$HOME/.jdks"
mkdir -p "$JDK_INSTALL_DIR"

# 인텔 JDK 경로 설정
INTEL_JDK_VERSION="17.0.9"
INTEL_JDK_DIR="$JDK_INSTALL_DIR/jdk-$INTEL_JDK_VERSION-intel"

# JDK가 이미 다운로드 되어 있는지 확인
if [ -d "$INTEL_JDK_DIR" ]; then
    echo "✅ 인텔 JDK가 이미 설치되어 있습니다: $INTEL_JDK_DIR"
else
    echo "📥 인텔 JDK 다운로드 중..."
    # JDK 다운로드 URL (Azul Zulu JDK - Intel Mac용)
    JDK_URL="https://cdn.azul.com/zulu/bin/zulu17.46.19-ca-jdk17.0.9-macosx_x64.tar.gz"
    JDK_TAR="$JDK_INSTALL_DIR/intel-jdk.tar.gz"
    
    # JDK 다운로드
    curl -L "$JDK_URL" -o "$JDK_TAR"
    
    # 압축 해제
    mkdir -p "$INTEL_JDK_DIR"
    tar -xf "$JDK_TAR" -C "$JDK_INSTALL_DIR"
    
    # 압축 해제된 디렉토리 이름 찾기
    EXTRACTED_DIR=$(find "$JDK_INSTALL_DIR" -maxdepth 1 -name "zulu*" -type d | head -1)
    
    if [ -n "$EXTRACTED_DIR" ]; then
        # 압축 해제된 파일을 원하는 디렉토리로 이동
        mv "$EXTRACTED_DIR"/* "$INTEL_JDK_DIR"
        rmdir "$EXTRACTED_DIR"
        rm "$JDK_TAR"
        echo "✅ 인텔 JDK 설치 완료: $INTEL_JDK_DIR"
    else
        echo "❌ JDK 압축 해제 실패"
        exit 1
    fi
fi

# 기존 빌드 디렉토리 정리
echo "🧹 빌드 디렉토리 정리 중..."
rm -rf "$NATIVE_BUILD_DIR/app" "$INTEL_BUILD_DIR/app" "$UNIVERSAL_BUILD_DIR"
mkdir -p "$UNIVERSAL_BUILD_DIR/app"

# 1. Apple Silicon 버전 빌드 (네이티브)
echo "🚀 Apple Silicon (ARM64) 버전 빌드 중..."
./gradlew clean createDistributable

# 2. Intel 버전 빌드 (Rosetta 2 사용)
echo "🚀 Intel (x86_64) 버전 빌드 중..."
arch -x86_64 /bin/bash -c "export JAVA_HOME='$INTEL_JDK_DIR'; \
./gradlew clean createDistributable \
-Dcompose.desktop.mac.minSdkVersion=10.15 \
-Dcompose.desktop.mac.archs=x86_64"

# 3. 유니버설 앱 생성
echo "🧩 유니버설 앱 생성 중..."

# 앱 경로 설정
ARM_APP="$NATIVE_BUILD_DIR/app/$APP_NAME.app"
INTEL_APP="$INTEL_BUILD_DIR/app/$APP_NAME.app"
UNIVERSAL_APP="$UNIVERSAL_BUILD_DIR/app/$APP_NAME.app"

# 기본 앱 구조 복사 (ARM64 버전)
cp -R "$ARM_APP" "$UNIVERSAL_APP"

# Intel 버전의 바이너리를 추출하고 유니버설 앱에 추가
echo "🔧 Intel 바이너리 통합 중..."

# 유니버설 앱의 Java 런타임 수정
# 1. 각 앱의 JRE 경로 찾기
ARM_JRE_PATH="$ARM_APP/Contents/runtime"
INTEL_JRE_PATH="$INTEL_APP/Contents/runtime"
UNIVERSAL_JRE_PATH="$UNIVERSAL_APP/Contents/runtime"

# 2. libjli.dylib 파일이 있는지 확인 (Java 런처 인터페이스)
ARM_LIBJLI="$ARM_JRE_PATH/Contents/Home/lib/libjli.dylib"
INTEL_LIBJLI="$INTEL_JRE_PATH/Contents/Home/lib/libjli.dylib"
UNIVERSAL_LIBJLI="$UNIVERSAL_JRE_PATH/Contents/Home/lib/libjli.dylib"

# 3. 메인 실행 파일
ARM_EXECUTABLE="$ARM_APP/Contents/MacOS/$APP_NAME"
INTEL_EXECUTABLE="$INTEL_APP/Contents/MacOS/$APP_NAME"
UNIVERSAL_EXECUTABLE="$UNIVERSAL_APP/Contents/MacOS/$APP_NAME"

# 4. 유니버설 바이너리 생성
echo "🧪 유니버설 바이너리 생성 중..."
mkdir -p "$UNIVERSAL_BUILD_DIR/tmp"

# libjli.dylib 유니버설 바이너리 생성
lipo -create "$ARM_LIBJLI" "$INTEL_LIBJLI" -output "$UNIVERSAL_BUILD_DIR/tmp/libjli.dylib"
cp "$UNIVERSAL_BUILD_DIR/tmp/libjli.dylib" "$UNIVERSAL_LIBJLI"

# 메인 실행 파일 유니버설 바이너리 생성
lipo -create "$ARM_EXECUTABLE" "$INTEL_EXECUTABLE" -output "$UNIVERSAL_BUILD_DIR/tmp/executable"
cp "$UNIVERSAL_BUILD_DIR/tmp/executable" "$UNIVERSAL_EXECUTABLE"
chmod +x "$UNIVERSAL_EXECUTABLE"

# Info.plist 확인 및 업데이트
echo "📄 Info.plist 업데이트 중..."
PLIST_PATH="$UNIVERSAL_APP/Contents/Info.plist"

# LSMinimumSystemVersion 업데이트 (Intel 호환을 위해 10.15로 설정)
/usr/libexec/PlistBuddy -c "Set :LSMinimumSystemVersion 10.15" "$PLIST_PATH"

# 4. DMG 생성
echo "💿 유니버설 DMG 패키징 중..."

# hdiutil 명령을 사용하여 DMG 생성
DMG_PATH="$UNIVERSAL_BUILD_DIR/$APP_NAME-$APP_VERSION-universal.dmg"
hdiutil create -volname "$APP_NAME" -srcfolder "$UNIVERSAL_APP" -ov -format UDZO "$DMG_PATH"

# 5. 유효성 검사
echo "✅ 유니버설 바이너리 검증 중..."
lipo -info "$UNIVERSAL_EXECUTABLE"
lipo -info "$UNIVERSAL_LIBJLI"

# 완료
echo ""
echo "✅ 유니버설 DMG 빌드 완료!"
echo "📦 DMG 파일: $DMG_PATH"
echo ""
echo "다음 명령으로 공증(notarization)을 진행할 수 있습니다:"
echo "xcrun notarytool submit \"$DMG_PATH\" --apple-id \"\$APPLE_ID\" --password \"\$APPLE_PASSWORD\" --team-id \"\$APPLE_TEAM_ID\" --wait"
echo ""
echo "🔍 빌드된 세 가지 버전은 다음 위치에서 확인할 수 있습니다:"
echo "   - ARM64 (Apple Silicon): $NATIVE_BUILD_DIR"
echo "   - x86_64 (Intel): $INTEL_BUILD_DIR"
echo "   - 유니버설 (Universal): $UNIVERSAL_BUILD_DIR"