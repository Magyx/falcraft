# 🎨 falcraft - AI-Powered 3D Generation & Texture Remix for Minecraft

A Fabric mod for Minecraft 1.21.1 that brings AI-powered 3D model generation and texture remixing directly into your game! Generate entire 3D structures from text prompts, or remix any block's texture in real-time.

[![GitHub issues](https://img.shields.io/github/issues/blendi-remade/falcraft)](https://github.com/blendi-remade/falcraft/issues)
[![GitHub stars](https://img.shields.io/github/stars/blendi-remade/falcraft)](https://github.com/blendi-remade/falcraft/stargazers)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![Fabric API](https://img.shields.io/badge/Fabric%20API-0.107.0-blue)
![License](https://img.shields.io/badge/License-CC0-lightgrey)

## 🎥 See It In Action

Watch as we transform Minecraft with AI-powered generation:

[![Watch the Demo](https://img.youtube.com/vi/2xAbEnfF1SM/maxresdefault.jpg)](https://www.youtube.com/watch?v=2xAbEnfF1SM)

## ✨ Features

- 🏗️ **3D Model Generation**: Generate complete 3D structures from text prompts
- 🎨 **Perceptual Color Matching**: Uses LAB color space for human-vision-accurate block selection
- 🎯 **Texture Remixing**: Point at any block and remix its texture with AI
- 🤖 **Powered by fal.ai**: Uses Meshy v6 for 3D generation and nano-banana for texture editing
- ⚡ **Dynamic Resource Packs**: Texture changes apply instantly - no restart needed
- 🧵 **Non-Blocking**: All processing runs in background threads

## 🎮 Usage

### Generate 3D Models (NEW!)

Create entire structures from text descriptions:

```
/fal generate <size> <prompt>
```

**Examples:**
```
/fal generate 32 a cute robot
/fal generate 48 medieval castle with towers
/fal generate 64 majestic desert palace
/fal generate 128 ancient dragon statue
```

**Size Guide:**
- **16-32**: Fast testing, rough shapes
- **48**: Balanced detail/speed (recommended)
- **64**: High detail
- **96-128**: Maximum detail (slower placement)

**How it works:**
1. Meshy v6 generates a textured 3D model (5-10 minutes)
2. Model is voxelized into Minecraft blocks
3. Colors are mapped using perceptual LAB color space
4. Structure is placed flat on the ground in your look direction

### Remix Block Textures

Transform existing block textures with AI:

```
/fal remix <prompt>
```

**Examples:**
```
/fal remix glowing alien texture
/fal remix mossy ancient ruins
/fal remix cyberpunk neon
```

## 🚀 Quick Start

1. **Prerequisites**:
   - Minecraft 1.21.1 + [Fabric Loader](https://fabricmc.net/use/) + [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Java 21+](https://adoptium.net/temurin/releases/)

2. **Get API Key**:
   - Sign up at [fal.ai](https://fal.ai) and get your API key

3. **Configure**:
   - Create `.env` in `.minecraft/` directory:
   ```
   FAL_API_KEY=your_api_key_here
   ```

4. **Install**:
   - Download from [Releases](https://github.com/blendi-remade/falcraft/releases)
   - Place JAR in `.minecraft/mods/`
   - Launch Minecraft!

### For Developers

```bash
git clone https://github.com/blendi-remade/falcraft.git
cd falcraft
echo "FAL_API_KEY=your_key" > run/.env
./gradlew runClient
```

## 🧠 Technical Overview

### 3D Generation Pipeline

1. **Meshy v6 AI** generates textured GLB model from text prompt
2. **Texture Extraction** pulls embedded textures from GLB binary
3. **Voxelization** converts smooth mesh into Minecraft block grid
4. **Perceptual Color Matching** uses LAB color space (matches human vision, not just RGB math)
5. **Smart Placement** finds ground and places structure flat

### Texture Remixing Pipeline

1. **Raycast** finds target block and extracts texture
2. **fal nano-banana** remixes texture with your prompt
3. **Dynamic Resource Pack** applies changes instantly

### Why LAB Color Space?

Instead of simple RGB distance, we use **CIE LAB color space**:
- Matches how humans actually perceive color differences
- Prevents bad matches (e.g., red → orange just because RGB distance is small)
- Uses D65 illuminant for realistic daylight matching
- Results in more accurate and natural-looking block selection

## 🐛 Troubleshooting

**"FAL_API_KEY not found"**
- Create `.env` file in `.minecraft/` directory with `FAL_API_KEY=your_key`

**Texture doesn't change**
- Check `logs/latest.log` for API errors
- Verify API key is valid
- Press F3+T to force reload

**Model placement issues**
- Structures place in your horizontal look direction
- Automatically finds ground and sits flat
- Ensure you're looking at an area with ground nearby

## 🤝 Contributing

Ideas welcome! Fork, create a feature branch, test with `./gradlew runClient`, and open a PR.

**Feature Ideas:**
- Undo/history for textures and models
- Preset prompts library
- Entity/item texture support
- Model scaling and rotation commands

## 📜 License & Credits

**CC0 1.0 Universal** - Public domain. Use freely, modify, redistribute, no attribution required!

Built with [Fabric](https://fabricmc.net/), [fal.ai](https://fal.ai/), and [Mojang Mappings](https://github.com/FabricMC/yarn).

## 📞 Support & Links

- 🐛 [Report Issues](https://github.com/blendi-remade/falcraft/issues)
- 💬 [Discussions](https://github.com/blendi-remade/falcraft/discussions)
- ⭐ Star the repo if you like it!

---

*Transform your Minecraft world with AI!* ✨🏗️
