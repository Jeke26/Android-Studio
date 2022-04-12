package com.tyron.builder.internal.service.scopes;

import com.tyron.builder.api.internal.event.ListenerManager;
import com.tyron.builder.api.internal.file.DefaultDeleter;
import com.tyron.builder.api.internal.file.Deleter;
import com.tyron.builder.api.internal.hash.DefaultStreamHasher;
import com.tyron.builder.api.internal.hash.StreamHasher;
import com.tyron.builder.api.internal.logging.events.OutputEventListener;
import com.tyron.builder.api.internal.logging.progress.ProgressLoggerFactory;
import com.tyron.builder.api.internal.operations.BuildOperation;
import com.tyron.builder.api.internal.operations.BuildOperationIdFactory;
import com.tyron.builder.api.internal.operations.DefaultBuildOperationIdFactory;
import com.tyron.builder.api.internal.provider.DefaultPropertyFactory;
import com.tyron.builder.api.internal.provider.PropertyFactory;
import com.tyron.builder.api.internal.provider.PropertyHost;
import com.tyron.builder.api.internal.reflect.service.scopes.BasicGlobalScopeServices;
import com.tyron.builder.api.internal.time.Clock;
import com.tyron.builder.api.internal.time.Time;
import com.tyron.builder.cache.internal.CrossBuildInMemoryCacheFactory;
import com.tyron.builder.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import com.tyron.builder.internal.logging.progress.DefaultProgressLoggerFactory;
import com.tyron.builder.internal.logging.services.ProgressLoggingBridge;

public class WorkerSharedGlobalScopeServices extends BasicGlobalScopeServices {

    Clock createClock() {
        return Time.clock();
    }

    Deleter createDeleter() {
        return new DefaultDeleter();
    }

    StreamHasher createStreamHasher() {
        return new DefaultStreamHasher();
    }

    PropertyFactory createPropertyFactory(PropertyHost propertyHost) {
        return new DefaultPropertyFactory(propertyHost);
    }

    ProgressLoggerFactory createProgressLoggerFactory(
            OutputEventListener outputEventListener,
            Clock clock,
            BuildOperationIdFactory buildOperationIdFactory
    ) {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(outputEventListener), clock, buildOperationIdFactory);
    }

    CrossBuildInMemoryCacheFactory createCrossBuildInMemoryFactory(
            ListenerManager listenerManager
    ) {
        return new DefaultCrossBuildInMemoryCacheFactory(listenerManager);
    }

    BuildOperationIdFactory createBuildOperationFactory() {
        return new DefaultBuildOperationIdFactory();
    }
}
