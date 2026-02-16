package com.bintianqi.owndroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.bintianqi.owndroid.dpm.dhizukuErrorStatus
import com.bintianqi.owndroid.ui.NavTransition
import com.bintianqi.owndroid.ui.navigation.Destination
import com.bintianqi.owndroid.ui.navigation.myEntryProvider
import com.bintianqi.owndroid.ui.theme.OwnDroidTheme
import java.util.Locale

@ExperimentalMaterial3Api
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val context = applicationContext
        val locale = context.resources?.configuration?.locale
        zhCN = locale == Locale.SIMPLIFIED_CHINESE || locale == Locale.CHINESE || locale == Locale.CHINA
        val vm by viewModels<MyViewModel>()
        if (
            VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        registerPackageRemovedReceiver(this) {
            vm.onPackageRemoved(it)
        }
        setContent {
            var appLockDialog by rememberSaveable { mutableStateOf(false) }
            val theme by vm.theme.collectAsStateWithLifecycle()
            OwnDroidTheme(theme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                val backstack = rememberNavBackStack(Destination.Home)
                NavDisplay(
                    backstack,
                    onBack = {
                        backstack.removeLastOrNull()
                    },
                    transitionSpec = {
                        NavTransition.transition
                    },
                    popTransitionSpec = {
                        NavTransition.popTransition
                    },
                    predictivePopTransitionSpec = {
                        NavTransition.popTransition
                    }
                ) {
                    myEntryProvider(it as Destination, backstack, vm)
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                if (appLockDialog) {
                    AppLockDialog({ appLockDialog = false }) { moveTaskToBack(true) }
                }
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (
                            (event == Lifecycle.Event.ON_CREATE && !SP.lockPasswordHash.isNullOrEmpty()) ||
                            (event == Lifecycle.Event.ON_RESUME && SP.lockWhenLeaving)
                        ) {
                            appLockDialog = true
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                LaunchedEffect(Unit) {
                    val profileNotActivated = !SP.managedProfileActivated && Privilege.status.value.work
                    if(profileNotActivated) {
                        Privilege.DPM.setProfileEnabled(Privilege.DAR)
                        SP.managedProfileActivated = true
                        context.popToast(R.string.work_profile_activated)
                    }
                }
                DhizukuErrorDialog {
                    dhizukuErrorStatus.value = 0
                    Privilege.updateStatus()
                    backstack += Destination.WorkingModes(false)
                    repeat(backstack.size - 1) {
                        backstack.removeFirstOrNull()
                    }
                }
            }
        }
    }
}

@Composable
private fun DhizukuErrorDialog(onClose: () -> Unit) {
    val status by dhizukuErrorStatus.collectAsState()
    if (status != 0) {
        LaunchedEffect(Unit) {
            SP.dhizuku = false
        }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClose) {
                    Text(stringResource(R.string.confirm))
                }
            },
            title = { Text(stringResource(R.string.dhizuku)) },
            text = {
                val text = stringResource(
                    when(status){
                        1 -> R.string.failed_to_init_dhizuku
                        2 -> R.string.dhizuku_permission_not_granted
                        else -> R.string.failed_to_init_dhizuku
                    }
                )
                Text(text)
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        )
    }
}
