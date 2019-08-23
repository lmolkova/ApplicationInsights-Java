package com.microsoft.applicationinsights.opencensus;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import com.microsoft.localforwarder.library.inputs.contracts.Request;
import com.sun.deploy.trace.Trace;
import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.samplers.Samplers;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Mockito.mock;

public class ApplicationInsightsTraceExporterTests {

  private TelemetryConfiguration configuration;
  private List<Telemetry> eventsSent;
  private TelemetryChannel channel;

  @Before
  public void setUp() {
    configuration = new TelemetryConfiguration();
    configuration.setInstrumentationKey("00000000-0000-0000-0000-000000000000");
    channel = mock(TelemetryChannel.class);
    configuration.setChannel(channel);

    eventsSent = new LinkedList<Telemetry>();
    // Setting the channel to add the sent telemetries to a collection, so they could be verified in tests.
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Telemetry telemetry = ((Telemetry) invocation.getArguments()[0]);
        eventsSent.add(telemetry);

        return null;
      }
    }).when(channel).send(Matchers.any(Telemetry.class));

    MockitoAnnotations.initMocks(this);
  }

  @After
  public void tearDown() {
    ApplicationInsightsTraceExporter.unregister();
  }

  @Test
  public void RegisterOnce() throws InterruptedException {
    ApplicationInsightsTraceExporter.createAndRegister(configuration);

    for (int i = 0; i < 33; i++) { // send batch to force export
      Span span = Tracing.getTracer()
          .spanBuilder("span")
          .setSampler(Samplers.alwaysSample())
          .startSpan();
      span.end();
    }

    for (int delay = 0; delay <= 5100; delay += 100) { // 5000 - default export interval
      if (eventsSent.size() == 0) {
        Thread.sleep(100);
      }
   }

    Assert.assertEquals(33, eventsSent.size());
    Assert.assertEquals("java-ot:0.22.0-SNAPSHOT", eventsSent.get(0).getContext().getInternal().getSdkVersion());
  }

  @Test
  public void RegisterWithSdkVersion() throws InterruptedException {
    ApplicationInsightsTraceExporter.setSdkVersionPrefix("foo");
    ApplicationInsightsTraceExporter.createAndRegister(configuration);

    for (int i = 0; i < 33; i++) { // send batch to force export
      Span span = Tracing.getTracer()
          .spanBuilder("span")
          .setSampler(Samplers.alwaysSample())
          .startSpan();
      span.end();
    }

    for (int delay = 0; delay <= 5100; delay += 100) { // 5000 - default export interval
      if (eventsSent.size() == 0) {
        Thread.sleep(100);
      }
    }

    Assert.assertEquals(33, eventsSent.size());
    Assert.assertEquals("foo:0.22.0-SNAPSHOT", eventsSent.get(0).getContext().getInternal().getSdkVersion());
  }

  @Test(expected = IllegalStateException.class)
  public void RegisterTwice(){
    ApplicationInsightsTraceExporter.createAndRegister();
    ApplicationInsightsTraceExporter.createAndRegister();
  }

  @Test
  public void RegisterActiveConfig() throws InterruptedException {

    final String guid = UUID.randomUUID().toString();
    TelemetryConfiguration.getActive().getTelemetryInitializers().add(new TelemetryInitializer() {
      @Override
      public void initialize(Telemetry telemetry) {
        telemetry.getProperties().put("some id", guid);
      }
    });

    TelemetryConfiguration.getActive().setInstrumentationKey("00000000-0000-0000-0000-000000000000");
    TelemetryConfiguration.getActive().setChannel(channel);
    ApplicationInsightsTraceExporter.createAndRegister();

    for (int i = 0; i < 33; i++) { // send batch to force export
      Span span = Tracing.getTracer()
          .spanBuilder("span")
          .setSampler(Samplers.alwaysSample())
          .startSpan();
      span.end();
    }

    for (int delay = 0; delay <= 5100; delay += 100) { // 5000 - default export interval
      if (eventsSent.size() == 0) {
        Thread.sleep(100);
      }
    }

    Assert.assertEquals(33, eventsSent.size());

    Telemetry request = eventsSent.get(0);

    Assert.assertTrue(request.getProperties().containsKey("some id"));
    Assert.assertEquals(guid, request.getProperties().get("some id"));
  }

  @Test
  public void CustomAppInsightsTelemetryIsTrackedInScopeOfRequest() throws InterruptedException {
    ApplicationInsightsTraceExporter.createAndRegister(configuration);

    Tracer tracer = Tracing.getTracer();
    configuration.getTelemetryInitializers().add(new SpanContextTelemetryInitializer());

    TelemetryClient aiClient = new TelemetryClient(configuration);

    Span span = tracer
        .spanBuilder("span")
        .setSampler(Samplers.alwaysSample())
        .startSpan();

    try (Scope s = tracer.withSpan(span)) {
      aiClient.trackTrace("hello world");
    }

    span.end();

    for (int delay = 0; delay <= 5100; delay += 100) { // 5000 - default export interval
      if (eventsSent.size() < 2 ) {
        Thread.sleep(100);
      }
    }

    Assert.assertEquals(2, eventsSent.size());
    Assert.assertTrue(eventsSent.get(0) instanceof TraceTelemetry);
    Assert.assertTrue(eventsSent.get(1) instanceof RequestTelemetry);

    TraceTelemetry trace = (TraceTelemetry)eventsSent.get(0);
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(1);

    Assert.assertEquals(trace.getContext().getOperation().getId(), request.getContext().getOperation().getId());
    Assert.assertEquals(trace.getContext().getOperation().getId(), request.getContext().getOperation().getId());
    Assert.assertEquals(trace.getContext().getOperation().getParentId(), request.getId());
  }
}
