package dev.blockwright.launcher

/** [versionId] is an exact id from the version manifest; assets are downloaded only with [includeAssets]. */
data class ResolveRequest(
    val versionId: String,
    val includeAssets: Boolean = false,
)
