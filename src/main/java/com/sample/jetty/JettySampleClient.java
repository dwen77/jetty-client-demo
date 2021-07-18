package com.sample.jetty;

import org.eclipse.jetty.client.DuplexConnectionPool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.eclipse.jetty.http.HttpMethod.CONNECT;
import static org.eclipse.jetty.http.HttpMethod.GET;

public class JettySampleClient {

    public static final int MAX_RESPONSE_LENGTH = 1024 * 1024 * 10;
    public static final String REMOTE_URL = "https://www.google.com/";
    private static final Logger LOGGER = LoggerFactory.getLogger(JettySampleClient.class);
    public static final int REQUEST_COUNT = 10;
    public static final int PRECREATE_CONNECTION_COUNT = 0;

    public static void main(String[] args) throws Exception {
        JettySampleClient client = new JettySampleClient();
        client.sendMultipleRequests();
    }

    private void sendMultipleRequests() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(REQUEST_COUNT);
        HttpClient httpClient = createHttpClient();
        warmup(httpClient);

//        httpClient.stop();
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("org.eclipse.jetty.client:context=HttpClient@*,type=duplexconnectionpool,*");
        Set<ObjectName> objectNames = server.queryNames(objectName, null);
        ScheduledExecutorService executorService1 = Executors.newScheduledThreadPool(1);
        executorService1.scheduleAtFixedRate(() -> {
            objectNames.forEach(objectName1 -> {
                try {
                    Object activeConnectionCount = server.getAttribute(objectName1, "activeConnectionCount");
                    Object connectionCount = server.getAttribute(objectName1, "connectionCount");
                    Object idleConnectionCount = server.getAttribute(objectName1, "idleConnectionCount");
                    LOGGER.info(" {} {} {} ", activeConnectionCount, connectionCount, idleConnectionCount);
                    if (countDownLatch.getCount() == 0) {
                        executorService1.shutdown();
                    }
                } catch (MBeanException | AttributeNotFoundException | InstanceNotFoundException | ReflectionException e) {
                    e.printStackTrace();
                }
            });
        }, 0, 10, TimeUnit.MILLISECONDS);

        ExecutorService executorService = Executors.newCachedThreadPool(new JettyRequestThreadPoolFactory());
        IntStream.range(0, REQUEST_COUNT)
                .forEach(value -> executorService.submit(() -> sendRequest(countDownLatch, httpClient)));
        countDownLatch.await();
        executorService.shutdown();
    }

    private void sendRequest(CountDownLatch countDownLatch, HttpClient httpClient) {
        final Request httpRequest = httpClient.newRequest(REMOTE_URL)
                .listener(new PerformanceListener())
                .method(GET);
        httpRequest.send(new OnCompleteBufferingResponseListener(countDownLatch));
    }

    private void warmup(HttpClient httpClient) {
        Request request = httpClient.newRequest(REMOTE_URL)
                .listener(new WarmupListener(httpClient, REMOTE_URL, 0))
                .method(HttpMethod.CONNECT);
        try {
            request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private HttpClient createHttpClient() throws Exception {
        HttpClient httpClient = new HttpClient(new SslContextFactory.Client());
        httpClient.setMaxConnectionsPerDestination(100);
        httpClient.setAddressResolutionTimeout(10000);
        httpClient.setConnectTimeout(10000);
        httpClient.setIdleTimeout(100000);

        if (PRECREATE_CONNECTION_COUNT > 0) {
            HttpClientTransport transport = httpClient.getTransport();
            transport.setConnectionPoolFactory(destination -> {
                final DuplexConnectionPool duplexConnectionPool = new DuplexConnectionPool(
                        destination,
                        httpClient.getMaxConnectionsPerDestination(),
                        destination);
                try {
                    int preCreateConnectionCount = PRECREATE_CONNECTION_COUNT;
                    duplexConnectionPool.preCreateConnections(preCreateConnectionCount)
                            .handle((unused, throwable) -> {
                                if (throwable != null) {
                                    LOGGER.warn("failed to pre-create connections due to {} ", throwable.getMessage());
                                } else {
                                    LOGGER.info("pre-create {} connections successfully", preCreateConnectionCount);
                                }
                                return null;
                            })
                            .get(10, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    LOGGER.warn("failed to pre-create connections due to {} ", e.getMessage());
                }
                return duplexConnectionPool;
            });
        }

        // Setup JMX.
        MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        httpClient.addBean(mbeanContainer);
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
            System.out.println("Response is " + getContentAsString().substring(0, 10));
            Optional.ofNullable(result.getResponseFailure()).ifPresent(
                    throwable -> System.out.println("Failed to call service due to " + throwable.getMessage())
            );
            this.countDownLatch.countDown();
        }
    }
}
