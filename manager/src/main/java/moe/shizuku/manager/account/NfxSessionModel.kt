package moe.shizuku.manager.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class NfxState(
    val session: NfxSession? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val deviceCode: String? = null,
    val adminData: NfxAdminData? = null,
    val completedGrantId: String? = null,
)

internal class NfxSessionModel : ViewModel() {
    private val api = NfxApi()
    private val mutable = MutableStateFlow(NfxState())
    private var operationSequence = 0L
    val state = mutable.asStateFlow()

    fun signIn(login: String, password: String, transfer: Boolean) = action {
        val session = api.login(login.trim(), password, transfer)
        mutable.value = NfxState(session = session, busy = true)
    }

    suspend fun checkSession() {
        val current = mutable.value.session ?: return
        try {
            val account = api.session(current)
            mutable.update { if (it.session?.token == current.token) it.copy(session = current.copy(account = account)) else it }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutable.update {
                if (it.session?.token == current.token) NfxState(error = (error as? NfxApiError)?.code ?: "CONNECTION") else it
            }
        }
    }

    fun logout() {
        val session = mutable.value.session ?: return
        operationSequence++
        mutable.value = NfxState()
        viewModelScope.launch {
            try { api.logout(session) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* The local session has already been cleared. */ }
        }
    }

    fun refreshAdmin() = adminAction { }
    fun createUser(login: String, password: String) = adminAction { api.createUser(it, login.trim(), password) }
    fun grant(id: Int, days: Int, requestId: String) = adminAction(grantId = requestId) {
        api.addSubscription(it, id, days, requestId)
    }
    fun decide(id: Int, approve: Boolean) = adminAction { api.decideTransfer(it, id, approve) }

    private fun adminAction(grantId: String? = null, operation: suspend (NfxSession) -> Unit) = action {
        val current = mutable.value.session?.takeIf { it.account.isAdmin } ?: return@action
        operation(current)
        val result = api.adminData(current)
        mutable.update {
            if (it.session?.token == current.token) it.copy(adminData = result, completedGrantId = grantId) else it
        }
    }

    private fun action(operation: suspend () -> Unit) {
        if (mutable.value.busy) return
        val sequence = ++operationSequence
        mutable.update { it.copy(busy = true, error = null, deviceCode = null) }
        val startedWith = mutable.value.session?.token
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val apiError = error as? NfxApiError
                val code = apiError?.code ?: "CONNECTION"
                mutable.update {
                    when {
                        it.session?.token != startedWith -> it
                        startedWith != null && (code == "SESSION_EXPIRED" || code == "SUBSCRIPTION_EXPIRED") -> NfxState(error = code)
                        else -> it.copy(error = code, deviceCode = apiError?.deviceCode)
                    }
                }
            } finally {
                if (sequence == operationSequence) mutable.update { it.copy(busy = false) }
            }
        }
    }
}
