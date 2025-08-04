#!/usr/bin/env pwsh

# =================================================================
# Multi-resolution ICO file creation script
# This script creates a proper Windows ICO file from PNG sources
# 
# Usage: ./scripts/create-ico.ps1 [OutputPath]
# Default output: app/src/main/resources/icon.ico
# =================================================================

param(
    [string]$OutputPath = "app/src/main/resources/icon.ico",
    [switch]$Force = $false
)

# Check if running on Windows (required for proper ICO creation)
if (-not $IsWindows -and -not ($PSVersionTable.PSVersion.Major -le 5)) {
    Write-Warning "This script is optimized for Windows. Results may vary on other platforms."
}

# 소스 PNG 파일 (이 파일에서 다양한 해상도로 리사이즈)
$sourcePng = "app/src/main/resources/icon.png"

# 생성할 해상도들
$targetSizes = @(16, 32, 64, 128, 256)

Write-Host "🎨 Creating multi-resolution ICO file..." -ForegroundColor Green
Write-Host "📁 Output: $OutputPath" -ForegroundColor Cyan

# 소스 파일 존재 확인
if (-not (Test-Path $sourcePng)) {
    Write-Error "❌ Source PNG file not found: $sourcePng"
    exit 1
}

Write-Host "✅ Found source PNG: $sourcePng" -ForegroundColor Green

# 출력 디렉토리 생성
$outputDir = Split-Path $OutputPath -Parent
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

# 기존 파일 백업 (덮어쓰기 전)
if ((Test-Path $OutputPath) -and -not $Force) {
    $backup = "$OutputPath.backup.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Copy-Item $OutputPath $backup
    Write-Host "📋 Backup created: $backup" -ForegroundColor Yellow
}

try {
    # Method 1: ImageMagick을 시도 (가장 좋은 품질)
    $magickPath = Get-Command "magick" -ErrorAction SilentlyContinue
    if ($magickPath) {
        Write-Host "🔧 Using ImageMagick for ICO creation..." -ForegroundColor Blue
        
        # 다양한 해상도로 리사이즈하면서 ICO 생성
        $resizeArgs = ($targetSizes | ForEach-Object { "`"$sourcePng`" -resize ${_}x${_}" }) -join " "
        $command = "magick $resizeArgs `"$OutputPath`""
        
        Write-Host "Executing: $command" -ForegroundColor Gray
        Invoke-Expression $command
        
        if ($LASTEXITCODE -eq 0 -and (Test-Path $OutputPath)) {
            $fileInfo = Get-Item $OutputPath
            Write-Host "✅ ICO file created successfully with ImageMagick!" -ForegroundColor Green
            Write-Host "📊 File size: $($fileInfo.Length) bytes" -ForegroundColor Cyan
            Write-Host "🎯 Resolutions included: $($targetSizes -join 'x', ', ')" -ForegroundColor Cyan
            exit 0
        }
    }
    
    # Method 2: Windows API를 사용한 방법 (더 나은 호환성)
    Write-Host "🔧 Using Windows API for ICO creation..." -ForegroundColor Blue
    
    Add-Type -AssemblyName System.Drawing
    Add-Type -AssemblyName System.Windows.Forms
    
    # 소스 이미지 로드
    $sourceImage = [System.Drawing.Image]::FromFile((Resolve-Path $sourcePng).Path)
    
    # ICO 파일 헤더 구조체
    $iconDir = [byte[]]::new(6)
    $iconDir[0] = 0  # Reserved (must be 0)
    $iconDir[1] = 0
    $iconDir[2] = 1  # Type (1 = Icon)
    $iconDir[3] = 0
    $iconDir[4] = $targetSizes.Count  # Number of images
    $iconDir[5] = 0
    
    $iconEntries = @()
    $imageData = @()
    $dataOffset = 6 + (16 * $targetSizes.Count)  # Header + entries
    
    foreach ($size in $targetSizes) {
        Write-Host "🔄 Processing ${size}x${size}..." -ForegroundColor Gray
        
        # 이미지 리사이즈
        $resizedBitmap = [System.Drawing.Bitmap]::new($size, $size)
        $graphics = [System.Drawing.Graphics]::FromImage($resizedBitmap)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        
        $graphics.DrawImage($sourceImage, 0, 0, $size, $size)
        $graphics.Dispose()
        
        # PNG 바이트로 변환
        $memoryStream = [System.IO.MemoryStream]::new()
        $resizedBitmap.Save($memoryStream, [System.Drawing.Imaging.ImageFormat]::Png)
        $pngBytes = $memoryStream.ToArray()
        $memoryStream.Dispose()
        $resizedBitmap.Dispose()
        
        # 이미지 크기 결정 (ICO 형식에서 256은 0으로 표시)
        $icoSize = if ($size -eq 256) { 0 } else { $size }
        
        # ICO 엔트리 생성 (16 bytes)
        $entry = [byte[]]::new(16)
        $entry[0] = $icoSize   # Width
        $entry[1] = $icoSize   # Height
        $entry[2] = 0          # Color count (0 = no palette)
        $entry[3] = 0          # Reserved
        $entry[4] = 1          # Color planes (low byte)
        $entry[5] = 0          # Color planes (high byte)
        $entry[6] = 32         # Bits per pixel (low byte)
        $entry[7] = 0          # Bits per pixel (high byte)
        
        # Size of image data (little endian)
        $sizeBytes = [System.BitConverter]::GetBytes([uint32]$pngBytes.Length)
        $entry[8] = $sizeBytes[0]
        $entry[9] = $sizeBytes[1]
        $entry[10] = $sizeBytes[2]
        $entry[11] = $sizeBytes[3]
        
        # Offset to image data (little endian)
        $offsetBytes = [System.BitConverter]::GetBytes([uint32]$dataOffset)
        $entry[12] = $offsetBytes[0]
        $entry[13] = $offsetBytes[1]
        $entry[14] = $offsetBytes[2]
        $entry[15] = $offsetBytes[3]
        
        $iconEntries += $entry
        $imageData += $pngBytes
        $dataOffset += $pngBytes.Length
    }
    
    # 소스 이미지 정리
    $sourceImage.Dispose()
    
    # ICO 파일 작성
    $stream = [System.IO.File]::Create($OutputPath)
    try {
        # 헤더 쓰기
        $stream.Write($iconDir, 0, $iconDir.Length)
        
        # 엔트리들 쓰기
        foreach ($entry in $iconEntries) {
            $stream.Write($entry, 0, $entry.Length)
        }
        
        # 이미지 데이터 쓰기
        foreach ($data in $imageData) {
            $stream.Write($data, 0, $data.Length)
        }
    }
    finally {
        $stream.Close()
    }
    
    if (Test-Path $OutputPath) {
        $fileInfo = Get-Item $OutputPath
        Write-Host "✅ Multi-resolution ICO file created successfully!" -ForegroundColor Green
        Write-Host "📊 File size: $($fileInfo.Length) bytes" -ForegroundColor Cyan
        Write-Host "🎯 Resolutions included: $(($targetSizes | ForEach-Object { "${_}x${_}" }) -join ', ')" -ForegroundColor Cyan
        
        # 유효성 검사
        try {
            $testIcon = [System.Drawing.Icon]::new($OutputPath)
            $testIcon.Dispose()
            Write-Host "✅ ICO file validation passed!" -ForegroundColor Green
        }
        catch {
            Write-Warning "⚠️ ICO file created but validation failed: $($_.Exception.Message)"
        }
    }
    else {
        throw "ICO file was not created"
    }
}
catch {
    Write-Error "❌ Failed to create ICO file: $($_.Exception.Message)"
    
    # Fallback: 단일 이미지로 ICO 생성
    Write-Host "🔄 Trying fallback method with source PNG..." -ForegroundColor Yellow
    
    try {
        if (Test-Path $sourcePng) {
            $img = [System.Drawing.Image]::FromFile((Resolve-Path $sourcePng).Path)
            $img.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Icon)
            $img.Dispose()
            
            $fileInfo = Get-Item $OutputPath
            Write-Host "✅ Fallback ICO created: $($fileInfo.Length) bytes" -ForegroundColor Green
        }
        else {
            throw "No source PNG found"
        }
    }
    catch {
        Write-Error "❌ Fallback also failed: $($_.Exception.Message)"
        exit 1
    }
}

Write-Host ""
Write-Host "🎉 ICO creation completed!" -ForegroundColor Green
Write-Host "📁 Output file: $OutputPath" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Test the build: ./gradlew clean app:packageMsi" -ForegroundColor Gray
Write-Host "2. If successful, commit the new icon: git add $OutputPath" -ForegroundColor Gray