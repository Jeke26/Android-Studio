package com.tyron.builder.launcher;

import com.tyron.builder.api.ProjectEvaluationListener;
import com.tyron.builder.api.ProjectState;
import com.tyron.builder.initialization.BuildCancellationToken;
import com.tyron.builder.internal.Factory;
import com.tyron.builder.api.internal.StartParameterInternal;
import com.tyron.builder.internal.UncheckedException;
import com.tyron.builder.internal.classpath.ClassPath;
import com.tyron.builder.internal.event.ListenerManager;
import com.tyron.builder.internal.invocation.BuildAction;
import com.tyron.builder.internal.reflect.service.ServiceRegistry;
import com.tyron.builder.api.logging.LogLevel;
import com.tyron.builder.api.BuildProject;
import com.tyron.builder.configuration.GradleLauncherMetaData;
import com.tyron.builder.initialization.BuildClientMetaData;
import com.tyron.builder.initialization.BuildEventConsumer;
import com.tyron.builder.initialization.BuildRequestContext;
import com.tyron.builder.initialization.DefaultBuildCancellationToken;
import com.tyron.builder.initialization.DefaultBuildRequestContext;
import com.tyron.builder.internal.buildTree.BuildActionRunner;
import com.tyron.builder.internal.logging.LoggingManagerInternal;
import com.tyron.builder.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import com.tyron.builder.internal.service.scopes.PluginServiceRegistry;
import com.tyron.builder.internal.session.BuildSessionState;
import com.tyron.builder.internal.session.state.CrossBuildSessionState;

import java.util.Collections;
import java.util.List;

public abstract class ProjectLauncher {

    private final StartParameterInternal startParameter;
    private final ServiceRegistry globalServices;

    public ProjectLauncher(StartParameterInternal startParameter) {
        this(startParameter, Collections.emptyList());
    }

    public ProjectLauncher(StartParameterInternal startParameter,
                           List<PluginServiceRegistry> pluginServiceRegistries) {
        this.startParameter = startParameter;
        globalServices = ProjectBuilderImpl.getGlobalServices(startParameter);
    }

    public ServiceRegistry getGlobalServices() {
        return globalServices;
    }

    private void prepare() {
        // enable info logging
        Factory<LoggingManagerInternal> factory = globalServices.getFactory(LoggingManagerInternal.class);
        LoggingManagerInternal loggingManagerInternal = factory.create();
        assert loggingManagerInternal != null;
        loggingManagerInternal.start().setLevelInternal(LogLevel.DEBUG);
    }

    public void execute() {
        prepare();

        BuildAction buildAction = new BuildAction() {
            @Override
            public StartParameterInternal getStartParameter() {
                return startParameter;
            }

            @Override
            public boolean isRunTasks() {
                return true;
            }

            @Override
            public boolean isCreateModel() {
                return false;
            }
        };

        BuildCancellationToken cancellationToken = new DefaultBuildCancellationToken();
        BuildEventConsumer consumer = System.out::println;
        BuildClientMetaData clientMetaData = new GradleLauncherMetaData();
        BuildRequestContext requestContext = DefaultBuildRequestContext.of(
                cancellationToken,
                consumer,
                clientMetaData,
                System.currentTimeMillis()
        );
        runBuildAction(buildAction, requestContext);
    }

    private void runBuildAction(BuildAction buildAction, BuildRequestContext requestContext) {
        try (CrossBuildSessionState crossBuildSessionState = new CrossBuildSessionState(
                globalServices, startParameter)) {
            try (BuildSessionState buildSessionState = new BuildSessionState(globalServices.get(
                    GradleUserHomeScopeServiceRegistry.class), crossBuildSessionState, startParameter, requestContext, ClassPath.EMPTY, requestContext.getCancellationToken(), requestContext.getClient(), requestContext.getEventConsumer())) {

                // register listeners
                ListenerManager listenerManager = globalServices.get(ListenerManager.class);
                listenerManager.addListener(new ProjectEvaluationListener() {
                    @Override
                    public void beforeEvaluate(BuildProject project) {
                        configure(project);
                    }

                    @Override
                    public void afterEvaluate(BuildProject project, ProjectState state) {

                    }
                });

                BuildActionRunner.Result result = buildSessionState.run(context -> context.execute(buildAction));

                if (result.getClientFailure() != null) {
                    throw UncheckedException.throwAsUncheckedException(result.getClientFailure());
                }

                if (result.getBuildFailure() != null) {
                    throw UncheckedException.throwAsUncheckedException(result.getBuildFailure());
                 }
            }
        }
    }

    /**
     * Callback to configure the given project. This will be called on the root project and
     * any existing projects that will be registered.
     *
     * @param project The project to configure
     */
    public abstract void configure(BuildProject project);
}
