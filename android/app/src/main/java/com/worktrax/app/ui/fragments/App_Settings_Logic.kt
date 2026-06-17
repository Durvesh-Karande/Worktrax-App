package com.worktrax.app.ui.fragments

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.worktrax.app.R
import com.worktrax.app.data.ThemeMode
import com.worktrax.app.data.WeightUnit
import com.worktrax.app.databinding.AppSettingsDesignBinding
import com.worktrax.app.lib.AnalyticsHelper
import com.worktrax.app.lib.Storage
import com.worktrax.app.lib.loadNativeAd
import com.worktrax.app.lib.populateNativeAdView
import com.worktrax.app.store.AuthViewModel
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class App_Settings_Logic : Fragment() {

    private var _binding: AppSettingsDesignBinding? = null
    private val binding get() = _binding!!

    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })
    private val authVM: AuthViewModel by viewModels({ requireActivity() })
    private var settingsNativeAd: com.google.android.gms.ads.nativead.NativeAd? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AppSettingsDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AnalyticsHelper.screenView("settings")

        settingsVM.refreshNotificationState()
        setupUI()
        setupAppearanceLabels()
        setupObservers()
        setupListeners()
        loadNativeAd(requireActivity(), "ca-app-pub-2162470152606094/9929808986", { ad ->
            settingsNativeAd = ad
            val view = android.view.LayoutInflater.from(requireContext())
                .inflate(R.layout.view_native_ad_settings, binding.containerNativeAd, false) as com.google.android.gms.ads.nativead.NativeAdView
            populateNativeAdView(ad, view)
            binding.containerNativeAd.removeAllViews()
            binding.containerNativeAd.addView(view)
        })
    }

    private fun setupAppearanceLabels() {
        binding.segmentedAppearance.option1.text = getString(R.string.light_label)
        binding.segmentedAppearance.option2.text = getString(R.string.dark_label)
    }

    private fun setupUI() {
        binding.includeTopBar.tvTopBarTitle.text = getString(R.string.settings_title)
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsVM.state.collectLatest { state ->
                    if (binding.etName.text.toString() != state.name) {
                        binding.etName.setText(state.name)
                    }
                    updateUnitUI(state.unit)
                    updateThemeUI(state.theme)
                    updateReminderUI(state)
                }
            }
        }
    }

    private fun updateReminderUI(state: com.worktrax.app.store.SettingsState) {
        binding.switchReminder.isChecked = state.reminderEnabled
        binding.rowReminderTime.visibility = if (state.reminderEnabled) View.VISIBLE else View.GONE
        if (state.reminderEnabled) {
            binding.tvReminderSummary.text = getString(
                R.string.reminder_summary_on,
                state.reminderHour,
                state.reminderMinute
            )
            binding.tvReminderTime.text = "%02d:%02d".format(state.reminderHour, state.reminderMinute)
        } else {
            binding.tvReminderSummary.text = getString(R.string.reminder_summary_off)
        }
    }

    private fun updateUnitUI(unit: WeightUnit) {
        if (unit == WeightUnit.KG) {
            binding.segmentedUnits.option1.setBackgroundResource(R.drawable.shape_chip_selected)
            binding.segmentedUnits.option1.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.segmentedUnits.option2.setBackgroundResource(R.drawable.shape_chip)
            binding.segmentedUnits.option2.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_2))
        } else {
            binding.segmentedUnits.option2.setBackgroundResource(R.drawable.shape_chip_selected)
            binding.segmentedUnits.option2.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.segmentedUnits.option1.setBackgroundResource(R.drawable.shape_chip)
            binding.segmentedUnits.option1.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_2))
        }
    }

    private fun updateThemeUI(theme: ThemeMode) {
        val lightSelected = theme == ThemeMode.LIGHT
        val darkSelected = theme == ThemeMode.DARK
        binding.segmentedAppearance.option1.setBackgroundResource(
            if (lightSelected) R.drawable.shape_chip_selected else R.drawable.shape_chip
        )
        binding.segmentedAppearance.option1.setTextColor(
            ContextCompat.getColor(requireContext(), if (lightSelected) R.color.white else R.color.ink_2)
        )
        binding.segmentedAppearance.option2.setBackgroundResource(
            if (darkSelected) R.drawable.shape_chip_selected else R.drawable.shape_chip
        )
        binding.segmentedAppearance.option2.setTextColor(
            ContextCompat.getColor(requireContext(), if (darkSelected) R.color.white else R.color.ink_2)
        )
    }

    private fun setupListeners() {
        binding.includeTopBar.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = s.toString()
                if (name.isNotBlank()) {
                    settingsVM.setName(name)
                }
            }
        })

        binding.segmentedUnits.option1.setOnClickListener {
            settingsVM.setUnit(WeightUnit.KG)
            AnalyticsHelper.unitChanged("kg")
        }
        binding.segmentedUnits.option2.setOnClickListener {
            settingsVM.setUnit(WeightUnit.LB)
            AnalyticsHelper.unitChanged("lb")
        }

        binding.segmentedAppearance.option1.setOnClickListener {
            settingsVM.setTheme(ThemeMode.LIGHT)
            AnalyticsHelper.themeChanged("light")
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        binding.segmentedAppearance.option2.setOnClickListener {
            settingsVM.setTheme(ThemeMode.DARK)
            AnalyticsHelper.themeChanged("dark")
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            settingsVM.setReminderEnabled(isChecked)
        }

        binding.rowReminderTime.setOnClickListener {
            val h = settingsVM.state.value.reminderHour
            val m = settingsVM.state.value.reminderMinute
            TimePickerDialog(requireContext(), { _, hour, minute ->
                settingsVM.setReminderTime(hour, minute)
            }, h, m, true).show()
        }

        binding.rowPrivacy.setOnClickListener {
            val url = "https://sites.google.com/view/privacy-policy-for-worktrax/home"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        }

        binding.rowDeleteAccount.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setPositiveButton(R.string.delete_account_confirm) { _, _ ->
                    authVM.deleteAccount { success, msg ->
                        if (success) {
                            Storage.prefs(requireContext()).edit().clear().apply()
                            settingsVM.reset()
                            try {
                                findNavController().navigate(R.id.action_settings_to_auth)
                            } catch (_: Exception) {}
                        }
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }

        binding.btnSignOut.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.sign_out_title)
                .setMessage(R.string.sign_out_message)
                .setPositiveButton(R.string.sign_out_confirm) { _, _ ->
                    AnalyticsHelper.signedOut()
                    authVM.signOut()
                    Storage.prefs(requireContext()).edit().clear().apply()
                    settingsVM.reset()
                    findNavController().navigate(R.id.action_settings_to_auth)
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
    }

    override fun onDestroyView() {
        settingsNativeAd?.destroy()
        settingsNativeAd = null
        super.onDestroyView()
        _binding = null
    }
}
