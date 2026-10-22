# Contributing to GrowTube

Thank you for your interest in contributing to **GrowTube**! We welcome contributions from developers, designers, and YouTube content creators.

## Code of Conduct
Please ensure a respectful, inclusive, and harassment-free experience for everyone in our community.

## How Can I Contribute?

### Reporting Bugs
- Check the [GitHub Issues](https://github.com) tab to see if the issue has already been reported.
- If not, create a new issue detailing:
  - Steps to reproduce
  - Expected behavior vs. actual behavior
  - Device/Android OS version
  - Screenshots or logcat outputs if applicable

### Suggesting Enhancements
- Feature requests and UX improvement suggestions are welcomed. Please outline the motivation and practical use case for YouTube creators.

### Pull Requests
1. **Fork** the repository and create your branch from `main`:
   ```bash
   git checkout -b feature/amazing-feature
   ```
2. **Coding Standards**:
   - Write clean, idiomatic Kotlin code following modern Android development practices.
   - Use Jetpack Compose and Material Design 3 guidelines for UI.
   - Keep secret keys in `.env` or user runtime configurations — **NEVER commit sensitive credentials or API keys**.
3. **Verify Build**:
   - Run standard build and tests before committing:
     ```bash
     gradle assembleDebug
     gradle testDebugUnitTest
     ```
4. **Commit & Push**:
   - Write descriptive commit messages.
   - Push to your fork and submit a Pull Request against `main`.

## License
By contributing to GrowTube, you agree that your contributions will be licensed under the project's [MIT License](LICENSE).
