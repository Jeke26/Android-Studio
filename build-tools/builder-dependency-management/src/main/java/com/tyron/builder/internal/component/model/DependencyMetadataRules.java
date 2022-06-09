/*
 * Copyright 2017 the original author or authors.
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

package com.tyron.builder.internal.component.model;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.tyron.builder.api.internal.artifacts.repositories.resolver.DependencyConstraintsMetadataAdapter;
import com.tyron.builder.api.internal.artifacts.repositories.resolver.DirectDependenciesMetadataAdapter;
import com.tyron.builder.internal.component.external.model.ModuleDependencyMetadata;
import com.tyron.builder.internal.component.external.model.VariantMetadataRules;

import com.tyron.builder.api.Action;
import com.tyron.builder.api.artifacts.DependencyConstraintMetadata;
import com.tyron.builder.api.artifacts.DependencyConstraintsMetadata;
import com.tyron.builder.api.artifacts.DirectDependenciesMetadata;
import com.tyron.builder.api.artifacts.DirectDependencyMetadata;
import com.tyron.builder.api.internal.attributes.ImmutableAttributesFactory;
import com.tyron.builder.internal.reflect.Instantiator;
import com.tyron.builder.internal.typeconversion.NotationParser;
import com.tyron.builder.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A set of rules provided by the build script author
 * (as {@link Action<DirectDependenciesMetadata>} or {@link Action<DependencyConstraintsMetadata>})
 * that are applied on the dependencies defined in variant/configuration metadata. The rules are applied
 * in the {@link #execute(VariantResolveMetadata, List)} method when the dependencies of a variant are needed during dependency resolution.
 */
public class DependencyMetadataRules {
    private static final Predicate<ModuleDependencyMetadata> DEPENDENCY_FILTER = dep -> !dep.isConstraint();
    private static final Predicate<ModuleDependencyMetadata> DEPENDENCY_CONSTRAINT_FILTER = DependencyMetadata::isConstraint;

    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser;
    private final List<VariantMetadataRules.VariantAction<? super DirectDependenciesMetadata>> dependencyActions = Lists.newArrayList();
    private final List<VariantMetadataRules.VariantAction<? super DependencyConstraintsMetadata>> dependencyConstraintActions = Lists.newArrayList();
    private final ImmutableAttributesFactory attributesFactory;

    public DependencyMetadataRules(Instantiator instantiator,
                                   NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser,
                                   NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser,
                                   ImmutableAttributesFactory attributesFactory) {
        this.instantiator = instantiator;
        this.dependencyNotationParser = dependencyNotationParser;
        this.dependencyConstraintNotationParser = dependencyConstraintNotationParser;
        this.attributesFactory = attributesFactory;
    }

    public void addDependencyAction(VariantMetadataRules.VariantAction<? super DirectDependenciesMetadata> action) {
        dependencyActions.add(action);
    }

    public void addDependencyConstraintAction(VariantMetadataRules.VariantAction<? super DependencyConstraintsMetadata> action) {
        dependencyConstraintActions.add(action);
    }

    public <T extends ModuleDependencyMetadata> List<T> execute(VariantResolveMetadata variant, List<T> dependencies) {
        ImmutableList.Builder<T> calculatedDependencies = new ImmutableList.Builder<>();
        calculatedDependencies.addAll(executeDependencyRules(variant, dependencies));
        calculatedDependencies.addAll(executeDependencyConstraintRules(variant, dependencies));
        return calculatedDependencies.build();
    }

    private <T extends ModuleDependencyMetadata> List<T> executeDependencyRules(VariantResolveMetadata variant, List<T> dependencies) {
        List<T> calculatedDependencies = new ArrayList<>(CollectionUtils.filter(dependencies, DEPENDENCY_FILTER));
        for (VariantMetadataRules.VariantAction<? super DirectDependenciesMetadata> dependenciesMetadataAction : dependencyActions) {
            dependenciesMetadataAction.maybeExecute(variant, instantiator.newInstance(
                DirectDependenciesMetadataAdapter.class, attributesFactory, calculatedDependencies, instantiator, dependencyNotationParser));
        }
        return calculatedDependencies;
    }

    private <T extends ModuleDependencyMetadata> List<T> executeDependencyConstraintRules(VariantResolveMetadata variant, List<T> dependencies) {
        List<T> calculatedDependencies = new ArrayList<>(CollectionUtils.filter(dependencies, DEPENDENCY_CONSTRAINT_FILTER));
        for (VariantMetadataRules.VariantAction<? super DependencyConstraintsMetadata> dependencyConstraintsMetadataAction : dependencyConstraintActions) {
            dependencyConstraintsMetadataAction.maybeExecute(variant, instantiator.newInstance(
                DependencyConstraintsMetadataAdapter.class, attributesFactory, calculatedDependencies, instantiator, dependencyConstraintNotationParser));
        }
        return calculatedDependencies;
    }
}
