# GridRush F1 - Environment Setup Guide

This guide explains how to properly configure your system to run the **GridRush F1** Multiplayer Game. Because the game relies on **JavaFX** for high-performance 60 FPS rendering, running it natively on Windows is highly recommended to take advantage of GPU Hardware Acceleration.

---

## 🚀 1. Native Windows Setup (Recommended)

Running the game via native Windows PowerShell bypasses the slow software-rendering limits of WSL, giving you a smooth, lag-free experience.

### Prerequisites Installation
The game requires **Java 17** (or newer). You do not need to install Maven manually, as the project includes a Maven Wrapper (`mvnw.cmd`) that will automatically download Maven for you.

You can install Java 17 easily using the Windows Package Manager (`winget`). Open **PowerShell as Administrator** and run:
```powershell
winget install Microsoft.OpenJDK.17
```

### Running the Game
1. Open a **brand new** PowerShell window (to ensure your system registers the newly installed Java).
2. Navigate to the project directory:
   ```powershell
   cd "path\to\project_137"
   ```
3. Set your `JAVA_HOME` to guarantee the wrapper uses Java 17 instead of any older Java 8 installations you might have:
   ```powershell
   $env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
   ```

4. **Start the Game Server:**
   ```powershell
   .\mvnw.cmd clean compile exec:java "-Dexec.mainClass=GameServer"
   ```

5. **Start a Game Client (Player):**
   Open another PowerShell window, set the `JAVA_HOME` again, and run:
   ```powershell
   .\mvnw.cmd javafx:run
   ```

---

## 🐧 2. WSL / Linux Setup

Running JavaFX inside WSL (via WSLg) forces the game to use **Software Rendering**, which relies entirely on your CPU. Running multiple clients this way will result in significant FPS drops and network latency. Only use this method for headless testing or if native Windows is unavailable.

### Prerequisites Installation
Inside your WSL terminal (Ubuntu), install OpenJDK 17 and Maven:
```bash
sudo apt update
sudo apt install openjdk-17-jdk maven -y
```

### Running the Game
Navigate to your project directory inside the WSL filesystem:
```bash
cd /path/to/project_137
```

**Start the Game Server:**
```bash
mvn clean compile exec:java -Dexec.mainClass="GameServer"
```

**Start a Game Client (Player):**
```bash
mvn javafx:run
```

*(Optional Performance Hack for WSL)*: You can try to force hardware acceleration by appending JavaFX arguments:
```bash
mvn javafx:run -Dprism.order=es2,sw -Dprism.forceGPU=true
```

---

## 🛠️ Troubleshooting

### "No compiler is provided in this environment"
**Cause:** The Maven Wrapper is trying to compile Java 17 code, but it is detecting an old Java 8 JRE on your system path.
**Fix:** Explicitly set the `JAVA_HOME` environment variable to your Java 17 installation path in the terminal right before running the `mvnw` command.
```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
```

### "Unknown lifecycle phase .mainClass=GameServer"
**Cause:** PowerShell interprets the `-D` flag differently than Bash and splits the argument.
**Fix:** Always wrap the execution argument in quotes when using PowerShell:
```powershell
.\mvnw.cmd exec:java "-Dexec.mainClass=GameServer"
```

