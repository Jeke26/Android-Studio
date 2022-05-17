package com.tyron.builder.api.plugins;

import com.tyron.builder.api.BuildProject;
import com.tyron.builder.api.internal.project.ProjectInternal;

import com.tyron.builder.api.Plugin;
import com.tyron.builder.api.artifacts.ConfigurationContainer;
import com.tyron.builder.api.artifacts.Dependency;
import com.tyron.builder.api.internal.plugins.BuildConfigurationRule;
import com.tyron.builder.api.internal.plugins.DefaultArtifactPublicationSet;
import com.tyron.builder.api.plugins.internal.DefaultBasePluginConvention;
import com.tyron.builder.api.plugins.internal.DefaultBasePluginExtension;
import com.tyron.builder.api.tasks.bundling.AbstractArchiveTask;
import com.tyron.builder.internal.deprecation.DeprecatableConfiguration;
import com.tyron.builder.jvm.tasks.Jar;
import com.tyron.builder.language.base.plugins.LifecycleBasePlugin;

/**
 * <p>A {@link com.tyron.builder.api.Plugin} which defines a basic project lifecycle and some common convention properties.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/base_plugin.html">Base plugin reference</a>
 */
public class BasePlugin implements Plugin<BuildProject> {
    public static final String CLEAN_TASK_NAME = LifecycleBasePlugin.CLEAN_TASK_NAME;
    public static final String ASSEMBLE_TASK_NAME = LifecycleBasePlugin.ASSEMBLE_TASK_NAME;
    public static final String BUILD_GROUP = LifecycleBasePlugin.BUILD_GROUP;

    @Override
    public void apply(final BuildProject project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        BasePluginExtension baseExtension = project.getExtensions().create(BasePluginExtension.class, "base", DefaultBasePluginExtension.class, project);
        BasePluginConvention convention = new DefaultBasePluginConvention(baseExtension);

        project.getConvention().getPlugins().put("base", convention);

        configureExtension(project, baseExtension);
        configureBuildConfigurationRule(project);
        configureArchiveDefaults(project, baseExtension);
        configureConfigurations(project);
        configureAssemble((ProjectInternal) project);
    }

    private void configureExtension(BuildProject project, BasePluginExtension extension) {
        extension.getArchivesName().convention(project.getName());
        extension.getLibsDirectory().convention(project.getLayout().getBuildDirectory().dir("libs"));
        extension.getDistsDirectory().convention(project.getLayout().getBuildDirectory().dir("distributions"));
    }

    private void configureArchiveDefaults(final BuildProject project, final BasePluginExtension extension) {
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(task -> {
            if (task instanceof Jar) {
                task.getDestinationDirectory().convention(extension.getLibsDirectory());
            } else {
                task.getDestinationDirectory().convention(extension.getDistsDirectory());
            }

            task.getArchiveVersion().convention(
                project.provider(() -> project.getVersion() == Project.DEFAULT_VERSION ? null : project.getVersion().toString())
            );

            task.getArchiveBaseName().convention(extension.getArchivesName());
        });
    }

    private void configureBuildConfigurationRule(BuildProject project) {
        project.getTasks().addRule(new BuildConfigurationRule(project.getConfigurations(), project.getTasks()));
    }

    private void configureConfigurations(final BuildProject project) {
        ConfigurationContainer configurations = project.getConfigurations();
        ((ProjectInternal)project).getInternalStatus().convention("integration");

        final DeprecatableConfiguration archivesConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(Dependency.ARCHIVES_CONFIGURATION).
            setDescription("Configuration for archive artifacts.");

        final DeprecatableConfiguration defaultConfiguration = (DeprecatableConfiguration) configurations.maybeCreate(Dependency.DEFAULT_CONFIGURATION).
            setDescription("Configuration for default artifacts.");

        final DefaultArtifactPublicationSet defaultArtifacts = project.getExtensions().create(
            "defaultArtifacts", DefaultArtifactPublicationSet.class, archivesConfiguration.getArtifacts()
        );

        archivesConfiguration.deprecateForResolution(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        defaultConfiguration.deprecateForResolution(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        archivesConfiguration.deprecateForDeclaration(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.API_CONFIGURATION_NAME);
        defaultConfiguration.deprecateForDeclaration(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.API_CONFIGURATION_NAME);

        configurations.all(configuration -> {
            if (!configuration.equals(archivesConfiguration)) {
                configuration.getArtifacts().configureEach(artifact -> {
                    if (configuration.isVisible()) {
                        defaultArtifacts.addCandidate(artifact);
                    }
                });
            }
        });
    }

    private void configureAssemble(final ProjectInternal project) {
        project.getTasks().named(ASSEMBLE_TASK_NAME, task -> {
            task.dependsOn(task.getProject().getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).getAllArtifacts().getBuildDependencies());
        });
    }
}
