package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.config.FinancialLookupSchedulerConfig;
import com.capdi.backend.domain.payment.config.InternalFinancialRecoveryConfig;
import com.capdi.backend.domain.payment.config.PaymentWebhookConsumerConfig;
import com.capdi.backend.domain.payment.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentWebhookSchedulerContextTest {
    private static final String CONSUMER = "payment.webhook.consumer.enabled";
    private static final String DELAY = "payment.webhook.consumer.delay-ms";

    static final class Probe {
        final AtomicInteger polls = new AtomicInteger();
        final AtomicBoolean firstPollOnly = new AtomicBoolean();
        final List<Long> nextIds = new CopyOnWriteArrayList<>();
        final List<Long> processed = new CopyOnWriteArrayList<>();
        final Set<Long> failures = ConcurrentHashMap.newKeySet();
        final CountDownLatch pollTwice = new CountDownLatch(2);
        final CountDownLatch processedOnce = new CountDownLatch(1);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({PaymentWebhookConsumerConfig.class, PaymentWebhookConsumer.class,
            FinancialLookupSchedulerConfig.class, InternalFinancialRecoveryConfig.class})
    static class SchedulerBeans {
        @Bean Clock paymentClock() { return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC); }
        @Bean PaymentWebhookEventRepository events(Probe probe) {
            var repository = mock(PaymentWebhookEventRepository.class);
            when(repository.findDueIds(any(Instant.class), anyInt())).thenAnswer(invocation -> {
                int poll = probe.polls.incrementAndGet();
                probe.pollTwice.countDown();
                if (probe.firstPollOnly.get() && poll > 1) return List.of();
                return List.copyOf(probe.nextIds);
            });
            return repository;
        }
        @Bean PaymentWebhookService service(Probe probe) {
            var service = mock(PaymentWebhookService.class);
            doAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                probe.processed.add(id);
                probe.processedOnce.countDown();
                if (probe.failures.contains(id)) throw new IllegalStateException("synthetic isolated failure");
                return null;
            }).when(service).process(anyLong());
            return service;
        }
    }

    private ApplicationContextRunner runner(Probe probe) {
        return new ApplicationContextRunner().withBean(Probe.class, () -> probe).withUserConfiguration(SchedulerBeans.class);
    }

    @Test void missingAndFalsePropertiesRegisterNoConsumerOrScheduler() {
        for (String value : new String[]{null, "false"}) {
            var probe = new Probe();
            var configured = runner(probe);
            if (value != null) configured = configured.withPropertyValues(CONSUMER + "=" + value);
            configured.run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(PaymentWebhookConsumer.class);
                assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
                assertThat(context.getBeansOfType(FinancialLookupSchedulerConfig.LookupOnlyScheduler.class)).isEmpty();
                assertThat(context.getBeansOfType(InternalFinancialRecoveryConfig.LookupWorker.class)).isEmpty();
                assertThat(probe.polls).hasValue(0);
                assertThat(probe.processed).isEmpty();
            });
        }
    }

    @Test void enabledPropertyRegistersConsumerAndSpringActuallySchedulesPolling() throws Exception {
        var probe = new Probe();
        probe.nextIds.add(71L);
        probe.firstPollOnly.set(true);
        runner(probe).withPropertyValues(CONSUMER + "=true", DELAY + "=35")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PaymentWebhookConsumer.class);
                    assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty();
                    assertThat(context.getBeansOfType(FinancialLookupSchedulerConfig.LookupOnlyScheduler.class)).isEmpty();
                    assertThat(context.getBeansOfType(InternalFinancialRecoveryConfig.LookupWorker.class)).isEmpty();
                    try {
                        assertThat(probe.processedOnce.await(2, TimeUnit.SECONDS)).isTrue();
                        assertThat(probe.pollTwice.await(2, TimeUnit.SECONDS)).isTrue();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    assertThat(probe.processed).containsExactly(71L);
                    assertThat(probe.polls.get()).isGreaterThanOrEqualTo(2);
                });
    }

    @Test void oneBatchFailureDoesNotPreventLaterEventsFromBeingAttempted() {
        var probe = new Probe();
        probe.nextIds.addAll(List.of(1L, 2L, 3L, 4L));
        probe.failures.addAll(List.of(2L, 4L));
        runner(probe).withPropertyValues(CONSUMER + "=true", DELAY + "=60000")
                .run(context -> {
                    context.getBean(PaymentWebhookConsumer.class).poll();
                    assertThat(probe.processed).containsExactly(1L, 2L, 3L, 4L);
                });
    }
}
