package com.sample.jetty;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.sample.jetty.JettySampleClient.PRECREATE_CONNECTION_COUNT;

public class WarmupListener implements Request.Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(WarmupListener.class);
    private final HttpClient httpClient;
    private final String remoteUrl;
    private final int count;

    public WarmupListener(HttpClient httpClient, String remoteUrl, int count) {
        this.httpClient = httpClient;
        this.remoteUrl = remoteUrl;
        this.count = count;
    }

    @Override
    public void onBegin(Request request) {
        if (count < PRECREATE_CONNECTION_COUNT) {
            Request nextRequest = httpClient.newRequest(remoteUrl)
                    .listener(new WarmupListener(httpClient, remoteUrl, count + 1))
                    .timeout(2000, TimeUnit.MILLISECONDS)
                    .method(HttpMethod.CONNECT);
            try {
                nextRequest.send();
                LOGGER.info("warmed connections {}", count);
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}
