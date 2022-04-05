package com.tyron.builder.api.execution;

import static com.tyron.builder.cache.FileLockManager.LockMode.OnDemand;
import static com.tyron.builder.cache.internal.filelock.LockOptionsBuilder.mode;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.tyron.builder.api.UncheckedIOException;
import com.tyron.builder.api.execution.plan.ExecutionNodeAccessHierarchies;
import com.tyron.builder.api.initialization.BuildCancellationToken;
import com.tyron.builder.api.internal.changedetection.state.LineEndingNormalizingFileSystemLocationSnapshotHasher;
import com.tyron.builder.api.internal.event.ListenerManager;
import com.tyron.builder.api.internal.execution.ExecutionEngine;
import com.tyron.builder.api.internal.execution.fingerprint.FileCollectionFingerprinter;
import com.tyron.builder.api.internal.execution.fingerprint.FileCollectionFingerprinterRegistry;
import com.tyron.builder.api.internal.execution.fingerprint.FileCollectionSnapshotter;
import com.tyron.builder.api.internal.execution.fingerprint.InputFingerprinter;
import com.tyron.builder.api.internal.execution.fingerprint.impl.DefaultFileCollectionFingerprinterRegistry;
import com.tyron.builder.api.internal.execution.fingerprint.impl.FingerprinterRegistration;
import com.tyron.builder.api.internal.execution.history.ExecutionHistoryStore;
import com.tyron.builder.api.internal.file.FileAccessTracker;
import com.tyron.builder.api.internal.file.FileCollectionFactory;
import com.tyron.builder.api.internal.file.FileOperations;
import com.tyron.builder.api.internal.file.temp.TemporaryFileProvider;
import com.tyron.builder.api.internal.fingerprint.DirectorySensitivity;
import com.tyron.builder.api.internal.fingerprint.LineEndingSensitivity;
import com.tyron.builder.api.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import com.tyron.builder.api.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter;
import com.tyron.builder.api.internal.fingerprint.impl.DefaultInputFingerprinter;
import com.tyron.builder.api.internal.fingerprint.impl.FileCollectionFingerprinterRegistrations;
import com.tyron.builder.api.internal.hash.ChecksumService;
import com.tyron.builder.api.internal.hash.ClassLoaderHierarchyHasher;
import com.tyron.builder.api.internal.id.UniqueId;
import com.tyron.builder.api.internal.operations.BuildOperationExecutor;
import com.tyron.builder.api.internal.operations.CurrentBuildOperationRef;
import com.tyron.builder.api.internal.project.ProjectInternal;
import com.tyron.builder.api.internal.reflect.service.DefaultServiceRegistry;
import com.tyron.builder.api.internal.resources.local.DefaultPathKeyFileStore;
import com.tyron.builder.api.internal.resources.local.PathKeyFileStore;
import com.tyron.builder.api.internal.scopeids.id.BuildInvocationScopeId;
import com.tyron.builder.api.internal.service.scopes.ExecutionGradleServices;
import com.tyron.builder.api.internal.snapshot.ValueSnapshotter;
import com.tyron.builder.api.internal.snapshot.impl.DefaultValueSnapshotter;
import com.tyron.builder.api.internal.snapshot.impl.ValueSnapshotterSerializerRegistry;
import com.tyron.builder.api.internal.tasks.TaskExecuter;
import com.tyron.builder.api.work.AsyncWorkTracker;
import com.tyron.builder.cache.CacheBuilder;
import com.tyron.builder.cache.CacheRepository;
import com.tyron.builder.cache.PersistentCache;
import com.tyron.builder.caching.internal.origin.OriginMetadataFactory;
import com.tyron.builder.caching.local.internal.BuildCacheTempFileStore;
import com.tyron.builder.caching.local.internal.DefaultBuildCacheTempFileStore;
import com.tyron.builder.caching.local.internal.DirectoryBuildCacheService;
import com.tyron.builder.caching.local.internal.LocalBuildCacheService;
import com.tyron.builder.initialization.DefaultBuildCancellationToken;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ProjectExecutionServices extends DefaultServiceRegistry {

    private final ProjectInternal projectInternal;

    public ProjectExecutionServices(ProjectInternal project) {
        super("Configured project services for '" + project.getPath() + "'", project.getServices());

        this.projectInternal = project;

        BuildCancellationToken token = new DefaultBuildCancellationToken();
        add(BuildCancellationToken.class, token);
        add(CurrentBuildOperationRef.class, CurrentBuildOperationRef.instance());

        addProvider(new ExecutionGradleServices(projectInternal));
    }

    ChecksumService createChecksumService() {
        return new ChecksumService() {
            @Override
            public HashCode md5(File file) {
                try {
                    return Hashing.md5().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode sha1(File file) {
                try {
                    return Hashing.sha1().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode sha256(File file) {
                try {
                    return Hashing.sha256().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode sha512(File file) {
                try {
                    return Hashing.sha512().hashBytes(FileUtils.readFileToByteArray(file));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public HashCode hash(File src, String algorithm) {
                switch (algorithm) {
                    case "md5": return md5(src);
                    case "sha1": return sha1(src);
                    case "sha256": return sha256(src);
                    default: return sha512(src);
                }
            }
        };
    }

    TemporaryFileProvider createTemporaryFileProvider() {
        return new TemporaryFileProvider() {
            @Override
            public File newTemporaryFile(String... path) {
                File tempDirectory = FileUtils.getTempDirectory();
                return new File(tempDirectory, "test");
            }

            @Override
            public File createTemporaryFile(String prefix,
                                            @Nullable String suffix,
                                            String... path) {
                try {
                    return File.createTempFile(prefix, suffix);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public File createTemporaryDirectory(String prefix,
                                                 @Nullable String suffix,
                                                 String... path) {
                File tempDirectory = FileUtils.getTempDirectory();
                return new File(tempDirectory, prefix + suffix);
            }
        };
    }

    FileAccessTracker createFileAccessTracker() {
        return file -> {
        };
    }

    PathKeyFileStore createPathKeyFileStore(
            ChecksumService checksumService
    ) {
        return new DefaultPathKeyFileStore(checksumService, projectInternal.getBuildDir());
    }

    LocalBuildCacheService createLocalBuildCacheService(
        CacheRepository cacheRepository,
        ChecksumService checksumService,
        TemporaryFileProvider temporaryFileProvider,
        FileAccessTracker fileAccessTracker
    ) {
        File buildDir = projectInternal.getBuildDir();

        PathKeyFileStore pathKeyFileStore = new DefaultPathKeyFileStore(checksumService, buildDir);
        PersistentCache cache = cacheRepository.cache(buildDir)
                .withDisplayName("Build cache")
                .withLockOptions(mode(OnDemand))
                .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
                .open();
        BuildCacheTempFileStore tempFileStore = new DefaultBuildCacheTempFileStore(temporaryFileProvider);

        return new DirectoryBuildCacheService(
                pathKeyFileStore,
                cache,
                tempFileStore,
                fileAccessTracker,
                ".failed"
        );
    }

    ValueSnapshotter createValueSnapshotter(
            List<ValueSnapshotterSerializerRegistry> valueSnapshotterSerializerRegistryList,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher
    ) {
        return new DefaultValueSnapshotter(
                valueSnapshotterSerializerRegistryList,
                classLoaderHierarchyHasher
        );
    }

    FileSystemLocationSnapshotHasher createFileSystemLocationSnapshotHasher() {
        return LineEndingNormalizingFileSystemLocationSnapshotHasher.DEFAULT;
    }

    FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(
            FileCollectionSnapshotter fileCollectionSnapshotter,
            FileSystemLocationSnapshotHasher fileSystemLocationSnapshotHasher
    ){
        List<FingerprinterRegistration> list = new ArrayList<>();
        list.add(FingerprinterRegistration.registration(
                DirectorySensitivity.DEFAULT,
                LineEndingSensitivity.DEFAULT,
                new AbsolutePathFileCollectionFingerprinter(
                        DirectorySensitivity.DEFAULT,
                        fileCollectionSnapshotter,
                        fileSystemLocationSnapshotHasher
                ))
        );
        return new DefaultFileCollectionFingerprinterRegistry(list);
    }

    InputFingerprinter createInputFingerprinter(
            FileCollectionSnapshotter fileCollectionSnapshotter,
            FileCollectionFingerprinterRegistry fileCollectionFingerprinterRegistry,
            ValueSnapshotter valueSnapshotter
    ) {
        return new DefaultInputFingerprinter(
                fileCollectionSnapshotter,
                fileCollectionFingerprinterRegistry,
                valueSnapshotter
        );
    }

    public TaskExecuter createTaskExecuter(
            ExecutionHistoryStore executionHistoryStore,
            BuildOperationExecutor buildOperationExecutor,
            AsyncWorkTracker asyncWorkTracker,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            ExecutionEngine executionEngine,
            InputFingerprinter inputFingerprinter,
            ListenerManager listenerManager,
            FileCollectionFactory factory,
            FileOperations fileOperations
    ) {
        TaskExecuter executer = new ExecuteActionsTaskExecuter(
                ExecuteActionsTaskExecuter.BuildCacheState.ENABLED,
                executionHistoryStore,
                buildOperationExecutor,
                asyncWorkTracker,
                classLoaderHierarchyHasher,
                executionEngine,
                inputFingerprinter,
                listenerManager,
                factory,
                fileOperations
        );
        executer = new SkipOnlyIfTaskExecuter(executer);
        return executer;
    }

    ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy createInputNodeAccessHierarchies(ExecutionNodeAccessHierarchies hierarchies) {
        return hierarchies.createInputHierarchy();
    }
}
