#!/bin/bash

# =================================================================
# Manual signing script for App Store distribution
# This script builds, signs, and packages the app for App Store submission
# 
# Required environment variables:
# - SIGNING_IDENTITY_APPSTORE: Apple Distribution identity
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

# Validate required environment variables
if [ -z "$SIGNING_IDENTITY_APPSTORE" ]; then
  echo "❌ Error: SIGNING_IDENTITY_APPSTORE environment variable is not set."
  echo "   Example: export SIGNING_IDENTITY_APPSTORE=\"Apple Distribution: Your Name (TEAMID)\""
  exit 1
fi

echo "📋 Build configuration:"
echo "   - Signing Identity: $SIGNING_IDENTITY_APPSTORE"
echo "   - Provisioning Profile: $PROVISIONING_PROFILE"
echo "   - Entitlements: $ENTITLEMENTS"
echo ""

# --- 1. Build the app without signing ---
echo "🔨 [Step 1/5] Building the app without automatic signing..."
BUILD_FOR_APP_STORE=true ./gradlew clean createDistributable --no-configuration-cache

if [ ! -d "$APP_PATH" ]; then
    echo "❌ Error: App not found at path: $APP_PATH. The build might have failed or the path changed."
    exit 1
fi
echo "✅ App built successfully at: $APP_PATH"

# --- 2. Embed the provisioning profile ---
echo "📄 [Step 2/5] Embedding provisioning profile..."
if [ ! -f "$PROVISIONING_PROFILE" ]; then
    echo "❌ Error: Provisioning profile not found at: $PROVISIONING_PROFILE"
    exit 1
fi
cp "$PROVISIONING_PROFILE" "$APP_PATH/Contents/embedded.provisionprofile"
echo "✅ Provisioning profile embedded"

# --- 3. Sign the app ---
echo "🔏 [Step 3/5] Signing the app with identity: $SIGNING_IDENTITY_APPSTORE"
/usr/bin/codesign --force --options runtime --deep --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$APP_PATH"

# --- 4. Verify the signature ---
echo "🔍 [Step 4/5] Verifying signature..."
/usr/bin/codesign -vvv --deep --strict "$APP_PATH"
echo "✅ App signed successfully for App Store distribution!"

# --- 5. Create the .pkg file for App Store submission ---
echo "📦 [Step 5/5] Creating .pkg file for App Store submission..."
mkdir -p "$PKG_DIR"
# Create unsigned .pkg file (Apple will re-sign it during submission)
/usr/bin/productbuild --component "$APP_PATH" /Applications "$PKG_PATH"

if [ ! -f "$PKG_PATH" ]; then
    echo "❌ Error: Failed to create .pkg file"
    exit 1
fi

echo ""
echo "🎉 Process completed successfully!"
echo "    You can find the .pkg file at: $PKG_PATH"
echo "    Upload this file to App Store Connect for distribution."
echo "    Note: This .pkg file is unsigned, but Apple will re-sign it during the submission process." 