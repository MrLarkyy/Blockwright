package dev.blockwright.agent.logic

import org.lwjgl.sdl.SDLHints

/**
 * Points SDL at its dummy video and audio drivers right before Minecraft initialises SDL: a window then exists
 * without any display server, GL driver or audio device, on Windows and Linux alike.
 */
object HeadlessDisplay {
    const val DRIVER = "dummy"

    fun configure() {
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_DRIVER, DRIVER)
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_AUDIO_DRIVER, DRIVER)
    }
}
