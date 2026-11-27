package dev.blockwright.agent.trace

import dev.blockwright.agent.AgentStatus
import net.minecraft.client.KeyMapping
import net.minecraft.client.Options
import net.minecraft.client.player.LocalPlayer
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Plays [script] through the real key mappings (so vanilla input handling runs unchanged) and writes one line per
 * player tick: position relative to the start, velocity, ground contact and the input the player saw. Runs only
 * on the client thread, once, from the tick after the join.
 */
class MovementTracer(private val out: Path, private val script: InputScript, private val status: AgentStatus) {
    private var writer: BufferedWriter? = null
    private var tick = 0
    private var startX = 0.0
    private var startY = 0.0
    private var startZ = 0.0
    private var finished = false

    val isActive: Boolean get() = writer != null

    fun begin(player: LocalPlayer) {
        if (writer != null || finished) return
        Files.createDirectories(out.toAbsolutePath().parent)
        writer = Files.newBufferedWriter(out).also { it.write(HEADER); it.newLine() }
        startX = player.x
        startY = player.y
        startZ = player.z
        status.emit("tracing", mapOf("script" to script.name, "ticks" to script.length))
    }

    /** Called right before vanilla reads the movement keys of this tick. */
    fun beforeInput(options: Options) {
        if (writer == null) return
        val input = script.at(tick) ?: ScriptedInput()
        press(options, input)
    }

    /** Called after the player's tick; closes the trace after the last scripted tick. */
    fun afterPlayerTick(player: LocalPlayer, options: Options) {
        val out = writer ?: return
        val velocity = player.deltaMovement
        val keys = player.input.keyPresses
        val seen = ScriptedInput(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), keys.shift(), keys.sprint())
        out.write(line(tick, player.x - startX, player.y - startY, player.z - startZ, velocity.x, velocity.y, velocity.z, player.onGround(), seen))
        out.newLine()
        tick++
        if (tick >= script.length) finish(options)
    }

    private fun finish(options: Options) {
        press(options, ScriptedInput())
        writer?.close()
        writer = null
        finished = true
        status.emit("traced", mapOf("ticks" to tick, "file" to out.fileName))
    }

    private fun press(options: Options, input: ScriptedInput) {
        set(options.keyUp, input.forward)
        set(options.keyDown, input.backward)
        set(options.keyLeft, input.left)
        set(options.keyRight, input.right)
        set(options.keyJump, input.jump)
        set(options.keyShift, input.shift)
        set(options.keySprint, input.sprint)
    }

    private fun set(key: KeyMapping, down: Boolean) = key.setDown(down)

    companion object {
        const val PROPERTY = "blockwright.trace.out"
        const val HEADER = "tick dx dy dz vx vy vz onGround input"

        /** Doubles in full precision (Double.toString round-trips); input as `FBLRJSs` letters or `-`. */
        fun line(tick: Int, dx: Double, dy: Double, dz: Double, vx: Double, vy: Double, vz: Double, onGround: Boolean, input: ScriptedInput): String =
            "$tick $dx $dy $dz $vx $vy $vz $onGround ${inputLetters(input)}"

        fun inputLetters(input: ScriptedInput): String = buildString {
            if (input.forward) append('F')
            if (input.backward) append('B')
            if (input.left) append('L')
            if (input.right) append('R')
            if (input.jump) append('J')
            if (input.shift) append('S')
            if (input.sprint) append('s')
        }.ifEmpty { "-" }
    }
}
