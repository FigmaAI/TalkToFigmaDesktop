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
APP_NAME="TalkToFigma Desktop"
APP_PATH="app/build/compose/binaries/main/app/${APP_NAME}.app"
ARCHIVE_NAME="TalkToFigma Desktop"
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
echo "🔨 [Step 1/6] Building the app without automatic signing..."
export BUILD_FOR_APP_STORE=true
./gradlew :app:createDistributable
echo "✅ App built successfully at: $APP_PATH"

# --- Step 2: Embed the provisioning profile ---
echo "📄 [Step 2/6] Embedding provisioning profile..."
mkdir -p "${APP_PATH}/Contents/embedded.provisionprofile"
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

# --- Step 5: Sign the app bundle ---
echo "🔏 [Step 5/6] Signing the app with identity: $SIGNING_IDENTITY_APPSTORE"
codesign --force --options runtime --entitlements "$ENTITLEMENTS" --sign "$SIGNING_IDENTITY_APPSTORE" "$APP_PATH"

# Verify the signing
echo "🔍 Verifying signature..."
codesign --verify --verbose "$APP_PATH"
echo "✅ App signed successfully for App Store distribution!"

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

# Fix embedded.provisionprofile if it's a directory
ARCHIVE_APP_PATH="$ARCHIVE_PATH/Products/Applications/$APP_NAME.app"
EMBEDDED_PROFILE_PATH="$ARCHIVE_APP_PATH/Contents/embedded.provisionprofile"

echo "   🔧 Fixing embedded.provisionprofile..."
if [ -d "$EMBEDDED_PROFILE_PATH" ]; then
    echo "      embedded.provisionprofile is a directory, converting to file..."
    # Find the actual provisioning profile file inside the directory
    PROVISIONING_FILE=$(find "$EMBEDDED_PROFILE_PATH" -name "*.provisionprofile" -type f | head -1)
    if [ -n "$PROVISIONING_FILE" ]; then
        echo "      Found provisioning profile: $(basename "$PROVISIONING_FILE")"
        # Copy the file to a temporary location first
        TEMP_PROFILE="/tmp/embedded_temp.provisionprofile"
        cp "$PROVISIONING_FILE" "$TEMP_PROFILE"
        # Remove the directory and copy the file as embedded.provisionprofile
        rm -rf "$EMBEDDED_PROFILE_PATH"
        cp "$TEMP_PROFILE" "$EMBEDDED_PROFILE_PATH"
        rm "$TEMP_PROFILE"
        echo "      ✅ embedded.provisionprofile converted to file"
    else
        echo "      ⚠️  No .provisionprofile file found in directory"
        # Try to use the main provisioning profile as fallback
        if [ -f "$PROVISIONING_PROFILE" ]; then
            echo "      📄 Using main App Store provisioning profile as fallback"
            rm -rf "$EMBEDDED_PROFILE_PATH"
            cp "$PROVISIONING_PROFILE" "$EMBEDDED_PROFILE_PATH"
            echo "      ✅ embedded.provisionprofile created from fallback"
        fi
    fi
elif [ -f "$EMBEDDED_PROFILE_PATH" ]; then
    echo "      ✅ embedded.provisionprofile is already a file"
else
    echo "      ⚠️  embedded.provisionprofile not found, creating from main profile..."
    if [ -f "$PROVISIONING_PROFILE" ]; then
        cp "$PROVISIONING_PROFILE" "$EMBEDDED_PROFILE_PATH"
        echo "      ✅ embedded.provisionprofile created"
    else
        echo "      ❌ $PROVISIONING_PROFILE not found!"
    fi
fi

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
            <string>arm64</string>
            <string>x86_64</string>
        </array>
        <key>CFBundleIdentifier</key>
        <string>${BUNDLE_ID}</string>
        <key>CFBundleShortVersionString</key>
        <string>1.0.4</string>
        <key>CFBundleVersion</key>
        <string>1.0.4</string>
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