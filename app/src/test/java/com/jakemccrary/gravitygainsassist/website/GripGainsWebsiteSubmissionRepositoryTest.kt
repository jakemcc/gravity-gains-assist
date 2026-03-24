package com.jakemccrary.gravitygainsassist.website

import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GripGainsWebsiteSubmissionRepositoryTest {
    private val reading = WeightReading(
        kilograms = 67.314,
        recordedAt = Instant.parse("2026-03-24T04:15:00Z"),
    )

    @Test
    fun `missing session skips submission`() = runTest {
        val authRepository = FakeAuthRepository(
            initialState = GripGainsSessionState(GripGainsSessionState.Status.NO_TOKEN),
        )
        val repository = createRepository(authRepository, FakeGripGainsApi(statusCode = 200))

        val result = repository.submitWeight(reading)

        assertEquals(SubmissionResult.MissingSession, result)
    }

    @Test
    fun `invalid session returns invalid session result`() = runTest {
        val authRepository = FakeAuthRepository(
            initialState = GripGainsSessionState(GripGainsSessionState.Status.INVALID_SESSION),
            session = GripGainsSession("jwt-token"),
        )
        val repository = createRepository(authRepository, FakeGripGainsApi(statusCode = 200))

        val result = repository.submitWeight(reading)

        assertEquals(SubmissionResult.InvalidSession, result)
    }

    @Test
    fun `successful submission returns submitted weight and marks session valid`() = runTest {
        val authRepository = FakeAuthRepository(
            initialState = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT),
            session = GripGainsSession("jwt-token"),
        )
        val api = FakeGripGainsApi(statusCode = 201)
        val repository = createRepository(authRepository, api)

        val result = repository.submitWeight(reading)

        assertEquals(
            SubmissionResult.Success(
                SubmittedWeight(
                    date = LocalDate.parse("2026-03-24"),
                    weightLbs = 148.4,
                ),
            ),
            result,
        )
        assertTrue(authRepository.markedValid)
        assertEquals("Bearer jwt-token", api.lastRequest?.headers?.get("Authorization"))
    }

    @Test
    fun `401 marks session invalid and returns auth expired`() = runTest {
        val authRepository = FakeAuthRepository(
            initialState = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT),
            session = GripGainsSession("jwt-token"),
        )
        val repository = createRepository(authRepository, FakeGripGainsApi(statusCode = 401))

        val result = repository.submitWeight(reading)

        assertEquals(SubmissionResult.AuthExpired, result)
        assertTrue(authRepository.markedInvalid)
    }

    @Test
    fun `network exception becomes network failure`() = runTest {
        val authRepository = FakeAuthRepository(
            initialState = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT),
            session = GripGainsSession("jwt-token"),
        )
        val repository = createRepository(
            authRepository,
            FakeGripGainsApi(throwable = IOException("offline")),
        )

        val result = repository.submitWeight(reading)

        assertTrue(result is SubmissionResult.NetworkFailure)
    }

    @Test
    fun `unexpected status becomes server failure`() = runTest {
        val authRepository = FakeAuthRepository(
            initialState = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT),
            session = GripGainsSession("jwt-token"),
        )
        val repository = createRepository(authRepository, FakeGripGainsApi(statusCode = 500))

        val result = repository.submitWeight(reading)

        assertEquals(SubmissionResult.ServerFailure(500), result)
    }

    private fun createRepository(
        authRepository: FakeAuthRepository,
        api: FakeGripGainsApi,
    ): GripGainsWebsiteSubmissionRepository {
        return GripGainsWebsiteSubmissionRepository(
            authRepository = authRepository,
            requestFactory = GripGainsRequestFactory(
                payloadMapper = GripGainsPayloadMapper(ZoneId.of("UTC")),
            ),
            gripGainsApi = api,
        )
    }

    private class FakeAuthRepository(
        initialState: GripGainsSessionState,
        private val session: GripGainsSession? = null,
    ) : AuthRepository {
        private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(initialState)
        var markedInvalid = false
        var markedValid = false

        override val sessionState = mutableState

        override fun getSessionState(): GripGainsSessionState = mutableState.value

        override fun getActiveSession(): GripGainsSession? {
            return if (mutableState.value.status == GripGainsSessionState.Status.TOKEN_PRESENT) {
                session
            } else {
                null
            }
        }

        override suspend fun saveToken(token: String) {
            mutableState.value = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT)
        }

        override suspend fun clearSession() {
            mutableState.value = GripGainsSessionState(GripGainsSessionState.Status.NO_TOKEN)
        }

        override suspend fun markSessionInvalid() {
            markedInvalid = true
            mutableState.value = GripGainsSessionState(GripGainsSessionState.Status.INVALID_SESSION)
        }

        override suspend fun markSessionValid() {
            markedValid = true
            mutableState.value = GripGainsSessionState(GripGainsSessionState.Status.TOKEN_PRESENT)
        }
    }

    private class FakeGripGainsApi(
        private val statusCode: Int = 200,
        private val throwable: Throwable? = null,
    ) : GripGainsApi {
        var lastRequest: GripGainsRequest? = null

        override suspend fun execute(request: GripGainsRequest): GripGainsApiResponse {
            lastRequest = request
            throwable?.let { throw it }
            return GripGainsApiResponse(statusCode)
        }
    }
}
