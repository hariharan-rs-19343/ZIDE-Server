package com.zoho.dzide.model

data class ZideService(
    val key: String,
    val properties: Map<String, String>
)

data class ZideConfigReadResult(
    val services: List<ZideService>,
    val service: ZideService?,
    val properties: ZidePropertiesResult?,
    val serviceOptions: List<String>
)

data class ZidePropertiesResult(
    val serviceKey: String,
    val properties: Map<String, String>
)
