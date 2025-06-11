# JavaFX Chat Application

A multi-user chat application with GUI built using JavaFX and Maven.

## Features
- Real-time messaging between multiple users
- Secure communication (AES encryption)
- Chat history persistence
- Group chat capabilities
- File attachment support
- Desktop notifications with sound

## Prerequisites
- Java 11 JDK
- Maven 3.6+
- JavaFX SDK 16

## Installation
1. Clone the repository
2. Ensure JavaFX SDK is placed in project root as `javafx-sdk-16`

## Building & Running
```bash
# First-time setup (builds package and creates run script)
setup.bat

# Subsequent runs (launch new client instances)
run.bat
```

## Project Structure
```
src/
├── main/
│   ├── java/com/chatapp/
│   │   ├── client/      # GUI and client logic
│   │   ├── server/      # Server implementation
│   │   ├── db/         # Database management
│   │   └── security/   # Encryption handling
│   └── resources/      # Media files and assets
Chat_history/           # Per-user chat histories
```

## Configuration
- Edit `pom.xml` for dependency management
- Modify `setup.bat` for environment-specific paths
- Server port configuration in `ChatServer.java`