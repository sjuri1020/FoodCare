// UserApiService.kt
import com.AzaAza.foodcare.models.AcceptInviteRequest
import com.AzaAza.foodcare.models.InviteRequest
import com.AzaAza.foodcare.models.InviteResponse
import com.AzaAza.foodcare.models.SignUpRequest
import com.AzaAza.foodcare.models.LoginRequest
import com.AzaAza.foodcare.models.LoginResponse
import com.AzaAza.foodcare.models.MemberResponse
import com.AzaAza.foodcare.models.MyGroupsResponse
import com.AzaAza.foodcare.models.SignupResponse
import com.AzaAza.foodcare.models.UserResponse
import com.AzaAza.foodcare.models.VerificationRequestDto
import com.AzaAza.foodcare.models.VerificationConfirmDto
import com.AzaAza.foodcare.models.VerificationResponseDto
import com.AzaAza.foodcare.models.PasswordChangeRequestDto
import com.AzaAza.foodcare.models.PasswordChangeResponseDto
import com.AzaAza.foodcare.models.ProfileImageResponse
import com.AzaAza.foodcare.models.UpdateFcmTokenRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface UserApiService {
    // 회원가입
    @POST("/user")
    fun signUp(@Body request: SignUpRequest): Call<SignupResponse>

    // 로그인 (login_id + password)
    @POST("/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    // 이메일 인증 코드 요청
    @POST("/user/verify/request")
    suspend fun requestVerificationCode(@Body request: VerificationRequestDto): VerificationResponseDto

    // 이메일 인증 코드 확인
    @POST("/user/verify/confirm")
    suspend fun confirmVerificationCode(@Body request: VerificationConfirmDto): VerificationResponseDto

    // (Optional) 전체 사용자 조회: GET /user
    @GET("/user")
    fun getUsers(): Call<List<UserResponse>>

    @GET("/user")
    fun getUserListAsSignUpRequest(): Call<List<SignUpRequest>>

    @DELETE("/user/{login_id}")
    fun deleteUser(@Path("login_id") loginId: String): Call<Void>

    // 비밀번호 변경
    @POST("user/password/change")
    suspend fun changePassword(@Body request: PasswordChangeRequestDto): PasswordChangeResponseDto

    @POST("/members/invite")
    fun inviteMember(@Body req: InviteRequest): Call<InviteResponse>

    @GET("/members/{owner_id}")
    fun getMembers(@Path("owner_id") ownerId: Int): Call<List<MemberResponse>>

    @POST("/members/accept")
    fun acceptInvite(@Body req: AcceptInviteRequest): Call<InviteResponse>

    @DELETE("/members/{owner_id}/{member_id}")
    fun deleteMember(
        @Path("owner_id") ownerId: Int,
        @Path("member_id") memberId: Int
    ): Call<InviteResponse>

    @POST("/user/update_token")
    fun updateFcmToken(@Body request: UpdateFcmTokenRequest): Call<Void>

    @GET("/members/pending/{member_id}")
    fun getPendingInvites(@Path("member_id") memberId: Int): Call<List<InviteResponse>>

    @POST("/members/create_my_group")
    fun createMyGroup(@Body userId: Int): Call<InviteResponse>

    @Multipart
    @POST("/user/profile_image")
    fun uploadProfileImage(
        @Part("login_id") loginId: RequestBody,
        @Part image: MultipartBody.Part
    ): Call<ProfileImageResponse>

    @GET("/members/my_groups/{user_id}")
    fun getMyGroups(@Path("user_id") userId: Int): Call<MyGroupsResponse>

}
