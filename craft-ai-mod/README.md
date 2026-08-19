# CraftAI Fabric Mod

This directory contains the Java 25/Fabric client for CraftAI on Minecraft 26.2.

From a clean checkout:

```bash
./gradlew build
```

For local development, start the backend as described in the [workspace README](../README.md), then run:

```bash
./gradlew runClient
```

Open a single-player world and use `/ask <question>`. Run `/craftai help` for local commands or `/craftai status` to check the backend connection and available game context.

The backend defaults to `http://localhost:3000`. Override it with `CRAFTAI_BACKEND_URL` or the `craftai.backendUrl` Java system property.
