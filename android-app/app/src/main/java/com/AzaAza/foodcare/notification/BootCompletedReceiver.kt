package com.AzaAza.foodcare.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "부팅이 완료되었습니다. 알림을 다시 예약합니다.")

            // 알림 채널 생성
            ExpiryNotificationManager.createNotificationChannel(context)

            // 알림 예약 다시 설정
            ExpiryNotificationManager.scheduleNotifications(context)

            // 즉시 만료 식자재 확인 및 알림 테스트 실행
            ExpiryNotificationManager.checkExpiringIngredients(context)

        }
    }
}
