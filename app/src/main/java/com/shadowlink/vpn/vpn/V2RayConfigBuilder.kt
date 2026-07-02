package com.shadowlink.vpn.vpn

import com.google.gson.Gson
import com.shadowlink.vpn.models.VpnProfile
import com.shadowlink.vpn.models.VpnProtocol
import com.shadowlink.vpn.utils.PrefsManager

object V2RayConfigBuilder {

    private val gson = Gson()

    fun build(profile: VpnProfile): String {
        val protocol = runCatching { VpnProtocol.valueOf(profile.protocol) }.getOrNull()
        val outbound = when (protocol) {
            VpnProtocol.V2RAY_VMESS -> buildVmess(profile)
            VpnProtocol.V2RAY_VLESS -> buildVless(profile)
            VpnProtocol.V2RAY_TROJAN -> buildTrojan(profile)
            else -> buildVmess(profile)
        }

        val config = mapOf(
            "log" to mapOf("loglevel" to "warning"),
            "inbounds" to listOf(
                mapOf(
                    "port" to 10808,
                    "listen" to "127.0.0.1",
                    "protocol" to "socks",
                    "settings" to mapOf("auth" to "noauth", "udp" to true)
                ),
                mapOf(
                    "port" to 10809,
                    "listen" to "127.0.0.1",
                    "protocol" to "http"
                )
            ),
            "outbounds" to listOf(outbound, mapOf("protocol" to "freedom", "tag" to "direct")),
            "dns" to mapOf(
                "servers" to listOf(PrefsManager.dns, "8.8.8.8")
            ),
            "routing" to mapOf(
                "domainStrategy" to "IPIfNonMatch",
                "rules" to listOf(
                    mapOf("type" to "field", "ip" to listOf("geoip:private"), "outboundTag" to "direct")
                )
            )
        )
        return gson.toJson(config)
    }

    private fun buildVmess(profile: VpnProfile): Map<String, Any> {
        val streamSettings = buildStreamSettings(profile)
        return mapOf(
            "protocol" to "vmess",
            "settings" to mapOf(
                "vnext" to listOf(
                    mapOf(
                        "address" to profile.host,
                        "port" to profile.port,
                        "users" to listOf(
                            mapOf(
                                "id" to profile.v2rayUuid,
                                "alterId" to profile.v2rayAlterId,
                                "security" to "auto"
                            )
                        )
                    )
                )
            ),
            "streamSettings" to streamSettings,
            "tag" to "proxy"
        )
    }

    private fun buildVless(profile: VpnProfile): Map<String, Any> {
        val streamSettings = buildStreamSettings(profile)
        return mapOf(
            "protocol" to "vless",
            "settings" to mapOf(
                "vnext" to listOf(
                    mapOf(
                        "address" to profile.host,
                        "port" to profile.port,
                        "users" to listOf(
                            mapOf(
                                "id" to profile.v2rayUuid,
                                "encryption" to "none",
                                "flow" to ""
                            )
                        )
                    )
                )
            ),
            "streamSettings" to streamSettings,
            "tag" to "proxy"
        )
    }

    private fun buildTrojan(profile: VpnProfile): Map<String, Any> {
        return mapOf(
            "protocol" to "trojan",
            "settings" to mapOf(
                "servers" to listOf(
                    mapOf(
                        "address" to profile.host,
                        "port" to profile.port,
                        "password" to profile.v2rayUuid
                    )
                )
            ),
            "streamSettings" to mapOf(
                "network" to "tcp",
                "security" to "tls",
                "tlsSettings" to mapOf(
                    "serverName" to profile.v2raySni.ifEmpty { profile.host },
                    "allowInsecure" to false
                )
            ),
            "tag" to "proxy"
        )
    }

    private fun buildStreamSettings(profile: VpnProfile): Map<String, Any> {
        val settings = mutableMapOf<String, Any>(
            "network" to profile.v2rayNetwork
        )

        if (profile.v2rayTls) {
            settings["security"] = "tls"
            settings["tlsSettings"] = mapOf(
                "serverName" to profile.v2raySni.ifEmpty { profile.host },
                "allowInsecure" to false
            )
        }

        when (profile.v2rayNetwork) {
            "ws" -> settings["wsSettings"] = mapOf(
                "path" to profile.v2rayPath.ifEmpty { "/" },
                "headers" to mapOf("Host" to profile.v2raySni.ifEmpty { profile.host })
            )
            "grpc" -> settings["grpcSettings"] = mapOf(
                "serviceName" to profile.v2rayPath
            )
            "h2" -> settings["httpSettings"] = mapOf(
                "path" to profile.v2rayPath,
                "host" to listOf(profile.v2raySni.ifEmpty { profile.host })
            )
        }

        return settings
    }
}
