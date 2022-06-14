/*
 * Copyright 2011 the original author or authors.
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
package com.tyron.builder.api.internal.artifacts.repositories;

import com.tyron.builder.api.Action;
import com.tyron.builder.api.artifacts.repositories.AuthenticationContainer;
import com.tyron.builder.api.artifacts.repositories.PasswordCredentials;
import com.tyron.builder.api.credentials.Credentials;
import com.tyron.builder.api.model.ObjectFactory;
import com.tyron.builder.api.provider.Property;
import com.tyron.builder.api.provider.ProviderFactory;
import com.tyron.builder.authentication.Authentication;
import com.tyron.builder.internal.Cast;
import com.tyron.builder.internal.artifacts.repositories.AuthenticationSupportedInternal;
import com.tyron.builder.internal.authentication.AuthenticationInternal;
import com.tyron.builder.internal.reflect.Instantiator;
import com.tyron.builder.util.internal.CollectionUtils;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractAuthenticationSupportedRepository extends AbstractResolutionAwareArtifactRepository implements AuthenticationSupportedInternal {
    private final AuthenticationSupporter delegate;
    private final ProviderFactory providerFactory;

    AbstractAuthenticationSupportedRepository(Instantiator instantiator, AuthenticationContainer authenticationContainer, ObjectFactory objectFactory, ProviderFactory providerFactory) {
        super(objectFactory);
        this.delegate = new AuthenticationSupporter(instantiator, objectFactory, authenticationContainer, providerFactory);
        this.providerFactory = providerFactory;
    }

    @Override
    public PasswordCredentials getCredentials() {
        invalidateDescriptor();
        return delegate.getCredentials();
    }

    @Override
    public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
        invalidateDescriptor();
        return delegate.getCredentials(credentialsType);
    }

    @Override
    public Property<Credentials> getConfiguredCredentials() {
        return delegate.getConfiguredCredentials();
    }

    @Override
    public void setConfiguredCredentials(Credentials credentials) {
        invalidateDescriptor();
        delegate.setConfiguredCredentials(credentials);
    }

    @Override
    public void credentials(Action<? super PasswordCredentials> action) {
        invalidateDescriptor();
        delegate.credentials(action);
    }

    @Override
    public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) throws IllegalStateException {
        invalidateDescriptor();
        delegate.credentials(credentialsType, action);
    }

    @Override
    public void credentials(Class<? extends Credentials> credentialsType) {
        invalidateDescriptor();
        delegate.credentials(credentialsType, providerFactory.provider(this::getName));
    }

    @Override
    public void authentication(Action<? super AuthenticationContainer> action) {
        invalidateDescriptor();
        delegate.authentication(action);
    }

    @Override
    public AuthenticationContainer getAuthentication() {
        invalidateDescriptor();
        return delegate.getAuthentication();
    }

    @Override
    public Collection<Authentication> getConfiguredAuthentication() {
        Collection<Authentication> configuredAuthentication = delegate.getConfiguredAuthentication();

        for (Authentication authentication : configuredAuthentication) {
            AuthenticationInternal authenticationInternal = (AuthenticationInternal) authentication;
            for (URI repositoryUrl : getRepositoryUrls()) {
                // only care about HTTP hosts right now
                if (repositoryUrl.getScheme().startsWith("http")) {
                    authenticationInternal.addHost(repositoryUrl.getHost(), repositoryUrl.getPort());
                }
            }
        }
        return configuredAuthentication;
    }

    protected Collection<URI> getRepositoryUrls() {
        return Collections.emptyList();
    }

    List<String> getAuthenticationSchemes() {
        return CollectionUtils.collect(getConfiguredAuthentication(), authentication -> Cast.cast(AuthenticationInternal.class, authentication).getType().getSimpleName());
    }

    boolean usesCredentials() {
        return delegate.usesCredentials();
    }
}
