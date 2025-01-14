package fr.free.nrw.commons.auth.csrf

import androidx.annotation.VisibleForTesting
import fr.free.nrw.commons.auth.SessionManager
import org.wikipedia.AppAdapter
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.SharedPreferenceCookieManager
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.dataclient.mwapi.MwQueryResponse
import fr.free.nrw.commons.auth.login.LoginClient
import fr.free.nrw.commons.auth.login.LoginCallback
import fr.free.nrw.commons.auth.login.LoginFailedException
import fr.free.nrw.commons.auth.login.LoginResult
import retrofit2.Call
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors.newSingleThreadExecutor

class CsrfTokenClient(
    private val csrfWikiSite: WikiSite,
    private val sessionManager: SessionManager
) {
    private var retries = 0
    private var csrfTokenCall: Call<MwQueryResponse?>? = null
    private val loginClient = LoginClient()

    @Throws(Throwable::class)
    fun getTokenBlocking(): String {
        var token = ""
        val service = ServiceFactory.get(csrfWikiSite, CsrfTokenInterface::class.java)
        val userName = AppAdapter.get().getUserName()
        val password = AppAdapter.get().getPassword()

        for (retry in 0 until MAX_RETRIES_OF_LOGIN_BLOCKING) {
            try {
                if (retry > 0) {
                    // Log in explicitly
                    LoginClient()
                        .loginBlocking(csrfWikiSite, userName, password, "")
                }

                // Get CSRFToken response off the main thread.
                val response = newSingleThreadExecutor().submit(Callable {
                    service.getCsrfTokenCall().execute()
                }).get()

                if (response.body()?.query()?.csrfToken().isNullOrEmpty()) {
                    continue
                }

                token = response.body()!!.query()!!.csrfToken()!!
                if (AppAdapter.get().isLoggedIn() && token == ANON_TOKEN) {
                    throw RuntimeException("App believes we're logged in, but got anonymous token.")
                }
                break
            } catch (t: Throwable) {
                Timber.w(t)
            }
        }

        if (token.isEmpty() || token == ANON_TOKEN) {
            throw IOException("Invalid token, or login failure.")
        }
        return token
    }

    @VisibleForTesting
    fun request(service: CsrfTokenInterface, cb: Callback): Call<MwQueryResponse?> =
        requestToken(service, object : Callback {
            override fun success(token: String?) {
                if (AppAdapter.get().isLoggedIn() && token == ANON_TOKEN) {
                    retryWithLogin(cb) {
                        RuntimeException("App believes we're logged in, but got anonymous token.")
                    }
                } else {
                    cb.success(token)
                }
            }

            override fun failure(caught: Throwable?) = retryWithLogin(cb) { caught }

            override fun twoFactorPrompt() = cb.twoFactorPrompt()
        })

    @VisibleForTesting
    fun requestToken(service: CsrfTokenInterface, cb: Callback): Call<MwQueryResponse?> {
        val call = service.getCsrfTokenCall()
        call.enqueue(object : retrofit2.Callback<MwQueryResponse?> {
            override fun onResponse(call: Call<MwQueryResponse?>, response: Response<MwQueryResponse?>) {
                if (call.isCanceled) {
                    return
                }
                cb.success(response.body()!!.query()!!.csrfToken())
            }

            override fun onFailure(call: Call<MwQueryResponse?>, t: Throwable) {
                if (call.isCanceled) {
                    return
                }
                cb.failure(t)
            }
        })
        return call
    }

    private fun retryWithLogin(callback: Callback, caught: () -> Throwable?) {
        val userName = AppAdapter.get().getUserName()
        val password = AppAdapter.get().getPassword()
        if (retries < MAX_RETRIES && !userName.isNullOrEmpty() && !password.isNullOrEmpty()) {
            retries++
            SharedPreferenceCookieManager.getInstance().clearAllCookies()
            login(userName, password, callback) {
                Timber.i("retrying...")
                cancel()
                csrfTokenCall = request(ServiceFactory.get(csrfWikiSite, CsrfTokenInterface::class.java), callback)
            }
        } else {
            callback.failure(caught())
        }
    }

    private fun login(
        username: String,
        password: String,
        callback: Callback,
        retryCallback: () -> Unit
    ) = LoginClient()
        .request(csrfWikiSite, username, password, object : LoginCallback {
        override fun success(loginResult: LoginResult) {
            if (loginResult.pass) {
                sessionManager.updateAccount(loginResult)
                retryCallback()
            } else {
                callback.failure(LoginFailedException(loginResult.message))
            }
        }

        override fun twoFactorPrompt(caught: Throwable, token: String?) =
            callback.twoFactorPrompt()

        // Should not happen here, but call the callback just in case.
        override fun passwordResetPrompt(token: String?) =
            callback.failure(LoginFailedException("Logged in with temporary password."))

        override fun error(caught: Throwable) = callback.failure(caught)
    })

    private fun cancel() {
        loginClient.cancel()
        if (csrfTokenCall != null) {
            csrfTokenCall!!.cancel()
            csrfTokenCall = null
        }
    }

    interface Callback {
        fun success(token: String?)
        fun failure(caught: Throwable?)
        fun twoFactorPrompt()
    }

    companion object {
        private const val ANON_TOKEN = "+\\"
        private const val MAX_RETRIES = 1
        private const val MAX_RETRIES_OF_LOGIN_BLOCKING = 2
    }
}
