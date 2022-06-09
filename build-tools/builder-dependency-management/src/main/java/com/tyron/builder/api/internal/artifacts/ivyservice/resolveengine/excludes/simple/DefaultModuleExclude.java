/*
 * Copyright 2019 the original author or authors.
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
package com.tyron.builder.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import com.tyron.builder.api.artifacts.ModuleIdentifier;
import com.tyron.builder.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude;
import com.tyron.builder.internal.component.model.IvyArtifactName;

final class DefaultModuleExclude implements ModuleExclude {
    private final String module;
    private final int hashCode;

    public static ModuleExclude of(String module) {
        return new DefaultModuleExclude(module);
    }

    private DefaultModuleExclude(String module) {
        this.module = module;
        this.hashCode = module.hashCode();
    }

    @Override
    public String getModule() {
        return module;
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        return this.module.equals(module.getName());
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName) {
        return false;
    }

    @Override
    public boolean mayExcludeArtifacts() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleExclude that = (DefaultModuleExclude) o;

        if (hashCode != that.hashCode) {
            return false;
        }
        return module.equals(that.module);

    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "{\"exclude module\" : \"" + module + "\"}";
    }
}
