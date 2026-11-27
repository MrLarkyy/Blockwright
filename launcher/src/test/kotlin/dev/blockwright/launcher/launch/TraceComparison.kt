package dev.blockwright.launcher.launch

import kotlin.math.abs

/**
 * Compares two movement traces (`tick dx dy dz vx vy vz onGround input`, header first): velocity, ground contact and
 * input must be identical, the position relative to the start may differ by [positionTolerance] (absolute spawn
 * coordinates round differently in the subtraction). Returns one message per differing tick.
 */
object TraceComparison {
    const val POSITION_TOLERANCE = 1e-9

    fun differences(reference: List<String>, candidate: List<String>, positionTolerance: Double = POSITION_TOLERANCE): List<String> {
        val ref = reference.drop(1)
        val cand = candidate.drop(1)
        val out = mutableListOf<String>()
        if (ref.size != cand.size) out += "length ${ref.size} != ${cand.size}"
        ref.zip(cand).forEach { (r, c) -> compareLine(r, c, positionTolerance)?.let { out += it } }
        return out
    }

    private fun compareLine(reference: String, candidate: String, tolerance: Double): String? {
        val r = reference.split(' ')
        val c = candidate.split(' ')
        if (r.size != FIELDS || c.size != FIELDS) return "malformed: '$reference' / '$candidate'"
        val positionsClose = (1..3).all { abs(r[it].toDouble() - c[it].toDouble()) <= tolerance }
        val restEqual = r[0] == c[0] && (4..8).all { r[it] == c[it] }
        return if (positionsClose && restEqual) null else "tick ${r[0]}: reference '$reference' vs logic '$candidate'"
    }

    private const val FIELDS = 9
}
