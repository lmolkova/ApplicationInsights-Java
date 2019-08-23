package com.microsoft.applicationinsights.opencensus;

import static org.mockito.Mockito.mock;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.applicationinsights.telemetry.RemoteDependencyTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import io.opencensus.common.Duration;
import io.opencensus.common.Timestamp;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Link;
import io.opencensus.trace.Link.Type;
import io.opencensus.trace.MessageEvent;
import io.opencensus.trace.Span.Kind;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.SpanId;
import io.opencensus.trace.Status;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.TraceOptions;
import io.opencensus.trace.Tracestate;
import io.opencensus.trace.export.SpanData;
import io.opencensus.trace.export.SpanData.Attributes;
import io.opencensus.trace.export.SpanData.Links;
import io.opencensus.trace.export.SpanData.TimedEvent;
import io.opencensus.trace.export.SpanData.TimedEvents;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ApplicationInsightsExporterHandlerTests {

  private static final String TRACE_ID = "d239036e7d5cec116b562147388b35bf";
  private static final String SPAN_ID = "9cc1e3049173be09";
  private static final String PARENT_SPAN_ID = "8b03ab423da481c5";
  private static final String PARENT_TELEMETRY_ID = "|d239036e7d5cec116b562147388b35bf.8b03ab423da481c5.";
  private static final String TELEMETRY_ID = "|d239036e7d5cec116b562147388b35bf.9cc1e3049173be09.";

  private static final String LINK0_TRACE_ID = "11111111111111111111111111111111";
  private static final String LINK0_SPAN_ID = "1111111111111111";

  private static final String LINK1_TRACE_ID = "22222222222222222222222222222222";
  private static final String LINK1_SPAN_ID = "2222222222222222";

  private static final Tracestate TRACESTATE = Tracestate.builder().build();
  private Map<String, AttributeValue> attributes = new HashMap<>();
  private List<TimedEvent<Annotation>> annotations = new ArrayList<>();
  private List<TimedEvent<MessageEvent>> messageEvents = new ArrayList<>();
  private List<Link> links = new ArrayList<>();

  private ApplicationInsightsExporterHandler exporter;
  private TelemetryConfiguration configuration;
  private List<Telemetry> eventsSent;
  private TelemetryClient telemetryClient;
  private TelemetryChannel channel;

  @Before
  public void setUp() {
    attributes = new HashMap<>();
    annotations = new ArrayList<>();
    messageEvents = new ArrayList<>();
    links = new ArrayList<>();

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

    telemetryClient = new TelemetryClient(configuration);
    exporter = new ApplicationInsightsExporterHandler(telemetryClient);
  }

  @Test
  public void ExportRequest()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("name", request.getName());
    Assert.assertEquals(TRACE_ID, request.getContext().getOperation().getId());
    Assert.assertEquals(PARENT_TELEMETRY_ID, request.getContext().getOperation().getParentId());
    Assert.assertEquals(TELEMETRY_ID, request.getId());
    Assert.assertTrue(request.isSuccess());
    Assert.assertEquals(startTimestamp, request.getTimestamp().getTime());
    Assert.assertEquals(1000, request.getDuration().getTotalMilliseconds());
    Assert.assertEquals(0, request.getProperties().size());
  }

  @Test
  public void ExportRequestCustomAttributes()
  {
    attributes = createAttributes("string", 123, true, 1.23d);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        Timestamp.fromMillis(System.currentTimeMillis()),
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals(4, request.getProperties().size());
    Assert.assertTrue(request.getProperties().containsKey("string"));
    Assert.assertTrue(request.getProperties().containsKey("bool"));
    Assert.assertTrue(request.getProperties().containsKey("double"));
    Assert.assertTrue(request.getProperties().containsKey("long"));
    Assert.assertEquals("string", request.getProperties().get("string"));
    Assert.assertEquals("true", request.getProperties().get("bool"));
    Assert.assertEquals("1.23", request.getProperties().get("double"));
    Assert.assertEquals("123", request.getProperties().get("long"));
  }

  @Test
  public void ExportRequestWithoutParent()
  {
    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        null,
        null,
        "name",
        Kind.SERVER,
        Timestamp.fromMillis(System.currentTimeMillis()),
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals(null, request.getContext().getOperation().getParentId());
    Assert.assertEquals(0, request.getProperties().size());
  }

  @Test
  public void ExportBasicHttpRequest()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", 201, "GET", "/route", "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("GET /route", request.getName());
    Assert.assertTrue(request.isSuccess());
    Assert.assertEquals("https://host/path", request.getUrlString());
    Assert.assertEquals("201", request.getResponseCode());
    Assert.assertEquals("userAgent", request.getContext().getUser().getUserAgent());
    Assert.assertEquals(0, request.getProperties().size());
  }

  @Test
  public void ExportHttpRequestNoRoute()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", 201, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("GET /path", request.getName());
    Assert.assertEquals("https://host/path", request.getUrlString());
  }

  @Test
  public void ExportHttpRequestNoUserAgent()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", 201, "GET", null, null),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertNull(request.getContext().getUser().getUserAgent());
  }

  @Test
  public void ExportHttpRequestNoStatusCode()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", null, "GET", "/route", "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.ABORTED,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertFalse(request.isSuccess());
    Assert.assertEquals("ABORTED", request.getResponseCode());
    Assert.assertEquals(0, request.getProperties().size());
  }

  @Test
  public void ExportHttpRequestNoStatusCodeErrorDescription() {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", null, "GET", "/route", "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.UNKNOWN.withDescription("error description"),
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertFalse(request.isSuccess());
    Assert.assertEquals("UNKNOWN", request.getResponseCode());
    Assert.assertEquals(1, request.getProperties().size());
    Assert.assertTrue(request.getProperties().containsKey("statusDescription"));
    Assert.assertEquals("error description", request.getProperties().get("statusDescription"));
  }

  @Test
  public void ExportHttpRequestNoPathHostRoute()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        createHttpIncomingRequestAttributes(null, "https://host/path", null, 404, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("GET /path", request.getName());
    Assert.assertTrue(request.isSuccess()); // Status is OK
    Assert.assertEquals("https://host/path", request.getUrlString());
    Assert.assertEquals("404", request.getResponseCode());
    Assert.assertEquals("userAgent", request.getContext().getUser().getUserAgent());
    Assert.assertEquals(0, request.getProperties().size());
  }

  @Test
  public void ExportHttpRequestInvalidUrl() throws MalformedURLException {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        start,
        createHttpIncomingRequestAttributes("host", "host/path", "/path", 201, "GET", "/route", "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("GET /route", request.getName());
    Assert.assertTrue(request.isSuccess());
    Assert.assertNull(null, request.getUrl());
    Assert.assertEquals("201", request.getResponseCode());
    Assert.assertEquals("userAgent", request.getContext().getUser().getUserAgent());
    Assert.assertEquals(0, request.getProperties().size());
  }

  @Test
  public void ExportRequestAnnotations()
  {
    Map<String, AttributeValue> annotationAttributes = createAttributes("string", 123, true, 1.23d);

    long annotationTime = System.currentTimeMillis() - 100;
    annotations.add(TimedEvent.create(
        Timestamp.fromMillis(annotationTime), Annotation.fromDescriptionAndAttributes("message", annotationAttributes)));

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        Timestamp.fromMillis(System.currentTimeMillis()),
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(2, eventsSent.size());
    TraceTelemetry log = (TraceTelemetry) eventsSent.get(0);
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(1);

    Assert.assertNotNull(log);
    Assert.assertNotNull(request);
    Assert.assertEquals("message", log.getMessage());
    Assert.assertEquals(0, request.getProperties().size());
    Assert.assertEquals(4, log.getProperties().size());
    Assert.assertTrue(log.getProperties().containsKey("string"));
    Assert.assertTrue(log.getProperties().containsKey("bool"));
    Assert.assertTrue(log.getProperties().containsKey("double"));
    Assert.assertTrue(log.getProperties().containsKey("long"));
    Assert.assertEquals("string", log.getProperties().get("string"));
    Assert.assertEquals("true", log.getProperties().get("bool"));
    Assert.assertEquals("1.23", log.getProperties().get("double"));
    Assert.assertEquals("123", log.getProperties().get("long"));

    Assert.assertEquals(log.getContext().getOperation().getId(), request.getContext().getOperation().getId());
    Assert.assertEquals(log.getContext().getOperation().getParentId(), request.getId());
  }

  @Test
  public void ExportRequestLinks()
  {
    Map<String, AttributeValue> link0Attributes = createAttributes("string", 123, true, 1.23d);

    SpanContext link0Context = SpanContext.create(
        TraceId.fromLowerBase16(LINK0_TRACE_ID),
        SpanId.fromLowerBase16(LINK0_SPAN_ID),
        TraceOptions.DEFAULT,
        TRACESTATE);

    SpanContext link1Context = SpanContext.create(
        TraceId.fromLowerBase16(LINK1_TRACE_ID),
        SpanId.fromLowerBase16(LINK1_SPAN_ID),
        TraceOptions.DEFAULT,
        Tracestate.builder().set("ts", "v").build());

    links.add(Link.fromSpanContext(link0Context, Type.PARENT_LINKED_SPAN, link0Attributes));
    links.add(Link.fromSpanContext(link1Context, Type.PARENT_LINKED_SPAN));

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        Timestamp.fromMillis(System.currentTimeMillis()),
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals(8, request.getProperties().size());

    Assert.assertTrue(request.getProperties().containsKey("link0_spanId"));
    Assert.assertTrue(request.getProperties().containsKey("link1_spanId"));
    Assert.assertTrue(request.getProperties().containsKey("link0_traceId"));
    Assert.assertTrue(request.getProperties().containsKey("link1_traceId"));

    Assert.assertTrue(request.getProperties().containsKey("link0_string"));
    Assert.assertTrue(request.getProperties().containsKey("link0_bool"));
    Assert.assertTrue(request.getProperties().containsKey("link0_long"));
    Assert.assertTrue(request.getProperties().containsKey("link0_double"));

    Assert.assertEquals(LINK0_SPAN_ID, request.getProperties().get("link0_spanId"));
    Assert.assertEquals(LINK1_SPAN_ID, request.getProperties().get("link1_spanId"));
    Assert.assertEquals(LINK0_TRACE_ID, request.getProperties().get("link0_traceId"));
    Assert.assertEquals(LINK1_TRACE_ID, request.getProperties().get("link1_traceId"));

    Assert.assertEquals("string", request.getProperties().get("link0_string"));
    Assert.assertEquals("123", request.getProperties().get("link0_long"));
    Assert.assertEquals("true", request.getProperties().get("link0_bool"));
    Assert.assertEquals("1.23", request.getProperties().get("link0_double"));
  }

  @Test
  public void ExportRequestTracestate()
  {
    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            Tracestate.builder().set("foo1", "bar1").set("foo2", "bar2").set("foo1", "bar3").build()),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        Timestamp.fromMillis(System.currentTimeMillis()),
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("name", request.getName());
    Assert.assertEquals(TRACE_ID, request.getContext().getOperation().getId());
    Assert.assertEquals(PARENT_TELEMETRY_ID, request.getContext().getOperation().getParentId());
    Assert.assertEquals(TELEMETRY_ID, request.getId());
    Assert.assertEquals(2, request.getProperties().size());

    Assert.assertTrue(request.getProperties().containsKey("foo1"));
    Assert.assertTrue(request.getProperties().containsKey("foo2"));

    Assert.assertEquals("bar3", request.getProperties().get("foo1"));
    Assert.assertEquals("bar2", request.getProperties().get("foo2"));
  }

  @Test
  public void ExportRequestTracestateAndAttributesDuplication()
  {
    attributes.put("foo", AttributeValue.stringAttributeValue("attributeBar"));
    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            Tracestate.builder().set("foo", "tracestateBar").build()),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.SERVER,
        Timestamp.fromMillis(System.currentTimeMillis()),
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("name", request.getName());
    Assert.assertEquals(TRACE_ID, request.getContext().getOperation().getId());
    Assert.assertEquals(PARENT_TELEMETRY_ID, request.getContext().getOperation().getParentId());
    Assert.assertEquals(TELEMETRY_ID, request.getId());
    Assert.assertEquals(1, request.getProperties().size());

    Assert.assertTrue(request.getProperties().containsKey("foo"));
    Assert.assertTrue(request.getProperties().get("foo").equals("tracestateBar") ||
        request.getProperties().get("foo").equals("attributeBar"));
  }

  @Test
  public void ExportDependency()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData dependencySpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        true,
        "name",
        Kind.CLIENT,
        start,
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(dependencySpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("name", dependency.getName());
    Assert.assertEquals(TRACE_ID, dependency.getContext().getOperation().getId());
    Assert.assertEquals(PARENT_TELEMETRY_ID, dependency.getContext().getOperation().getParentId());
    Assert.assertEquals(TELEMETRY_ID, dependency.getId());
    Assert.assertTrue(dependency.getSuccess());
    Assert.assertEquals(startTimestamp, dependency.getTimestamp().getTime());
    Assert.assertEquals(1000, dependency.getDuration().getTotalMilliseconds());
    Assert.assertEquals(0, dependency.getProperties().size());
  }

  @Test
  public void ExportBasicHttpDependency()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", 201, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("Http", dependency.getType());
    Assert.assertEquals("GET /path", dependency.getName());
    Assert.assertTrue(dependency.getSuccess());
    Assert.assertEquals("https://host/path", dependency.getCommandName());
    Assert.assertEquals("201", dependency.getResultCode());
    Assert.assertEquals("userAgent", dependency.getContext().getUser().getUserAgent());
    Assert.assertEquals(0, dependency.getProperties().size());
    Assert.assertEquals("host", dependency.getTarget());
  }

  @Test
  public void ExportHttpDependencyNoUserAgent()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", 201, "GET", null, null),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("Http", dependency.getType());
    Assert.assertNull(dependency.getContext().getUser().getUserAgent());
  }

  @Test
  public void ExporHttpDependencyNoStatusCode()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", null, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.ABORTED,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("Http", dependency.getType());
    Assert.assertFalse(dependency.getSuccess());
    Assert.assertEquals("ABORTED", dependency.getResultCode());
    Assert.assertEquals(0, dependency.getProperties().size());
  }

  @Test
  public void ExporHttpDependencyNoStatusCodeErrorDescription() {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        start,
        createHttpIncomingRequestAttributes("host", "https://host/path", "/path", null, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.UNKNOWN.withDescription("error description"),
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("Http", dependency.getType());
    Assert.assertFalse(dependency.getSuccess());
    Assert.assertEquals("UNKNOWN", dependency.getResultCode());
    Assert.assertEquals(1, dependency.getProperties().size());
    Assert.assertTrue(dependency.getProperties().containsKey("statusDescription"));
    Assert.assertEquals("error description", dependency.getProperties().get("statusDescription"));
  }

  @Test
  public void ExportHttpDependencyNoPathHost()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        start,
        createHttpIncomingRequestAttributes(null, "https://host:8080/path", null, 404, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("Http", dependency.getType());
    Assert.assertEquals("GET /path", dependency.getName());
    Assert.assertTrue(dependency.getSuccess()); // Status is OK
    Assert.assertEquals("https://host:8080/path", dependency.getCommandName());
    Assert.assertEquals("404", dependency.getResultCode());
    Assert.assertEquals("userAgent", dependency.getContext().getUser().getUserAgent());
    Assert.assertEquals(0, dependency.getProperties().size());
    Assert.assertEquals("host:8080", dependency.getTarget());
  }

  @Test
  public void ExportHttpDependencyInvalidUrl() {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        start,
        createHttpIncomingRequestAttributes("host", "host/path", "/path", 201, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("Http", dependency.getType());
    Assert.assertEquals("GET /path", dependency.getName());
    Assert.assertEquals("host/path", dependency.getCommandName());
    Assert.assertEquals(0, dependency.getProperties().size());
  }

  @Test
  public void ExportHttpDependencyInvalidUrlNoHost() {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        start,
        createHttpIncomingRequestAttributes(null, "host/path", "/path", 201, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(1, eventsSent.size());
    RemoteDependencyTelemetry dependency = (RemoteDependencyTelemetry) eventsSent.get(0);

    Assert.assertNotNull(dependency);
    Assert.assertEquals("GET /path", dependency.getName());
    Assert.assertEquals("host/path", dependency.getCommandName());
    Assert.assertEquals("201", dependency.getResultCode());
    Assert.assertNull(dependency.getTarget());
    Assert.assertEquals(0, dependency.getProperties().size());
  }

  @Test
  public void ExportHttpDependencyToAppInsightsEndpoint1()
  {
    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        Timestamp.fromMillis(System.currentTimeMillis()),
        createHttpIncomingRequestAttributes("host", "https://dc.services.visualstudio.com", "/path", 201, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(0, eventsSent.size());
  }

  @Test
  public void ExportHttpDependencyToAppInsightsEndpoint2()
  {
    SpanData requestSpan = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        null,
        "name",
        Kind.CLIENT,
        Timestamp.fromMillis(System.currentTimeMillis()),
        createHttpIncomingRequestAttributes("host", "https://rt.services.visualstudio.com", "/path", 201, "GET", null, "userAgent"),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        Timestamp.fromMillis(System.currentTimeMillis()));

    exporter.export(Arrays.asList(requestSpan));

    Assert.assertEquals(0, eventsSent.size());
  }

  @Test
  public void ExportSpanWithoutKindIsRequest()
  {
    long startTimestamp = System.currentTimeMillis() - 1000;
    Timestamp start = Timestamp.fromMillis(startTimestamp);

    SpanData span = SpanData.create(
        SpanContext.create(
            TraceId.fromLowerBase16(TRACE_ID),
            SpanId.fromLowerBase16(SPAN_ID),
            TraceOptions.DEFAULT,
            TRACESTATE),
        SpanId.fromLowerBase16(PARENT_SPAN_ID),
        true,
        "name",
        null,
        start,
        Attributes.create(attributes, 0),
        TimedEvents.create(annotations, 0),
        TimedEvents.create(messageEvents, 0),
        Links.create(links, 0),
        null,
        Status.OK,
        start.addDuration(Duration.create(1,0)));

    exporter.export(Arrays.asList(span));

    Assert.assertEquals(1, eventsSent.size());
    RequestTelemetry request = (RequestTelemetry) eventsSent.get(0);

    Assert.assertNotNull(request);
    Assert.assertEquals("name", request.getName());
    Assert.assertEquals(TRACE_ID, request.getContext().getOperation().getId());
    Assert.assertEquals(PARENT_TELEMETRY_ID, request.getContext().getOperation().getParentId());
    Assert.assertEquals(TELEMETRY_ID, request.getId());
    Assert.assertTrue(request.isSuccess());
    Assert.assertEquals(startTimestamp, request.getTimestamp().getTime());
    Assert.assertEquals(1000, request.getDuration().getTotalMilliseconds());
    Assert.assertEquals(0, request.getProperties().size());
  }

  private Attributes createHttpIncomingRequestAttributes(
      String host,
      String url,
      String path,
      Integer status,
      String method,
      String route,
      String userAgent) {
    HashMap<String, AttributeValue> attributes = new HashMap<>();

    if (host != null)
      attributes.put("http.host", AttributeValue.stringAttributeValue(host));

    if (url != null)
      attributes.put("http.url", AttributeValue.stringAttributeValue(url));

    if (path != null)
      attributes.put("http.path", AttributeValue.stringAttributeValue(path));

    if (status != null)
      attributes.put("http.status_code", AttributeValue.longAttributeValue(status));

    if (method != null)
      attributes.put("http.method", AttributeValue.stringAttributeValue(method));

    if (route != null)
      attributes.put("http.route", AttributeValue.stringAttributeValue(route));

    if (userAgent != null)
      attributes.put("http.user_agent", AttributeValue.stringAttributeValue(userAgent));

    return Attributes.create(attributes, 0);
  }

  private Map<String, AttributeValue> createAttributes(String stringValue, long longValue, boolean boolValue, double doubleValue)
  {
    Map<String, AttributeValue> typedAttributes = new HashMap<>();
    typedAttributes.put("string", AttributeValue.stringAttributeValue(stringValue));
    typedAttributes.put("long", AttributeValue.longAttributeValue(longValue));
    typedAttributes.put("bool", AttributeValue.booleanAttributeValue(boolValue));
    typedAttributes.put("double", AttributeValue.doubleAttributeValue(doubleValue));

    return typedAttributes;
  }
}
