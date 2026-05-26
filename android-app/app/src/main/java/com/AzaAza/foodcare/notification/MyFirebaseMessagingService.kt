package com.AzaAza.foodcare.notification

import android.util.Log
import com.AzaAza.foodcare.api.RetrofitClient
import com.AzaAza.foodcare.models.UpdateFcmTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage



class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "푸시 메시지 도착: ${remoteMessage.data}") // 꼭 로그 찍기!
        val type = remoteMessage.data["type"]
        if (type == "invite") {
            val inviter = remoteMessage.data["inviter_name"] ?: "누군가"
            InviteNotificationManager.showInviteNotification(applicationContext, inviter)
        }
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "새 FCM 토큰: $token")
        Log.e("FCM", "로그인 아이디가 null임, FCM 토큰 저장 실패")


        // SharedPreferences(혹은 로그인 시 세팅한 값 등)에서 내 로그인 아이디 꺼내기
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val loginId = prefs.getString("USER_LOGIN_ID", null)
        Log.d("FCM", "onNewToken에서 불러온 loginId=$loginId")
        if (loginId != null) {
            val req = UpdateFcmTokenRequest(login_id = loginId, fcm_token = token)
            RetrofitClient.userApiService.updateFcmToken(req)
                .enqueue(object : retrofit2.Callback<Void> {
                    override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                        Log.d("FCM", "서버로 FCM 토큰 저장 성공")
                    }
                    override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                        Log.e("FCM", "서버로 FCM 토큰 저장 실패: ${t.message}")
                    }
                })
        } else {
            Log.e("FCM", "로그인 아이디가 null임, FCM 토큰 저장 실패")
        }
    }
}