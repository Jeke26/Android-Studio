package com.tyron.builder.api.internal.project;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.tyron.builder.api.Action;
import com.tyron.builder.api.Describable;
import com.tyron.builder.api.InvalidUserDataException;
import com.tyron.builder.api.PathValidation;
import com.tyron.builder.api.ProjectEvaluationListener;
import com.tyron.builder.api.Task;
import com.tyron.builder.api.UnknownProjectException;
import com.tyron.builder.api.artifacts.Dependency;
import com.tyron.builder.api.artifacts.ExternalModuleDependency;
import com.tyron.builder.api.artifacts.MinimalExternalModuleDependency;
import com.tyron.builder.api.artifacts.dsl.ComponentMetadataHandler;
import com.tyron.builder.api.artifacts.dsl.ComponentModuleMetadataHandler;
import com.tyron.builder.api.artifacts.dsl.DependencyConstraintHandler;
import com.tyron.builder.api.artifacts.dsl.DependencyHandler;
import com.tyron.builder.api.artifacts.dsl.ExternalModuleDependencyVariantSpec;
import com.tyron.builder.api.artifacts.query.ArtifactResolutionQuery;
import com.tyron.builder.api.artifacts.transform.TransformAction;
import com.tyron.builder.api.artifacts.transform.TransformParameters;
import com.tyron.builder.api.artifacts.transform.TransformSpec;
import com.tyron.builder.api.artifacts.transform.VariantTransform;
import com.tyron.builder.api.artifacts.type.ArtifactTypeContainer;
import com.tyron.builder.api.attributes.AttributesSchema;
import com.tyron.builder.api.initialization.dsl.ScriptHandler;
import com.tyron.builder.api.internal.DynamicObjectAware;
import com.tyron.builder.api.internal.ProcessOperations;
import com.tyron.builder.api.internal.artifacts.Module;
import com.tyron.builder.api.internal.initialization.ClassLoaderScope;
import com.tyron.builder.api.internal.initialization.ScriptHandlerFactory;
import com.tyron.builder.api.internal.initialization.ScriptHandlerInternal;
import com.tyron.builder.api.internal.plugins.DefaultObjectConfigurationAction;
import com.tyron.builder.api.internal.plugins.PluginManagerInternal;
import com.tyron.builder.api.logging.Logger;
import com.tyron.builder.api.logging.Logging;
import com.tyron.builder.api.logging.LoggingManager;
import com.tyron.builder.api.plugins.ExtensionContainer;
import com.tyron.builder.api.plugins.ObjectConfigurationAction;
import com.tyron.builder.api.plugins.PluginContainer;
import com.tyron.builder.api.plugins.PluginManager;
import com.tyron.builder.api.provider.ProviderConvertible;
import com.tyron.builder.configuration.ConfigurationTargetIdentifier;
import com.tyron.builder.configuration.ScriptPluginFactory;
import com.tyron.builder.configuration.project.ProjectEvaluator;
import com.tyron.builder.api.file.ConfigurableFileTree;
import com.tyron.builder.api.file.FileTree;
import com.tyron.builder.api.internal.GradleInternal;
import com.tyron.builder.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import com.tyron.builder.groovy.scripts.ScriptSource;
import com.tyron.builder.internal.Cast;
import com.tyron.builder.internal.Factory;
import com.tyron.builder.internal.deprecation.DeprecationLogger;
import com.tyron.builder.internal.event.ListenerBroadcast;
import com.tyron.builder.api.file.ConfigurableFileCollection;
import com.tyron.builder.api.file.DeleteSpec;
import com.tyron.builder.api.internal.file.FileLookup;
import com.tyron.builder.api.internal.file.FileOperations;
import com.tyron.builder.api.internal.file.FileResolver;
import com.tyron.builder.internal.extensibility.ExtensibleDynamicObject;
import com.tyron.builder.internal.instantiation.InstanceGenerator;
import com.tyron.builder.api.internal.plugins.ExtensionContainerInternal;
import com.tyron.builder.internal.instantiation.InstantiatorFactory;
import com.tyron.builder.internal.logging.LoggingManagerInternal;
import com.tyron.builder.internal.logging.StandardOutputCapture;
import com.tyron.builder.internal.metaobject.DynamicObject;
import com.tyron.builder.internal.model.ModelContainer;
import com.tyron.builder.internal.reflect.DirectInstantiator;
import com.tyron.builder.internal.reflect.service.ServiceRegistry;
import com.tyron.builder.internal.resource.TextUriResourceLoader;
import com.tyron.builder.internal.service.scopes.ServiceRegistryFactory;
import com.tyron.builder.api.internal.tasks.TaskContainerInternal;
import com.tyron.builder.api.model.ObjectFactory;
import com.tyron.builder.api.plugins.Convention;
import com.tyron.builder.api.BuildProject;
import com.tyron.builder.api.provider.Property;
import com.tyron.builder.api.provider.Provider;
import com.tyron.builder.api.reflect.ObjectInstantiationException;
import com.tyron.builder.api.tasks.WorkResult;
import com.tyron.builder.util.ConfigureUtil;
import com.tyron.builder.util.Path;
import com.tyron.builder.internal.extensibility.DefaultConvention;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import groovy.lang.Closure;
import groovy.lang.Script;

public abstract class DefaultProject extends AbstractPluginAware implements ProjectInternal, DynamicObjectAware {

    private static final Logger BUILD_LOGGER = Logging.getLogger(BuildProject.class);

    private final ProjectStateUnk owner;
    private final ProjectInternal rootProject;
    private final File projectDir;
    private final File buildFile;
    private final ProjectInternal parent;
    private final String name;
    private final ProjectStateInternal state;
    private final int depth;
    private final ServiceRegistry services;
    private final TaskContainerInternal taskContainer;
    private final GradleInternal gradle;
    private final ScriptSource buildScriptSource;
    private final ClassLoaderScope classLoaderScope;
    private final ClassLoaderScope baseClassLoaderScope;
    private final ExtensibleDynamicObject extensibleDynamicObject;
    private String description;
    private Object group;
    private Object version;
    private List<String> defaultTasks = new ArrayList<>();
    private Property<Object> status;
    private File buildDir;
    private ListenerBroadcast<ProjectEvaluationListener> evaluationListener = newProjectEvaluationListenerBroadcast();

    public DefaultProject(String name,
                          @Nullable ProjectInternal parent,
                          File projectDir,
                          File buildFile,
                          ScriptSource buildScriptSource,
                          GradleInternal gradle,
                          ProjectStateUnk owner,
                          ServiceRegistryFactory serviceRegistryFactory,
                          ClassLoaderScope selfClassLoaderScope,
                          ClassLoaderScope baseClassLoaderScope
    ) {
        this.owner = owner;
        this.classLoaderScope = selfClassLoaderScope;
        this.baseClassLoaderScope = baseClassLoaderScope;
        this.rootProject = parent != null ? parent.getRootProject() : this;
        this.projectDir = projectDir;
        this.buildFile = buildFile;
        this.parent = parent;
        this.name = name;
        this.state = new ProjectStateInternal();
        this.buildScriptSource = buildScriptSource;
        this.gradle = gradle;

        services = serviceRegistryFactory.createFor(this);
        taskContainer = services.get(TaskContainerInternal.class);

        extensibleDynamicObject = new ExtensibleDynamicObject(this, BuildProject.class, services.get(
                InstantiatorFactory.class).decorateLenient(services));
        if (parent != null) {
            extensibleDynamicObject.setParent(parent.getInheritedScope());
        }
        extensibleDynamicObject.addObject(taskContainer.getTasksAsDynamicObject(), ExtensibleDynamicObject.Location.AfterConvention);

        if (parent == null) {
            depth = 0;
        } else {
            depth = parent.getDepth() + 1;
        }

        setBuildDir(new File(projectDir, "build"));
    }

    private ListenerBroadcast<ProjectEvaluationListener> newProjectEvaluationListenerBroadcast() {
        return new ListenerBroadcast<>(ProjectEvaluationListener.class);
    }

    private String instanceDescriptorFor(String path) {
        return "Project.<init>." + path + "()";
    }

    @Override
    public ProjectInternal getRootProject() {
        return getRootProject(this);
    }

    @Override
    public ProjectInternal getRootProject(ProjectInternal referrer) {
        return getCrossProjectModelAccess().access(referrer, rootProject);
    }

    @Override
    public Convention getConvention() {
        return extensibleDynamicObject.getConvention();
    }

    @Override
    public BuildProject evaluate() {
        getProjectEvaluator().evaluate(this, state);
        return this;
    }

    @Inject
    protected abstract ProjectEvaluator getProjectEvaluator();

    @Override
    public ProjectInternal bindAllModelRules() {
        return null;
    }

    @Override
    public TaskContainerInternal getTasks() {
        return taskContainer;
    }

    @Override
    public ScriptSource getBuildScriptSource() {
        return buildScriptSource;
    }

    @Override
    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return evaluationListener.getSource();
    }

    @Override
    public void beforeEvaluate(Action<? super BuildProject> action) {

    }

    @Override
    public void afterEvaluate(Action<? super BuildProject> action) {

    }

    @Override
    public boolean hasProperty(String propertyName) {
        return false;
    }

    @Override
    public Map<String, ?> getProperties() {
        return DeprecationLogger.whileDisabled(new Factory<Map<String, ?>>() {
            @Nullable
            @Override
            public Map<String, ?> create() {
                return extensibleDynamicObject.getProperties();
            }
        });
    }

    @Nullable
    @Override
    public Object property(String propertyName) {
        return null;
    }

    @Nullable
    @Override
    public Object findProperty(String propertyName) {
        return null;
    }

    @Override
    public Logger getLogger() {
        return BUILD_LOGGER;
    }

    private LoggingManagerInternal loggingManagerInternal;

    @Override
    public LoggingManagerInternal getLogging() {
        if (loggingManagerInternal == null) {
            loggingManagerInternal = services.get(LoggingManagerInternal.class);
        }
        return loggingManagerInternal;
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return getLogging();
    }

    @Override
    public <T> Iterable<T> configure(Iterable<T> objects, Action<? super T> configureAction) {
        return null;
    }

    @Override
    public ProjectInternal project(String path) throws UnknownProjectException {
        return project(this, path);
    }

    @Override
    public BuildProject project(String path, Action<? super BuildProject> configureAction) {
        ProjectInternal project = project(path);
        configureAction.execute(project);
        return project;
    }

    @Override
    public ProjectEvaluationListener stepEvaluationListener(ProjectEvaluationListener listener, Action<ProjectEvaluationListener> step) {
        ListenerBroadcast<ProjectEvaluationListener> original = this.evaluationListener;
        ListenerBroadcast<ProjectEvaluationListener> nextBatch = newProjectEvaluationListenerBroadcast();
        this.evaluationListener = nextBatch;
        try {
            step.execute(listener);
        } finally {
            this.evaluationListener = original;
        }
        return nextBatch.isEmpty()
                ? null
                : nextBatch.getSource();
    }

    @Override
    public Map<BuildProject, Set<Task>> getAllTasks(boolean recursive) {
        final Map<BuildProject, Set<Task>> foundTargets = new TreeMap<>();
        Action<BuildProject> action = project -> {
            // Don't force evaluation of rules here, let the task container do what it needs to
            ((ProjectInternal) project).getOwner().ensureTasksDiscovered();

            foundTargets.put(project, new TreeSet<>(project.getTasks()));
        };
        if (recursive) {
            allprojects(action);
        } else {
            action.execute(this);
        }
        return foundTargets;
    }

    @Override
    public Set<Task> getTasksByName(final String name, boolean recursive) {
        if (Strings.isNullOrEmpty(name)) {
            throw new InvalidUserDataException("Name is not specified!");
        }
        final Set<Task> foundTasks = new HashSet<>();
        Action<BuildProject> action = project -> {
            // Don't force evaluation of rules here, let the task container do what it needs to
            ((ProjectInternal) project).getOwner().ensureTasksDiscovered();

            Task task = project.getTasks().findByName(name);
            if (task != null) {
                foundTasks.add(task);
            }
        };
        if (recursive) {
            allprojects(action);
        } else {
            action.execute(this);
        }
        return foundTasks;
    }

    @Override
    public File getProjectDir() {
        return owner.getProjectDir();
    }

    @Override
    @Inject
    public abstract FileOperations getFileOperations();

    private FileResolver getProjectFileResolver() {
        FileLookup fileLookup = getServices().get(FileLookup.class);
        return fileLookup.getFileResolver(getProjectDir());
    }

    @Override
    public File file(Object path) {
        return file(path, PathValidation.NONE);
    }


    @Override
    public File file(Object path, PathValidation validation) throws InvalidUserDataException {
        return getProjectFileResolver().resolve(path, validation);
    }

    @Override
    public URI uri(Object path) {
        return getProjectFileResolver().resolveUri(path);
    }

    @Override
    public String relativePath(Object path) {
        return getProjectFileResolver().resolveAsRelativePath(path);
    }

    @Override
    public ConfigurableFileCollection files(Object... paths) {
        return getObjects().fileCollection().from(paths);
    }

    @Override
    public ConfigurableFileCollection files(Object paths,
                                            Action<? super ConfigurableFileCollection> configureAction) {
        ConfigurableFileCollection files = files(paths);
        configureAction.execute(files);
        return files;
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir) {
        return getObjects().fileTree().from(baseDir);
    }

    @Override
    public ConfigurableFileTree fileTree(Object baseDir,
                                         Action<? super ConfigurableFileTree> configureAction) {
        ConfigurableFileTree files = fileTree(baseDir);
        configureAction.execute(files);
        return files;
    }

    @Override
    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return null;
    }

    @Override
    public FileTree zipTree(Object zipPath) {
        return null;
    }

    @Override
    public FileTree tarTree(Object tarPath) {
        return null;
    }

    @Override
    public <T> Provider<T> provider(Callable<T> value) {
        return null;
    }

    @Override
    public ObjectFactory getObjects() {
        return getServices().get(ObjectFactory.class);
    }

    @Override
    public void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript());
    }

    @Override
    public File mkdir(Object path) {
        return getFileOperations().mkdir(path);
    }

    @Override
    public boolean delete(Object... paths) {
        return getFileOperations().delete(paths);
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        throw new UnsupportedOperationException("This method is not yet supported.");
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer,
                                   String path) throws UnknownProjectException {
        ProjectInternal project = getCrossProjectModelAccess().findProject(referrer, this, path);
        if (project == null) {
            throw new UnknownProjectException(String.format("Project with path '%s' could not be found in %s.", path, this));
        }
        return project;
    }

    @Override
    public ProjectInternal project(ProjectInternal referrer, String path, Action<? super BuildProject> configureAction) {
        ProjectInternal project = project(referrer, path);
        getProjectConfigurator().project(project, configureAction);
        return project;
    }

    @Nullable
    @Override
    public ProjectInternal findProject(String path) {
        return findProject(this, path);
    }

    @Nullable
    @Override
    public ProjectInternal findProject(ProjectInternal referrer, String path) {
        return getCrossProjectModelAccess().findProject(referrer, this, path);
    }


    @Override
    public void subprojects(Action<? super BuildProject> action) {
        subprojects(this, action);
    }

    @Override
    public void subprojects(ProjectInternal referrer, Action<? super BuildProject> configureAction) {
        getProjectConfigurator().subprojects(getCrossProjectModelAccess().getSubprojects(referrer, this), configureAction);
    }

    @Override
    public Set<? extends ProjectInternal> getSubprojects(ProjectInternal referrer) {
        return getCrossProjectModelAccess().getSubprojects(referrer, this);
    }

    @Override
    public Set<BuildProject> getSubprojects() {
        return Cast.uncheckedCast(getSubprojects(this));
    }

    @Override
    public Set<? extends ProjectInternal> getAllprojects(ProjectInternal referrer) {
        return getCrossProjectModelAccess().getAllprojects(referrer, this);
    }

    @Override
    public void allprojects(Action<? super BuildProject> action) {
        allprojects(this, action);
    }

    @Override
    public void allprojects(ProjectInternal referrer,
                            Action<? super BuildProject> configureAction) {
        getProjectConfigurator().allprojects(getCrossProjectModelAccess().getAllprojects(referrer, this), configureAction);
    }

    @Override
    public ProjectStateUnk getOwner() {
        return owner;
    }

    @Override
    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    public ProcessOperations getProcessOperations() {
        return null;
    }

    @Override
    public File getBuildFile() {
        return buildFile;
    }

    @Override
    public File getRootDir() {
        return rootProject.getProjectDir();
    }

    @Override
    public File getBuildDir() {
        return buildDir;
    }

    @Override
    public void setBuildDir(File path) {
        this.buildDir = path;
    }

    @Override
    public void setBuildDir(Object path) {
        setBuildDir(file(path));
    }

    @Override
    public DependencyHandler getDependencies() {
        return null;
    }

    @Override
    public void dependencies(Closure configureClosure) {
        BUILD_LOGGER.warn("Dependencies block is not yet supported.");
    }

    @Inject
    @Override
    public abstract ScriptHandlerInternal getBuildscript();

    @Override
    public ProjectInternal getParent() {
        return getParent(this);
    }


    @Nullable
    @Override
    public ProjectInternal getParent(ProjectInternal referrer) {
        if (parent == null) {
            return null;
        }
        return getCrossProjectModelAccess().access(referrer, parent);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        StringBuilder builder = new StringBuilder();
        if (parent == null && gradle.isRootBuild()) {
            builder.append("root project '");
            builder.append(name);
            builder.append('\'');
        } else {
            builder.append("project '");
            builder.append(getIdentityPath());
            builder.append("'");
        }
        return builder.toString();
    }

    @Override
    public DynamicObject getAsDynamicObject() {
        return extensibleDynamicObject;
    }

    @Override
    public DynamicObject getInheritedScope() {
        return extensibleDynamicObject.getInheritable();
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Object getGroup() {
        if (group != null) {
            return group;
        } else if (this == rootProject) {
            return "";
        }
        group = rootProject.getName() +
                (getParent() == rootProject ? "" : "." + getParent().getPath().substring(1).replace(':', '.'));
        return group;
    }

    @Override
    public void setGroup(Object group) {
        this.group = group;
    }

    @Override
    public Object getVersion() {
        return version == null ? DEFAULT_VERSION : version;
    }

    @Override
    public void setVersion(Object version) {
        this.version = version;
    }

    @Override
    public Object getStatus() {
        return getInternalStatus().get();
    }

    @Override
    public void setStatus(Object s) {
        getInternalStatus().set(s);
    }

    public Property<Object> getInternalStatus() {
        if (status == null) {
            status = getObjects().property(Object.class).convention(DEFAULT_STATUS);
        }
        return status;
    }

    @Override
    public Map<String, BuildProject> getChildProjects() {
        Map<String, BuildProject> childProjects = Maps.newTreeMap();
        for (ProjectStateUnk project : owner.getChildProjects()) {
            childProjects.put(project.getName(), project.getMutableModel());
        }
        return childProjects;
    }

    @Override
    public void setProperty(String name, @Nullable Object value) {

    }

    @Override
    public ProjectInternal getProject() {
        return this;
    }

    @Override
    public Set<BuildProject> getAllprojects() {
        return Cast.uncheckedCast(getAllprojects(this));
    }

    @Override
    public List<String> getDefaultTasks() {
        return defaultTasks;
    }

    @Override
    public void setDefaultTasks(List<String> defaultTasks) {
        this.defaultTasks = defaultTasks;
    }

    @Override
    public void defaultTasks(String... defaultTasks) {
        if (this.defaultTasks == null) {
            this.defaultTasks = new ArrayList<>();
        }
        this.defaultTasks.addAll(Arrays.asList(defaultTasks));
    }

    @Override
    public BuildProject evaluationDependsOn(String path) throws UnknownProjectException {
        return null;
    }

    @Override
    public void evaluationDependsOnChildren() {

    }

    @Override
    public ProjectStateInternal getState() {
        return state;
    }

    @Override
    public String getPath() {
        return owner.getProjectPath().toString();
    }

    @javax.annotation.Nullable
    @Override
    public ProjectIdentifier getParentIdentifier() {
        return parent;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @NotNull
    @Override
    public String toString() {
        return getDisplayName();
    }


    @Override
    public int depthCompare(BuildProject otherProject) {
        return Ints.compare(getDepth(), otherProject.getDepth());
    }

    @Override
    public int compareTo(BuildProject otherProject) {
        int depthCompare = depthCompare(otherProject);
        if (depthCompare == 0) {
            return getProjectPath().compareTo(((ProjectInternal) otherProject).getProjectPath());
        } else {
            return depthCompare;
        }
    }

    @Override
    public String absoluteProjectPath(String path) {
        return getProjectPath().absolutePath(path);
    }

    @Override
    public String relativeProjectPath(String path) {
        return getProjectPath().relativePath(path);
    }

    @Override
    public Path getProjectPath() {
        return owner.getProjectPath();
    }

    @Override
    public ModelContainer<?> getModel() {
        return owner;
    }

    @Override
    public Path getBuildPath() {
        return gradle.getIdentityPath();
    }

    @Override
    public Path projectPath(String name) {
        return getProjectPath().child(name);
    }

    @Override
    public boolean isScript() {
        return false;
    }

    @Override
    public boolean isRootScript() {
        return false;
    }

    @Override
    public boolean isPluginContext() {
        return false;
    }

    @Override
    public boolean isDetachedState() {
        return ProjectInternal.super.isDetachedState();
    }

    @Override
    public Path getIdentityPath() {
        return owner.getIdentityPath();
    }

    @Override
    public Path identityPath(String name) {
        return getIdentityPath().child(name);
    }

    @Override
    public DependencyMetaDataProvider getDependencyMetaDataProvider() {
        return () -> {
            throw new UnsupportedOperationException();
        };
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public ClassLoaderScope getClassLoaderScope() {
        return classLoaderScope;
    }

    @Override
    public ClassLoaderScope getBaseClassLoaderScope() {
        return baseClassLoaderScope;
    }

    @Override
    public void setScript(Script script) {

    }

    @Override
    public ExtensionContainerInternal getExtensions() {
        return ((ExtensionContainerInternal) getConvention());
    }


    @Override
    public void addDeferredConfiguration(Runnable configuration) {

    }

    @Override
    public PluginManagerInternal getPluginManager() {
        return services.get(PluginManagerInternal.class);
    }

    @Override
    protected DefaultObjectConfigurationAction createObjectConfigurationAction() {
        TextUriResourceLoader.Factory textUriResourceLoaderFactory = services.get(TextUriResourceLoader.Factory.class);
        return new DefaultObjectConfigurationAction(getFileResolver(), getScriptPluginFactory(), getScriptHandlerFactory(), getBaseClassLoaderScope(), textUriResourceLoaderFactory, this);
    }

    // getter from services

    @Override
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        return services.get(ConfigurationTargetIdentifier.class);
    }

    protected FileResolver getFileResolver() {
        return services.get(FileResolver.class);
    }

    protected ScriptPluginFactory getScriptPluginFactory() {
        return services.get(ScriptPluginFactory.class);
    }

    protected ScriptHandlerFactory getScriptHandlerFactory() {
        return services.get(ScriptHandlerFactory.class);
    }

    @Inject
    protected abstract CrossProjectModelAccess getCrossProjectModelAccess();

    protected CrossProjectConfigurator getProjectConfigurator() {
        return services.get(CrossProjectConfigurator.class);
    }
}