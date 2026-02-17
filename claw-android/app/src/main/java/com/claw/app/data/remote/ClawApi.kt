package com.claw.app.data.remote

import com.claw.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ClawApi(private val client: OkHttpClient) {
    
    private var baseUrl = "http://152.53.164.238:4200"
    
    fun setBaseUrl(url: String) {
        baseUrl = url
    }
    
    // --- Auth ---
    
    suspend fun register(email: String, password: String, name: String? = null): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
                name?.let { put("name", it) }
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/auth/register")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                val jsonBody = JSONObject(body!!)
                Result.success(parseAuthResponse(jsonBody))
            } else {
                Result.failure(IOException("Registration failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                val jsonBody = JSONObject(body!!)
                Result.success(parseAuthResponse(jsonBody))
            } else {
                Result.failure(IOException("Login failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // --- Instances ---
    
    suspend fun getInstances(): Result<List<Instance>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/instances")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                val jsonArray = org.json.JSONArray(body!!)
                val instances = mutableListOf<Instance>()
                for (i in 0 until jsonArray.length()) {
                    instances.add(parseInstance(jsonArray.getJSONObject(i)))
                }
                Result.success(instances)
            } else {
                Result.failure(IOException("Failed to get instances: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createInstance(): Result<Instance> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/instances")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                Result.success(parseInstance(JSONObject(body!!)))
            } else {
                Result.failure(IOException("Failed to create instance: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun startInstance(instanceId: String): Result<Instance> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/instances/$instanceId/start")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                Result.success(parseInstance(JSONObject(body!!)))
            } else {
                Result.failure(IOException("Failed to start instance: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun stopInstance(instanceId: String): Result<Instance> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/instances/$instanceId/stop")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                Result.success(parseInstance(JSONObject(body!!)))
            } else {
                Result.failure(IOException("Failed to stop instance: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteInstance(instanceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/instances/$instanceId")
                .delete()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(IOException("Failed to delete instance: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approvePairing(instanceId: String, code: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("code", code)
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/instances/$instanceId/pairing")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(IOException("Failed to approve pairing: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // --- Config ---
    
    suspend fun updateConfig(instanceId: String, config: JSONObject): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/instances/$instanceId/config")
                .put(config.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(IOException("Failed to update config: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getConfig(instanceId: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/instances/$instanceId/config")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                Result.success(JSONObject(body!!))
            } else {
                Result.failure(IOException("Failed to get config: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // --- Usage ---
    
    suspend fun getUsage(startDate: String? = null, endDate: String? = null): Result<UsageSummary> = withContext(Dispatchers.IO) {
        try {
            var url = "$baseUrl/api/usage"
            val params = mutableListOf<String>()
            startDate?.let { params.add("startDate=$it") }
            endDate?.let { params.add("endDate=$it") }
            if (params.isNotEmpty()) {
                url += "?" + params.joinToString("&")
            }
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                val obj = JSONObject(body!!)
                val recordsArray = obj.getJSONArray("records")
                val records = mutableListOf<UsageRecord>()
                for (i in 0 until recordsArray.length()) {
                    val record = recordsArray.getJSONObject(i)
                    records.add(UsageRecord(
                        id = record.getString("id"),
                        date = record.getString("date"),
                        inputTokens = record.getInt("inputTokens"),
                        outputTokens = record.getInt("outputTokens"),
                        totalCost = record.getDouble("totalCost"),
                        messagesCount = record.getInt("messagesCount"),
                        toolCallsCount = record.getInt("toolCallsCount")
                    ))
                }
                val totals = obj.getJSONObject("totals")
                Result.success(UsageSummary(
                    records = records,
                    totals = UsageTotals(
                        inputTokens = totals.getInt("inputTokens"),
                        outputTokens = totals.getInt("outputTokens"),
                        totalCost = totals.getDouble("totalCost"),
                        messagesCount = totals.getInt("messagesCount"),
                        toolCallsCount = totals.getInt("toolCallsCount")
                    )
                ))
            } else {
                Result.failure(IOException("Failed to get usage: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSubscriptionStatus(): Result<SubscriptionStatus> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/usage/subscription")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            if (response.isSuccessful) {
                val obj = JSONObject(body!!)
                Result.success(SubscriptionStatus(
                    subscription = Subscription.valueOf(obj.getString("subscription")),
                    limits = SubscriptionLimits(
                        tokens = obj.getJSONObject("limits").getInt("tokens"),
                        messages = obj.getJSONObject("limits").getInt("messages"),
                        channels = obj.getJSONObject("limits").getInt("channels")
                    ),
                    usage = SubscriptionUsage(
                        tokens = obj.getJSONObject("usage").getInt("tokens"),
                        messages = obj.getJSONObject("usage").getInt("messages"),
                        cost = obj.getJSONObject("usage").getDouble("cost")
                    )
                ))
            } else {
                Result.failure(IOException("Failed to get subscription: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // --- Parsers ---
    
    private fun parseAuthResponse(json: JSONObject): AuthResponse {
        val userObj = json.getJSONObject("user")
        return AuthResponse(
            user = User(
                id = userObj.getString("id"),
                email = userObj.getString("email"),
                name = userObj.optString("name"),
                subscription = Subscription.valueOf(userObj.getString("subscription")),
                createdAt = userObj.getString("createdAt")
            ),
            token = json.getString("token")
        )
    }
    
    private fun parseInstance(obj: JSONObject): Instance {
        return Instance(
            id = obj.getString("id"),
            userId = obj.getString("userId"),
            dockerPort = obj.getInt("dockerPort"),
            status = InstanceStatus.valueOf(obj.getString("status")),
            openclawVersion = obj.optString("openclawVersion"),
            gatewayToken = obj.optString("gatewayToken"),
            createdAt = obj.getString("createdAt")
        )
    }
}

data class AuthResponse(
    val user: User,
    val token: String
)
