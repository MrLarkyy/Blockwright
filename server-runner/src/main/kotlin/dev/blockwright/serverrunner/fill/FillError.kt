package dev.blockwright.serverrunner.fill

import kotlinx.serialization.Serializable

/** Fill's error body, e.g. `{"ok":false,"error":"build_not_found","message":"..."}`. */
@Serializable
data class FillError(val ok: Boolean? = null, val error: String? = null, val message: String? = null)
