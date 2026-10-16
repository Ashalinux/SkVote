<div align="center">

# SkVote

**A minimalist, dependency-free Votifier replacement for Skript.**

![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spigot](https://img.shields.io/badge/Spigot-26.1.2-orange)
![Skript](https://img.shields.io/badge/Skript-2.15-orange)
![License](https://img.shields.io/badge/license-MIT-green)

</div>

SkVote listens for votes from server lists directly – no NuVotifier, no extra plugins. It generates its own RSA key pair on first start and exposes a simple Skript event.

> 💡 Need LuckPerms integration and tablist teams too? Check out **[SkBridge](https://github.com/Ashalinux/SkBridge)**, the successor of this project.

## ✨ Features

- Built-in Votifier-compatible listener (RSA-2048)
- Automatic key generation on first start
- Votes from offline players are queued and fired on join
- One clean Skript event – nothing else to learn

## 📦 Installation

1. Build the plugin with `mvn package` (or download a release)
2. Drop the jar into `/plugins` – Skript must be installed
3. Start the server once; keys are generated in `plugins/SkVote/`
4. Paste the **public key** into your server list's voting settings and open the port (default `8192`)

## ⚙️ Configuration

```yaml
port: 8192
debug: false
```

## 🛠 Usage

```skript
on online vote:
    give player 10 diamonds
    send "&aThanks for voting for the server!" to player
```

## 📄 License

Released under the [MIT License](LICENSE).
