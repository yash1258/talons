package com.claw.app.domain.model

data class User(
    val id: String,
    val email: String,
    val name: String?,
    val subscription: Subscription,
    val createdAt: String
)

enum class Subscription {
    FREE,
    PRO,
    PREMIUM
}

data class Instance(
    val id: String,
    val userId: String,
    val dockerPort: Int,
    val status: InstanceStatus,
    val openclawVersion: String?,
    val createdAt: String,
    val runtimeStatus: RuntimeStatus? = null
)

enum class InstanceStatus {
    PENDING,
    STARTING,
    RUNNING,
    STOPPED,
    ERROR,
    DELETED
}

data class RuntimeStatus(
    val running: Boolean,
    val containerStatus: String,
    val uptime: Long? = null
)

data class UsageRecord(
    val id: String,
    val date: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalCost: Double,
    val messagesCount: Int,
    val toolCallsCount: Int
)

data class UsageSummary(
    val records: List<UsageRecord>,
    val totals: UsageTotals
)

data class UsageTotals(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalCost: Double,
    val messagesCount: Int,
    val toolCallsCount: Int
)

data class SubscriptionStatus(
    val subscription: Subscription,
    val limits: SubscriptionLimits,
    val usage: SubscriptionUsage
)

data class SubscriptionLimits(
    val tokens: Int,
    val messages: Int,
    val channels: Int
)

data class SubscriptionUsage(
    val tokens: Int,
    val messages: Int,
    val cost: Double
)
