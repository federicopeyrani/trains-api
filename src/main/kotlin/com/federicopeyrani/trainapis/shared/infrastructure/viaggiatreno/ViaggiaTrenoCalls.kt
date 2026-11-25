package com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno

import com.federicopeyrani.trainapis.shared.domain.DomainException
import com.federicopeyrani.trainapis.shared.domain.ProviderUnavailableException
import com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client.infrastructure.HttpResponse
import kotlinx.coroutines.CancellationException

/**
 * Runs a ViaggiaTreno call, translating transport and decoding failures into [ProviderUnavailableException].
 */
internal suspend fun <T> viaggiaTrenoCall(operation: String, block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DomainException) {
        throw e
    } catch (e: Exception) {
        throw ProviderUnavailableException("ViaggiaTreno $operation failed: ${e.message}", e)
    }

/**
 * Body of a successful response. The generated client does not throw on non-2xx statuses (e.g. the 403 returned
 * when rate limited), so they are checked here before decoding.
 */
internal suspend fun <T> HttpResponse<T>.bodyOrThrow(operation: String): T {
    if (!success) throw ProviderUnavailableException("ViaggiaTreno $operation returned HTTP $status")
    return body()
}
