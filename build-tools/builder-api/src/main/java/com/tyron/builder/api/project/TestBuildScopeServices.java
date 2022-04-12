package com.tyron.builder.api.project;

import com.tyron.builder.api.initialization.BuildCancellationToken;
import com.tyron.builder.api.internal.reflect.service.ServiceRegistry;
import com.tyron.builder.api.internal.reflect.service.scopes.BuildScopeServices;
import com.tyron.builder.configuration.GradleLauncherMetaData;
import com.tyron.builder.initialization.BuildClientMetaData;
import com.tyron.builder.initialization.DefaultBuildCancellationToken;
import com.tyron.builder.internal.build.BuildModelControllerServices;

import java.io.File;

public class TestBuildScopeServices extends BuildScopeServices {
    private final File homeDir;

    public TestBuildScopeServices(ServiceRegistry parent, File homeDir, BuildModelControllerServices.Supplier supplier) {
        super(parent, supplier);
        this.homeDir = homeDir;
    }

    protected BuildCancellationToken createBuildCancellationToken() {
        return new DefaultBuildCancellationToken();
    }

    protected BuildClientMetaData createClientMetaData() {
        return new GradleLauncherMetaData();
    }
}
