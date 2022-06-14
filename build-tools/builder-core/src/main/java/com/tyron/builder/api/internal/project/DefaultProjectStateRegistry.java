package com.tyron.builder.api.internal.project;

import com.google.common.collect.Maps;
import com.tyron.builder.api.BuildProject;
import com.tyron.builder.api.artifacts.component.BuildIdentifier;
import com.tyron.builder.api.artifacts.component.ProjectComponentIdentifier;
import com.tyron.builder.api.internal.artifacts.DefaultProjectComponentIdentifier;
import com.tyron.builder.api.internal.initialization.ClassLoaderScope;
import com.tyron.builder.initialization.DefaultProjectDescriptor;
import com.tyron.builder.internal.Describables;
import com.tyron.builder.internal.DisplayName;
import com.tyron.builder.internal.Factories;
import com.tyron.builder.internal.Factory;
import com.tyron.builder.internal.build.BuildProjectRegistry;
import com.tyron.builder.internal.build.BuildState;
import com.tyron.builder.internal.model.CalculatedModelValue;
import com.tyron.builder.internal.model.ModelContainer;
import com.tyron.builder.internal.resources.ProjectLeaseRegistry;
import com.tyron.builder.internal.resources.ResourceLock;
import com.tyron.builder.internal.work.WorkerLeaseService;
import com.tyron.builder.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultProjectStateRegistry implements ProjectStateRegistry {
    private final WorkerLeaseService workerLeaseService;
    private final Object lock = new Object();
    private final Map<Path, ProjectStateImpl> projectsByPath = Maps.newLinkedHashMap();
    private final Map<ProjectComponentIdentifier, ProjectStateImpl> projectsById = Maps.newHashMap();
    private final Map<BuildIdentifier, DefaultBuildProjectRegistry> projectsByBuild = Maps.newHashMap();

    public DefaultProjectStateRegistry(WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public void registerProjects(BuildState owner, ProjectRegistry<DefaultProjectDescriptor> projectRegistry) {
        Set<DefaultProjectDescriptor> allProjects = projectRegistry.getAllProjects();
        synchronized (lock) {
            DefaultBuildProjectRegistry buildProjectRegistry = getBuildProjectRegistry(owner);
            if (!buildProjectRegistry.projectsByPath.isEmpty()) {
                throw new IllegalStateException("Projects for " + owner.getDisplayName() + " have already been registered.");
            }
            for (DefaultProjectDescriptor descriptor : allProjects) {
                addProject(owner, buildProjectRegistry, descriptor);
            }
        }
    }

    private DefaultBuildProjectRegistry getBuildProjectRegistry(BuildState owner) {
        DefaultBuildProjectRegistry buildProjectRegistry = projectsByBuild.get(owner.getBuildIdentifier());
        if (buildProjectRegistry == null) {
            buildProjectRegistry = new DefaultBuildProjectRegistry(owner);
            projectsByBuild.put(owner.getBuildIdentifier(), buildProjectRegistry);
        }
        return buildProjectRegistry;
    }

    @Override
    public ProjectStateUnk registerProject(BuildState owner, DefaultProjectDescriptor projectDescriptor) {
        synchronized (lock) {
            DefaultBuildProjectRegistry buildProjectRegistry = getBuildProjectRegistry(owner);
            return addProject(owner, buildProjectRegistry, projectDescriptor);
        }
    }

    private ProjectStateUnk addProject(BuildState owner, DefaultBuildProjectRegistry projectRegistry, DefaultProjectDescriptor descriptor) {
        Path projectPath = descriptor.path();
        Path identityPath = owner.calculateIdentityPathForProject(projectPath);
        String name = descriptor.getName();
        ProjectComponentIdentifier projectIdentifier = new DefaultProjectComponentIdentifier(owner.getBuildIdentifier(), identityPath, projectPath, name);
        IProjectFactory projectFactory = owner.getMutableModel().getServices().get(IProjectFactory.class);
        ProjectStateImpl projectState = new ProjectStateImpl(owner, identityPath, projectPath, descriptor.getName(), projectIdentifier, descriptor, projectFactory);
        projectsByPath.put(identityPath, projectState);
        projectsById.put(projectIdentifier, projectState);
        projectRegistry.add(projectPath, projectState);
        return projectState;
    }

    @Override
    public Collection<ProjectStateImpl> getAllProjects() {
        synchronized (lock) {
            return projectsByPath.values();
        }
    }

    // TODO - can kill this method, as the caller can use ProjectInternal.getOwner() instead
    @Override
    public ProjectStateUnk stateFor(BuildProject project) {
        return ((ProjectInternal) project).getOwner();
    }

    @Override
    public ProjectStateUnk stateFor(ProjectComponentIdentifier identifier) {
        synchronized (lock) {
            ProjectStateImpl projectState = projectsById.get(identifier);
            if (projectState == null) {
                throw new IllegalArgumentException(identifier.getDisplayName() + " not found.");
            }
            return projectState;
        }
    }

    @Override
    public BuildProjectRegistry projectsFor(BuildIdentifier buildIdentifier) throws IllegalArgumentException {
        synchronized (lock) {
            BuildProjectRegistry registry = projectsByBuild.get(buildIdentifier);
            if (registry == null) {
                throw new IllegalArgumentException("Projects for " + buildIdentifier + " have not been registered yet.");
            }
            return registry;
        }
    }

    @Override
    public void withMutableStateOfAllProjects(Runnable runnable) {
        withMutableStateOfAllProjects(Factories.toFactory(runnable));
    }

    @Override
    public <T> T withMutableStateOfAllProjects(Factory<T> factory) {
        ResourceLock allProjectsLock = workerLeaseService.getAllProjectsLock();
        Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
        if (locks.contains(allProjectsLock)) {
            // Holds the lock so run the action
            return factory.create();
        }
        return workerLeaseService.withLocks(Collections.singletonList(allProjectsLock), () -> workerLeaseService.withoutLocks(locks, factory));
    }

    @Override
    public <T> T allowUncontrolledAccessToAnyProject(Factory<T> factory) {
        return workerLeaseService.allowUncontrolledAccessToAnyProject(factory);
    }

    private static class DefaultBuildProjectRegistry implements BuildProjectRegistry {
        private final BuildState owner;
        private final Map<Path, ProjectStateImpl> projectsByPath = Maps.newLinkedHashMap();

        public DefaultBuildProjectRegistry(BuildState owner) {
            this.owner = owner;
        }

        public void add(Path projectPath, ProjectStateImpl projectState) {
            projectsByPath.put(projectPath, projectState);
        }

        @Override
        public ProjectStateUnk getRootProject() {
            return getProject(Path.ROOT);
        }

        @Override
        public ProjectStateUnk getProject(Path projectPath) {
            ProjectStateImpl projectState = projectsByPath.get(projectPath);
            if (projectState == null) {
                throw new IllegalArgumentException("Project with path '" + projectPath + "' not found in " + owner.getDisplayName() + ".");
            }
            return projectState;
        }

        @Nullable
        @Override
        public ProjectStateUnk findProject(Path projectPath) {
            return projectsByPath.get(projectPath);
        }

        @Override
        public Set<? extends ProjectStateUnk> getAllProjects() {
            TreeSet<ProjectStateUnk> projects = new TreeSet<>(Comparator.comparing(ProjectStateUnk::getIdentityPath));
            projects.addAll(projectsByPath.values());
            return projects;
        }
    }

    private class ProjectStateImpl implements ProjectStateUnk {
        private final Path projectPath;
        private final String projectName;
        private final ProjectComponentIdentifier identifier;
        private final DefaultProjectDescriptor descriptor;
        private final IProjectFactory projectFactory;
        private final BuildState owner;
        private final Path identityPath;
        private final ResourceLock projectLock;
        private final ResourceLock taskLock;
        private final Set<Thread> canDoAnythingToThisProject = new CopyOnWriteArraySet<>();
        private ProjectInternal project;

        ProjectStateImpl(BuildState owner, Path identityPath, Path projectPath, String projectName, ProjectComponentIdentifier identifier, DefaultProjectDescriptor descriptor, IProjectFactory projectFactory) {
            this.owner = owner;
            this.identityPath = identityPath;
            this.projectPath = projectPath;
            this.projectName = projectName;
            this.identifier = identifier;
            this.descriptor = descriptor;
            this.projectFactory = projectFactory;
            this.projectLock = workerLeaseService.getProjectLock(owner.getIdentityPath(), identityPath);
            this.taskLock = workerLeaseService.getTaskExecutionLock(owner.getIdentityPath(), identityPath);
        }

        @Override
        public DisplayName getDisplayName() {
            if (projectPath.equals(Path.ROOT)) {
                return Describables.quoted("root project", projectName);
            }
            return Describables.of(identifier);
        }

        @Override
        public String toString() {
            return getDisplayName().getDisplayName();
        }

        @Override
        public BuildState getOwner() {
            return owner;
        }

        @Nullable
        @Override
        public ProjectStateUnk getParent() {
            return identityPath.getParent() == null ? null : projectsByPath.get(identityPath.getParent());
        }

        @Nullable
        @Override
        public ProjectStateUnk getBuildParent() {
            if (descriptor.getParent() != null) {
                // Identity path of parent can be different to identity path parent, if the names are tweaked in the settings file
                // Ideally they would be exactly the same, always
                Path parentPath = owner.calculateIdentityPathForProject(descriptor.getParent().path());
                ProjectStateImpl parentState = projectsByPath.get(parentPath);
                if (parentState == null) {
                    throw new IllegalStateException("Parent project " + parentPath + " is not registered for project " + identityPath);
                }
                return parentState;
            } else {
                return null;
            }
        }

        @Override
        public Set<ProjectStateUnk> getChildProjects() {
            Set<ProjectStateUnk> children = new TreeSet<>(Comparator.comparing(ProjectStateUnk::getIdentityPath));
            for (DefaultProjectDescriptor child : descriptor.children()) {
                children.add(projectsByPath.get(owner.calculateIdentityPathForProject(child.path())));
            }
            return children;
        }

        @Override
        public String getName() {
            return projectName;
        }

        @Override
        public Path getIdentityPath() {
            return identityPath;
        }

        @Override
        public Path getProjectPath() {
            return projectPath;
        }

        @Override
        public File getProjectDir() {
            return descriptor.getProjectDir();
        }

        @Override
        public void createMutableModel(ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope) {
            synchronized (this) {
                if (this.project != null) {
                    throw new IllegalStateException(String.format("The project object for project %s has already been attached.", getIdentityPath()));
                }

                ProjectStateUnk parent = getBuildParent();
                ProjectInternal parentModel = parent == null ? null : parent.getMutableModel();
                this.project = projectFactory.createProject(owner.getMutableModel(), descriptor, this, parentModel, selfClassLoaderScope, baseClassLoaderScope);
            }
        }

        @Override
        public ProjectInternal getMutableModel() {
            synchronized (this) {
                if (project == null) {
                    throw new IllegalStateException(String.format("The project object for project %s has not been attached yet.", getIdentityPath()));
                }
                return project;
            }
        }

        @Override
        public void ensureConfigured() {
            // Need to configure intermediate parent projects for configure-on-demand
            ProjectStateUnk parent = getBuildParent();
            if (parent != null) {
                parent.ensureConfigured();
            }
            synchronized (this) {
                getMutableModel().evaluate();
            }
        }

        @Override
        public void ensureTasksDiscovered() {
            synchronized (this) {
                ProjectInternal project = getMutableModel();
                project.evaluate();
                project.getTasks().discoverTasks();
                project.bindAllModelRules();
            }
        }

        @Override
        public ProjectComponentIdentifier getComponentIdentifier() {
            return identifier;
        }

        @Override
        public ResourceLock getAccessLock() {
            return projectLock;
        }

        @Override
        public ResourceLock getTaskExecutionLock() {
            return taskLock;
        }

        @Override
        public void applyToMutableState(Consumer<? super ProjectInternal> action) {
            fromMutableState(p -> {
                action.accept(p);
                return null;
            });
        }

        @Override
        public <S> S fromMutableState(Function<? super ProjectInternal, ? extends S> function) {
            Thread currentThread = Thread.currentThread();
            if (workerLeaseService.isAllowedUncontrolledAccessToAnyProject() || canDoAnythingToThisProject.contains(currentThread)) {
                // Current thread is allowed to access anything at any time, so run the function
                return function.apply(getMutableModel());
            }

            Collection<? extends ResourceLock> currentLocks = workerLeaseService.getCurrentProjectLocks();
            if (currentLocks.contains(projectLock) || currentLocks.contains(workerLeaseService.getAllProjectsLock())) {
                // if we already hold the project lock for this project
                if (currentLocks.size() == 1) {
                    // the lock for this project is the only lock we hold, can run the function
                    return function.apply(getMutableModel());
                } else {
                    throw new IllegalStateException("Current thread holds more than one project lock. It should hold only one project lock at any given time.");
                }
            } else {
                // we don't currently hold the project lock
                if (!currentLocks.isEmpty()) {
                    // we hold other project locks that we should release first
                    return workerLeaseService.withoutLocks(currentLocks, () -> withProjectLock(projectLock, function));
                } else {
                    // we just need to get the lock for this project
                    return withProjectLock(projectLock, function);
                }
            }
        }

        @Override
        public <S> S forceAccessToMutableState(Function<? super ProjectInternal, ? extends S> factory) {
            Thread currentThread = Thread.currentThread();
            boolean added = canDoAnythingToThisProject.add(currentThread);
            try {
                return factory.apply(getMutableModel());
            } finally {
                if (added) {
                    canDoAnythingToThisProject.remove(currentThread);
                }
            }
        }

        private <S> S withProjectLock(ResourceLock projectLock, final Function<? super ProjectInternal, ? extends S> function) {
            return workerLeaseService.withLocks(Collections.singleton(projectLock), () -> function.apply(getMutableModel()));
        }

        @Override
        public boolean hasMutableState() {
            Thread currentThread = Thread.currentThread();
            if (canDoAnythingToThisProject.contains(currentThread) || workerLeaseService.isAllowedUncontrolledAccessToAnyProject()) {
                return true;
            }
            Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
            return locks.contains(projectLock) || locks.contains(workerLeaseService.getAllProjectsLock());
        }

        @Override
        public <T> CalculatedModelValue<T> newCalculatedValue(@Nullable T initialValue) {
            return new CalculatedModelValueImpl<>(this, workerLeaseService, initialValue);
        }
    }

    private static class CalculatedModelValueImpl<T> implements CalculatedModelValue<T> {
        private final ProjectLeaseRegistry projectLeaseRegistry;
        private final ModelContainer<?> owner;
        private final ReentrantLock lock = new ReentrantLock();
        private volatile T value;

        public CalculatedModelValueImpl(ProjectStateImpl owner, WorkerLeaseService projectLeaseRegistry, @Nullable T initialValue) {
            this.projectLeaseRegistry = projectLeaseRegistry;
            this.value = initialValue;
            this.owner = owner;
        }

        @Override
        public T get() throws IllegalStateException {
            T currentValue = getOrNull();
            if (currentValue == null) {
                throw new IllegalStateException("No calculated value is available for " + owner);
            }
            return currentValue;
        }

        @Override
        public T getOrNull() {
            // Grab the current value, ignore updates that may be happening
            return value;
        }

        @Override
        public void set(T newValue) {
            assertCanMutate();
            value = newValue;
        }

        @Override
        public T update(Function<T, T> updateFunction) {
            acquireUpdateLock();
            try {
                T newValue = updateFunction.apply(value);
                value = newValue;
                return newValue;
            } finally {
                releaseUpdateLock();
            }
        }

        private void acquireUpdateLock() {
            // It's important that we do not block waiting for the lock while holding the project mutation lock.
            // Doing so can lead to deadlocks.

            assertCanMutate();

            if (lock.tryLock()) {
                // Update lock was not contended, can keep holding the project locks
                return;
            }

            // Another thread holds the update lock, release the project locks and wait for the other thread to finish the update
            projectLeaseRegistry.blocking(lock::lock);
        }

        private void assertCanMutate() {
            if (!owner.hasMutableState()) {
                throw new IllegalStateException("Current thread does not hold the state lock for " + owner);
            }
        }

        private void releaseUpdateLock() {
            lock.unlock();
        }
    }
}
