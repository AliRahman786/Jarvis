package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages Firebase Authentication and Google Sign-In via Jetpack Credential Manager.
 * Uses main module Firebase imports (BoM 34.x+) and strictly authenticates via Google Sign-In.
 */
class JarvisAuthManager {
    private val firebaseAuth: FirebaseAuth? = try {
        Firebase.auth
    } catch (e: Exception) {
        Log.w("JarvisAuth", "FirebaseAuth not initialized: ${e.message}")
        null
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(firebaseAuth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        _currentUser.value = auth.currentUser
    }

    init {
        try {
            firebaseAuth?.addAuthStateListener(authStateListener)
        } catch (_: Exception) {}
    }

    fun clearAuthMessage() {
        _authMessage.value = null
    }

    /**
     * Silent Auto-Sign-In on startup using GetGoogleIdOption (separate single-option request).
     */
    fun attemptAutoSignIn(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit = {},
        onUnauthenticated: () -> Unit = {}
    ) {
        val auth = firebaseAuth
        if (auth?.currentUser != null) {
            _currentUser.value = auth.currentUser
            onAuthSuccess()
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onUnauthenticated()
            return
        }

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth?.signInWithCredential(authCredential)?.await()
                    _currentUser.value = auth?.currentUser
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    /**
     * Interactive Google Sign-In using GetSignInWithGoogleOption via CredentialManager.
     */
    fun onGoogleSignInClicked(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit = {},
        onAuthError: (String) -> Unit = {},
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            val msg = "Google Sign-In configuration missing: default_web_client_id not found"
            _authMessage.value = msg
            onAuthError(msg)
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        _isSigningIn.value = true
        _authMessage.value = null

        scope.launch {
            try {
                val activityContext = (context as? Activity) ?: context
                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    firebaseAuth?.signInWithCredential(authCredential)?.await()
                    _currentUser.value = firebaseAuth?.currentUser
                    _isSigningIn.value = false
                    _authMessage.value = "Authenticated via Google Identity."
                    onAuthSuccess()
                } else {
                    _isSigningIn.value = false
                    val msg = "Unexpected credential type returned."
                    _authMessage.value = msg
                    onAuthError(msg)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                _isSigningIn.value = false
                _authMessage.value = "Google Sign-In cancelled."
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                _isSigningIn.value = false
                val msg = e.localizedMessage ?: "Sign in failed"
                _authMessage.value = msg
                onAuthError(msg)
            }
        }
    }

    /**
     * Clears CredentialManager state first inside NonCancellable, then signs out of FirebaseAuth.
     */
    fun signOut(
        context: Context,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        val credentialManager = CredentialManager.create(context)
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                } finally {
                    try {
                        firebaseAuth?.signOut()
                    } catch (_: Exception) {}
                    _currentUser.value = null
                    _authMessage.value = "Signed out of Google account."
                    onSignOutComplete()
                }
            }
        }
    }

    fun cleanup() {
        try {
            firebaseAuth?.removeAuthStateListener(authStateListener)
        } catch (_: Exception) {}
    }
}
