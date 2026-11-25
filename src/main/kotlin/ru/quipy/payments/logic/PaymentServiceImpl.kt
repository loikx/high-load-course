package ru.quipy.payments.logic

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Service
class PaymentSystemImpl(
    private val paymentAccounts: List<PaymentExternalSystemAdapter>,
    private val meterRegistry: MeterRegistry,
    @Value("\${payment.max-attempts:2}") private val maxAttempts: Int = 2,
    @Value("\${payment.retry-delay-ms:6600}") private val retryDelayMillis: Long = 6600,
    private val retryScheduler: ScheduledExecutorService,
) : PaymentService {
    companion object {
        val logger = LoggerFactory.getLogger(PaymentSystemImpl::class.java)
    }

    private val successCounter = Counter.builder("http_request_success_pay")
        .description("Counts the number of success pays").register(meterRegistry)
    private val failCounter = Counter.builder("http_request_fail_pay")
        .description("Counts the number of fail pay").register(meterRegistry)

    override fun submitPaymentRequest(paymentId: UUID, amount: Int, paymentStartedAt: Long, deadline: Long) {
        for (account in paymentAccounts.filter { it.isEnabled() }) {
            attempt(account, paymentId, amount, paymentStartedAt, deadline, 1)
        }
    }

    private fun attempt(
        account: PaymentExternalSystemAdapter, paymentId: UUID, amount: Int,
        paymentStartedAt: Long, deadline: Long, attemptNumber: Int,
    ) {
        if (now() >= deadline) return
        if (attemptNumber > 1) meterRegistry.counter("payment_retries", "account", account.name()).increment()
        val result = account.performPaymentAsync(paymentId, amount, paymentStartedAt, deadline)
        if (result.success) {
            successCounter.increment()
            return
        }
        failCounter.increment()
        if (!result.retryable || attemptNumber >= maxAttempts) return

        val delay = retryDelayMillis * attemptNumber
        if (now() + delay >= deadline) return
        // Waiting for a retry must not occupy a payment-submission worker.
        retryScheduler.schedule(
            { attempt(account, paymentId, amount, paymentStartedAt, deadline, attemptNumber + 1) },
            delay, TimeUnit.MILLISECONDS,
        )
    }
}
