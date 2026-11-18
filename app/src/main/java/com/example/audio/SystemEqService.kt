package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class SystemEqService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                SystemDspEngine.setServiceActive(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val active = intent.getBooleanExtra(EXTRA_MASTER_ACTIVE, true)
                val preset = intent.getStringExtra(EXTRA_PRESET_NAME) ?: "DEFAULT"
                startOrUpdateForeground(active, preset)
                SystemDspEngine.setServiceActive(true)
            }
            else -> {
                val active = intent?.getBooleanExtra(EXTRA_MASTER_ACTIVE, true) ?: true
                val preset = intent?.getStringExtra(EXTRA_PRESET_NAME) ?: "DEFAULT"
                startOrUpdateForeground(active, preset)
                SystemDspEngine.setServiceActive(true)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        SystemDspEngine.setServiceActive(false)
        super.onDestroy()
    }

    private fun startOrUpdateForeground(masterActive: Boolean, presetName: String) {
        val notification = buildNotification(masterActive, presetName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(masterActive: Boolean, presetName: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SystemEqService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (masterActive) {
            getString(R.string.notification_title_active)
        } else {
            getString(R.string.notification_title_bypass)
        }

        val statusText = if (masterActive) {
            "Profil: $presetName · System-Audio aktiv"
        } else {
            "Profil: $presetName · DSP umgangen"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(title)
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.notification_action_stop), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "doteq_system_dsp_channel"
        private const val NOTIFICATION_ID = 4041
        const val ACTION_START = "com.example.audio.ACTION_START"
        const val ACTION_UPDATE = "com.example.audio.ACTION_UPDATE"
        const val ACTION_STOP = "com.example.audio.ACTION_STOP"
        const val EXTRA_MASTER_ACTIVE = "extra_master_active"
        const val EXTRA_PRESET_NAME = "extra_preset_name"

        fun startOrUpdate(context: Context, masterActive: Boolean, presetName: String) {
            val intent = Intent(context, SystemEqService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_MASTER_ACTIVE, masterActive)
                putExtra(EXTRA_PRESET_NAME, presetName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SystemEqService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
