package ru.quipy.payments.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.quipy.common.utils.NamedThreadFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration
class PaymentRetryConfiguration {
    @Bean(destroyMethod = "shutdown")
    fun paymentRetryScheduler(): ScheduledExecutorService = Executors.newScheduledThreadPool(
        8, NamedThreadFactory("payment-retry")
    )
}
