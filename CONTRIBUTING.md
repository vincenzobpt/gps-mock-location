# Contributing to MockLocation

Thank you for your interest in contributing!

## Issues

- Use **GitHub Issues** to report bugs or request features
- Provide a clear description, steps to reproduce, and device/OS version for
  bugs
- Check existing issues before opening a duplicate

## Pull requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes with clear messages
4. Build and test locally:
   ```bash
   ./gradlew assembleDebug
   ```
5. Open a pull request against the `main` branch

## Code style

- Follow standard [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use **Jetpack Compose** for all UI
- Keep the MVVM architecture: ViewModels hold state, Screens are pure
  composables, Network/Model layers are separate
- Run `./gradlew lint` before submitting

## License

By contributing, you agree that your contributions will be licensed under the
[MIT License](LICENSE).
