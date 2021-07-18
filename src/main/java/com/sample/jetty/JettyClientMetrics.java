package com.sample.jetty;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.management.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class JettyClientMetrics implements MeterBinder, AutoCloseable {

    private final MBeanServer mBeanServer;
    private final Iterable<Tag> tags;
    private final Set<NotificationListener> notificationListeners = ConcurrentHashMap.newKeySet();

    public JettyClientMetrics(MBeanServer mBeanServer, Iterable<Tag> tags) {
        this.mBeanServer = mBeanServer;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registerConnectionPoolMetrics(registry);
    }

    private void registerConnectionPoolMetrics(MeterRegistry registry) {
        registerMetricsEventually("org.eclipse.jetty.client", ":context=*,type=duplexconnectionpool*", (name, allTags) -> {
            Gauge.builder("jetty.connectionpool.active.count", mBeanServer,
                    s -> safeLong(() -> s.getAttribute(name, "activeConnectionCount")))
                    .tags(allTags)
                    .baseUnit(BaseUnits.CONNECTIONS)
                    .register(registry);
        });
    }

    /**
     * If the Tomcat MBeans already exist, register metrics immediately. Otherwise register an MBean registration listener
     * with the MBeanServer and register metrics when/if the MBeans becomes available.
     */
    private void registerMetricsEventually(String jmxDomain, String namePatternSuffix, BiConsumer<ObjectName, Iterable<Tag>> perObject) {
        if (hasObjectName(jmxDomain)) {
            Set<ObjectName> objectNames = this.mBeanServer.queryNames(getNamePattern(jmxDomain, namePatternSuffix), null);
            if (!objectNames.isEmpty()) {
                // MBeans are present, so we can register metrics now.
                objectNames.forEach(objectName -> perObject.accept(objectName, Tags.concat(tags, nameTag(objectName))));
                return;
            }
        }

        // MBean isn't yet registered, so we'll set up a notification to wait for them to be present and register
        // metrics later.
        NotificationListener notificationListener = new NotificationListener() {
            @Override
            public void handleNotification(Notification notification, Object handback) {
                MBeanServerNotification mBeanServerNotification = (MBeanServerNotification) notification;
                ObjectName objectName = mBeanServerNotification.getMBeanName();
                perObject.accept(objectName, Tags.concat(tags, nameTag(objectName)));
                if (getNamePattern(jmxDomain, namePatternSuffix).isPattern()) {
                    // patterns can match multiple MBeans so don't remove listener
                    return;
                }
                try {
                    mBeanServer.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this);
                    notificationListeners.remove(this);
                } catch (InstanceNotFoundException | ListenerNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
        notificationListeners.add(notificationListener);

        NotificationFilter notificationFilter = notification -> {
            if (!MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(notification.getType())) {
                return false;
            }

            // we can safely downcast now
            ObjectName objectName = ((MBeanServerNotification) notification).getMBeanName();
            return getNamePattern(jmxDomain, namePatternSuffix).apply(objectName);
        };

        try {
            mBeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, notificationListener, notificationFilter, null);
        } catch (InstanceNotFoundException e) {
            // should never happen
            throw new RuntimeException("Error registering MBean listener", e);
        }
    }

    private ObjectName getNamePattern(String domainName, String namePatternSuffix) {
        try {
            return new ObjectName(domainName + namePatternSuffix);
        } catch (MalformedObjectNameException e) {
            // should never happen
            throw new RuntimeException("Error registering Tomcat JMX based metrics", e);
        }
    }

    private boolean hasObjectName(String name) {
        try {
            return this.mBeanServer.queryNames(new ObjectName(name), null).size() == 1;
        } catch (MalformedObjectNameException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Iterable<Tag> nameTag(ObjectName name) {
        String nameTagValue = name.getKeyProperty("name");
        if (nameTagValue != null) {
            return Tags.of("name", nameTagValue.replaceAll("\"", ""));
        }
        return Collections.emptyList();
    }

    private long safeLong(Callable<Object> callable) {
        try {
            return Long.parseLong(callable.call().toString());
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void close() {
        for (NotificationListener notificationListener : this.notificationListeners) {
            try {
                this.mBeanServer.removeNotificationListener(MBeanServerDelegate.DELEGATE_NAME, notificationListener);
            } catch (InstanceNotFoundException | ListenerNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
