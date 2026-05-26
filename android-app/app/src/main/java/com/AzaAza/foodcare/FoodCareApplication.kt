package com.AzaAza.foodcare

import android.app.Application
import android.util.Log
import com.AzaAza.foodcare.notification.ExpiryNotificationManager

class FoodCareApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.d("FoodCareApp", "앱 초기화 시작")

        // 알림 채널 생성
        ExpiryNotificationManager.createNotificationChannel(this)

        // 정해진 시간에만 울리게 알림 스케줄만 예약
        ExpiryNotificationManager.scheduleNotifications(this)

        Log.d("FoodCareApp", "앱이 초기화되었습니다")
    }
}