#!/bin/bash

# The build script for App Store distribution (latest policy applied)
echo "🚀 Start building for App Store..."

# 1. Check certificates
echo "📋 Checking certificates..."
CERTIFICATES=$(security find-identity -v -p codesigning)
if [ -z "$CERTIFICATES" ]; then
    echo "❌ No code signing certificates found."
    echo "   Please run setup-certificates.sh first."
    exit 1
fi
echo "✅ Certificates checked successfully"

# 2. Automatically detect Team ID
echo "📋 Automatically detecting Team ID..."
TEAM_ID=$(xcodebuild -list -json 2>/dev/null | grep -o '"teamID":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$TEAM_ID" ]; then
    echo "⚠️  Team ID cannot be automatically detected."
    echo "   Please set manually: export APPLE_TEAM_ID=\"YOUR_TEAM_ID\""
    TEAM_ID="ZQC7QNZ4J8"  # Default value
fi
echo "✅ Team ID: $TEAM_ID"

# 3. Set environment variables
export BUILD_FOR_APP_STORE=true
export APPLE_TEAM_ID="$TEAM_ID"

# Automatically detect Apple Distribution certificate
DISTRIBUTION_CERT=$(security find-identity -v -p codesigning | grep "Apple Distribution" | head -1 | cut -d'"' -f2)
if [ -n "$DISTRIBUTION_CERT" ]; then
    export SIGNING_IDENTITY="$DISTRIBUTION_CERT"
    echo "✅ Signing certificate: $SIGNING_IDENTITY"
else
    echo "⚠️  Apple Distribution certificate not found."
    echo "   Please create Apple Distribution certificate in Xcode."
    exit 1
fi

echo ""
echo "📋 Build settings:"
echo "  - App Store distribution: $BUILD_FOR_APP_STORE"
echo "  - Signing certificate: $SIGNING_IDENTITY"
echo "  - Team ID: $APPLE_TEAM_ID"

# 4. Run build
echo ""
echo "🔨 Start building..."
./gradlew clean build

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build completed!"
    echo "📦 Created files:"
    echo "  - app/build/compose/binaries/main/apple/AppStore/TalkToFigma Desktop.pkg"
    echo ""
    echo "🎯 Next steps:"
    echo "   1. Login to App Store Connect"
    echo "   2. My Apps > + button > New App"
    echo "   3. Upload the created .pkg file"
else
    echo ""
    echo "❌ Build failed!"
    echo "   Please check the error log."
    exit 1
fi 