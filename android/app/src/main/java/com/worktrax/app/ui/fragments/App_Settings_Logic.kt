package com.worktrax.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
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
import com.worktrax.app.lib.Storage
import com.worktrax.app.store.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class App_Settings_Logic : Fragment() {

    private var _binding: AppSettingsDesignBinding? = null
    private val binding get() = _binding!!

    private val settingsVM: SettingsViewModel by viewModels({ requireActivity() })

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

        setupUI()
        setupAppearanceLabels()
        setupObservers()
        setupListeners()
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
                }
            }
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

        binding.segmentedUnits.option1.setOnClickListener { settingsVM.setUnit(WeightUnit.KG) }
        binding.segmentedUnits.option2.setOnClickListener { settingsVM.setUnit(WeightUnit.LB) }

        binding.segmentedAppearance.option1.setOnClickListener {
            settingsVM.setTheme(ThemeMode.LIGHT)
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        binding.segmentedAppearance.option2.setOnClickListener {
            settingsVM.setTheme(ThemeMode.DARK)
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        binding.btnSignOut.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.sign_out_title)
                .setMessage(R.string.sign_out_message)
                .setPositiveButton(R.string.sign_out_confirm) { _, _ ->
                    Storage.prefs(requireContext()).edit().clear().apply()
                    settingsVM.reset()
                    findNavController().popBackStack(R.id.homeFragment, false)
                }
                .setNegativeButton(R.string.cancel_label, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
