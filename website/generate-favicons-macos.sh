#!/bin/bash

# Generate favicons from the VajraPulse logo using macOS sips
# This script works on macOS without any additional dependencies

LOGO_PATH="assets/images/vajrapulse_logo.png"
OUTPUT_DIR="assets/images/favicons"

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "Generating favicons from logo using macOS sips..."

# Check if logo exists
if [ ! -f "$LOGO_PATH" ]; then
    echo "❌ Error: Logo file not found at $LOGO_PATH"
    echo "   Make sure you're running this script from the website directory."
    exit 1
fi

# Generate standard favicon sizes
echo "  Generating standard favicon sizes..."
sips -z 16 16 "$LOGO_PATH" --out "$OUTPUT_DIR/favicon-16x16.png" > /dev/null 2>&1
sips -z 32 32 "$LOGO_PATH" --out "$OUTPUT_DIR/favicon-32x32.png" > /dev/null 2>&1
sips -z 48 48 "$LOGO_PATH" --out "$OUTPUT_DIR/favicon-48x48.png" > /dev/null 2>&1

# Generate Apple touch icon (180x180)
echo "  Generating Apple touch icon..."
sips -z 180 180 "$LOGO_PATH" --out "$OUTPUT_DIR/apple-touch-icon.png" > /dev/null 2>&1

# Generate Android Chrome icons
echo "  Generating Android Chrome icons..."
sips -z 192 192 "$LOGO_PATH" --out "$OUTPUT_DIR/android-chrome-192x192.png" > /dev/null 2>&1
sips -z 512 512 "$LOGO_PATH" --out "$OUTPUT_DIR/android-chrome-512x512.png" > /dev/null 2>&1

# Generate site.webmanifest compatible icons
sips -z 192 192 "$LOGO_PATH" --out "$OUTPUT_DIR/icon-192x192.png" > /dev/null 2>&1
sips -z 512 512 "$LOGO_PATH" --out "$OUTPUT_DIR/icon-512x512.png" > /dev/null 2>&1

# Generate ICO file (using iconutil on macOS)
echo "  Generating ICO file..."
ICONSET_DIR="$OUTPUT_DIR/favicon.iconset"
mkdir -p "$ICONSET_DIR"

# Create all required sizes for ICO
sips -z 16 16 "$LOGO_PATH" --out "$ICONSET_DIR/icon_16x16.png" > /dev/null 2>&1
sips -z 32 32 "$LOGO_PATH" --out "$ICONSET_DIR/icon_16x16@2x.png" > /dev/null 2>&1
sips -z 32 32 "$LOGO_PATH" --out "$ICONSET_DIR/icon_32x32.png" > /dev/null 2>&1
sips -z 64 64 "$LOGO_PATH" --out "$ICONSET_DIR/icon_32x32@2x.png" > /dev/null 2>&1
sips -z 128 128 "$LOGO_PATH" --out "$ICONSET_DIR/icon_128x128.png" > /dev/null 2>&1
sips -z 256 256 "$LOGO_PATH" --out "$ICONSET_DIR/icon_128x128@2x.png" > /dev/null 2>&1
sips -z 256 256 "$LOGO_PATH" --out "$ICONSET_DIR/icon_256x256.png" > /dev/null 2>&1
sips -z 512 512 "$LOGO_PATH" --out "$ICONSET_DIR/icon_256x256@2x.png" > /dev/null 2>&1
sips -z 512 512 "$LOGO_PATH" --out "$ICONSET_DIR/icon_512x512.png" > /dev/null 2>&1
sips -z 1024 1024 "$LOGO_PATH" --out "$ICONSET_DIR/icon_512x512@2x.png" > /dev/null 2>&1

# Convert iconset to icns, then we'll need to convert to ico
iconutil -c icns "$ICONSET_DIR" -o "$OUTPUT_DIR/favicon.icns" 2>/dev/null

# For ICO, we'll just copy the 32x32 PNG as favicon.ico (most browsers accept PNG as ICO)
cp "$OUTPUT_DIR/favicon-32x32.png" "$OUTPUT_DIR/favicon.ico"

# Clean up iconset directory
rm -rf "$ICONSET_DIR"

echo ""
echo "✅ All favicons generated successfully in $OUTPUT_DIR/"
echo ""
echo "Generated files:"
ls -lh "$OUTPUT_DIR/" | grep -v "^total" | awk '{print "  - " $9 " (" $5 ")"}'

