package com.AzaAza.foodcare.data

import android.content.Context

object UserSession {
    private const val PREF_NAME = "user_prefs"
    private const val USER_ID_KEY = "USER_ID"
    private const val USER_LOGIN_ID_KEY = "USER_LOGIN_ID"
    private const val USER_USERNAME_KEY = "USER_USERNAME"  // 추가
    private const val IS_LOGGED_IN_KEY = "IS_LOGGED_IN"

    /**
     * 현재 로그인한 사용자 ID 가져오기
     */
    fun getUserId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(USER_ID_KEY, -1)
    }

    /**
     * 현재 로그인한 사용자 로그인 ID 가져오기
     */
    fun getUserLoginId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(USER_LOGIN_ID_KEY, null)
    }

    /**
     * 현재 로그인한 사용자 이름 가져오기 (새로 추가)
     */
    fun getUsername(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(USER_USERNAME_KEY, "사용자") ?: "사용자"
    }

    /**
     * 로그인 여부 확인
     */
    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(IS_LOGGED_IN_KEY, false)
    }

    /**
     * 사용자 정보 저장 (수정됨 - username 파라미터 추가)
     */
    fun saveUserInfo(context: Context, userId: Int, loginId: String, username: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(USER_ID_KEY, userId)
            .putString(USER_LOGIN_ID_KEY, loginId)
            .putString(USER_USERNAME_KEY, username)  // 추가
            .putBoolean(IS_LOGGED_IN_KEY, true)
            .apply()
    }

    /**
     * 기존 호환성을 위한 오버로드 메서드 (username 없이 호출할 수 있도록)
     */
    fun saveUserInfo(context: Context, userId: Int, loginId: String) {
        saveUserInfo(context, userId, loginId, "사용자")
    }

    /**
     * 로그아웃 (사용자 정보 삭제)
     */
    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}