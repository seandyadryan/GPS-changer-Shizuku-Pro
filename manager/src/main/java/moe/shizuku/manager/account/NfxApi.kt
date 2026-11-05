package moe.shizuku.manager.account

import android.os.Build
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.TimeUnit

internal data class NfxAccount(
    val id: Int,
    val login: String,
    val isAdmin: Boolean,
    val subscriptionUntil: String?,
    val deviceLabel: String?,
    val deviceCode: String?,
)

internal data class NfxTransfer(val id: Int, val login: String, val deviceLabel: String, val deviceCode: String)
internal data class NfxAdminData(val users: List<NfxAccount>, val transfers: List<NfxTransfer>)
internal data class NfxSession(val token: String, val account: NfxAccount, val clockOffset: Long)
internal class NfxApiError(val code: String, val deviceCode: String? = null) : Exception(code)

internal class NfxApi {
    private val client = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    suspend fun login(login: String, password: String, requestTransfer: Boolean): NfxSession = withContext(Dispatchers.IO) {
        val challenge = request("POST", "/v1/challenge", JSONObject())
        val clockOffset = challenge.getLong("time") - SystemClock.elapsedRealtime()
        val nonce = challenge.getString("nonce")
        val key = deviceKey()
        val response = request("POST", "/v1/login", JSONObject()
            .put("login", login)
            .put("password", password)
            .put("deviceKey", encode(key.certificate.publicKey.encoded))
            .put("signature", sign("nfx-login-v1:$nonce", key))
            .put("nonce", nonce)
            .put("deviceLabel", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("requestTransfer", requestTransfer))
        NfxSession(response.getString("token"), response.getJSONObject("account").account(), clockOffset)
    }

    suspend fun session(session: NfxSession): NfxAccount = withContext(Dispatchers.IO) {
        request("GET", "/v1/session", session = session).getJSONObject("account").account()
    }

    suspend fun logout(session: NfxSession) = withContext(Dispatchers.IO) {
        request("POST", "/v1/logout", JSONObject(), session)
    }

    suspend fun adminData(session: NfxSession): NfxAdminData = withContext(Dispatchers.IO) {
        val response = request("GET", "/v1/admin/users", session = session)
        val users = response.getJSONArray("users")
        val transfers = response.getJSONArray("transfers")
        NfxAdminData(
            List(users.length()) { users.getJSONObject(it).account() },
            List(transfers.length()) {
                val transfer = transfers.getJSONObject(it)
                NfxTransfer(transfer.getInt("id"), transfer.getString("login"),
                    transfer.getString("deviceLabel"), transfer.getString("deviceCode"))
            }
        )
    }

    suspend fun createUser(session: NfxSession, login: String, password: String) = withContext(Dispatchers.IO) {
        request("POST", "/v1/admin/users", JSONObject().put("login", login).put("password", password), session)
    }

    suspend fun addSubscription(session: NfxSession, id: Int, days: Int, requestId: String) = withContext(Dispatchers.IO) {
        request("POST", "/v1/admin/users/$id/subscription", JSONObject().put("days", days).put("requestId", requestId), session)
    }

    suspend fun decideTransfer(session: NfxSession, id: Int, approve: Boolean) = withContext(Dispatchers.IO) {
        request("POST", "/v1/admin/transfers/$id", JSONObject().put("approve", approve), session)
    }

    private fun request(method: String, path: String, body: JSONObject? = null, session: NfxSession? = null): JSONObject {
        val payload = body?.toString()?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        val request = Request.Builder().url(BuildConfig.NFX_API_URL.trimEnd('/') + path)
            .method(method, if (body == null) null else payload.toRequestBody("application/json".toMediaType()))
        if (session != null) {
            val timestamp = SystemClock.elapsedRealtime() + session.clockOffset
            val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
            val message = "nfx-request-v1:${session.token}:$method:$path:$timestamp:$hash"
            request.header("Authorization", "Bearer ${session.token}")
                .header("X-NFX-Time", timestamp.toString())
                .header("X-NFX-Proof", sign(message, deviceKey()))
        }
        client.newCall(request.build()).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful) throw NfxApiError(json.optString("code", "SERVER_ERROR"), json.optional("deviceCode"))
            return json
        }
    }

    @Synchronized
    private fun deviceKey(): KeyStore.PrivateKeyEntry {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "nfx_location_device_v1"
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build())
            generator.generateKeyPair()
        }
        return store.getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }

    private fun sign(message: String, key: KeyStore.PrivateKeyEntry): String =
        encode(Signature.getInstance("SHA256withECDSA").apply {
            initSign(key.privateKey)
            update(message.toByteArray(Charsets.UTF_8))
        }.sign())

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.account() = NfxAccount(
        getInt("id"), getString("login"), getString("role") == "admin",
        optional("subscriptionUntil"), optional("deviceLabel"), optional("deviceCode"),
    )
}
