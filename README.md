# 🎨 falcraft - AI-Powered 3D Generation & Texture Remix for Minecraft

A Fabric mod for Minecraft 1.21.1 that brings AI-powered 3D model generation and texture remixing directly into your game.

[![GitHub stars](https://img.shields.io/github/stars/blendi-remade/falcraft)](https://github.com/blendi-remade/falcraft/stargazers)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)
![Fabric API](https://img.shields.io/badge/Fabric%20API-0.107.0-blue)

## 🎮 Commands

### Generate 3D Structures

```
/fal generate <size> <prompt>
```

**Examples:**
```
/fal generate 48 medieval castle
/fal generate 64 spongebob squarepants
/fal generate 96 ancient temple
```

**Controls in preview mode:**
- **Right-click** - Place the structure
- **G** - Rotate 90°

**Size guide:** 16-32 (small), 48-64 (recommended), 96-128 (large/detailed)

Generation takes ~30 seconds using Z-Image + SAM-3D pipeline.

### Legacy Mode (Meshy-6)

For UV-textured models (~7 minutes):
```
/fal generate legacy <size> <prompt>
```

### Remix Block Textures

Point at any block and run:
```
/fal remix <prompt>
```

## 🚀 Setup

1. Install [Fabric Loader](https://fabricmc.net/use/) + [Fabric API](https://modrinth.com/mod/fabric-api) for MC 1.21.1
2. Drop the mod JAR in `.minecraft/mods/`
3. Launch Minecraft and run:
   ```
   /fal setkey YOUR_API_KEY
   ```
   Get your API key at [fal.ai/dashboard/keys](https://fal.ai/dashboard/keys)

That's it! Your key is saved to `config/falcraft/api-key.txt`.

### Check Status
```
/fal status
```
Shows if your API key is configured.

## 🧠 How It Works

1. **Z-Image Turbo** generates a 2D image from your prompt
2. **SAM-3D** converts the image to a 3D GLB model with vertex colors
3. **Voxelizer** converts the mesh to Minecraft blocks using triangle-voxel intersection
4. **Block Mapper** matches colors using CIE-LAB perceptual color space (100+ block palette)
5. **Ghost Preview** shows the structure before placement
6. **Animated Build** places blocks layer-by-layer

## 🐛 Troubleshooting

- **"API key not configured"** - Run `/fal setkey YOUR_KEY` or check with `/fal status`
- **Structure not visible** - Look where you want to place (up to 200 blocks away)
- **Colors look off** - The 100+ block palette maps colors as close as Minecraft allows

## 📜 License

CC0 1.0 - Public domain. Use freely!

Built with [Fabric](https://fabricmc.net/) and [fal.ai](https://fal.ai).
