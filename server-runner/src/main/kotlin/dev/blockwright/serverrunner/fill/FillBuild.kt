package dev.blockwright.serverrunner.fill

import kotlinx.serialization.Serializable

/** One entry of `GET /v3/projects/paper/versions/{version}/builds[/{build}]`. */
@Serializable
data class FillBuild(val id: Int, val channel: String, val downloads: Map<String, FillDownload> = emptyMap())
