package com.jetbrains.performancePlugin.commands;

import com.google.gson.Gson;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.SpanBuilderWithSystemInfoAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;

public abstract class PerformanceCommand extends AbstractCommand {

  private static final String PREFIX = "%";
  private static final Gson gson = new Gson();
  private final Tracer tracer = isWarmupMode() ? PerformanceTestSpan.WARMUP_TRACER : PerformanceTestSpan.TRACER;

  public PerformanceCommand(@NotNull String text, int line) {
    super(text, line);
  }

  public PerformanceCommand(@NotNull String text, int line, boolean executeInAwt) {
    super(text, line, executeInAwt);
  }

  protected abstract String getName();

  protected String getPrefix() {
    return PREFIX + getName();
  }

  protected Boolean isWarmupMode() {
    return extractCommandArgument(getPrefix()).contains("WARMUP");
  }

  protected Boolean systemMetricsEnabled() {
    return extractCommandArgument(getPrefix()).contains("ENABLE_SYSTEM_METRICS");
  }

  private SpanBuilder wrapIfNeed(SpanBuilder spanBuilder) {
    if (systemMetricsEnabled()) {
      return new SpanBuilderWithSystemInfoAttributes(spanBuilder);
    }
    return spanBuilder;
  }

  protected Span startSpan(String name) {
    SpanBuilder spanBuilder = wrapIfNeed(tracer.spanBuilder(name));
    return spanBuilder.startSpan();
  }

  protected <T> T deserializeOptionsFromJson(String json, Class<T> clazz) {
    return gson.fromJson(json, clazz);
  }
}
