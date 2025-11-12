# 🎨 falcraft - AI-Powered Minecraft Texture Remix Mod

A Fabric mod for Minecraft 1.21.10 that brings AI-powered texture generation directly into your game! Point at any block, describe how you want it to look, and watch as AI instantly remixes the texture in real-time.

[![GitHub issues](https://img.shields.io/github/issues/blendi-remade/falcraft)](https://github.com/blendi-remade/falcraft/issues)
[![GitHub stars](https://img.shields.io/github/stars/blendi-remade/falcraft)](https://github.com/blendi-remade/falcraft/stargazers)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.10-brightgreen)
![Fabric API](https://img.shields.io/badge/Fabric%20API-0.136.0-blue)
![License](https://img.shields.io/badge/License-CC0-lightgrey)

## 🎥 See It In Action

Watch as we transform Minecraft blocks in real-time using AI-powered texture generation:

[![Watch the Demo](https://img.youtube.com/vi/2xAbEnfF1SM/maxresdefault.jpg)](https://www.youtube.com/watch?v=2xAbEnfF1SM)

> *Point at any block, run `/fal remix <your prompt>`, and watch the magic happen!*

## ✨ Features

- 🎯 **Point-and-Remix**: Look at any block and remix its texture with a simple command
- 🤖 **AI-Powered**: Uses Fal AI's nano-banana/edit model for high-quality texture generation
- ⚡ **Instant Application**: Changes apply immediately via dynamic resource packs - no restart needed!
- 🔄 **Multi-Texture Support**: Automatically detects and remixes ALL textures for complex blocks (like grass blocks with multiple faces)
- 🧵 **Non-Blocking**: Runs in background threads so your game stays smooth
- 💾 **Persistent**: Remixed textures are saved and survive game restarts

## 🎮 Usage

1. **Look at any block** in the game world
2. **Run the command**: `/fal remix <your creative prompt>`
3. **Watch the magic happen!** The mod will:
   - Extract the block's texture
   - Send it to fal with your prompt
   - Apply the remixed texture instantly

### Examples

```
/fal remix glowing alien texture
/fal remix mossy ancient ruins
/fal remix cyberpunk neon glowing
/fal remix made of pure gold shiny
/fal remix candy and sweets themed
/fal remix lava texture molten fire
```

## 🚀 Installation

### For Players

1. **Prerequisites**:
   - ✅ [Minecraft 1.21.10](https://www.minecraft.net/) (you probably have this!)
   - ✅ [Java 21+](https://adoptium.net/temurin/releases/) (required for Minecraft 1.21+)
   - 📦 [Fabric Loader 0.17.3+](https://fabricmc.net/use/) (install if you haven't)
   - 📦 [Fabric API 0.136.0+](https://modrinth.com/mod/fabric-api) (goes in your mods folder)

2. **Get a fal API Key**:
   - Sign up at [fal.ai](https://fal.ai)
   - Get your API key from the dashboard

3. **Configure the mod**:
   - Create a `.env` file in your `.minecraft` directory
   - Add your API key:
     ```
     FAL_API_KEY=your_api_key_here
     ```
   - For development: place `.env` in the `run/` directory

4. **Install the mod**:
   - Download the latest release from [Releases](https://github.com/blendi-remade/falcraft/releases)
   - Place the JAR file in your `.minecraft/mods/` folder
   - Launch Minecraft with the Fabric profile

### For Developers

**Requirements**: [Java 21+](https://adoptium.net/temurin/releases/) (Gradle handles everything else!)

1. **Clone the repository**:
   ```bash
   git clone https://github.com/blendi-remade/falcraft.git
   cd falcraft
   ```

2. **Set up your API key**:
   ```bash
   echo "FAL_API_KEY=your_api_key_here" > run/.env
   ```

3. **Run the development client**:
   ```bash
   ./gradlew runClient
   ```

4. **Build the mod**:
   ```bash
   ./gradlew build
   ```
   The compiled JAR will be in `build/libs/`

## 🏗️ How It Works

falcraft uses a sophisticated pipeline to seamlessly integrate AI texture generation into Minecraft:

```
┌─────────────────────────────────────────────────────────────┐
│  Player runs: /fal remix make it glowing                    │
└──────────────────────┬──────────────────────────────────────┘
                       ↓
          ┌────────────────────────┐
          │  RemixCommand          │  Coordinates the entire process
          │  (Background Thread)   │  Keeps game responsive
          └────────┬───────────────┘
                   ↓
          ┌────────────────────────┐
          │ ClientTextureGrabber   │  • Raycasts to find target block
          │                        │  • Extracts block texture
          └────────┬───────────────┘  • Saves as temporary PNG
                   ↓
          ┌────────────────────────┐
          │ FalAPI                 │  • Converts image to base64
          │                        │  • Submits to fal queue
          └────────┬───────────────┘  • Polls for completion
                   ↓                  • Downloads remixed PNG
          ┌────────────────────────┐
          │ PackIO                 │  • Writes to dynamic resource pack
          │                        │  • Hot-reloads Minecraft resources
          └────────┬───────────────┘  • No restart needed!
                   ↓
          ┌────────────────────────┐
          │ ✨ Texture Applied!    │
          └────────────────────────┘
```

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/falcraft/
│   │   └── ExampleMod.java           # Main mod entrypoint
│   └── resources/
│       ├── fabric.mod.json            # Mod metadata
│       └── modid.mixins.json          # Mixin configuration
│
└── client/
    ├── java/com/falcraft/
    │   ├── FalcraftClient.java        # Client entrypoint
    │   ├── commands/
    │   │   └── RemixCommand.java      # /fal remix command handler
    │   └── util/
    │       ├── ClientTextureGrabber.java  # Extracts block textures
    │       ├── FalAPI.java                # Fal AI API integration
    │       └── PackIO.java                # Resource pack management
    └── resources/
        └── modid.client.mixins.json   # Client mixin config
```

## 🔧 Technical Details

### Technologies Used

- **Fabric API**: Modern Minecraft modding framework
- **fal**: Nano-banana/edit model for image-to-image generation
- **Java 21**: Modern Java features (HttpClient, pattern matching)
- **Dynamic Resource Packs**: Hot-reload textures without restart
- **Reflection**: Access Minecraft's internal texture data

### Key Features

- **Thread-Safe UI Updates**: All chat messages scheduled on main thread
- **Async Processing**: API calls run on background threads
- **Queue-Based Processing**: Implements fal's queue workflow (submit → poll → fetch)
- **Automatic Resource Pack Management**: Creates and enables pack dynamically

### Performance

- ⏱️ **Texture Remix Time**: 5-20 seconds (depends on fal queue)
- 🎮 **Game Impact**: Zero! All heavy processing is async
- 💾 **Memory Usage**: Minimal - temporary files cleaned up automatically
- 🔄 **Reload Time**: <1 second for resource pack hot-reload

## 🤝 Contributing

Contributions are welcome! Here are some ideas:

- [ ] Add texture history/undo functionality
- [ ] Implement preset prompt templates
- [ ] Support for entity textures (partially implemented)
- [ ] Support for item textures
- [ ] Batch remix multiple blocks
- [ ] GUI for easier prompt input
- [ ] Texture gallery/sharing system

### Development Setup

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Test thoroughly with `./gradlew runClient`
5. Commit: `git commit -m 'Add amazing feature'`
6. Push: `git push origin feature/amazing-feature`
7. Open a Pull Request

## 🐛 Troubleshooting

### "FAL_API_KEY not found" Error

**Solution**: Create a `.env` file in your Minecraft directory:
```bash
# On Windows: C:\Users\YourName\AppData\Roaming\.minecraft\.env
# On Linux/Mac: ~/.minecraft/.env
FAL_API_KEY=your_key_here
```

### "You must be looking at a block" Error

**Solution**: Make sure your crosshair is pointing directly at a block (not air)

### Texture Doesn't Change

**Solutions**:
1. Check logs for API errors: `logs/latest.log`
2. Verify your API key is valid
3. Try pressing F3+T to force resource reload
4. Some modded blocks may not work yet

### Resource Pack Shows as "Incompatible"

**Solution**: Delete the old pack and let the mod regenerate it:
```bash
# Delete: .minecraft/resourcepacks/falcraft_generated/pack.mcmeta
# Then restart Minecraft
```

## 📜 License

This project is released under the **CC0 1.0 Universal License** - public domain dedication.

Feel free to:
- ✅ Use commercially
- ✅ Modify and redistribute
- ✅ Use in your own projects
- ✅ No attribution required (but appreciated!)

## 🙏 Credits

Built with:
- [Fabric](https://fabricmc.net/) - Modern Minecraft modding framework
- [fal](https://fal.ai/) - AI model infrastructure
- [Mojang Mappings](https://github.com/FabricMC/yarn) - For Minecraft code access

Special thanks to the Fabric community for excellent documentation and tools!

## 📞 Support

- 🐛 **Bug Reports**: [Open an issue](https://github.com/blendi-remade/falcraft/issues)
- 💡 **Feature Requests**: [Start a discussion](https://github.com/blendi-remade/falcraft/discussions)
- 💬 **Questions**: Check existing issues or start a discussion

## 🔮 Future Plans

- Support for more AI models
- Entity texture support (fix detection issues)
- Texture animation support
- Multiplayer sync (server-side texture distribution)
- Texture marketplace/sharing
- Integration with more fal models (text-to-texture, style transfer, etc.)

---

**Made with ❤️ and ☕ by the Fabric modding community**

*Transform your Minecraft world, one block at a time!* ✨
