# VajraPulse Website

This is the official website for VajraPulse, a high-performance load testing framework built on Java 21 virtual threads.

## Structure

```
website/
├── index.html              # Main homepage
├── assets/
│   ├── css/
│   │   └── styles.css      # All styling
│   ├── js/
│   │   └── script.js       # Interactive functionality
│   ├── images/
│   │   ├── vajrapulse_logo.png  # Logo file
│   │   ├── favicon.svg          # SVG favicon (modern browsers)
│   │   └── favicons/            # Generated PNG favicons
│   └── site.webmanifest    # Web app manifest
├── generate-favicons-macos.sh  # macOS favicon generator
├── generate-favicons.py        # Python favicon generator (requires Pillow)
└── README.md               # This file
```

## Features

The website includes:

- **Hero Section**: Eye-catching introduction with code preview
- **Features Section**: Showcases 6 key features with icons
- **Advantages Section**: Highlights why VajraPulse is unique
- **Code Examples**: Interactive tabs showing:
  - Basic Task implementation
  - Load pattern examples
  - Programmatic execution
  - CLI usage
- **Getting Started**: Installation instructions for Gradle and Maven
- **Performance Benchmarks**: Key metrics display
- **Responsive Design**: Works on all devices

## Usage

### Local Development

Simply open `index.html` in a web browser:

```bash
# Using Python
python3 -m http.server 8000

# Using Node.js
npx serve

# Or just open the file
open index.html
```

### Deployment

The website is static HTML/CSS/JS, so it can be deployed to:

- **GitHub Pages**: Push to `gh-pages` branch
- **Netlify**: Drag and drop the `website` folder
- **Vercel**: Connect your repository
- **AWS S3**: Upload files to S3 bucket with static hosting
- **Any web server**: Upload files via FTP/SFTP

### Customization

- **Colors**: Edit CSS variables in `assets/css/styles.css` (`:root` section)
- **Content**: Edit `index.html` directly
- **Logo**: Replace `assets/images/vajrapulse_logo.png` with your own

## Browser Support

- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)
- Mobile browsers (iOS Safari, Chrome Mobile)

## Favicons

The website includes comprehensive favicon support:

- **SVG Favicon**: Modern, scalable favicon (`favicon.svg`)
- **PNG Favicons**: Multiple sizes (16x16, 32x32, 48x48)
- **Apple Touch Icon**: 180x180 for iOS devices
- **Android Icons**: 192x192 and 512x512 for Android Chrome
- **ICO File**: Traditional favicon.ico for older browsers

### Generating Favicons

If you need to regenerate favicons from the logo:

**On macOS:**
```bash
cd website
./generate-favicons-macos.sh
```

**With Python (requires Pillow):**
```bash
cd website
pip install Pillow
python3 generate-favicons.py
```

**With ImageMagick:**
```bash
cd website
./generate-favicons.sh
```

## Notes

- The website uses modern CSS features (CSS Grid, Flexbox, CSS Variables)
- JavaScript is used for interactivity but the site works without it
- All external fonts are loaded from Google Fonts
- The logo is included from the project root
- Favicons are automatically linked in the HTML head section

## Logo Design Suggestion

The current logo is functional but could be enhanced. Consider:

1. **Simplified Version**: A cleaner, more minimal design for web use
2. **SVG Format**: Vector format for scalability
3. **Icon Variant**: A simplified icon version for favicons
4. **Dark Mode Variant**: A version optimized for dark backgrounds

Would you like me to create an improved logo design?

