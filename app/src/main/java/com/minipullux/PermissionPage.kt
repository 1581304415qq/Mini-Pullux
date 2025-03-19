package com.minipullux

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionPage() {
    val onPermissionDenied = { deniedPermissions: List<String> ->
        // 处理权限被拒绝的情况
        if (deniedPermissions.contains(Manifest.permission.BLUETOOTH_SCAN)) {
            // 提示用户需要蓝牙扫描权限
        }
        if (deniedPermissions.isEmpty()) {
        }
    }
    RequestPermission(onPermissionDenied)
}

/*  权限申请    */
@Composable
private fun RequestPermission(callBack: (list: List<String>) -> Unit) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val permissionList = mutableListOf<String>()
    permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
    permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
        permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
        permissionList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionList.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    //存储用户拒绝授权的权限
    val permissionTemp: ArrayList<String> = ArrayList()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { isGranted ->
            permissionTemp.clear()
            isGranted.keys.forEach { permission ->
                if (isGranted[permission] == false) {
                    permissionTemp.add(permission)
                    toastShow(
                        context = context,
                        msg = "requestPermissions ERROR $permission",
                    )
                }
            }
            callBack(permissionTemp.toList())
        })

    val lifecycleObserver = remember {
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                launcher.launch(permissionList.toTypedArray())
            }
        }
    }

    DisposableEffect(lifecycle, lifecycleObserver) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
}


fun toastShow(context: Context, tag: String = "TAG", msg: String) =
    Toast.makeText(context, "$tag $msg", Toast.LENGTH_SHORT).show()