package com.AzaAza.foodcare.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.IngredientDto
import com.AzaAza.foodcare.ui.FoodManagementActivity
import com.AzaAza.foodcare.data.UserSession  // 수정된 import 경로
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ExpiryNotificationManager {

    companion object {
        private const val CHANNEL_ID = "expiry_notification_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "ExpiryNotification"

        // 새로 추가할 문구 리스트들
        private val overdueTexts = listOf(
            "🚨 [식자재명]의 소비기한이 지나버렸어요! 바로 확인해주세요.",
            "⚰️ [식자재명]이(가) 냉장고에서 잠들었어요... 이제 보내줄 시간이에요.",
            "📦 [식자재명], 소비기한이 [지난 일수]일 지났습니다. 폐기 여부를 확인해주세요.",
            "🥶 [식자재명]이(가) 꽤 오래된 것 같아요. 다시 사용할 수 있을지 꼭 점검해보세요!",
            "🗑️ [식자재명]의 소비기한이 지났어요. 정리를 고려해보시는 건 어떨까요?",
            "👃 [식자재명]... 혹시 냄새가 이상하진 않나요? 소비기한이 지났습니다!",
            "⛔ [식자재명]의 소비기한 초과! 건강을 위해 섭취 전 꼭 확인해주세요.",
            "😵 [식자재명]이(가) 냉장고에서 구조 요청을 보내고 있어요. 소비기한 확인!",
            "🧊 [식자재명], 시간이 멈춘 줄 알았지만... 이미 소비기한이 지났어요!"
        )

        private val nearExpiryTexts = listOf(
            "⚠️ [식자재명]의 소비기한이 곧 도래합니다. 신속히 사용해주세요!",
            "⏰ [식자재명]의 소비기한이 [남은 일수]일 남았습니다. 빠른 소비를 권장합니다.",
            "🍽️ [식자재명], 이제 곧 작별 인사를 할 시간이에요. 오늘의 요리에 활용해보세요!",
            "👩‍🍳 [식자재명]의 소비기한이 임박했습니다. 오늘은 맛있는 요리를 만들어보는 건 어떨까요?",
            "🍳 [식자재명]을(를) 활용한 맛있는 요리로 오늘의 식사를 준비해보세요. 소비기한이 가까워지고 있어요!"
        )

        private const val todayText = "📅 [식자재명]의 소비기한이 오늘입니다. 즉시 사용하시기 바랍니다."

        // 알림 채널 생성 함수
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "식자재 소비기한 알림"
                val descriptionText = "식자재의 소비기한이 임박했을 때 알림을 표시합니다"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    setShowBadge(true)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)

                Log.d(TAG, "알림 채널이 생성되었습니다")
            }
        }
        /* 미사용으로 삭제 됨
                // 배터리 최적화 예외 설정 화면으로 이동
                fun requestBatteryOptimizationExemption(context: Context) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "배터리 최적화 예외 설정 화면 열기 실패", e)
                            try {
                                val settingsIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                context.startActivity(settingsIntent)
                            } catch (e2: Exception) {
                                Log.e(TAG, "배터리 설정 화면 열기 실패", e2)
                                Toast.makeText(context, "설정 앱에서 배터리 > 배터리 최적화에서 FoodCare 앱을 '최적화 안함'으로 설정해주세요.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }*/

        // 알림 일정 예약 함수 (개선됨)
        fun scheduleNotifications(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // 정확한 알람 권한 확인 (Android 12 이상)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "정확한 알람 권한이 없습니다.")
                    requestExactAlarmPermission(context)
                    return
                }
            }

            // 알림 시간 설정
            val notificationTimes = listOf(
                Pair(7, 0),    // 오전 7시
                Pair(8, 0),    // 오전 8시
                Pair(11, 0),   // 오전 11시
                Pair(18, 0),   // 오후 6시
                Pair(19, 0)    // 오후 7시
            )

            // 기존 알람 취소
            cancelAllAlarms(context)

            Log.d(TAG, "알림 예약을 시작합니다...")

            notificationTimes.forEachIndexed { index, (hour, minute) ->
                val intent = Intent(context, ExpiryNotificationReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val now = System.currentTimeMillis()
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    if (timeInMillis <= now) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                        scheduleRepeatingAlarm(context, alarmManager, hour, minute, index + 100)
                    } else {
                        alarmManager.setRepeating(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            AlarmManager.INTERVAL_DAY,
                            pendingIntent
                        )
                    }

                    Log.d(TAG, "알림이 예약되었습니다: ${hour}시 ${minute}분, 다음 알림: ${calendar.time}")
                } catch (e: Exception) {
                    Log.e(TAG, "알림 예약 실패: ${hour}시 ${minute}분", e)
                }
            }

            Log.d(TAG, "모든 알림 예약이 완료되었습니다.")
        }

        private fun scheduleRepeatingAlarm(context: Context, alarmManager: AlarmManager, hour: Int, minute: Int, requestCode: Int) {
            val intent = Intent(context, ExpiryNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val tomorrowCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    tomorrowCalendar.timeInMillis,
                    pendingIntent
                )
            }
        }

        private fun cancelAllAlarms(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            for (i in 0 until 5) {
                val intent = Intent(context, ExpiryNotificationReceiver::class.java)

                val pendingIntent = PendingIntent.getBroadcast(
                    context, i, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                val repeatingPendingIntent = PendingIntent.getBroadcast(
                    context, i + 100, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )

                pendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
                repeatingPendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
            }

            val testIntent = Intent(context, ExpiryNotificationReceiver::class.java)
            val testPendingIntent = PendingIntent.getBroadcast(
                context, 9999, testIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            testPendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }

            Log.d(TAG, "기존 알람들이 취소되었습니다.")
        }

        private fun requestExactAlarmPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "정확한 알람 권한 설정 화면 열기 실패", e)
                }
            }
        }

        /* 미사용으로 삭제 됨
        fun showExpiryNotificationNow(context: Context) {
            Log.d(TAG, "즉시 알림 테스트 실행")
            checkExpiringIngredients(context)
        }*/

        // 현재 로그인한 사용자의 소비기한 임박 식자재 확인 및 알림 표시
        fun checkExpiringIngredients(context: Context) {
            Log.d(TAG, "소비기한 확인을 시작합니다...")

            val currentUserId = UserSession.getUserId(context)
            if (currentUserId == -1) {
                Log.w(TAG, "로그인한 사용자 정보가 없습니다. 알림을 건너뜁니다.")
                return
            }

            RetrofitClient.ingredientApiService.getIngredients(currentUserId)
                .enqueue(object : Callback<List<IngredientDto>> {
                    override fun onResponse(call: Call<List<IngredientDto>>, response: Response<List<IngredientDto>>) {
                        if (response.isSuccessful) {
                            val ingredients = response.body()
                            Log.d(TAG, "사용자 $currentUserId 의 ${ingredients?.size ?: 0}개 식자재 데이터를 받았습니다.")

                            if (ingredients != null && ingredients.isNotEmpty()) {
                                val userIngredients = ingredients.filter { it.userId == currentUserId }
                                if (userIngredients.isNotEmpty()) {
                                    processIngredients(context, userIngredients)
                                } else {
                                    Log.d(TAG, "현재 사용자의 식자재가 없습니다")
                                }
                            } else {
                                Log.d(TAG, "소비기한이 임박한 식자재가 없습니다")
                            }
                        } else {
                            Log.e(TAG, "서버 응답 오류: ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<List<IngredientDto>>, t: Throwable) {
                        Log.e(TAG, "서버 연결 실패", t)
                    }
                })
        }

        private fun processIngredients(context: Context, ingredientDtos: List<IngredientDto>) {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val nearExpiryIngredients = ingredientDtos.filter { ingredient ->
                try {
                    val expiryDate = apiDateFormat.parse(ingredient.expiryDate) ?: return@filter false
                    val diffDays = ((expiryDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
                    diffDays <= 3
                } catch (e: Exception) {
                    Log.e(TAG, "날짜 파싱 오류", e)
                    false
                }
            }.sortedBy {
                try {
                    apiDateFormat.parse(it.expiryDate)?.time ?: Long.MAX_VALUE
                } catch (e: Exception) {
                    Long.MAX_VALUE
                }
            }

            Log.d(TAG, "소비기한이 임박한 식자재: ${nearExpiryIngredients.size}개")

            if (nearExpiryIngredients.isNotEmpty()) {
                showNotification(context, nearExpiryIngredients)
            } else {
                Log.d(TAG, "소비기한이 임박한 식자재가 없습니다")
            }
        }

        private fun showNotification(context: Context, nearExpiryIngredients: List<IngredientDto>) {
            Log.d(TAG, "알림을 표시합니다: ${nearExpiryIngredients.size}개 식자재")

            val intent = Intent(context, FoodManagementActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val firstIngredient = nearExpiryIngredients.first()

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val expiryDate = apiDateFormat.parse(firstIngredient.expiryDate) ?: today
            val diffDays = ((expiryDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()

            val notificationText = when {
                diffDays < 0 -> {
                    overdueTexts.random()
                        .replace("[식자재명]", firstIngredient.name)
                        .replace("[지난 일수]", (-diffDays).toString())
                }
                diffDays == 0 -> {
                    todayText.replace("[식자재명]", firstIngredient.name)
                }
                else -> {
                    nearExpiryTexts.random()
                        .replace("[식자재명]", firstIngredient.name)
                        .replace("[남은 일수]", diffDays.toString())
                }
            }

            val notificationTitle = if (nearExpiryIngredients.size > 1) {
                "${nearExpiryIngredients.size}개 식자재의 소비기한이 임박했습니다"
            } else {
                "${firstIngredient.name}의 소비기한이 임박했습니다"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.bell)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setColor(ContextCompat.getColor(context, R.color.your_background_color))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())

            if (nearExpiryIngredients.size > 1) {
                val inboxStyle = NotificationCompat.InboxStyle()
                    .setBigContentTitle("${nearExpiryIngredients.size}개 식자재의 소비기한이 임박했습니다")

                nearExpiryIngredients.take(5).forEach { ingredient ->
                    try {
                        val expDate = apiDateFormat.parse(ingredient.expiryDate) ?: today
                        val expDiffDays = ((expDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
                        inboxStyle.addLine("${ingredient.name}: ${if (expDiffDays == 0) "오늘까지" else "${expDiffDays}일 남음"}")
                    } catch (e: Exception) {
                        inboxStyle.addLine("${ingredient.name}: 소비기한 확인 필요")
                    }
                }

                builder.setStyle(inboxStyle)
            }

            with(NotificationManagerCompat.from(context)) {
                try {
                    notify(NOTIFICATION_ID, builder.build())
                    Log.d(TAG, "알림이 성공적으로 표시되었습니다: $notificationTitle")
                } catch (e: SecurityException) {
                    Log.e(TAG, "알림 권한이 없습니다", e)
                }
            }
        }
    }
}