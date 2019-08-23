/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.web.extensibility.modules;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.extensibility.TelemetryModule;
import com.microsoft.applicationinsights.extensibility.context.OperationContext;
import com.microsoft.applicationinsights.internal.util.DateTimeUtils;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.correlation.InstrumentationKeyResolver;
import com.microsoft.applicationinsights.web.internal.correlation.ProfileFetcherResultTaskStatus;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtils;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtilsTests;
import com.microsoft.applicationinsights.web.internal.correlation.mocks.MockProfileFetcher;
import com.microsoft.applicationinsights.web.utils.HttpHelper;
import com.microsoft.applicationinsights.web.utils.JettyTestServer;
import com.microsoft.applicationinsights.web.utils.MockTelemetryChannel;
import com.microsoft.applicationinsights.web.utils.ServletUtils;
import io.opencensus.common.Scope;
import io.opencensus.trace.BlankSpan;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracing;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.http.HttpMethod;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Created by yonisha on 2/2/2015.
 */
public class WebRequestTrackingTelemetryModuleTests {
    private static JettyTestServer server = new JettyTestServer();
    private static WebRequestTrackingTelemetryModule defaultModule;
    private static WebRequestTrackingTelemetryModule currentModule;
    private static MockTelemetryChannel channel;
    private static MockProfileFetcher mockProfileFetcher;

    // region Initialization

    @BeforeClass
    public static void classInitialize() throws Exception {
        server.start();

        // Set mock channel
        channel = MockTelemetryChannel.INSTANCE;
        currentModule = getCurrentWebRequestTrackingModule();
        TelemetryConfiguration.getActive().setChannel(channel);
        TelemetryConfiguration.getActive().setInstrumentationKey("SOME_INT_KEY");
    }

    @Before
    public void testInitialize() {

        // initialize mock profile fetcher (for resolving ikeys to appIds)
        mockProfileFetcher = new MockProfileFetcher();
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.PENDING);
        InstrumentationKeyResolver.INSTANCE.setProfileFetcher(mockProfileFetcher);
        InstrumentationKeyResolver.INSTANCE.clearCache();

        defaultModule = new WebRequestTrackingTelemetryModule();
        defaultModule.initialize(TelemetryConfiguration.getActive());
        defaultModule.setRequestTelemetryContext(null);
        channel.reset();
    }

    @After
    public void testDestroy() {
        currentModule.isW3CEnabled = false;
        defaultModule.isW3CEnabled = false;
        defaultModule.setRequestTelemetryContext(null);
        defaultModule.setEnableBackCompatibilityForW3C(true);
        ThreadContext.remove();
    }

    @AfterClass
    public static void classCleanup() throws Exception {
        server.shutdown();
    }

    // endregion Initialization

    // region Tests

    @Test
    public void testHttpRequestTrackedSuccessfully() throws Exception {
        HttpHelper.sendRequestAndGetResponseCookie(server.getPortNumber());
        Thread.sleep(1000);
        List<RequestTelemetry> items = channel.getTelemetryItems(RequestTelemetry.class);

        assertEquals(1, items.size());
        RequestTelemetry requestTelemetry = items.get(0);

        assertEquals(String.valueOf(HttpStatus.SC_OK), requestTelemetry.getResponseCode());
        assertEquals(HttpMethod.GET.asString() + " /", requestTelemetry.getName());
        assertEquals(HttpMethod.GET.asString(), requestTelemetry.getHttpMethod());
        assertEquals("http://localhost:" + server.getPortNumber() + "/", requestTelemetry.getUrl().toString());
    }

    @Test
    public void testResponseHeaderIsSetForRequestContext() throws Exception {

        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);
        mockProfileFetcher.setAppIdToReturn("myId");

        Map<String, List<String>> headers = HttpHelper.sendRequestAndGetHeaders(server.getPortNumber());
        List<String> requestContextValues = headers.get(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME);
        Assert.assertNotNull(requestContextValues);
        Assert.assertTrue(requestContextValues.size() == 1);

        String requestContext = requestContextValues.get(0);
        Assert.assertEquals("appId=cid-v1:myId", requestContext);
    }

    @Test
    public void testResponseHeaderIsSetForRequestContextWhenUsingW3C() throws Exception {
        currentModule.isW3CEnabled = true;
        testResponseHeaderIsSetForRequestContext();
    }

    @Test
    public void testOnBeginRequestCatchAllExceptions() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        defaultModule.onBeginRequest(request, null);
    }

    @Test
    public void testOnEndRequestCatchAllExceptions() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        defaultModule.onEndRequest(request, null);
    }


    @Test
    public void testUserAgentIsBeingSet() throws Exception {
        HttpHelper.sendRequestAndGetResponseCookie(server.getPortNumber());

        List<RequestTelemetry> items = channel.getTelemetryItems(RequestTelemetry.class);
        assertEquals(1, items.size());
        RequestTelemetry requestTelemetry = items.get(0);

        Assert.assertEquals(HttpHelper.TEST_USER_AGENT, requestTelemetry.getContext().getUser().getUserAgent());
    }

    @Test
    public void testCrossComponentCorrelationHeadersAreCaptured() {

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        String incomingId = "|guid.bcec871c_1.";

        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, incomingId);
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers, 1);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        // verify ID's are set as expected in request telemetry
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNotNull(requestTelemetry.getId());
        Assert.assertEquals(incomingId.length() + 9, requestTelemetry.getId().length());
        Assert.assertTrue(requestTelemetry.getId().startsWith(incomingId));

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertEquals("guid", operation.getId());
        Assert.assertEquals(incomingId, operation.getParentId());

        //validate custom properties
        Assert.assertEquals(2, requestTelemetry.getProperties().size());
        Assert.assertEquals("value1", requestTelemetry.getProperties().get("key1"));
        Assert.assertEquals("value2", requestTelemetry.getProperties().get("key2"));

        //run onEnd
        defaultModule.onEndRequest(request, null);

        //validate source
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", null), requestTelemetry.getSource());
    }

    @Test
    public void testCrossComponentCorrelationHeadersAreCapturedWhenW3CTurnedOn() {

        // Turn W3C on
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();
        String traceId = "0123456789abcdef0123456789abcdef";
        String parentSpanId = "0123456789abcdef";

        headers.put(ServletUtils.TRACEPARENT_HEADER_NAME, "00-" + traceId + "-" + parentSpanId + "-00");
        headers.put(ServletUtils.TRACESTATE_HEADER_NAME, "foo=1,bar=xyz");
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", null));

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);
        Assert.assertNotNull(Tracing.getTracer().getCurrentSpan());

        // verify ID's are set as expected in request telemetry
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNotNull(requestTelemetry.getId());
        // spanIds are different
        String[] id = requestTelemetry.getId().split("[.]");
        Assert.assertNotEquals(parentSpanId, id[1]);
        // traceIds are same
        Assert.assertTrue(requestTelemetry.getId().startsWith("|" + traceId + "."));

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertEquals(traceId, operation.getId());
        Assert.assertEquals("|" + traceId + "." + parentSpanId  + ".", operation.getParentId());
        //run onEnd
        defaultModule.onEndRequest(request, null);

        //validate source
        Assert.assertNotNull(requestTelemetry.getSource());
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1" , null), requestTelemetry.getSource());

        Assert.assertEquals(BlankSpan.INSTANCE, Tracing.getTracer().getCurrentSpan());
        Assert.assertTrue(requestTelemetry.getProperties().containsKey("foo"));
        Assert.assertTrue(requestTelemetry.getProperties().containsKey("bar"));
        Assert.assertEquals("1", requestTelemetry.getProperties().get("foo"));
        Assert.assertEquals("xyz", requestTelemetry.getProperties().get("bar"));
    }

    @Test
    public void testLegacyHeadersAreCapturedWhenW3CIsTurnedOn() {
        // Turn W3C on
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();

        String incomingId = "|0123456789abcdef0123456789abcdef.bcec871c_1.";
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, incomingId);
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        // verify ID's are set as expected in request telemetry
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNotNull(requestTelemetry.getId());

        Assert.assertTrue(requestTelemetry.getId().startsWith("|0123456789abcdef0123456789abcdef."));

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertEquals("0123456789abcdef0123456789abcdef", operation.getId());
        Assert.assertEquals(incomingId, operation.getParentId());

        //run onEnd
        defaultModule.onEndRequest(request, null);

        //validate source
        Assert.assertNotNull(requestTelemetry.getSource());
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", null), requestTelemetry.getSource());

        Assert.assertFalse(requestTelemetry.getContext().getProperties().containsKey("ai_legacyRootId"));
        Assert.assertFalse(requestTelemetry.getProperties().containsKey("tracestate"));
    }

    @Test
    public void testTraceparentIsCreatedWhenCorrelationFallsBackToRequestIdWhenIncorrectHeaderValues() {
        // Turn W3C on
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();

        String incomingId = "|guid.bcec871c_1.";
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, incomingId);
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.
            getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        // verify ID's are set as expected in request telemetry
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNotNull(requestTelemetry.getId());

        //"guid" is invalid trace-id in W3C. A new Traceparent is created
        Assert.assertFalse(requestTelemetry.getId().startsWith("|guid."));

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertNotEquals("guid", operation.getId());
        Assert.assertEquals(incomingId, operation.getParentId());

        //run onEnd
        defaultModule.onEndRequest(request, null);

        //validate source
        Assert.assertNotNull(requestTelemetry.getSource());
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", null), requestTelemetry.getSource());

        // validate ai_legacyRootID is set
        Assert.assertTrue(requestTelemetry.getContext().getProperties().containsKey("ai_legacyRootId"));
        Assert.assertEquals(requestTelemetry.getContext().getProperties().get("ai_legacyRootId"),
            "guid");
    }

    @Test
    public void traceParentIsCreatedInBackPortModeWhenRequestIdIsEmpty() {
        // Turn W3C on
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();

        String incomingId = "";
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, incomingId);
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.
            getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = (HttpServletResponse)ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        // verify ID's are set as expected in request telemetry
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNotNull(requestTelemetry.getId());

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertNotNull(operation.getId());

        // request-id is empty
        Assert.assertNull(operation.getParentId());

        //run onEnd
        defaultModule.onEndRequest(request, null);

        //validate source
        Assert.assertNotNull(requestTelemetry.getSource());
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", null), requestTelemetry.getSource());

        // No ai_legacyRootID is set as request-id is empty
        Assert.assertFalse(requestTelemetry.getContext().getProperties().containsKey("ai_legacyRootId"));
    }

    @Test
    public void traceParentIsCreatedInBackPortModeWhenRequestIdIsNull() {
        // Turn W3C on
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();

        String incomingId = null;
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, incomingId);
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.
            getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = (HttpServletResponse)ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        // verify ID's are set as expected in request telemetry
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNotNull(requestTelemetry.getId());

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertNotNull(operation.getId());

        // request-id is empty
        Assert.assertNull(operation.getParentId());

        //run onEnd
        defaultModule.onEndRequest(request, null);

        //validate source
        Assert.assertNotNull(requestTelemetry.getSource());
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", null), requestTelemetry.getSource());

        // No ai_legacyRootID is set as request-id is empty
        Assert.assertFalse(requestTelemetry.getContext().getProperties().containsKey("ai_legacyRootId"));

    }

    @Test
    public void testLegacyHeadersAreNotCapturedWhenW3CIsTurnedOnAndBackPortSwitchIsOff() {
        // Turn W3C on
        defaultModule.isW3CEnabled = true;
        defaultModule.setEnableBackCompatibilityForW3C(false);

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();

        String incomingId = "|0123456789abcdef0123456789abcdef.bcec871c_1.";
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, incomingId);
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        // verify ID's are set as expected in request telemetry
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNotNull(requestTelemetry.getId());

        // old headers are not captured
        Assert.assertFalse(requestTelemetry.getId().startsWith("|0123456789abcdef0123456789abcdef."));

        //validate operation context ID's
        OperationContext operation = requestTelemetry.getContext().getOperation();
        Assert.assertNotEquals("0123456789abcdef0123456789abcdef", operation.getId());
        Assert.assertNotEquals(incomingId, operation.getParentId());

        //run onEnd
        defaultModule.onEndRequest(request, null);

        //request-context headers are checked
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", null), requestTelemetry.getSource());

        Assert.assertFalse(requestTelemetry.getContext().getProperties().containsKey("ai_legacyRootId"));
    }

    @Test
    public void testTelemetryCreatedWithinRequestScopeIsRequestChild() {

        //setup: initialize a request context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();

        String incomingId = "|guid.bcec871c_1.";
        headers.put(TelemetryCorrelationUtils.CORRELATION_HEADER_NAME, incomingId);

        String correlationContext = "key1=value1, key2=value2";
        headers.put(TelemetryCorrelationUtils.CORRELATION_CONTEXT_HEADER_NAME, correlationContext);

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers, 1);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //run module
        defaultModule.onBeginRequest(request, response);

        //additional telemetry is manually tracked
        TelemetryClient telemetryClient = new TelemetryClient();
        telemetryClient.trackException(new Exception());

        List<ExceptionTelemetry> items = channel.getTelemetryItems(ExceptionTelemetry.class);
        Assert.assertEquals(1, items.size());
        ExceptionTelemetry exceptionTelemetry = items.get(0);

        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();

        //validate manually tracked telemetry is a child of the request telemetry
        Assert.assertEquals("guid", exceptionTelemetry.getContext().getOperation().getId());
        Assert.assertEquals(requestTelemetry.getId(), exceptionTelemetry.getContext().getOperation().getParentId());
        Assert.assertEquals(2, exceptionTelemetry.getProperties().size());
        Assert.assertEquals("value1", exceptionTelemetry.getProperties().get("key1"));
        Assert.assertEquals("value2", exceptionTelemetry.getProperties().get("key2"));
    }

    @Test
    public void testTelemetryCreatedWithinRequestScopeIsRequestChildWhenW3CEnabled() {

        //turn on W3C
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();

        String traceId = "0123456789abcdef0123456789abcdef";
        String parentSpanId = "0123456789abcdef";

        headers.put(ServletUtils.TRACEPARENT_HEADER_NAME, "00-" + traceId + "-" + parentSpanId + "-00");
        headers.put(ServletUtils.TRACESTATE_HEADER_NAME, "foo=1,bar=xyz");

        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        //additional telemetry is manually tracked
        TelemetryClient telemetryClient = new TelemetryClient();
        telemetryClient.trackException(new Exception());

        List<ExceptionTelemetry> items = channel.getTelemetryItems(ExceptionTelemetry.class);
        Assert.assertEquals(1, items.size());
        ExceptionTelemetry exceptionTelemetry = items.get(0);

        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();

        //validate manually tracked telemetry is a child of the request telemetry
        Assert.assertEquals(traceId, exceptionTelemetry.getContext().getOperation().getId());
        Assert.assertEquals(requestTelemetry.getId(), exceptionTelemetry.getContext().getOperation().getParentId());

        defaultModule.onEndRequest(request, response);

        Assert.assertEquals(BlankSpan.INSTANCE, Tracing.getTracer().getCurrentSpan());
        Assert.assertFalse(exceptionTelemetry.getProperties().containsKey("tracestate"));
        Assert.assertTrue(requestTelemetry.getProperties().containsKey("foo"));
        Assert.assertTrue(requestTelemetry.getProperties().containsKey("bar"));
        Assert.assertEquals("1", requestTelemetry.getProperties().get("foo"));
        Assert.assertEquals("xyz", requestTelemetry.getProperties().get("bar"));
    }

    @Test
    public void testTracestateIsEmptyWhenHeaderIsNotPresent() {
        //turn on W3C
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        ThreadContext.setRequestTelemetryContext(context);
        defaultModule.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<>();

        String traceId = "0123456789abcdef0123456789abcdef";
        String parentSpanId = "0123456789abcdef";

        headers.put(ServletUtils.TRACEPARENT_HEADER_NAME, "00-" + traceId + "-" + parentSpanId + "00");
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();

        //run
        defaultModule.onBeginRequest(request, response);
        defaultModule.onEndRequest(request, response);

        Assert.assertFalse(requestTelemetry.getProperties().containsKey("tracestate"));
    }


    @Test
    public void testOnEndAddsSourceFieldForRequestWithRequestContext() {

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);
        defaultModule.onEndRequest(request, null);

        //validate source
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", null), requestTelemetry.getSource());
    }

    @Test
    public void testOnEndDoesNotAddSourceFieldForRequestFromSameApp() {

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        ThreadContext.setRequestTelemetryContext(context);
        defaultModule.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return the same appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id1");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);
        defaultModule.onEndRequest(request, null);

        //validate source
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNull(requestTelemetry.getSource());
    }

    @Test
    public void testOnEndAddsSourceFieldForRequestWithRoleNameOnly() {

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue(null, "Front End"));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);
        defaultModule.onEndRequest(request, null);

        //validate source
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue(null, "Front End"), requestTelemetry.getSource());
    }

    @Test
    public void testOnEndAddsSourceFieldForRequestWithRoleNameAndAppId() {

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", "Front End"));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);
        defaultModule.onEndRequest(request, null);

        //validate source
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue("id1", "Front End"), requestTelemetry.getSource());
    }

    @Test
    public void testOnEndAddsSourceFieldForRequestWithRoleNameForSameApp() {

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", "Front End"));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return the same appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id1");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);
        defaultModule.onEndRequest(request, null);

        //validate source
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertEquals(TelemetryCorrelationUtilsTests.getRequestSourceValue(null, "Front End"), requestTelemetry.getSource());
    }

    @Test
    public void testOnEndDoesNotOverrideSourceField() {

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        requestTelemetry.setSource("myAppId");

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", "Front End"));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);
        defaultModule.onEndRequest(request, null);

        //validate source
        Assert.assertEquals("myAppId", requestTelemetry.getSource());
    }

    @Test
    public void testInstrumentationKeyIsResolvedDuringModuleInit() {
        Assert.assertEquals(0, mockProfileFetcher.callCount());
        String ikey = TelemetryConfiguration.getActive().getInstrumentationKey();

        //calling resolver now will actually retrieve the appId from the completed task
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);
        mockProfileFetcher.setAppIdToReturn("someAppId");
        String appId = InstrumentationKeyResolver.INSTANCE.resolveInstrumentationKey(ikey);
        Assert.assertEquals(1, mockProfileFetcher.callCount());
        Assert.assertEquals("cid-v1:someAppId", appId);

        //calling it again should retrieve appId from cache (i.e. fetcher call count remains 2)
        appId = InstrumentationKeyResolver.INSTANCE.resolveInstrumentationKey(ikey);
        Assert.assertEquals(1, mockProfileFetcher.callCount());
        Assert.assertEquals("cid-v1:someAppId", appId);
    }

    @Test
    public void testInstrumentationKeyIsResolvedIfModifiedAtRuntime() {
        Assert.assertEquals(0, mockProfileFetcher.callCount());

        //setup: initialize a request telemetry context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        headers.put(TelemetryCorrelationUtils.REQUEST_CONTEXT_HEADER_NAME, TelemetryCorrelationUtilsTests.getRequestContextHeaderValue("id1", null));
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers);
        HttpServletResponse response = (HttpServletResponse)ServletUtils.generateDummyServletResponse();

        //configure mock appId fetcher to return different appId from what's on the request header
        mockProfileFetcher.setAppIdToReturn("id2");
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        //run
        defaultModule.onBeginRequest(request, response);

        //onBegin must have called fetcher
        Assert.assertEquals(1, mockProfileFetcher.callCount());

        // mimic customer modifying ikey at runtime in request handler (e.g. controller)
        TelemetryConfiguration.getActive().setInstrumentationKey("myOtherIkey");

        // module.onEndRequest must detect change and start resolving new ikey
        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.PENDING);
        mockProfileFetcher.setAppIdToReturn("id3");
        defaultModule.onEndRequest(request, null);

        //the ikey is new, which means its appId ("id3") is not in cache, so again we call the fetcher
        Assert.assertEquals(2, mockProfileFetcher.callCount());

        // at this point source won't be set yet because the ikey has changed and so a new resolve task has started
        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertNull(requestTelemetry.getSource());

        //another request comes in
        RequestTelemetryContext context2 = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context2);
        HttpServletRequest request2 = ServletUtils.createServletRequestWithHeaders(headers);

        mockProfileFetcher.setResultStatus(ProfileFetcherResultTaskStatus.COMPLETE);

        defaultModule.onBeginRequest(request2, response);

        // at this point, the new appId should be available in the cache
        Assert.assertEquals(3, mockProfileFetcher.callCount());

        defaultModule.onEndRequest(request, null);
        Assert.assertEquals(3, mockProfileFetcher.callCount());

        RequestTelemetry requestTelemetry2 = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertEquals("cid-v1:id1", requestTelemetry2.getSource());

        // if another request comes in, it should retrieve appId from cache
        RequestTelemetryContext context3 = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context3);
        HttpServletRequest request3 = ServletUtils.createServletRequestWithHeaders(headers);
        defaultModule.onBeginRequest(request3, response);
        Assert.assertEquals(3, mockProfileFetcher.callCount());

        // module.onEndRequest will attempt to retrieve new appId from task if it is completed
        defaultModule.onEndRequest(request, null);
        Assert.assertEquals(3, mockProfileFetcher.callCount());
        RequestTelemetry requestTelemetry3 = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();
        Assert.assertEquals("cid-v1:id1", requestTelemetry3.getSource());
    }

    @Test
    public void testTelemetryCreatedAsyncWithinRequestScopeIsAnOrphan() {
        // Turn W3C on
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers, 1);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //run module
        defaultModule.onBeginRequest(request, response);

        final TelemetryClient telemetryClient = new TelemetryClient();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {

                // ThreadLocal is gone
                Assert.assertEquals(BlankSpan.INSTANCE, Tracing.getTracer().getCurrentSpan());
                // clear ThreadContext - it is done with InheritableThreadLocal and not reliable
                ThreadContext.remove();

                telemetryClient.trackException(new Exception());
                return "hello!";
            }
        });

        try {
            future.get();
        } catch (Exception e) {
        }

        List<ExceptionTelemetry> items = channel.getTelemetryItems(ExceptionTelemetry.class);
        Assert.assertEquals(1, items.size());
        ExceptionTelemetry exceptionTelemetry = items.get(0);

        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();

        //validate manually tracked telemetry is a child of the request telemetry
        Assert.assertNull(exceptionTelemetry.getContext().getOperation().getId());
        Assert.assertNull(exceptionTelemetry.getContext().getOperation().getParentId());
    }

    @Test
    public void testTelemetryCreatedAsyncWithinRequestScopeWithManualPropagationIsChild() {
        // Turn W3C on
        defaultModule.isW3CEnabled = true;

        //setup: initialize a request context
        RequestTelemetryContext context = new RequestTelemetryContext(DateTimeUtils.getDateTimeNow().getTime());
        defaultModule.setRequestTelemetryContext(context);
        ThreadContext.setRequestTelemetryContext(context);

        //mock a servlet request with cross-component correlation headers
        Map<String, String> headers = new HashMap<String, String>();
        HttpServletRequest request = ServletUtils.createServletRequestWithHeaders(headers, 1);
        HttpServletResponse response = ServletUtils.generateDummyServletResponse();

        //run module
        defaultModule.onBeginRequest(request, response);

        final TelemetryClient telemetryClient = new TelemetryClient();

        final Span span = Tracing.getTracer().getCurrentSpan();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {

                // ThreadLocal is gone
                Assert.assertEquals(BlankSpan.INSTANCE, Tracing.getTracer().getCurrentSpan());
                // clear ThreadContext - it is done with InheritableThreadLocal and not reliable
                ThreadContext.remove();

                try (Scope _ = Tracing.getTracer().withSpan(span)) {
                    telemetryClient.trackException(new Exception());
                    return "hello!";
                }
            }
        });

        try {
            future.get();
        } catch (Exception e) {
        }

        List<ExceptionTelemetry> items = channel.getTelemetryItems(ExceptionTelemetry.class);
        Assert.assertEquals(1, items.size());
        ExceptionTelemetry exceptionTelemetry = items.get(0);

        RequestTelemetry requestTelemetry = defaultModule.getRequestTelemetryContext().getHttpRequestTelemetry();

        //validate manually tracked telemetry is a child of the request telemetry
        Assert.assertEquals(requestTelemetry.getContext().getOperation().getId(), exceptionTelemetry.getContext().getOperation().getId());
        Assert.assertEquals(requestTelemetry.getId(), exceptionTelemetry.getContext().getOperation().getParentId());
    }
    // endregion Tests

    // region Private methods

    private static WebRequestTrackingTelemetryModule getCurrentWebRequestTrackingModule() {
        List<TelemetryModule> modules = TelemetryConfiguration.getActive().getTelemetryModules();
        for (TelemetryModule module : modules) {
            if (module instanceof WebRequestTrackingTelemetryModule) {
                return (WebRequestTrackingTelemetryModule) module;
            }
        }
        return null;
    }

    // endregion Private methods
}