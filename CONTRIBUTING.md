# Contributing to Align

Thank you for your interest in contributing to **Align**! We welcome contributions ranging from bug fixes and documentation improvements to new focus session features and localizations.

---

## 🚀 Getting Started

### Prerequisites
* **JDK 17**: Ensure your `JAVA_HOME` points to JDK 17 (e.g. Eclipse Temurin 17).
* **Android Studio**: Koala, Ladybug, or newer recommended.
* **Android SDK**: Compile SDK `35`, Min SDK `26`.

### Building Locally
1. Fork and clone the repository:
   ```bash
   git clone https://github.com/<your-username>/Align.git
   cd Align
   ```
2. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run tests / linting:
   ```bash
   ./gradlew test
   ./gradlew lint
   ```

---

## 🔒 Fork-Safe Automated Builds

You do **not** need the maintainer's release signing key to test your changes.
* When you fork Align and push to your fork, GitHub Actions automatically generates a throwaway development key.
* The **Build** workflow builds, verifies, and packages an APK on your fork automatically (downloadable from the run's artifacts).
* The **Release** workflow is maintainer-only: it needs the real signing secrets and fails without them.

---

## 📐 Architecture & Guidelines

* **Pure Offline Privacy**: Align contains zero analytics, telemetry, or network-calling dependencies. Any PR introducing network permissions or tracking will be rejected.
* **Zero Accessibility Abuse**: The blocking mechanism relies strictly on `UsageStatsManager` and standard WindowManager `TYPE_APPLICATION_OVERLAY`. Do not introduce Accessibility Services.
* **UI Toolkit**: 100% Jetpack Compose using Material Design 3 and BOM `2024.10.01`.
* **Icons**: Built using native Phosphor vector drawables under MIT.

---

## 🤝 Submitting a Pull Request

1. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. Make your changes and test on an emulator or physical device running Android 8.0+.
3. Commit with a clear, descriptive message (e.g., `feat(focus): add preset timer interval`).
4. Push to your fork and submit a Pull Request to `main`.
5. Ensure GitHub Actions build passes on your PR.

---

## 💬 Code of Conduct

Be welcoming, polite, and constructive. We value respect and helpful feedback across all issues and pull requests.
