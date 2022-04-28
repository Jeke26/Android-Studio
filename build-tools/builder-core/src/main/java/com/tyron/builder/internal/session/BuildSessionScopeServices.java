package com.tyron.builder.internal.session;

import com.tyron.builder.StartParameter;
import com.tyron.builder.api.internal.StartParameterInternal;
import com.tyron.builder.api.internal.changedetection.state.CrossBuildFileHashCache;
import com.tyron.builder.api.internal.file.FileLookup;
import com.tyron.builder.api.internal.file.FileResolver;
import com.tyron.builder.api.internal.project.BuildOperationCrossProjectConfigurator;
import com.tyron.builder.api.internal.project.CrossProjectConfigurator;
import com.tyron.builder.cache.CacheRepository;
import com.tyron.builder.cache.internal.BuildScopeCacheDir;
import com.tyron.builder.cache.internal.InMemoryCacheDecoratorFactory;
import com.tyron.builder.cache.internal.scopes.DefaultBuildTreeScopedCache;
import com.tyron.builder.cache.scopes.BuildTreeScopedCache;
import com.tyron.builder.initialization.BuildCancellationToken;
import com.tyron.builder.initialization.BuildClientMetaData;
import com.tyron.builder.initialization.BuildEventConsumer;
import com.tyron.builder.initialization.BuildRequestMetaData;
import com.tyron.builder.initialization.GradleUserHomeDirProvider;
import com.tyron.builder.initialization.layout.BuildLayout;
import com.tyron.builder.initialization.layout.BuildLayoutConfiguration;
import com.tyron.builder.initialization.layout.BuildLayoutFactory;
import com.tyron.builder.initialization.layout.ProjectCacheDir;
import com.tyron.builder.internal.build.BuildLayoutValidator;
import com.tyron.builder.internal.buildevents.BuildStartedTime;
import com.tyron.builder.internal.classpath.ClassPath;
import com.tyron.builder.internal.event.DefaultListenerManager;
import com.tyron.builder.internal.file.Deleter;
import com.tyron.builder.internal.logging.progress.ProgressLoggerFactory;
import com.tyron.builder.internal.model.StateTransitionControllerFactory;
import com.tyron.builder.internal.operations.BuildOperationExecutor;
import com.tyron.builder.internal.reflect.service.ServiceRegistration;
import com.tyron.builder.internal.service.scopes.PluginServiceRegistry;
import com.tyron.builder.internal.service.scopes.Scopes;
import com.tyron.builder.internal.service.scopes.WorkerSharedBuildSessionScopeServices;
import com.tyron.builder.internal.time.Clock;
import com.tyron.builder.internal.work.DefaultAsyncWorkTracker;

import java.io.Closeable;
import java.util.List;

// accessed reflectively
@SuppressWarnings("unused")
public class BuildSessionScopeServices extends WorkerSharedBuildSessionScopeServices {
    private final StartParameterInternal startParameter;
    private final BuildRequestMetaData buildRequestMetaData;
    private final ClassPath injectedPluginClassPath;
    private final BuildCancellationToken buildCancellationToken;
    private final BuildClientMetaData buildClientMetaData;
    private final BuildEventConsumer buildEventConsumer;

    public BuildSessionScopeServices(StartParameterInternal startParameter, BuildRequestMetaData buildRequestMetaData, ClassPath injectedPluginClassPath, BuildCancellationToken buildCancellationToken, BuildClientMetaData buildClientMetaData, BuildEventConsumer buildEventConsumer) {
        this.startParameter = startParameter;
        this.buildRequestMetaData = buildRequestMetaData;
        this.injectedPluginClassPath = injectedPluginClassPath;
        this.buildCancellationToken = buildCancellationToken;
        this.buildClientMetaData = buildClientMetaData;
        this.buildEventConsumer = buildEventConsumer;
    }

    void configure(ServiceRegistration registration, List<PluginServiceRegistry> pluginServiceRegistries) {
        registration.add(StartParameterInternal.class, startParameter);
        for (PluginServiceRegistry pluginServiceRegistry : pluginServiceRegistries) {
            pluginServiceRegistry.registerBuildSessionServices(registration);
        }
//        registration.add(InjectedPluginClasspath.class, new InjectedPluginClasspath(injectedPluginClassPath));
        registration.add(BuildCancellationToken.class, buildCancellationToken);
        registration.add(BuildRequestMetaData.class, buildRequestMetaData);
        registration.add(BuildClientMetaData.class, buildClientMetaData);
        registration.add(BuildEventConsumer.class, buildEventConsumer);
//        registration.add(CalculatedValueContainerFactory.class);
        registration.add(StateTransitionControllerFactory.class);
        registration.add(BuildLayoutValidator.class);
        registration.add(DefaultAsyncWorkTracker.class);
        // Must be no higher than this scope as needs cache repository services.
//        registration.addProvider(new ScopeIdsServices());

        // from ToolingBuildScopeServices
    }

//    PendingChangesManager createPendingChangesManager(ListenerManager listenerManager) {
//        return new PendingChangesManager(listenerManager);
//    }
//
//    DefaultDeploymentRegistry createDeploymentRegistry(PendingChangesManager pendingChangesManager, BuildOperationExecutor buildOperationExecutor, ObjectFactory objectFactory) {
//        return new DefaultDeploymentRegistry(pendingChangesManager, buildOperationExecutor, objectFactory);
//    }

    DefaultListenerManager createListenerManager(DefaultListenerManager parent) {
        return parent.createChild(Scopes.BuildSession.class);
    }

    CrossProjectConfigurator createCrossProjectConfigurator(BuildOperationExecutor buildOperationExecutor) {
        return new BuildOperationCrossProjectConfigurator(buildOperationExecutor);
    }

    BuildLayout createBuildLayout(BuildLayoutFactory buildLayoutFactory, StartParameter startParameter) {
        return buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(startParameter));
    }

    FileResolver createFileResolver(FileLookup fileLookup, BuildLayout buildLayout) {
        return fileLookup.getFileResolver(buildLayout.getRootDirectory());
    }

    ProjectCacheDir createProjectCacheDir(
            GradleUserHomeDirProvider userHomeDirProvider,
            BuildLayout buildLayout,
            Deleter deleter,
            ProgressLoggerFactory progressLoggerFactory,
            StartParameter startParameter
    ) {
        BuildScopeCacheDir
                cacheDir = new BuildScopeCacheDir(userHomeDirProvider, buildLayout, startParameter);
        return new ProjectCacheDir(cacheDir.getDir(), progressLoggerFactory, deleter);
    }

    BuildTreeScopedCache createBuildTreeScopedCache(ProjectCacheDir projectCacheDir, CacheRepository cacheRepository) {
        return new DefaultBuildTreeScopedCache(projectCacheDir.getDir(), cacheRepository);
    }
//
//    BuildSessionScopeFileTimeStampInspector createFileTimeStampInspector(BuildTreeScopedCache scopedCache) {
//        File workDir = scopedCache.baseDirForCache("fileChanges");
//        return new BuildSessionScopeFileTimeStampInspector(workDir);
//    }
//
//    ScriptSourceHasher createScriptSourceHasher() {
//        return new DefaultScriptSourceHasher();
//    }
//
//    DefaultImmutableAttributesFactory createImmutableAttributesFactory(IsolatableFactory isolatableFactory, NamedObjectInstantiator instantiator) {
//        return new DefaultImmutableAttributesFactory(isolatableFactory, instantiator);
//    }

//    UserScopeId createUserScopeId(PersistentScopeIdLoader persistentScopeIdLoader) {
//        return persistentScopeIdLoader.getUser();
//    }
//
//    protected WorkspaceScopeId createWorkspaceScopeId(PersistentScopeIdLoader persistentScopeIdLoader) {
//        return persistentScopeIdLoader.getWorkspace();
//    }

    BuildStartedTime createBuildStartedTime(Clock clock, BuildRequestMetaData buildRequestMetaData) {
        long currentTime = clock.getCurrentTime();
        return BuildStartedTime.startingAt(Math.min(currentTime, buildRequestMetaData.getStartTime()));
    }

//    FeaturePreviews createExperimentalFeatures() {
//        return new FeaturePreviews();
//    }
//
//    CleanupActionFactory createCleanupActionFactory(BuildOperationExecutor buildOperationExecutor) {
//        return new CleanupActionFactory(buildOperationExecutor);
//    }
//
//    protected ExecFactory decorateExecFactory(ExecFactory execFactory, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, BuildCancellationToken buildCancellationToken, ObjectFactory objectFactory, JavaModuleDetector javaModuleDetector) {
//        return execFactory.forContext()
//                .withFileResolver(fileResolver)
//                .withFileCollectionFactory(fileCollectionFactory)
//                .withInstantiator(instantiator)
//                .withBuildCancellationToken(buildCancellationToken)
//                .withObjectFactory(objectFactory)
//                .withJavaModuleDetector(javaModuleDetector)
//                .build();
//    }

    CrossBuildFileHashCacheWrapper createCrossBuildChecksumCache(BuildTreeScopedCache scopedCache, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        CrossBuildFileHashCache crossBuildCache = new CrossBuildFileHashCache(scopedCache, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.CHECKSUMS);
        return new CrossBuildFileHashCacheWrapper(crossBuildCache);
    }

//    ChecksumService createChecksumService(
//            StringInterner stringInterner,
//            FileSystem fileSystem,
//            CrossBuildFileHashCacheWrapper crossBuildCache,
//            BuildSessionScopeFileTimeStampInspector inspector,
//            FileHasherStatistics.Collector statisticsCollector
//    ) {
//        return new DefaultChecksumService(stringInterner, crossBuildCache.delegate, fileSystem, inspector, statisticsCollector);
//    }

//    UserInputHandler createUserInputHandler(BuildRequestMetaData requestMetaData, OutputEventListenerManager outputEventListenerManager, Clock clock) {
//        if (!requestMetaData.isInteractive()) {
//            return new NonInteractiveUserInputHandler();
//        }
//
//        return new DefaultUserInputHandler(outputEventListenerManager.getBroadcaster(), clock, new DefaultUserInputReader());
//    }

    // Wraps CrossBuildFileHashCache so that it doesn't conflict
    // with other services in different scopes
    static class CrossBuildFileHashCacheWrapper implements Closeable {
        private final CrossBuildFileHashCache delegate;

        private CrossBuildFileHashCacheWrapper(CrossBuildFileHashCache delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
