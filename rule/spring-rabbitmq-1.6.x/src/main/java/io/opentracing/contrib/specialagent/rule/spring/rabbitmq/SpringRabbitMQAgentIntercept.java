/* Copyright 2019 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.rule.spring.rabbitmq;

import java.util.Map;

import org.springframework.amqp.core.Message;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.OpenTracingApiUtil;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringRabbitMQAgentIntercept {
  static final String COMPONENT_NAME = "spring-rabbitmq-1.6.x";

  public static void onMessageEnter(final Object msg) {
    if (LocalSpanContext.get(COMPONENT_NAME) != null) {
      LocalSpanContext.get(COMPONENT_NAME).increment();
      return;
    }

    final Message message = (Message)msg;
    message.getMessageProperties().getAppId();
    message.getMessageProperties().getClusterId();
    message.getMessageProperties().getConsumerTag();
    message.getMessageProperties().getConsumerQueue();
    message.getMessageProperties().getMessageId();
    message.getMessageProperties().getDeliveryTag();

    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder builder = tracer
      .buildSpan("onMessage")
      .withTag(Tags.COMPONENT, COMPONENT_NAME)
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    if (message.getMessageProperties() != null) {

      builder.withTag("appId", message.getMessageProperties().getAppId())
              .withTag("clusterId", message.getMessageProperties().getClusterId())
              .withTag("consumerTag", message.getMessageProperties().getConsumerTag())
              .withTag("ConsumerQueue", message.getMessageProperties().getConsumerQueue())
              .withTag("messageId", message.getMessageProperties().getMessageId());

      final Map<String,Object> headers = message.getMessageProperties().getHeaders();
      final SpanContext spanContext = tracer.extract(Builtin.TEXT_MAP, new HeadersMapExtractAdapter(headers));
      if (spanContext != null)
        builder.addReference(References.FOLLOWS_FROM, spanContext);
    }

    final Span span = builder.start();
    LocalSpanContext.set(COMPONENT_NAME, span, tracer.activateSpan(span));
  }

  public static void onMessageExit(final Throwable thrown) {
    final LocalSpanContext context = LocalSpanContext.get(COMPONENT_NAME);
    if (context == null || context.decrementAndGet() != 0)
      return;

    if (thrown != null)
      OpenTracingApiUtil.setErrorTag(context.getSpan(), thrown);

    context.closeAndFinish();
  }

}