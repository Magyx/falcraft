# Contributing to Falcraft

First off, thanks for taking the time to contribute! 🎉

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- **Minecraft version**
- **Fabric Loader version**
- **Fabric API version**
- **Mod version**
- **Steps to reproduce**
- **Expected vs actual behavior**
- **Log files** (`logs/latest.log`)
- **Screenshots** (if applicable)

### Suggesting Features

Feature suggestions are welcome! Please provide:

- **Clear description** of the feature
- **Use case** - why is it useful?
- **Examples** - how would it work?
- **Mockups** - if it's a UI feature

### Pull Requests

1. **Fork** the repo and create your branch from `main`
2. **Set up** your development environment:
   ```bash
   git clone https://github.com/blendi-remade/falcraft.git
   cd falcraft/fabric-example-mod
   echo "FAL_API_KEY=your_key" > .env
   ./gradlew runClient
   ```

3. **Make your changes**:
   - Follow existing code style
   - Add comments for complex logic
   - Update README if needed

4. **Test thoroughly**:
   - Test in dev environment (`./gradlew runClient`)
   - Build and test the JAR (`./gradlew build`)
   - Test with different blocks and prompts

5. **Commit**:
   - Use clear, descriptive commit messages
   - Reference issues if applicable: `Fixes #123`

6. **Push** and submit a Pull Request

## Development Guidelines

### Code Style

- **Java 21** features encouraged (records, pattern matching, etc.)
- **4 spaces** for indentation (not tabs)
- **Clear naming** - descriptive variable/method names
- **Comments** - explain "why", not "what"
- **Logging** - use SLF4J logger for debugging

### Project Structure

```
src/
├── main/              # Server + client code
│   └── java/com/falcraft/
└── client/            # Client-only code
    └── java/com/falcraft/
        ├── commands/  # Command implementations
        └── util/      # Utility classes
```

### Testing Checklist

- [ ] Code compiles without warnings
- [ ] Mod loads in dev environment
- [ ] Command works as expected
- [ ] Error handling works (invalid input, network errors, etc.)
- [ ] No memory leaks (temp files cleaned up)
- [ ] Logs are clear and helpful
- [ ] README updated if needed

### Common Pitfalls

❌ **Don't** call UI methods from background threads:
```java
// BAD
new Thread(() -> {
    source.sendFeedback(...);  // Will crash!
}).start();
```

✅ **Do** schedule on main thread:
```java
// GOOD
new Thread(() -> {
    Minecraft.getInstance().execute(() -> {
        source.sendFeedback(...);  // Safe!
    });
}).start();
```

❌ **Don't** block the main thread:
```java
// BAD - game freezes for 10 seconds!
Thread.sleep(10000);
```

✅ **Do** use background threads for slow operations:
```java
// GOOD - game stays responsive
new Thread(() -> {
    // Long API call here
}, "Background-Thread").start();
```

## Areas That Need Help

- **Documentation** - more tutorials, examples
- **Testing** - edge cases, different Minecraft versions
- **Features** - see [Future Plans](README.md#-future-plans)
- **Performance** - optimization opportunities
- **Error Handling** - better error messages

## Questions?

Feel free to:
- Open an issue with the `question` label
- Start a discussion
- Comment on existing issues/PRs

## Code of Conduct

- Be respectful and constructive
- Help others learn
- Give credit where due
- Have fun! 🎮

---

**Thanks for contributing to Falcraft!** 🚀

