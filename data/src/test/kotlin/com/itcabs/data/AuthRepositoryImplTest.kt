package com.itcabs.data

import com.itcabs.core.database.UserDao
import com.itcabs.core.database.UserEntity
import com.itcabs.core.network.AuthApi
import com.itcabs.core.network.dto.OtpRequestDto
import com.itcabs.core.network.dto.OtpVerifyDto
import com.itcabs.core.network.dto.RefreshDto
import com.itcabs.core.network.dto.SentDto
import com.itcabs.core.network.dto.TokensDto
import com.itcabs.core.network.dto.UserDto
import com.itcabs.domain.AppResult
import com.itcabs.domain.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Fake AuthApi driven by canned responses — no HTTP, so these run on plain JVM. */
private class FakeAuthApi(private val verify: Response<TokensDto>) : AuthApi {
    override suspend fun requestOtp(body: OtpRequestDto) = Response.success(SentDto(true))
    override suspend fun verifyOtp(body: OtpVerifyDto) = verify
    override suspend fun refresh(body: RefreshDto) = verify
    override suspend fun me() = Response.success(UserDto(1, "+91", "DRIVER", "T", "ACTIVE"))
}

/** No-op UserDao — the auth tests exercise API mapping + token storage, not the cache. */
private class FakeUserDao : UserDao {
    override fun getUserFlow(): Flow<UserEntity?> = flowOf(null)
    override suspend fun insertUser(user: UserEntity) = Unit
    override suspend fun clear() = Unit
}

class AuthRepositoryImplTest {

    @Test
    fun verify_success_stores_tokens_and_maps_session() = runTest {
        val store = InMemoryTokenStore()
        val repo = AuthRepositoryImpl(
            FakeAuthApi(Response.success(TokensDto("acc", "ref", 7, "DRIVER"))),
            store,
            FakeUserDao(),
        )

        val ok = assertIs<AppResult.Ok<*>>(repo.verifyOtp("+91", "123456", UserRole.DRIVER, "T"))
        val session = ok.value as com.itcabs.domain.model.Session
        assertEquals(7, session.userId)
        assertEquals(UserRole.DRIVER, session.role)
        // tokens were persisted for the interceptor to use
        assertEquals("acc", store.accessToken())
        assertEquals("ref", store.refreshToken())
    }

    @Test
    fun verify_failure_propagates_code_and_leaves_store_empty() = runTest {
        val store = InMemoryTokenStore()
        val err: Response<TokensDto> = Response.error(
            401,
            """{"error":"wrong code"}""".toResponseBody("application/json".toMediaType()),
        )
        val repo = AuthRepositoryImpl(FakeAuthApi(err), store, FakeUserDao())

        val failure = assertIs<AppResult.Err>(repo.verifyOtp("+91", "000000", UserRole.DRIVER, "T"))
        assertEquals(401, failure.code)
        assertNull(store.accessToken())
    }

    @Test
    fun refresh_without_a_stored_token_is_401() = runTest {
        val repo = AuthRepositoryImpl(
            FakeAuthApi(Response.success(TokensDto("a", "b", 1, "DRIVER"))),
            InMemoryTokenStore(),
            FakeUserDao(),
        )

        val failure = assertIs<AppResult.Err>(repo.refresh())
        assertEquals(401, failure.code)
    }
}
