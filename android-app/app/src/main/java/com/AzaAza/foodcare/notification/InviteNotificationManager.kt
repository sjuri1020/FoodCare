package com.AzaAza.foodcare.notification


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.ui.MemberActivity
import kotlin.random.Random

object InviteNotificationManager {

    private const val CHANNEL_ID = "invite_notification_channel"

    // 알림 채널 생성 (최초 한 번만)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "구성원 초대 알림"
            val descriptionText = "다른 사용자가 나를 구성원으로 초대했을 때 알림"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 초대 알림 표시
    fun showInviteNotification(context: Context, inviterName: String) {
        createNotificationChannel(context)

        val intent = Intent(context, MemberActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.bell)
            .setContentTitle("구성원 초대 알림")
            .setContentText("${inviterName}님이 당신을 구성원으로 추가하셨습니다")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // 1. Android 13+ 알림 권한 체크!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context, permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                // 권한 없으면 알림 띄우지 않고 return (or Log)
                return
            }
        }

        // 2. 실제 알림 띄우기 (권한 있을 때만)
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(Random.nextInt(), builder.build())
            } catch (e: SecurityException) {
                // 혹시라도 권한 없어서 예외 발생하면 무시 (또는 Log)
            }
        }
    }


}