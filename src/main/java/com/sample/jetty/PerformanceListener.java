package com.sample.jetty;

import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class PerformanceListener implements Request.Listener, Response.Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger(JettySampleClient.class);
    private final StopWatch stopWatch;
    private long lastSplit;

    public PerformanceListener() {
        stopWatch = StopWatch.createStarted();
        lastSplit = 0;
    }

    @Override
    public void onBegin(Request request) {
        LOGGER.info("Request:onBegin {} ", getSplitTime());
    }

    @Override
    public void onCommit(Request request) {
        LOGGER.info("Request:onCommit {} ", getSplitTime());
    }

    @Override
    public void onContent(Request request, ByteBuffer content) {
        LOGGER.info("Request:onContent {} ", getSplitTime());
    }

    @Override
    public void onFailure(Request request, Throwable failure) {
        LOGGER.info("Request:onFailure {} ", getSplitTime());
    }

    @Override
    public void onHeaders(Request request) {
        LOGGER.info("Request:onHeaders {} ", getSplitTime());
    }

    @Override
    public void onQueued(Request request) {
        LOGGER.info("Request:onQueued {} ", getSplitTime());
    }

    @Override
    public void onSuccess(Request request) {
        LOGGER.info("Request:onSuccess {} ", getSplitTime());
    }

    @Override
    public void onContent(Response response, ByteBuffer content, Callback callback) {
        LOGGER.info("Response:onContent {} ", getSplitTime());
    }

    @Override
    public void onBegin(Response response) {
        LOGGER.info("Response:onBegin {} ", getSplitTime());
    }

    @Override
    public void onComplete(Result result) {
        LOGGER.info("Response:onComplete {} ", getSplitTime());
    }

    @Override
    public void onContent(Response response, ByteBuffer content) {
        LOGGER.info("Response:onContent {} ", getSplitTime());
    }

    @Override
    public void onFailure(Response response, Throwable failure) {
        LOGGER.info("Response:onFailure {} ", getSplitTime());
    }

    @Override
    public boolean onHeader(Response response, HttpField field) {
        LOGGER.info("Response:onHeader {} ", getSplitTime());
        return false;
    }

    @Override
    public void onHeaders(Response response) {
        LOGGER.info("Response:onHeaders {} ", getSplitTime());
    }

    @Override
    public void onSuccess(Response response) {
        LOGGER.info("Response:onSuccess {} ", getSplitTime());
    }

    private long getSplitTime() {
        stopWatch.split();
        long splitTime = stopWatch.getSplitTime();
        long l = splitTime - lastSplit;
        lastSplit = splitTime;
        return l;
    }
}
