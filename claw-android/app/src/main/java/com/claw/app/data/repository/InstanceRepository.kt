package com.claw.app.data.repository

import com.claw.app.data.remote.ClawApi
import com.claw.app.domain.model.Instance

class InstanceRepository(
    private val api: ClawApi
) {
    suspend fun getInstances(): Result<List<Instance>> {
        return api.getInstances()
    }
    
    suspend fun createInstance(): Result<Instance> {
        return api.createInstance()
    }
    
    suspend fun startInstance(instanceId: String): Result<Instance> {
        return api.startInstance(instanceId)
    }
    
    suspend fun stopInstance(instanceId: String): Result<Instance> {
        return api.stopInstance(instanceId)
    }
    
    suspend fun deleteInstance(instanceId: String): Result<Boolean> {
        return api.deleteInstance(instanceId)
    }
}
