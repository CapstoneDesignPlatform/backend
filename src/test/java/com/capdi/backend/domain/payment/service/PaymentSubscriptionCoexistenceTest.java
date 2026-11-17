package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.config.PaymentProcessingConfig;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.subscription.config.SubscriptionConfig;
import com.capdi.backend.domain.subscription.gateway.SubscriptionPgClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.*;
class PaymentSubscriptionCoexistenceTest {
 @ParameterizedTest @ValueSource(booleans={true,false})
 void independentClocksAndDisabledProvidersCoexistInEitherConfigurationOrder(boolean paymentFirst){
  try(var context=new AnnotationConfigApplicationContext()){
   context.registerBean(ObjectMapper.class,()->new ObjectMapper());
   if(paymentFirst)context.register(PaymentProcessingConfig.class,SubscriptionConfig.class);
   else context.register(SubscriptionConfig.class,PaymentProcessingConfig.class);
   context.refresh();
   Clock financial=context.getBean("paymentClock",Clock.class),subscription=context.getBean("subscriptionClock",Clock.class);
   assertThat(context.getBeansOfType(Clock.class)).containsOnlyKeys("paymentClock","subscriptionClock");
   assertThat(context.getBeanFactory().getBeanDefinition("paymentClock").isPrimary()).isTrue();
   assertThat(context.getBeanFactory().getBeanDefinition("subscriptionClock").isPrimary()).isFalse();
   assertThat(context.getBean(Clock.class)).isSameAs(financial);assertThat(subscription.getZone()).isEqualTo(java.time.ZoneOffset.UTC);
   assertThat(context.getBean(PaymentGateway.class).identity().code()).isEqualTo("DISABLED");
   assertThat(context.getBean(SubscriptionPgClient.class).available()).isFalse();
  }
 }
}
