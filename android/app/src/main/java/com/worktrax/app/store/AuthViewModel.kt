package com.worktrax.app.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthResult
import com.worktrax.app.lib.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class AuthState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(AuthState(isLoggedIn = auth.currentUser != null))
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val currentUser get() = auth.currentUser

    private val _googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(app.getString(com.worktrax.app.R.string.web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(app, gso)
    }

    val googleSignInClient get() = _googleSignInClient

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                withTimeout(15_000L) {
                    auth.signInWithEmailAndPassword(email, password).await()
                }
                _state.value = AuthState(isLoggedIn = true)
            } catch (e: TimeoutCancellationException) {
                _state.value = AuthState(isLoggedIn = false, error = "Request timed out. Check your connection.")
            } catch (e: Exception) {
                _state.value = AuthState(isLoggedIn = false, error = e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                withTimeout(15_000L) {
                    auth.createUserWithEmailAndPassword(email, password).await()
                }
                migrateIfNeeded()
                _state.value = AuthState(isLoggedIn = true)
            } catch (e: TimeoutCancellationException) {
                _state.value = AuthState(isLoggedIn = false, error = "Request timed out. Check your connection.")
            } catch (e: Exception) {
                _state.value = AuthState(isLoggedIn = false, error = e.localizedMessage ?: "Sign up failed")
            }
        }
    }

    fun firebaseAuthWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                withTimeout(15_000L) {
                    auth.signInWithCredential(credential).await()
                }
                migrateIfNeeded()
                _state.value = AuthState(isLoggedIn = true)
            } catch (e: TimeoutCancellationException) {
                _state.value = AuthState(isLoggedIn = false, error = "Request timed out. Check your connection.")
            } catch (e: Exception) {
                _state.value = AuthState(isLoggedIn = false, error = e.localizedMessage ?: "Google sign in failed")
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _googleSignInClient.signOut()
        _state.value = AuthState(isLoggedIn = false)
    }

    private suspend fun migrateIfNeeded() {
        val ctx = getApplication<Application>()
        if (!FirestoreRepository.hasMigrated(ctx)) {
            FirestoreRepository.migrateLocalData(ctx)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
