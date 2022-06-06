/*
 * Copyright 2012 the original author or authors.
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

package com.tyron.builder.api.internal.artifacts.repositories.resolver;

import com.tyron.builder.api.artifacts.ModuleIdentifier;
import com.tyron.builder.api.artifacts.component.ModuleComponentIdentifier;
import com.tyron.builder.internal.component.external.model.ModuleComponentArtifactMetadata;
import com.tyron.builder.internal.component.model.IvyArtifactName;
import com.tyron.builder.internal.resource.ExternalResourceName;

public interface ResourcePattern {
    /**
     * Returns this pattern converted to a String.
     */
    String getPattern();

    /**
     * Returns the path for the given artifact.
     */
    ExternalResourceName getLocation(ModuleComponentArtifactMetadata artifact);

    /**
     * Returns the pattern which can be used to search for versions of the given artifact.
     * The returned pattern should include at least one [revision] placeholder.
     */
    ExternalResourceName toVersionListPattern(ModuleIdentifier module, IvyArtifactName artifact);

    /**
     * Returns the path for the given module.
     */
    ExternalResourceName toModulePath(ModuleIdentifier moduleIdentifier);

    /**
     * Returns the path for the given component.
     */
    ExternalResourceName toModuleVersionPath(ModuleComponentIdentifier componentIdentifier);

    /**
     * Checks if the given identifier contains sufficient information to bind the tokens in this pattern.
     */
    boolean isComplete(ModuleIdentifier moduleIdentifier);

    /**
     * Checks if the given identifier contains sufficient information to bind the tokens in this pattern.
     */
    boolean isComplete(ModuleComponentIdentifier componentIdentifier);

    /**
     * Checks if the given identifier contains sufficient information to bind the tokens in this pattern.
     */
    boolean isComplete(ModuleComponentArtifactMetadata id);

}
