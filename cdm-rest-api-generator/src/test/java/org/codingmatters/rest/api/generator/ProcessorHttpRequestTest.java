package org.codingmatters.rest.api.generator;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * Created by nelt on 5/26/17.
 */
public class ProcessorHttpRequestTest extends AbstractProcessorHttpRequestTest {

    @Before
    public void setUp() throws Exception {
        ProcessorGeneratorTestHelper helper = new ProcessorGeneratorTestHelper(this.dir, this.fileHelper)
                .setUpWithResource("processor/processor-request.raml");
        this.compiled = helper.compiled();
        this.fileHelper.printFile(this.dir.getRoot(), "TestAPIProcessor.java");
    }

    @Test
    public void requestPayload() throws Exception {
        AtomicReference requestHolder = new AtomicReference();
        this.setupProcessorWithHandler(
                "rootPostHandler",
                req -> {
                    requestHolder.set(req);
                    return null;
                });

        Response response = this.client.newCall(new Request.Builder().url(this.undertow.baseUrl() + "/api/root/")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf_8"), "{\"prop\":\"val\"}"))
                .build()).execute();
        Object request = requestHolder.get();

        assertThat(response.code(), is(200));
        assertThat(request, is(notNullValue()));
        assertThat(
                this.compiled.on(request).castedTo("org.generated.api.RootPostRequest").invoke("payload"),
                isA(this.compiled.getClass("org.generated.types.Req"))
        );
    }

    @Test
    public void requestPayload_emptyPayload() throws Exception {
        AtomicReference requestHolder = new AtomicReference();
        this.setupProcessorWithHandler(
                "rootPostHandler",
                req -> {
                    requestHolder.set(req);
                    return null;
                });

        Response response = this.client.newCall(new Request.Builder().url(this.undertow.baseUrl() + "/api/root/")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf_8"), "{}"))
                .build()).execute();
        Object request = requestHolder.get();

        assertThat(response.code(), is(200));
        assertThat(request, is(notNullValue()));
        assertThat(
                this.compiled.on(request).castedTo("org.generated.api.RootPostRequest").invoke("payload"),
                isA(this.compiled.getClass("org.generated.types.Req"))
        );
    }

    @Test
    public void requestPayload_nullPayload() throws Exception {
        AtomicReference requestHolder = new AtomicReference();
        this.setupProcessorWithHandler(
                "rootPostHandler",
                req -> {
                    requestHolder.set(req);
                    return null;
                });

        Response response = this.client.newCall(new Request.Builder().url(this.undertow.baseUrl() + "/api/root/")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf_8"), "null"))
                .build()).execute();
        Object request = requestHolder.get();

        assertThat(response.code(), is(200));
        assertThat(request, is(notNullValue()));
        assertThat(
                this.compiled.on(request).castedTo("org.generated.api.RootPostRequest").invoke("payload"),
                is(nullValue())
        );
    }

    @Test
    public void requestPayload_unparseable() throws Exception {
        AtomicReference requestHolder = new AtomicReference();
        this.setupProcessorWithHandler(
                "rootPostHandler",
                req -> {
                    requestHolder.set(req);
                    return null;
                });

        Response response = this.client.newCall(new Request.Builder().url(this.undertow.baseUrl() + "/api/root/")
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf_8"), "yopyop tagada"))
                .build()).execute();
        Object request = requestHolder.get();

        assertThat(response.code(), is(400));
        assertThat(response.body().string(), is("bad request body, see logs"));
        assertThat(request, is(nullValue()));
    }

}
