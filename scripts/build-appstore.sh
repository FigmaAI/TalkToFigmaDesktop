#!/bin/bash

# =================================================================
# App Store build and archive script
# This script builds, signs, and creates an Xcode archive for App Store submission
# 
# Required environment variables:
# - SIGNING_IDENTITY_APPSTORE: Apple Distribution identity
# - INSTALLER_IDENTITY: 3rd Party Mac Developer Installer identity (for reference)
# =================================================================

set -e # Exit immediately if a command exits with a non-zero status.

echo "🚀 Starting App Store build and archive process..."

# --- Configuration ---
APP_NAME="Cursor Talk to Figma desktop"
APP_PATH="app/build/compose/binaries/main/app/${APP_NAME}.app"
ARCHIVE_NAME="Cursor Talk to Figma desktop"
ARCHIVE_PATH="app/build/compose/binaries/main/archive/${ARCHIVE_NAME}.xcarchive"
PROVISIONING_PROFILE="TalkToFigma_App_Store.provisionprofile"
ENTITLEMENTS="entitlements.plist"
BUNDLE_ID="kr.co.metadata.mcp.talktofigma"

# Check for required environment variables
if [ -z "$SIGNING_IDENTITY_APPSTORE" ]; then
    echo "❌ Error: SIGNING_IDENTITY_APPSTORE environment variable is not set."
    echo "   Example: export SIGNING_IDENTITY_APPSTORE=\"Apple Distribution: Your Name (TEAMID)\""
    echo "   Using: \"Apple Distribution: JooHyung Park (ZQC7QNZ4J8)\" as default"
    SIGNING_IDENTITY_APPSTORE="Apple Distribution: JooHyung Park (ZQC7QNZ4J8)"
fi

echo "📋 Build configuration:"
echo "   - Signing Identity: $SIGNING_IDENTITY_APPSTORE"
echo "   - Provisioning Profile: $PROVISIONING_PROFILE"
echo "   - Entitlements: $ENTITLEMENTS"
echo "   - Archive Path: $ARCHIVE_PATH"
echo ""

# --- Step 1: Build the app without automatic signing ---
echo "🔨 [Step 1/6] Building universal binary for App Store..."
export BUILD_FOR_APP_STORE=true

# Check current architecture
ARCH=$(uname -m)
echo "🖥️  Current architecture: $ARCH"

# Universal binary support with proper JVM settings
if [ "$ARCH" == "arm64" ]; then
    echo "🚀 Building universal binary on Apple Silicon..."
    # Use Intel JDK for proper Intel compatibility if available
    INTEL_JDK_PATH="$HOME/.jdks/jdk-17.0.9-intel"
    if [ -d "$INTEL_JDK_PATH" ]; then
        echo "📱 Using Intel JDK for better Intel compatibility: $INTEL_JDK_PATH"
        export INTEL_JAVA_HOME="$INTEL_JDK_PATH"
    else
        echo "⚠️  Intel JDK not found. Using current JDK for universal build."
    fi
fi

# === Gradle Build Configuration ===
# JDK path and system properties (JavaFX removed)
GRADLE_PROPS=(
  "-Dcompose.desktop.mac.archs=x86_64,arm64"
  "-Dcompose.desktop.mac.minSdkVersion=10.15" 
  "-Dcompose.desktop.verbose=true"
  "-Dapple.awt.UIElement=true"
  "-Dorg.gradle.parallel=true"
)

# JVM optimization options (JavaFX removed)
JVM_OPTS=(
  "-Dkotlin.daemon.jvmargs=-Xmx2g -XX:+UseParallelGC"
  "-Dorg.gradle.jvmargs=-Xmx2g -XX:+UseParallelGC -Dapple.awt.UIElement=true"
)

echo "🚀 Starting Universal App build (x86_64, arm64)..."

# Execute Gradle build
./gradlew :app:createDistributable "${GRADLE_PROPS[@]}" "${JVM_OPTS[@]}"

BUILD_RESULT=$?
if [ $BUILD_RESULT -ne 0 ]; then
  echo "❌ Build failed! Exiting."
  exit $BUILD_RESULT
fi

echo "✅ App built successfully at: $APP_PATH"

# JavaFX verification removed - now using Skiko for WebP animations

# --- Step 2: Embed the provisioning profile ---
echo "📄 [Step 2/6] Embedding provisioning profile..."
mkdir -p "${APP_PATH}/Contents"
cp "${PROVISIONING_PROFILE}" "${APP_PATH}/Contents/embedded.provisionprofile"
echo "✅ Provisioning profile embedded"

# --- Step 3: Sign the jspawnhelper with sandbox entitlements ---
echo "🔏 [Step 3/6] Signing runtime components with sandbox entitlements..."
# Find and sign jspawnhelper specifically (this is a known issue with JVM apps)
JSPAWNHELPER_PATH=$(find "${APP_PATH}" -name "jspawnhelper" -type f)
if [ -n "$JSPAWNHELPER_PATH" ]; then
    echo "   Signing jspawnhelper: $JSPAWNHELPER_PATH"
    codesign --force --options runtime --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$JSPAWNHELPER_PATH"
fi

# Function to efficiently sign binary files
sign_binary() {
    local file="$1"
    echo "   Signing: $file"
    codesign --force --options runtime --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$file"
    return $?
}

echo "🔏 Signing all binary files in the app bundle..."

# 1. Sign all native library files (.dylib, .jnilib, .so)
echo "   - Signing native library files..."
find "${APP_PATH}" -type f \( -name "*.dylib" -o -name "*.jnilib" -o -name "*.so" \) | while read -r binary; do
    sign_binary "$binary"
done

# 2. Sign all JAR files
echo "   - Signing JAR files..."
find "${APP_PATH}" -type f -name "*.jar" | while read -r jar; do
    sign_binary "$jar"
done

# 3. Sign executable binary files
echo "   - Signing executable files..."
find "${APP_PATH}" -type f -perm +111 | grep -v "\(dylib\|jnilib\|so\|jar\)$" | while read -r executable; do
    sign_binary "$executable"
done

# JavaFX verification removed - now using Skiko for WebP animations

# Sign any other executables in the runtime directory
echo "   Signing other runtime executables..."
RUNTIME_DIR="${APP_PATH}/Contents/runtime"
find "$RUNTIME_DIR" -type f -perm +111 | while read -r executable; do
    echo "   Signing executable: $executable"
    codesign --force --options runtime --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$executable"
done

# --- Step 4: Add application-identifier to Info.plist ---
echo "📝 [Step 4/6] Adding required keys to Info.plist..."
TEAM_ID="ZQC7QNZ4J8"
APP_IDENTIFIER="${TEAM_ID}.${BUNDLE_ID}"
INFO_PLIST="${APP_PATH}/Contents/Info.plist"

# Add application-identifier to Info.plist
/usr/libexec/PlistBuddy -c "Add :application-identifier string ${APP_IDENTIFIER}" "$INFO_PLIST" 2>/dev/null || \
/usr/libexec/PlistBuddy -c "Set :application-identifier ${APP_IDENTIFIER}" "$INFO_PLIST"
echo "✅ application-identifier added to Info.plist: $APP_IDENTIFIER"

# Add encryption info to Info.plist to avoid encryption questions in future submissions
/usr/libexec/PlistBuddy -c "Add :ITSAppUsesNonExemptEncryption bool NO" "$INFO_PLIST" 2>/dev/null || \
/usr/libexec/PlistBuddy -c "Set :ITSAppUsesNonExemptEncryption NO" "$INFO_PLIST"
echo "✅ ITSAppUsesNonExemptEncryption set to NO in Info.plist"

# JavaFX properties no longer needed - using Skiko for WebP animations

# --- Step 5: Sign the app bundle ---
echo "🔏 [Step 5/6] Signing the app with identity: $SIGNING_IDENTITY_APPSTORE"

# First sign the app bundle with a shallow signature
echo "   - Signing app bundle..."
codesign --force --options runtime --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$APP_PATH"

# Signature verification (standard verification)
echo "🔍 Performing basic signature verification..."
codesign --verify --verbose "$APP_PATH"

# Deep verification - Verify all signatures before App Store submission
echo "🔬 Performing deep signature verification (--deep)..."
codesign --verify --verbose --deep "$APP_PATH"
DEEP_VERIFY_RESULT=$?

if [ $DEEP_VERIFY_RESULT -eq 0 ]; then
    echo "✅ App signature verification complete: All signatures are valid!"
else
    echo "⚠️  Warning: There may be issues with deep signature verification. This might cause problems during App Store submission."
fi

# spctl verification - System policy verification
echo "🔒 Performing system policy verification..."
spctl --assess --verbose=4 --type execute "$APP_PATH" || echo "⚠️  spctl verification warning: This may not be an issue for App Store submission."

echo "✅ App signing complete!"

# --- Step 6: Create Xcode Archive ---
echo "📦 [Step 6/6] Creating Xcode Archive..."

# Create archive directory structure
echo "   📁 Creating archive directory structure..."
rm -rf "$ARCHIVE_PATH"
mkdir -p "$ARCHIVE_PATH/Products/Applications"
mkdir -p "$ARCHIVE_PATH/dSYMs"
mkdir -p "$ARCHIVE_PATH/BCSymbolMaps"

# Copy app to archive
echo "   📦 Copying app to archive..."
cp -R "$APP_PATH" "$ARCHIVE_PATH/Products/Applications/"

# Get current date in ISO 8601 format
CREATION_DATE=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Create Archive Info.plist
echo "   📝 Creating Archive Info.plist..."
cat > "$ARCHIVE_PATH/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>ApplicationProperties</key>
    <dict>
        <key>ApplicationPath</key>
        <string>Applications/${APP_NAME}.app</string>
        <key>Architectures</key>
        <array>
            <string>x86_64</string>
            <string>arm64</string>
        </array>
        <key>CFBundleIdentifier</key>
        <string>${BUNDLE_ID}</string>
        <key>CFBundleShortVersionString</key>
        <string>1.0.5</string>
        <key>CFBundleVersion</key>
        <string>4</string>
        <key>SigningIdentity</key>
        <string>${SIGNING_IDENTITY_APPSTORE}</string>
        <key>Team</key>
        <string>${TEAM_ID}</string>
    </dict>
    <key>ArchiveVersion</key>
    <integer>2</integer>
    <key>CreationDate</key>
    <date>${CREATION_DATE}</date>
    <key>Name</key>
    <string>${ARCHIVE_NAME}</string>
    <key>SchemeName</key>
    <string>${ARCHIVE_NAME}</string>
</dict>
</plist>
EOF

echo ""
echo "🎉 App Store build and archive process completed successfully!"
echo ""
echo "📍 Xcode Archive location: $ARCHIVE_PATH"
echo ""
echo "🎯 Next steps:"
echo "1. Open Xcode"
echo "2. Go to Window → Organizer (⌘+Shift+9)"
echo "3. The archive should appear in the Archives tab"
echo "4. Select the archive and click 'Distribute App'"
echo "5. Choose 'App Store Connect' → 'Upload'"
echo ""
echo "✨ This archive includes all necessary signatures and entitlements for App Store submission!" 