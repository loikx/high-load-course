package ru.quipy.payments.logic

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PaymentRetryTest {
    private val scheduler = mock(ScheduledExecutorService::class.java)
    private val registry = SimpleMeterRegistry()

    private class Account(private val outcomes: List<PaymentAttempt>) : PaymentExternalSystemAdapter {
        var calls = 0
        override fun performPaymentAsync(paymentId: UUID, amount: Int, paymentStartedAt: Long, deadline: Long) =
            outcomes[minOf(calls++, outcomes.lastIndex)]
        override fun name() = "test"
        override fun price() = 1
        override fun isEnabled() = true
    }

    private fun service(account: Account) = PaymentSystemImpl(
        listOf(account), registry, retryDelayMillis = 6600, retryScheduler = scheduler
    )

    private fun executeScheduledRetry() {
        val task = ArgumentCaptor.forClass(Runnable::class.java)
        verify(scheduler).schedule(task.capture(), eq(6600L), eq(TimeUnit.MILLISECONDS))
        task.value.run()
    }

    @Test
    fun `retry is deferred without occupying caller and counted only when executed`() {
        val account = Account(listOf(PaymentAttempt(false, true), PaymentAttempt(true)))
        service(account).submitPaymentRequest(UUID.randomUUID(), 1, now(), now() + 20000)
        assertEquals(1, account.calls)
        assertEquals(null, registry.find("payment_retries").counter())
        executeScheduledRetry()
        assertEquals(2, account.calls)
        assertEquals(1.0, registry.get("payment_retries").counter().count())
        assertEquals(1.0, registry.get("http_request_success_pay").counter().count())
    }

    @Test
    fun `permanent failure is not retried`() {
        val account = Account(listOf(PaymentAttempt(false)))
        service(account).submitPaymentRequest(UUID.randomUUID(), 1, now(), now() + 20000)
        assertEquals(1, account.calls)
        verifyNoInteractions(scheduler)
    }

    @Test
    fun `expired request never calls provider`() {
        val account = Account(listOf(PaymentAttempt(true)))
        service(account).submitPaymentRequest(UUID.randomUUID(), 1, now(), now() - 1)
        assertEquals(0, account.calls)
        verifyNoInteractions(scheduler)
    }

    @Test
    fun `temporary failure stops after two attempts`() {
        val account = Account(listOf(PaymentAttempt(false, true)))
        service(account).submitPaymentRequest(UUID.randomUUID(), 1, now(), now() + 20000)
        executeScheduledRetry()
        assertEquals(2, account.calls)
        verifyNoMoreInteractions(scheduler)
    }

    @Test
    fun `retry is skipped when delay cannot fit within deadline`() {
        val account = Account(listOf(PaymentAttempt(false, true)))
        service(account).submitPaymentRequest(UUID.randomUUID(), 1, now(), now() + 1000)
        assertEquals(1, account.calls)
        verifyNoInteractions(scheduler)
    }
}
