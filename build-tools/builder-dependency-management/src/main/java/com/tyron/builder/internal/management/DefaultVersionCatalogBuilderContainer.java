/*
 * Copyright 2020 the original author or authors.
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
package com.tyron.builder.internal.management;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.tyron.builder.api.internal.catalog.DefaultVersionCatalogBuilder;

import com.tyron.builder.api.Action;
import com.tyron.builder.api.InvalidUserCodeException;
import com.tyron.builder.api.InvalidUserDataException;
import com.tyron.builder.api.initialization.dsl.VersionCatalogBuilder;
import com.tyron.builder.api.initialization.resolve.MutableVersionCatalogContainer;
import com.tyron.builder.api.internal.AbstractNamedDomainObjectContainer;
import com.tyron.builder.api.internal.CollectionCallbackActionDecorator;
import com.tyron.builder.api.internal.DocumentationRegistry;
import com.tyron.builder.api.internal.FeaturePreviews;
import com.tyron.builder.api.internal.artifacts.DependencyResolutionServices;
import com.tyron.builder.api.internal.artifacts.ImmutableVersionConstraint;
import com.tyron.builder.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import com.tyron.builder.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import com.tyron.builder.api.model.ObjectFactory;
import com.tyron.builder.api.provider.ProviderFactory;
import com.tyron.builder.api.reflect.TypeOf;
import com.tyron.builder.configuration.internal.UserCodeApplicationContext;
import com.tyron.builder.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.tyron.builder.api.reflect.TypeOf.typeOf;

public class DefaultVersionCatalogBuilderContainer extends AbstractNamedDomainObjectContainer<VersionCatalogBuilder> implements MutableVersionCatalogContainer {
    private static final String VALID_EXTENSION_NAME = "[a-z]([a-zA-Z0-9])+";
    private static final Pattern VALID_EXTENSION_PATTERN = Pattern.compile(VALID_EXTENSION_NAME);

    private final Interner<String> strings = Interners.newStrongInterner();
    private final Interner<ImmutableVersionConstraint> versions = Interners.newStrongInterner();
    private final Supplier<DependencyResolutionServices> dependencyResolutionServices;
    private final FeaturePreviews featurePreviews;

    private final ObjectFactory objects;
    private final ProviderFactory providers;
    private final UserCodeApplicationContext context;

    @Inject
    public DefaultVersionCatalogBuilderContainer(Instantiator instantiator,
                                                 CollectionCallbackActionDecorator callbackActionDecorator,
                                                 ObjectFactory objects,
                                                 ProviderFactory providers,
                                                 Supplier<DependencyResolutionServices> dependencyResolutionServices,
                                                 UserCodeApplicationContext context,
                                                 FeaturePreviews featurePreviews) {
        super(VersionCatalogBuilder.class, instantiator, callbackActionDecorator);
        this.objects = objects;
        this.providers = providers;
        this.context = context;
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.featurePreviews = featurePreviews;
    }

    private static void validateName(String name) {
        if (!VALID_EXTENSION_PATTERN.matcher(name).matches()) {
            throw new InvalidUserDataException("Invalid model name '" + name + "': it must match the following regular expression: " + VALID_EXTENSION_NAME);
        }
    }

    @Override
    public VersionCatalogBuilder create(String name, Action<? super VersionCatalogBuilder> configureAction) throws InvalidUserDataException {
        if (!featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.VERSION_CATALOGS)) {
            throw new InvalidUserCodeException("Using dependency catalogs requires the activation of the matching feature preview.\n" +
                "See the documentation at " + new DocumentationRegistry().getDocumentationFor("platforms", "sub:central-declaration-of-dependencies"));
        }
        validateName(name);
        return super.create(name, model -> {
            UserCodeApplicationContext.Application current = context.current();
            DefaultVersionCatalogBuilder builder = (DefaultVersionCatalogBuilder) model;
            builder.withContext(current == null ? "Settings" : current.getDisplayName().getDisplayName(), () -> configureAction.execute(model));
        });
    }

    private static ProjectFinder makeUnknownProjectFinder() {
        return new UnknownProjectFinder("Project dependencies are not allowed in shared dependency resolution services");
    }

    @Override
    protected VersionCatalogBuilder doCreate(String name) {
        return objects.newInstance(DefaultVersionCatalogBuilder.class, name, strings, versions, objects, providers, dependencyResolutionServices);
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(MutableVersionCatalogContainer.class);
    }

}
