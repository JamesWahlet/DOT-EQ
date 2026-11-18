package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.configureAppCheck
import com.example.ui.DotEqAppRoot
import com.example.ui.theme.DotEqTheme
import com.example.ui.viewmodel.EqViewModel

class MainActivity : ComponentActivity() {

    private val eqViewModel: EqViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val themeMode by eqViewModel.themeMode.collectAsStateWithLifecycle()
            DotEqTheme(themeMode = themeMode) {
                DotEqAppRoot(viewModel = eqViewModel)
            }
        }
    }
}
