package com.compatchecker.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
inline fun <reified T : ViewModel> compatViewModel(
    crossinline create: () -> T,
): T = viewModel(
    factory = viewModelFactory {
        initializer { create() }
    },
)
