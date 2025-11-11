# Environment Setup

## Required: fal API Key

Falcraft requires a fal API key to function. Here's how to set it up:

### 1. Get Your API Key

1. Visit [fal.ai](https://fal.ai)
2. Sign up or log in
3. Go to your dashboard
4. Copy your API key

### 2. Create `.env` File

Create a file named `.env` in one of these locations:

**For Development:**
```
falcraft/run/.env
```

**For Production (Players):**
```
.minecraft/.env
```

**Full paths by OS:**
- Windows: `C:\Users\YourName\AppData\Roaming\.minecraft\.env`
- Linux: `~/.minecraft/.env`
- macOS: `~/Library/Application Support/minecraft/.env`

### 3. Add Your API Key

Open the `.env` file in a text editor and add:

```
FAL_API_KEY=your_actual_api_key_here
```

**Example:**
```
FAL_API_KEY=abc123def456ghi789jkl012mno345
```

### 4. Verify Setup

Start Minecraft with the mod and check the logs:

**✅ Success:**
```
[FalAPI] ✓ Loaded API key from .env file: /path/to/.env
[FalAPI] fal API key loaded successfully
```

**❌ Error:**
```
[FalAPI] ✗ .env file not found at: /path/to/.env
[FalAPI] FAL_API_KEY not found! Please set it in .env file or as environment variable.
```

## Security Notes

⚠️ **Important:**
- **Never commit** `.env` files to git
- **Never share** your API key publicly
- The `.gitignore` already blocks `.env` files
- If you accidentally commit your key, **regenerate it immediately** on fal.ai

## Alternative: Environment Variable

Instead of a `.env` file, you can set a system environment variable:

**Windows (PowerShell):**
```powershell
$env:FAL_API_KEY="your_key_here"
```

**Linux/macOS:**
```bash
export FAL_API_KEY="your_key_here"
```

**Permanent (add to shell profile):**
```bash
# Add to ~/.bashrc or ~/.zshrc
export FAL_API_KEY="your_key_here"
```

## Troubleshooting

### Key Not Loading

1. **Check file location** - `.env` must be in `.minecraft/` or `run/` (for development)
2. **Check file name** - must be exactly `.env` (not `env.txt` or `.env.txt`)  
3. **Check format** - must be `FAL_API_KEY=value` (no spaces around `=`)
4. **Check permissions** - file must be readable
5. **Restart Minecraft** - changes require restart

### API Key Invalid

1. Verify key on [fal.ai](https://fal.ai) dashboard
2. Regenerate if needed
3. Make sure no extra spaces/characters in `.env`

### Still Not Working?

Check the full logs at `.minecraft/logs/latest.log` for detailed error messages.

