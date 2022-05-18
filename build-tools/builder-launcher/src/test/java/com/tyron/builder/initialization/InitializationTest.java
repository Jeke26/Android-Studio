package com.tyron.builder.initialization;

import com.tyron.builder.api.BuildProject;
import com.tyron.builder.api.internal.StartParameterInternal;
import com.tyron.builder.api.logging.configuration.ShowStacktrace;
import com.tyron.builder.launcher.ProjectLauncher;
import com.tyron.builder.plugin.CodeAssistPlugin;
import com.tyron.common.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class InitializationTest {

    private ProjectLauncher projectLauncher;

    @Before
    public void setup() {
        System.setProperty("org.gradle.native", "true");

        CodeAssistPlugin plugin = null;
        File resourcesDir = TestUtil.getResourcesDirectory();
        File projectDir = new File(resourcesDir, "TestProject");

        StartParameterInternal startParameterInternal = new StartParameterInternal();
        startParameterInternal.setShowStacktrace(ShowStacktrace.ALWAYS_FULL);
        startParameterInternal.setProjectDir(projectDir);
        startParameterInternal.setGradleUserHomeDir(new File(resourcesDir, ".gradle"));

        projectLauncher = new ProjectLauncher(startParameterInternal) {
            @Override
            public void configure(BuildProject project) {

            }
        };
    }

    public interface SomeInterface {
        void log(String message);
    }

    @Test
    public void testInitialization() {
        projectLauncher.execute();
    }
}
