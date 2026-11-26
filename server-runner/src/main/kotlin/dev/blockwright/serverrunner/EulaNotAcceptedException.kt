package dev.blockwright.serverrunner

class EulaNotAcceptedException : ServerRunnerException(
    "The Minecraft EULA (https://aka.ms/MinecraftEULA) must be accepted explicitly by the user " +
        "(ServerRunConfig.acceptEula = true) before a Paper server is downloaded or started",
)
