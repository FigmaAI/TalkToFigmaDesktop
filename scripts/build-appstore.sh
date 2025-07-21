#!/bin/bash

# =================================================================
# Manual signing script for App Store distribution
# This script builds, signs, and packages the app for App Store submission
# 
# Required environment variables:
# - SIGNING_IDENTITY_APPSTORE: Apple Distribution identity
# - INSTALLER_IDENTITY: 3rd Party Mac Developer Installer identity
# =================================================================

set -e # Exit immediately if a command exits with a non-zero status.

echo "🚀 Starting App Store signing process..."

# --- Configuration ---
APP_NAME="TalkToFigma Desktop"
APP_PATH="app/build/compose/binaries/main/app/${APP_NAME}.app"
PKG_DIR="app/build/compose/binaries/main/pkg"
PKG_PATH="${PKG_DIR}/${APP_NAME}.pkg"
PROVISIONING_PROFILE="TalkToFigma_App_Store.provisionprofile"
ENTITLEMENTS="entitlements-appstore.plist"

# Check for required environment variables
if [ -z "$SIGNING_IDENTITY_APPSTORE" ]; then
    echo "❌ Error: SIGNING_IDENTITY_APPSTORE environment variable is not set."
    echo "   Example: export SIGNING_IDENTITY_APPSTORE=\"Apple Distribution: Your Name (TEAMID)\""
    echo "   Using: \"Apple Distribution: JooHyung Park (ZQC7QNZ4J8)\" as default"
    SIGNING_IDENTITY_APPSTORE="Apple Distribution: JooHyung Park (ZQC7QNZ4J8)"
fi

if [ -z "$INSTALLER_IDENTITY" ]; then
    echo "❌ Error: INSTALLER_IDENTITY environment variable is not set."
    echo "   Example: export INSTALLER_IDENTITY=\"3rd Party Mac Developer Installer: Your Name (TEAMID)\""
    echo "   Using: \"3rd Party Mac Developer Installer: JooHyung Park (ZQC7QNZ4J8)\" as default"
    INSTALLER_IDENTITY="3rd Party Mac Developer Installer: JooHyung Park (ZQC7QNZ4J8)"
fi

echo "📋 Build configuration:"
echo "   - Signing Identity: $SIGNING_IDENTITY_APPSTORE"
echo "   - Installer Identity: $INSTALLER_IDENTITY"
echo "   - Provisioning Profile: $PROVISIONING_PROFILE"
echo "   - Entitlements: $ENTITLEMENTS"
echo ""

# --- Step 1: Build the app without automatic signing ---
echo "🔨 [Step 1/5] Building the app without automatic signing..."
export BUILD_FOR_APP_STORE=true
./gradlew :app:createDistributable
echo "✅ App built successfully at: $APP_PATH"

# --- Step 2: Embed the provisioning profile ---
echo "📄 [Step 2/5] Embedding provisioning profile..."
mkdir -p "${APP_PATH}/Contents/embedded.provisionprofile"
cp "${PROVISIONING_PROFILE}" "${APP_PATH}/Contents/embedded.provisionprofile"
echo "✅ Provisioning profile embedded"

# --- Step 3: Sign the jspawnhelper with sandbox entitlements ---
echo "🔏 [Step 3/5] Signing runtime components with sandbox entitlements..."
# Find and sign jspawnhelper specifically (this is a known issue with JVM apps)
JSPAWNHELPER_PATH=$(find "${APP_PATH}" -name "jspawnhelper" -type f)
if [ -n "$JSPAWNHELPER_PATH" ]; then
    echo "   Signing jspawnhelper: $JSPAWNHELPER_PATH"
    codesign --force --options runtime --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$JSPAWNHELPER_PATH"
fi

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
BUNDLE_ID="kr.co.metadata.mcp.talktofigma"
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

# --- Step 5: Sign the app bundle ---
echo "🔏 [Step 5/6] Signing the app with identity: $SIGNING_IDENTITY_APPSTORE"
codesign --force --options runtime --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$APP_PATH"

# Verify the signing
echo "🔍 [Step 6/6] Verifying signature..."
codesign --verify --verbose "$APP_PATH"
echo "✅ App signed successfully for App Store distribution!"

# --- Step 7: Create the PKG installer ---
echo "📦 [Step 7/7] Creating .pkg file for App Store submission..."
mkdir -p "$PKG_DIR"
productbuild --component "$APP_PATH" /Applications --sign "$INSTALLER_IDENTITY" "$PKG_PATH"

echo ""
echo "🎉 Process completed successfully!"
echo "    You can find the .pkg file at: $PKG_PATH"
echo "    Upload this file to App Store Connect for distribution." 