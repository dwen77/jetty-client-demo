package com.sample.jetty;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.eclipse.jetty.http.HttpMethod.GET;

public class JettySampleClient {

    public static final int MAX_RESPONSE_LENGTH = 1024 * 1024 * 10;
    public static final String REMOTE_URL = "https://www.google.com/";

    public static void main(String[] args) throws Exception {
        JettySampleClient client = new JettySampleClient();
        client.sendMultipleRequests();
        client.sendSameRequestMultipleTimes();
    }

    private void sendMultipleRequests() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(4);
        HttpClient httpClient = createHttpClient();
        final Request httpRequest = httpClient.newRequest(REMOTE_URL)
                .method(GET);
        final Request httpRequest1 = httpClient.newRequest(REMOTE_URL)
                .method(GET);
        final Request httpRequest2 = httpClient.newRequest(REMOTE_URL)
                .method(GET);
        final Request httpRequest3 = httpClient.newRequest(REMOTE_URL)
                .method(GET);
        httpRequest.send(new OnCompleteBufferingResponseListener(countDownLatch));
        httpRequest1.send(new OnCompleteBufferingResponseListener(countDownLatch));
        httpRequest2.send(new OnCompleteBufferingResponseListener(countDownLatch));
        httpRequest3.send(new OnCompleteBufferingResponseListener(countDownLatch));
        countDownLatch.await();
        httpClient.stop();
    }

    private void sendSameRequestMultipleTimes() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(4);
        HttpClient httpClient = createHttpClient();
        final Request httpRequest = httpClient.newRequest(REMOTE_URL)
                .method(GET);
        httpRequest.send(new OnCompleteBufferingResponseListener(countDownLatch));
        httpRequest.send(new OnCompleteBufferingResponseListener(countDownLatch));
        httpRequest.send(new OnCompleteBufferingResponseListener(countDownLatch));
        httpRequest.send(new OnCompleteBufferingResponseListener(countDownLatch));
        countDownLatch.await();
        httpClient.stop();
    }

    private HttpClient createHttpClient() throws Exception {
        HttpClient httpClient = new HttpClient(new SslContextFactory.Client());
        httpClient.setMaxConnectionsPerDestination(100);
        httpClient.setAddressResolutionTimeout(10000);
        httpClient.setConnectTimeout(10000);
        httpClient.setIdleTimeout(10000);
        httpClient.start();
        return httpClient;
    }

    static class OnCompleteBufferingResponseListener extends BufferingResponseListener {
        private final CountDownLatch countDownLatch;

        private OnCompleteBufferingResponseListener(CountDownLatch countDownLatch) {
            super(MAX_RESPONSE_LENGTH);
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void onBegin(Response response) {
            super.onBegin(response);
        }

        @Override
        public void onComplete(Result result) {
            System.out.println("Response is " + getContentAsString());
            Optional.ofNullable(result.getResponseFailure()).ifPresent(
                    throwable -> System.out.println("Failed to call service due to " + throwable.getMessage())
            );
            this.countDownLatch.countDown();
        }
    }
}
