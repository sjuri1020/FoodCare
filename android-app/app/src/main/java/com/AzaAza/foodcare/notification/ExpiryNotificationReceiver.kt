package com.AzaAza.foodcare.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ExpiryNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ExpiryNotification", "📢 BroadcastReceiver 실행됨! 시간: ${System.currentTimeMillis()}")

        // 테스트 알림인지 확인
        if (intent.getBooleanExtra("TEST_NOTIFICATION", false)) {
            Log.d("ExpiryNotification", "테스트 알림 실행")
        }

        // 일회성 테스트인지 확인
        if (intent.getBooleanExtra("ONE_TIME_TEST", false)) {
            Log.d("ExpiryNotification", "일회성 테스트 알림이 실행되었습니다")
        }

        // 소비기한 알림 확인 및 표시
        ExpiryNotificationManager.checkExpiringIngredients(context)

        // Android 12 이상에서 다음 알림 재예약 (체인 방식)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            ExpiryNotificationManager.scheduleNotifications(context)
        }
    }
}