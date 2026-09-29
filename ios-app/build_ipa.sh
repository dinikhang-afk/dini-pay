#!/usr/bin/env bash
set -e

APP_NAME="DiniPay"
IPA_NAME="DiniPay"
BUILD_DIR="build"

OUTPUT_DIR="output"
PAYLOAD_DIR="${BUILD_DIR}/Payload"
APP_DIR="${PAYLOAD_DIR}/${APP_NAME}.app"

echo "=== [1/4] Chuẩn bị thư mục build ==="
rm -rf "${BUILD_DIR}" "${OUTPUT_DIR}"
mkdir -p "${APP_DIR}"
mkdir -p "${OUTPUT_DIR}"

echo "=== [2/4] Biên dịch mã nguồn Swift ==="
SWIFT_FILES=$(find Sources -name "*.swift")
SDK_PATH=$(xcrun --sdk iphoneos --show-sdk-path 2>/dev/null || echo "")

if [ -n "$SDK_PATH" ]; then
    xcrun -sdk iphoneos swiftc \
        -target arm64-apple-ios16.0 \
        -sdk "${SDK_PATH}" \
        -O \
        -parse-as-library \
        -framework UIKit \
        -framework SwiftUI \
        -framework Foundation \
        -framework CoreServices \
        -framework StoreKit \
        ${SWIFT_FILES} \
        -o "${APP_DIR}/${APP_NAME}"
else
    echo "⚠️ Không tìm thấy macOS Xcode SDK, kiểm tra Theos..."
    if command -v make &> /dev/null && [ -n "$THEOS" ]; then
        make package FINALPACKAGE=1
        cp packages/*.deb "${OUTPUT_DIR}/" || true
        exit 0
    fi
fi

echo "=== [3/4] Copy Info.plist & Assets ==="
cp Info.plist "${APP_DIR}/Info.plist"
if [ -f "AppIcon.png" ]; then
    cp AppIcon.png "${APP_DIR}/AppIcon.png"
    cp AppIcon.png "${APP_DIR}/AppIcon60x60@2x.png" 2>/dev/null || true
    cp AppIcon.png "${APP_DIR}/AppIcon60x60@3x.png" 2>/dev/null || true
fi

# Fake codesign with ldid if available
if command -v ldid &> /dev/null; then
    echo "Signing with entitlements..."
    ldid -Sentitlements.plist "${APP_DIR}/${APP_NAME}"
fi

echo "=== [4/4] Đóng gói thành file .ipa & .tipa (TrollStore) ==="
cd "${BUILD_DIR}"
zip -qr "../${OUTPUT_DIR}/${IPA_NAME}.ipa" Payload
cp "../${OUTPUT_DIR}/${IPA_NAME}.ipa" "../${OUTPUT_DIR}/${IPA_NAME}.tipa"
cd ..

echo "✅ ĐÃ TẠO THÀNH CÔNG FILE IPA:"
echo "   ➜ ${OUTPUT_DIR}/${IPA_NAME}.ipa (Dùng cho Sideloadly / AltStore / Esign)"
echo "   ➜ ${OUTPUT_DIR}/${IPA_NAME}.tipa (Dùng cho TrollStore - Không bao giờ bị thu hồi)"
