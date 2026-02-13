package com.claw.app.data.repository

import com.claw.app.data.remote.ClawApi
import com.claw.app.domain.model.SubscriptionStatus
import com.claw.app.domain.model.UsageSummary

class UsageRepository(
    private val api: ClawApi
) {
    suspend fun getUsage(startDate: String? = null, endDate: String? = null): Result<UsageSummary> {
        return api.getUsage(startDate, endDate)
    }
    
    suspend fun getSubscriptionStatus(): Result<SubscriptionStatus> {
        return api.getSubscriptionStatus()
    }
}
