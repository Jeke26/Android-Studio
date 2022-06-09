/*
 * Copyright 2021 the original author or authors.
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

package com.tyron.builder.api.plugins.jvm.internal;

import com.tyron.builder.api.Buildable;
import com.tyron.builder.api.internal.tasks.AbstractTaskDependency;
import com.tyron.builder.api.internal.tasks.TaskDependencyResolveContext;
import com.tyron.builder.api.plugins.JavaBasePlugin;
import com.tyron.builder.api.plugins.jvm.JvmTestSuiteTarget;
import com.tyron.builder.api.tasks.TaskContainer;
import com.tyron.builder.api.tasks.TaskDependency;
import com.tyron.builder.api.tasks.TaskProvider;
import com.tyron.builder.api.tasks.testing.Test;
import com.tyron.builder.util.internal.GUtil;

import javax.inject.Inject;

public abstract class DefaultJvmTestSuiteTarget implements JvmTestSuiteTarget, Buildable {
    private final String name;
    private final TaskProvider<Test> testTask;

    @Inject
    public DefaultJvmTestSuiteTarget(String name, TaskContainer tasks) {
        this.name = name;

        // Might not always want Test type here?
        this.testTask = tasks.register(name, Test.class, t -> {
            t.setDescription("Runs the " + GUtil.toWords(name) + " suite.");
            t.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        });
    }

    @Override
    public String getName() {
        return name;
    }

    public TaskProvider<Test> getTestTask() {
        return testTask;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                context.add(getTestTask());
            }
        };
    }
}
