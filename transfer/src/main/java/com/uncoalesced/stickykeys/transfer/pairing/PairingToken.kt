// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.pairing

import org.json.JSONObject

data class PairingToken(
    val publicKeyBase64: String,
    val ip: String,
    val port: Int,
    val timestamp: Long
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("pk", publicKeyBase64)
        json.put("ip", ip)
        json.put("port", port)
        json.put("ts", timestamp)
        return json.toString()
    }

    companion object {
        fun fromJsonString(jsonString: String): PairingToken? {
            return try {
                val json = JSONObject(jsonString)
                PairingToken(
                    publicKeyBase64 = json.getString("pk"),
                    ip = json.getString("ip"),
                    port = json.getInt("port"),
                    timestamp = json.getLong("ts")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
