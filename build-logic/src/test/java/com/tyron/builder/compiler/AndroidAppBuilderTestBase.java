package com.tyron.builder.compiler;

import com.tyron.builder.compiler.incremental.resource.IncrementalAapt2Task;
import com.tyron.builder.project.mock.MockAndroidProject;
import com.tyron.builder.project.mock.MockFileManager;

import org.junit.Before;

import java.io.File;

public class AndroidAppBuilderTestBase {

    protected MockFileManager mFileManager;
    protected MockAndroidProject mProject;
    protected File mResourcesDir;

    @Before
    public void setup() throws Exception {
        mResourcesDir = TestUtil.getResourcesDirectory();
        File projectDir = new File(mResourcesDir, "TestProject");

        mFileManager = new MockFileManager(projectDir);
        mProject = new MockAndroidProject(projectDir, mFileManager);
        mProject.setLambdaStubsJarFile(new File(mResourcesDir, "bootstraps/core-lambda-stubs.jar"));
        mProject.setBootstrapFile(new File(mResourcesDir, "bootstraps/rt.jar"));


        File aapt2;
        if (TestUtil.isWindows()) {
            aapt2 = new File(mResourcesDir, "aapt2/aapt2.exe");
        } else {
            aapt2 = new File(mResourcesDir, "aapt2/libaapt2.so");
            assert aapt2.setExecutable(true);
        }
        IncrementalAapt2Task.setAapt2Binary(aapt2);

        ApkSigner.setTestCertFile(new File(mResourcesDir, "apksigner/testkey.x509.pem"));
        ApkSigner.setTestKeyFile(new File(mResourcesDir, "apksigner/testkey.pk8"));
    }
}