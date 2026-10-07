package com.bondhu.cockpitserver.server

import retrofit2.Call
import retrofit2.http.*

/**
 * Server Driver (v1): SimSupport-এর device API contract (subset)।
 * Auth: device_id self-asserted (existing protocol)।
 */
interface ApiInterface {

    @POST("/api/device/register")
    @FormUrlEncoded
    fun register(
        @Field("device_id") deviceId: String,
        @Field("device_name") deviceName: String,
        @Field("device_type") deviceType: String = "cockpit"
    ): Call<Map<String, Any>>

    @POST("/api/device/connect")
    @FormUrlEncoded
    fun connect(
        @Field("device_id") deviceId: String
    ): Call<Map<String, Any>>

    @POST("/api/device/disconnect")
    @FormUrlEncoded
    fun disconnect(
        @Field("device_id") deviceId: String
    ): Call<Map<String, Any>>

    @POST("/api/device/heartbeat")
    @FormUrlEncoded
    fun heartbeat(
        @Field("device_id") deviceId: String,
        @Field("battery_health") batteryHealth: String = ""
    ): Call<Map<String, Any>>

    @POST("/api/device/status")
    @FormUrlEncoded
    fun updateStatus(
        @Field("device_id") deviceId: String,
        @Field("status") status: String
    ): Call<Map<String, Any>>

    /**
     * Cockpit device polling — সার্ভার থেকে assigned job নাও।
     * Socket.IO-এর বদলে HTTP polling (নির্ভরযোগ্য)।
     */
    @GET("/api/cockpit/device/{device_id}/pending")
    fun getPendingJobs(
        @Path("device_id") deviceId: String
    ): Call<Map<String, Any>>

    /**
     * রিচার্জ ফলাফল রিপোর্ট।
     * status: completed | failed | waiting
     * sms: Cockpit transaction ID (reconciliation-এর জন্য)
     * reason: বিস্তারিত (যেমন: "powerload 98TK trx BD..." / "normal")
     */
    @POST("/api/recharges/{id}/status")
    @FormUrlEncoded
    fun reportRechargeStatus(
        @Path("id") rechargeId: String,
        @Field("status") status: String,
        @Field("sms") sms: String = "",
        @Field("reason") reason: String = "",
        @Field("actioned_by") actionedBy: String = "cockpit"
    ): Call<Map<String, Any>>
}
