# Changelog

All notable changes to Falcraft will be documented in this file.

## [1.0.0] - 2024-12-08

### Added
- `/fal generate <size> <prompt>` - AI-powered 3D structure generation using Z-Image + SAM-3D pipeline (~30 seconds)
- `/fal generate legacy <size> <prompt>` - Original Meshy-6 pipeline for high-quality results (~7 minutes)
- `/fal remix` - AI texture remixing for existing blocks
- Ghost block preview system showing the structure before placement
- Rotation support (press G to rotate 90°)
- Distance control (scroll wheel to adjust preview distance)
- Animated block-by-block placement
- Support for structures up to 128x128x128 blocks
- Expanded block palette with 105 Minecraft blocks for accurate color matching

### Technical
- Triangle splitting voxelization algorithm (based on obj2voxel)
- GLB parsing with support for both UV-mapped textures and vertex colors
- CIE LAB color space matching for accurate block selection
- Surface voxel extraction for efficient preview rendering

