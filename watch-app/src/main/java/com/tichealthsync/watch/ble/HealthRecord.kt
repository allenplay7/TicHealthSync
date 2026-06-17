package com.tichealthsync.watch.ble

data class HealthRecord(
    val recordId: String,
    val deviceId: String,
    val type: String,
    val value: Double,
    val unit: String,
    val startTime: String,
    val endTime: String,
    val createdAt: String,
    val sequence: Long,
    var syncStatus: String = "pending",
    var batchId: String? = null
)

