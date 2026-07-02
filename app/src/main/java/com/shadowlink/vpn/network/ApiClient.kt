package com.shadowlink.vpn.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shadowlink.vpn.models.*
import com.shadowlink.vpn.utils.PrefsManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ApiClient {

    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    // Utilise maintenant le client avec certificate pinning
    private val client get() = PinnedHttpClient.build()

    private fun baseUrl() = PrefsManager.panelUrl

    // ── AUTH ──────────────────────────────────────────────

    suspend fun login(username: String, password: String, hwid: String): Result<LoginResponse> {
        return try {
            val body = gson.toJson(LoginRequest(username, password, hwid)).toRequestBody(JSON)
            val req  = Request.Builder().url("${baseUrl()}/api/auth/login").post(body).build()
            val res  = client.newCall(req).execute()
            val rb   = res.body?.string() ?: ""
            if (res.isSuccessful) {
                Result.success(gson.fromJson(rb, LoginResponse::class.java))
            } else {
                val err = runCatching { gson.fromJson(rb, LoginResponse::class.java) }.getOrNull()
                Result.failure(Exception(err?.message ?: "Connexion échouée (${res.code})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau : ${e.message}"))
        }
    }

    suspend fun logout(): Result<Boolean> {
        return try {
            val req = Request.Builder().url("${baseUrl()}/api/auth/logout")
                .post("{}".toRequestBody(JSON)).build()
            client.newCall(req).execute()
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    suspend fun getUserInfo(): Result<UserInfo> {
        return try {
            val req = Request.Builder().url("${baseUrl()}/api/user/info").get().build()
            val res = client.newCall(req).execute()
            val rb  = res.body?.string() ?: ""
            if (res.isSuccessful) {
                val type = object : TypeToken<ApiResponse<UserInfo>>() {}.type
                val api  = gson.fromJson<ApiResponse<UserInfo>>(rb, type)
                if (api.success && api.data != null) Result.success(api.data)
                else Result.failure(Exception(api.message ?: "Erreur"))
            } else Result.failure(Exception("Session expirée"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau"))
        }
    }

    suspend fun getProfiles(): Result<List<VpnProfile>> {
        return try {
            val req = Request.Builder().url("${baseUrl()}/api/vpn/profiles").get().build()
            val res = client.newCall(req).execute()
            val rb  = res.body?.string() ?: ""
            if (res.isSuccessful) {
                val type = object : TypeToken<ApiResponse<List<VpnProfile>>>() {}.type
                val api  = gson.fromJson<ApiResponse<List<VpnProfile>>>(rb, type)
                if (api.success && api.data != null) Result.success(api.data)
                else Result.failure(Exception(api.message ?: "Erreur"))
            } else Result.failure(Exception("Impossible de récupérer les profils"))
        } catch (e: Exception) {
            Result.failure(Exception("Erreur réseau : ${e.message}"))
        }
    }

    suspend fun checkUpdate(currentVersionCode: Int): Result<AppUpdate?> {
        return try {
            val req = Request.Builder().url("${baseUrl()}/api/app/update?version=$currentVersionCode").get().build()
            val res = client.newCall(req).execute()
            val rb  = res.body?.string() ?: ""
            if (res.isSuccessful) {
                val type = object : TypeToken<ApiResponse<AppUpdate>>() {}.type
                val api  = gson.fromJson<ApiResponse<AppUpdate>>(rb, type)
                Result.success(api.data)
            } else Result.success(null)
        } catch (e: Exception) {
            Result.failure(Exception("Impossible de vérifier les mises à jour"))
        }
    }

    suspend fun reportReward(minutesWatched: Int, hwid: String): Result<Boolean> {
        return try {
            val body = gson.toJson(mapOf("minutes" to minutesWatched, "hwid" to hwid)).toRequestBody(JSON)
            val req  = Request.Builder().url("${baseUrl()}/api/reward/grant").post(body).build()
            val res  = client.newCall(req).execute()
            Result.success(res.isSuccessful)
        } catch (e: Exception) {
            Result.success(true)
        }
    }
}
