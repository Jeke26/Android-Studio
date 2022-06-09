/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tyron.builder.api.internal.artifacts.ivyservice.ivyresolve;

import com.tyron.builder.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;

import com.tyron.builder.api.artifacts.component.ModuleComponentIdentifier;
import com.tyron.builder.api.internal.component.ArtifactType;
import com.tyron.builder.internal.component.external.model.ModuleDependencyMetadata;
import com.tyron.builder.internal.component.model.ComponentArtifactMetadata;
import com.tyron.builder.internal.component.model.ComponentOverrideMetadata;
import com.tyron.builder.internal.component.model.ComponentResolveMetadata;
import com.tyron.builder.internal.component.model.ConfigurationMetadata;
import com.tyron.builder.internal.component.model.ModuleSources;
import com.tyron.builder.internal.resolve.result.BuildableArtifactResolveResult;
import com.tyron.builder.internal.resolve.result.BuildableArtifactSetResolveResult;
import com.tyron.builder.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import com.tyron.builder.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import com.tyron.builder.internal.resolve.result.BuildableModuleVersionListingResolveResult;

public class BaseModuleComponentRepositoryAccess implements ModuleComponentRepositoryAccess {
    private final ModuleComponentRepositoryAccess delegate;

    public BaseModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess delegate) {
        this.delegate = delegate;
    }

    public ModuleComponentRepositoryAccess getDelegate() {
        return delegate;
    }

    @Override
    public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
        delegate.listModuleVersions(dependency, result);
    }

    @Override
    public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
        delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        delegate.resolveArtifactsWithType(component, artifactType, result);
    }

    @Override
    public void resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata variant, BuildableComponentArtifactsResolveResult result) {
        delegate.resolveArtifacts(component, variant, result);
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactResolveResult result) {
        delegate.resolveArtifact(artifact, moduleSources, result);
    }

    @Override
    public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
        return delegate.estimateMetadataFetchingCost(moduleComponentIdentifier);
    }
}
