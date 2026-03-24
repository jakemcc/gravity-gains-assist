package com.jakemccrary.gravitygainsassist.website

import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import java.util.Locale

data class GripGainsRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class PreparedGripGainsSubmission(
    val submittedWeight: SubmittedWeight,
    val request: GripGainsRequest,
)

class GripGainsRequestFactory(
    private val payloadMapper: GripGainsPayloadMapper,
    private val authHeadersFormatter: GripGainsAuthHeadersFormatter = GripGainsAuthHeadersFormatter(),
) {
    fun create(reading: WeightReading, session: GripGainsSession): PreparedGripGainsSubmission {
        val submittedWeight = payloadMapper.map(reading)
        return PreparedGripGainsSubmission(
            submittedWeight = submittedWeight,
            request = GripGainsRequest(
                method = "POST",
                url = BODYWEIGHT_URL,
                headers = authHeadersFormatter.format(session),
                body = buildJsonBody(submittedWeight),
            ),
        )
    }

    private fun buildJsonBody(submittedWeight: SubmittedWeight): String {
        return String.format(
            Locale.US,
            """{"date":"%s","weight_lbs":%.1f}""",
            submittedWeight.date,
            submittedWeight.weightLbs,
        )
    }

    private companion object {
        const val BODYWEIGHT_URL = "https://gripgains.ca/api/bodyweight/"
    }
}

class GripGainsAuthHeadersFormatter {
    fun format(session: GripGainsSession): Map<String, String> {
        return linkedMapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Referer" to "https://gripgains.ca/gravity-gains",
            "Origin" to "https://gripgains.ca",
            "Authorization" to "Bearer ${session.token}",
            "Cookie" to session.cookieHeader,
        )
    }
}
