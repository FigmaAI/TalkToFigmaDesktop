#!/bin/bash

# =================================================================
# Intel Mac Compatible DMG Build Script
# Build using Intel JDK to solve architecture compatibility issues
# 
# Usage: ./scripts/build-intel-mac.sh
# =================================================================

set -e # Exit immediately if a command exits with a non-zero status.

# --- Configuration ---
APP_NAME="Cursor Talk to Figma desktop"
APP_VERSION=$(cd app && ../gradlew -q printVersion)
BUILD_DIR="app/build/compose/binaries/main"
INTEL_BUILD_DIR="app/build/compose/binaries/intel"
TEMP_BUILD_DIR="app/build/compose/binaries/temp"

# Check current architecture
ARCH=$(uname -m)
echo "🖥️  Current architecture: $ARCH"
echo "📋 Configuration:"
echo "   - App Version: $APP_VERSION"
echo "   - App Name: $APP_NAME"

# Check if Apple Silicon build already exists
ARM64_DMG_DIR="app/build/compose/binaries/main/dmg"
if [ -d "$ARM64_DMG_DIR" ] && [ -n "$(find "$ARM64_DMG_DIR" -name "*.dmg" 2>/dev/null | head -1)" ]; then
    echo "✅ Apple Silicon DMG found. Will add Intel build alongside it."
else
    echo "⚠️ Apple Silicon DMG not found. Consider running notarize.sh first for a complete set of builds."
    echo "   Continuing with Intel build only..."
fi

# Check if Rosetta 2 is installed (needed only on Apple Silicon Mac)
check_rosetta() {
    if [ "$ARCH" == "arm64" ]; then
        echo "Checking if Rosetta 2 is installed..."
        if ! /usr/bin/pgrep -q oahd; then
            echo "Rosetta 2 is not running. It may not be installed."
            echo "Installing Rosetta 2..."
            softwareupdate --install-rosetta --agree-to-license
        else
            echo "Rosetta 2 is installed and running."
        fi
    else
        echo "Running on Intel Mac, Rosetta not needed."
    fi
}

# Download Intel JDK
download_intel_jdk() {
    # JDK installation path
    JDK_INSTALL_DIR="$HOME/.jdks"
    mkdir -p "$JDK_INSTALL_DIR"
    
    # Set Intel JDK path
    INTEL_JDK_VERSION="21.0.5.11.1"
    INTEL_JDK_DIR="$JDK_INSTALL_DIR/amazon-corretto-$INTEL_JDK_VERSION-intel"
    
    # Check if JDK is already downloaded and has proper structure
    if [ -d "$INTEL_JDK_DIR" ] && [ -f "$INTEL_JDK_DIR/Contents/Home/bin/java" ]; then
        echo "Intel JDK already exists at $INTEL_JDK_DIR"
    else
        echo "Downloading Intel JDK..."
        # JDK download URL (AWS Corretto JDK - for Intel Mac)
        JDK_URL="https://corretto.aws/downloads/resources/$INTEL_JDK_VERSION/amazon-corretto-$INTEL_JDK_VERSION-macosx-x64.tar.gz"
        JDK_TAR="$JDK_INSTALL_DIR/amazon-corretto-intel.tar.gz"
        
        # Download JDK
        curl -L "$JDK_URL" -o "$JDK_TAR"
        
        # Extract archive directly to final directory
        echo "Extracting JDK to $INTEL_JDK_DIR..."
        mkdir -p "$INTEL_JDK_DIR"
        tar -xf "$JDK_TAR" --strip-components=1 -C "$INTEL_JDK_DIR" || {
            echo "Failed to extract with strip-components, trying alternative method..."
            rm -rf "$INTEL_JDK_DIR"
            mkdir -p "$INTEL_JDK_DIR"
            
            # Extract to temporary location first
            TEMP_EXTRACT_DIR="$JDK_INSTALL_DIR/temp_extract"
            rm -rf "$TEMP_EXTRACT_DIR"
            mkdir -p "$TEMP_EXTRACT_DIR"
            tar -xf "$JDK_TAR" -C "$TEMP_EXTRACT_DIR"
            
            # Find the extracted JDK directory
            EXTRACTED_JDK=$(find "$TEMP_EXTRACT_DIR" -name "*.jdk" -type d | head -1)
            if [ -z "$EXTRACTED_JDK" ]; then
                EXTRACTED_JDK=$(find "$TEMP_EXTRACT_DIR" -name "jdk*" -type d | head -1)
            fi
            
            if [ -n "$EXTRACTED_JDK" ]; then
                echo "Found extracted JDK at: $EXTRACTED_JDK"
                cp -R "$EXTRACTED_JDK"/* "$INTEL_JDK_DIR/"
                rm -rf "$TEMP_EXTRACT_DIR"
                echo "Intel JDK extracted to $INTEL_JDK_DIR"
            else
                echo "Failed to find extracted JDK directory"
                ls -la "$TEMP_EXTRACT_DIR"
                rm -rf "$TEMP_EXTRACT_DIR"
                exit 1
            fi
        }
        
        # Clean up downloaded tar file
        rm "$JDK_TAR"
        
        # Verify installation
        if [ -f "$INTEL_JDK_DIR/Contents/Home/bin/java" ]; then
            echo "✅ Intel JDK successfully installed to $INTEL_JDK_DIR"
        else
            echo "❌ Intel JDK installation verification failed"
            echo "Contents of $INTEL_JDK_DIR:"
            ls -la "$INTEL_JDK_DIR" || true
            exit 1
        fi
    fi
    
    echo "Using Intel JDK at: $INTEL_JDK_DIR"
    export INTEL_JDK_PATH="$INTEL_JDK_DIR/Contents/Home"
}

# Run build with Intel JDK
build_with_intel_jdk() {
    # Create temporary build directory and result directory
    mkdir -p "$TEMP_BUILD_DIR" "$INTEL_BUILD_DIR/dmg"
    
    # Ensure we don't overwrite any existing builds
    echo "ℹ️ Building Intel-specific version without affecting existing builds..."
    
    # JVM and build options for Intel Mac compatibility
    GRADLE_OPTS="$GRADLE_OPTS -Xmx2G -XX:MaxMetaspaceSize=512m"
    
    # macOS settings via system properties (without modifying build.gradle.kts)
    SYSTEM_PROPS="-Dcompose.desktop.mac.sign=false -Dcompose.desktop.mac.minSdkVersion=10.15 -Dcompose.desktop.mac.archs=x86_64"
    
    if [ "$ARCH" == "arm64" ]; then
        echo "🚀 Running Gradle with Intel JDK under Rosetta 2..."
        # Skip clean task to preserve existing Apple Silicon build
        arch -x86_64 /bin/bash -c "export JAVA_HOME='$INTEL_JDK_PATH'; export GRADLE_OPTS='$GRADLE_OPTS'; ./gradlew packageDmg $SYSTEM_PROPS --info -x clean"
    else
        echo "🚀 Running Gradle with Intel JDK..."
        export JAVA_HOME="$INTEL_JDK_PATH"
        export GRADLE_OPTS="$GRADLE_OPTS"
        # Skip clean task to preserve existing Apple Silicon build
        ./gradlew packageDmg $SYSTEM_PROPS --info -x clean
    fi
}

# Copy and organize build results
finalize_build() {
    # Copy results from the default build directory to Intel-specific build directory
    DEFAULT_DMG_DIR="app/build/compose/binaries/main/dmg"
    
    if [ -d "$DEFAULT_DMG_DIR" ]; then
        echo "📦 Finding DMG file in default build directory: $DEFAULT_DMG_DIR"
        
        # Find DMG file
        DMG_FILE=$(find "$DEFAULT_DMG_DIR" -name "*.dmg" | head -1)
        
        if [ -n "$DMG_FILE" ]; then
            # Check and create Intel DMG directory
            mkdir -p "${INTEL_BUILD_DIR}/dmg"
            
            # Copy with -intel suffix added to the filename
            INTEL_DMG="${INTEL_BUILD_DIR}/dmg/$(basename "${DMG_FILE%.dmg}")-intel.dmg"
            
            echo "📋 Original DMG path: $DMG_FILE"
            echo "📋 Intel DMG path: $INTEL_DMG"
            
            cp "$DMG_FILE" "$INTEL_DMG"
            echo "✅ Intel-compatible DMG created: $INTEL_DMG"
        else
            echo "❌ Error: DMG file not found in default build directory: $DEFAULT_DMG_DIR"
            exit 1
        fi
    else
        echo "❌ Error: Default build directory not found: $DEFAULT_DMG_DIR"
        exit 1
    fi
}

# Function to perform notarization
notarize_dmg() {
    echo "🔐 Performing notarization for Intel version DMG..."
    
    # Check environment variables
    if [ -z "$APPLE_ID" ] || [ -z "$APPLE_PASSWORD" ] || [ -z "$APPLE_TEAM_ID" ]; then
        echo "⚠️ Environment variables needed for notarization (APPLE_ID, APPLE_PASSWORD, APPLE_TEAM_ID) are not set."
        echo "   Skipping notarization and continuing. You can notarize manually later."
        echo "   To perform notarization, run the following command:"
        echo "   APPLE_ID=... APPLE_PASSWORD=... APPLE_TEAM_ID=... ./scripts/notarize.sh"
        return 1
    fi
    
    # Check Intel build directory
    mkdir -p "$INTEL_BUILD_DIR/dmg"
    
    # Check Intel DMG file
    INTEL_DMG_PATH=$(find "$INTEL_BUILD_DIR/dmg" -name "*-intel.dmg" | head -1)
    
    if [ -z "$INTEL_DMG_PATH" ]; then
        echo "❌ Cannot find Intel DMG file."
        return 1
    fi
    
    echo "🔍 Intel DMG file to notarize: $INTEL_DMG_PATH"
    
    # Create temporary app directory
    APP_TEMP_DIR="app/build/temp_intel_app"
    rm -rf "$APP_TEMP_DIR"
    mkdir -p "$APP_TEMP_DIR"
    
    # Mount DMG
    echo "💿 Mounting DMG..."
    MOUNT_POINT="/Volumes/$APP_NAME"
    
    # Unmount if already mounted
    if [ -d "$MOUNT_POINT" ]; then
        hdiutil detach "$MOUNT_POINT" -force || true
    fi
    
    # Mount DMG file
    hdiutil attach "$INTEL_DMG_PATH" -nobrowse || {
        echo "❌ Failed to mount DMG"
        return 1
    }
    
    # Check mount directory
    if [ ! -d "$MOUNT_POINT" ]; then
        echo "❌ Mount point not found: $MOUNT_POINT"
        echo "⚠️ It may have been mounted with a different volume name..."
        
        # Find alternative mount point
        POSSIBLE_MOUNT=$(ls -1 /Volumes/ | grep -i "Cursor" | head -1)
        if [ -n "$POSSIBLE_MOUNT" ]; then
            MOUNT_POINT="/Volumes/$POSSIBLE_MOUNT"
            echo "✅ Alternative mount point found: $MOUNT_POINT"
        else
            echo "❌ Mount point not found"
            return 1
        fi
    fi
    
    # Find .app file
    APP_IN_DMG=$(find "$MOUNT_POINT" -name "*.app" | head -1)
    
    if [ -z "$APP_IN_DMG" ]; then
        echo "❌ Could not find app inside DMG."
        hdiutil detach "$MOUNT_POINT" -force || true
        return 1
    fi
    
    # Copy app to required location for notarize.sh
    echo "📂 Copying app for notarization: $APP_IN_DMG -> $APP_TEMP_DIR/"
    cp -R "$APP_IN_DMG" "$APP_TEMP_DIR/" || {
        echo "❌ Failed to copy app"
        hdiutil detach "$MOUNT_POINT" -force || true
        return 1
    }
    
    # Unmount DMG
    hdiutil detach "$MOUNT_POINT" -force || true
    
    # Path to copied app
    COPIED_APP="$APP_TEMP_DIR/$(basename "$APP_IN_DMG")"
    
    if [ ! -d "$COPIED_APP" ]; then
        echo "❌ Failed to copy app."
        return 1
    fi
    
    # Run notarize.sh with Intel app path as environment variable
    echo "🚀 Running notarize.sh..."
    INTEL_APP="$COPIED_APP" ./scripts/notarize.sh
    NOTARIZE_RESULT=$?
    
    # Delete temporary folder after notarization
    rm -rf "$APP_TEMP_DIR"
    
    # Process results
    if [ $NOTARIZE_RESULT -eq 0 ]; then
        echo "✅ Intel version notarization complete!"
        return 0
    else
        echo "❌ Intel version notarization failed"
        return 1
    fi
}

# Main execution flow
echo "==== 🖥️  Building DMG for Intel Mac compatibility ===="
check_rosetta
download_intel_jdk
build_with_intel_jdk
finalize_build

echo "==== ✅ Build completed ===="
echo "📦 Intel-compatible DMG file is available at: $INTEL_BUILD_DIR/dmg/"
echo "🔍 Both ARM64 and Intel versions can now be found in:"
echo "   - ARM64 (Native): $BUILD_DIR/dmg/"
echo "   - Intel x86_64: $INTEL_BUILD_DIR/dmg/"

# Automatically run notarization after successful build
echo "🔐 Automatically notarizing Intel version DMG..."

notarize_dmg
exit_code=$?

if [ $exit_code -ne 0 ]; then
    echo "⚠️ There was a problem with notarization. You can run notarization manually."
    echo "   Please refer to ./scripts/notarize.sh script."
    echo "   Make sure required environment variables are set: APPLE_ID, APPLE_PASSWORD, APPLE_TEAM_ID"
else
    echo "✅ Notarization completed successfully!"
fi

echo ""
echo "🎉 Intel Mac build process completed!"