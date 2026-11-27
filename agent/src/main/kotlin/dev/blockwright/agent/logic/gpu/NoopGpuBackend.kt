package dev.blockwright.agent.logic.gpu

import com.mojang.renderpearl.api.device.GpuBackend
import com.mojang.renderpearl.api.device.GpuDebugOptions
import com.mojang.renderpearl.api.device.GpuDevice
import org.lwjgl.sdl.SDLVideo

/**
 * The graphics backend of the logic profile, offered to Minecraft instead of OpenGL and Vulkan. Its window is a
 * hidden SDL window of the dummy video driver (no display, no GL context) and its device draws nothing.
 */
class NoopGpuBackend : GpuBackend {
    override fun getName(): String = NAME

    override fun loadLibrary() {
        // No graphics library to load.
    }

    override fun unloadLibrary() {
        // Nothing was loaded.
    }

    override fun createWindow(title: String?, width: Int, height: Int, flags: Long): Long =
        SDLVideo.SDL_CreateWindow(title, width, height, flags or SDLVideo.SDL_WINDOW_HIDDEN)

    override fun createDevice(options: GpuDebugOptions): GpuDevice = NoopGpuDevice()

    companion object {
        const val NAME = "Blockwright headless"
    }
}
