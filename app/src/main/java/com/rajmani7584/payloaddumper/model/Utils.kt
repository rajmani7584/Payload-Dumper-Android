package com.rajmani7584.payloaddumper.model

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.rajmani7584.payloaddumper.ui.screens.LogManager
import java.io.File


object Utils {

    fun hasPermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotifyPermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun requestPermission(activity: Activity) {
        LogManager.log("Requesting file permission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = "package:${activity.packageName}".toUri()
            activity.startActivity(intent)
        } else {
            if (activity.shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LogManager.log("Error: can't show permission dialog! allow from setting")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                activity.startActivity(intent)
            } else {
                activity.requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
            }
        }
    }

    fun requestNotifyPermission(activity: Activity) {
        LogManager.log("Requesting notification permission")
        if (activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
            LogManager.log("Error: can't show permission dialog! allow from setting")
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", activity.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", activity.packageName, null)
                intent.data = uri
                activity.startActivity(intent)
            }
        }
    }
    fun setupPartitionName(
        outputDirectory: String,
        partitionName: String,
        overwrite: Boolean,
        counter: Int = 0
    ): String {
        val outDir = File(outputDirectory)
        if (!outDir.exists()) outDir.mkdirs()
        val partition = File(outDir, "${partitionName}${if (counter == 0) "" else "(${counter})"}.img")

        if (overwrite) return "${outputDirectory}/${partition.name}"
        return if (!partition.exists()) {
            "${outputDirectory}/${partition.name}"
        } else {
            setupPartitionName(outputDirectory, partitionName, false, counter + 1)
        }
    }

    fun parseSize(bytes: Long): String = when {
        bytes < 1024 ->
            "$bytes B"
        bytes < 1024L * 1024 ->
            "%.2f KB".format(bytes / 1000.0)
        bytes < 1024L * 1024 * 1024 ->
            "%.2f MB".format(bytes / (1024.0 * 1024))
        else ->
            "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    fun parseBufSize(kbyte: Int): String = when {
        kbyte < 1024 -> "$kbyte KB"
        else -> "${kbyte / 1024} MB"
    }
}