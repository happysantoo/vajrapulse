#!/bin/bash

# Generate favicons from the VajraPulse logo
# Requires ImageMagick (install via: brew install imagemagick)

LOGO_PATH="assets/images/vajrapulse_logo.png"
OUTPUT_DIR="assets/images/favicons"

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "Generating favicons from logo..."

# Generate standard favicon sizes
convert "$LOGO_PATH" -resize 16x16 "$OUTPUT_DIR/favicon-16x16.png"
convert "$LOGO_PATH" -resize 32x32 "$OUTPUT_DIR/favicon-32x32.png"
convert "$LOGO_PATH" -resize 48x48 "$OUTPUT_DIR/favicon-48x48.png"

# Generate Apple touch icon
convert "$LOGO_PATH" -resize 180x180 -background white -gravity center -extent 180x180 "$OUTPUT_DIR/apple-touch-icon.png"

# Generate Android Chrome icons
convert "$LOGO_PATH" -resize 192x192 -background white -gravity center -extent 192x192 "$OUTPUT_DIR/android-chrome-192x192.png"
convert "$LOGO_PATH" -resize 512x512 -background white -gravity center -extent 512x512 "$OUTPUT_DIR/android-chrome-512x512.png"

# Generate ICO file (multi-size)
convert "$LOGO_PATH" -resize 16x16 -resize 32x32 -resize 48x48 "$OUTPUT_DIR/favicon.ico"

# Generate site.webmanifest compatible icons
convert "$LOGO_PATH" -resize 192x192 -background white -gravity center -extent 192x192 "$OUTPUT_DIR/icon-192x192.png"
convert "$LOGO_PATH" -resize 512x512 -background white -gravity center -extent 512x512 "$OUTPUT_DIR/icon-512x512.png"

echo "Favicons generated successfully in $OUTPUT_DIR/"
echo ""
echo "Generated files:"
ls -lh "$OUTPUT_DIR/"

