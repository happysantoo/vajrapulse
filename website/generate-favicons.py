#!/usr/bin/env python3
"""
Generate favicons from the VajraPulse logo.
Requires Pillow: pip install Pillow
"""

import os
from pathlib import Path
from PIL import Image

LOGO_PATH = "assets/images/vajrapulse_logo.png"
OUTPUT_DIR = Path("assets/images/favicons")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

print("Generating favicons from logo...")

try:
    # Open the logo
    logo = Image.open(LOGO_PATH)
    
    # Convert to RGBA if needed
    if logo.mode != 'RGBA':
        logo = logo.convert('RGBA')
    
    # Generate standard favicon sizes
    sizes = [
        (16, 16, "favicon-16x16.png"),
        (32, 32, "favicon-32x32.png"),
        (48, 48, "favicon-48x48.png"),
    ]
    
    for width, height, filename in sizes:
        favicon = logo.resize((width, height), Image.Resampling.LANCZOS)
        output_path = OUTPUT_DIR / filename
        favicon.save(output_path, "PNG")
        print(f"  ✓ Generated {filename} ({width}x{height})")
    
    # Generate Apple touch icon (180x180 with white background)
    apple_icon = logo.resize((180, 180), Image.Resampling.LANCZOS)
    apple_bg = Image.new('RGBA', (180, 180), (255, 255, 255, 255))
    apple_bg.paste(apple_icon, (0, 0), apple_icon if apple_icon.mode == 'RGBA' else None)
    apple_bg.save(OUTPUT_DIR / "apple-touch-icon.png", "PNG")
    print(f"  ✓ Generated apple-touch-icon.png (180x180)")
    
    # Generate Android Chrome icons
    android_sizes = [
        (192, 192, "android-chrome-192x192.png"),
        (512, 512, "android-chrome-512x512.png"),
    ]
    
    for width, height, filename in android_sizes:
        icon = logo.resize((width, height), Image.Resampling.LANCZOS)
        bg = Image.new('RGBA', (width, height), (255, 255, 255, 255))
        bg.paste(icon, (0, 0), icon if icon.mode == 'RGBA' else None)
        bg.save(OUTPUT_DIR / filename, "PNG")
        print(f"  ✓ Generated {filename} ({width}x{height})")
    
    # Generate ICO file (multi-size)
    ico_sizes = [16, 32, 48]
    ico_images = [logo.resize((s, s), Image.Resampling.LANCZOS) for s in ico_sizes]
    ico_path = OUTPUT_DIR / "favicon.ico"
    ico_images[0].save(ico_path, format='ICO', sizes=[(s, s) for s in ico_sizes])
    print(f"  ✓ Generated favicon.ico (multi-size)")
    
    # Generate site.webmanifest compatible icons
    manifest_sizes = [
        (192, 192, "icon-192x192.png"),
        (512, 512, "icon-512x512.png"),
    ]
    
    for width, height, filename in manifest_sizes:
        icon = logo.resize((width, height), Image.Resampling.LANCZOS)
        bg = Image.new('RGBA', (width, height), (255, 255, 255, 255))
        bg.paste(icon, (0, 0), icon if icon.mode == 'RGBA' else None)
        bg.save(OUTPUT_DIR / filename, "PNG")
        print(f"  ✓ Generated {filename} ({width}x{height})")
    
    print(f"\n✅ All favicons generated successfully in {OUTPUT_DIR}/")
    print(f"\nGenerated files:")
    for file in sorted(OUTPUT_DIR.glob("*")):
        size = file.stat().st_size
        print(f"  - {file.name} ({size:,} bytes)")

except ImportError:
    print("❌ Error: Pillow is not installed.")
    print("   Install it with: pip install Pillow")
    exit(1)
except FileNotFoundError:
    print(f"❌ Error: Logo file not found at {LOGO_PATH}")
    print("   Make sure you're running this script from the website directory.")
    exit(1)
except Exception as e:
    print(f"❌ Error: {e}")
    exit(1)

