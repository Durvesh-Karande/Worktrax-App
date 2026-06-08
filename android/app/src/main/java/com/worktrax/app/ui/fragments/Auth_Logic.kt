package com.worktrax.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.worktrax.app.R
import com.worktrax.app.databinding.AuthDesignBinding
import com.worktrax.app.store.AuthViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Suppress("ClassName")
class Auth_Logic : Fragment() {

    private var _binding: AuthDesignBinding? = null
    private val binding get() = _binding!!

    private val authVM: AuthViewModel by viewModels()

    private var isSignUp = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                authVM.firebaseAuthWithGoogle(idToken)
            } else {
                binding.tvError.text = "Google Sign-In failed: no ID token"
                binding.tvError.visibility = View.VISIBLE
            }
        } catch (e: ApiException) {
            binding.tvError.text = "Google Sign-In failed (Status Code: ${e.statusCode}). Please check your SHA-1 and Client ID configuration."
            binding.tvError.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AuthDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnPrimary.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isBlank()) {
                binding.tvError.text = getString(R.string.auth_error_email_blank)
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (password.isBlank()) {
                binding.tvError.text = getString(R.string.auth_error_password_blank)
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (isSignUp && password.length < 6) {
                binding.tvError.text = getString(R.string.auth_error_weak_password)
                binding.tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            binding.tvError.visibility = View.GONE
            if (isSignUp) authVM.signUpWithEmail(email, password)
            else authVM.signInWithEmail(email, password)
        }

        binding.btnToggleMode.setOnClickListener {
            isSignUp = !isSignUp
            binding.btnPrimary.text = if (isSignUp) getString(R.string.auth_sign_up)
            else getString(R.string.auth_sign_in)
            binding.btnToggleMode.text = if (isSignUp) getString(R.string.auth_toggle_sign_in)
            else getString(R.string.auth_toggle_sign_up)
            binding.tvTitle.text = if (isSignUp) getString(R.string.auth_sign_up)
            else getString(R.string.auth_title)
            binding.tvError.visibility = View.GONE
        }

        binding.btnGoogle.setOnClickListener {
            val signInIntent = authVM.googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authVM.state.collect { state ->
                    if (!isAdded) return@collect

                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.btnPrimary.isEnabled = !state.isLoading
                    binding.btnGoogle.isEnabled = !state.isLoading

                    if (state.error != null) {
                        binding.tvError.text = state.error
                        binding.tvError.visibility = View.VISIBLE
                    }

                    if (state.isLoggedIn) {
                        findNavController().navigate(R.id.action_auth_to_home)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
