package com.sample.jetty;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class JettyRequestThreadPoolFactory implements ThreadFactory {
    private static final AtomicInteger threadNumber = new AtomicInteger(1);

    public Thread newThread(Runnable r) {
        return new Thread(r, "jetty-request-thread-" + threadNumber.getAndIncrement());
    }
}
