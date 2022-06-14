/*
 * Copyright 2015 the original author or authors.
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

package com.tyron.builder.tooling.events;

import com.tyron.builder.api.Incubating;
import com.tyron.builder.tooling.events.ProgressListener;

/**
 * Enumerates the different types of operations for which progress events can be received.
 *
 * @see com.tyron.builder.tooling.LongRunningOperation#addProgressListener(ProgressListener, java.util.Set)
 */
public enum OperationType {

    /**
     * Flag for test operation progress events.
     *
     * <p>
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link com.tyron.builder.tooling.events.test.TestStartEvent}</li>
     *     <li>{@link com.tyron.builder.tooling.events.test.TestFinishEvent}</li>
     * </ul>
     */
    TEST,

    /**
     * Flag for task operation progress events.
     *
     * <p>
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link com.tyron.builder.tooling.events.task.TaskStartEvent}</li>
     *     <li>{@link com.tyron.builder.tooling.events.task.TaskFinishEvent}</li>
     * </ul>
     *
     */
    TASK,

    /**
     * Flag for operations with no specific type.
     *
     * <p>
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link StartEvent}</li>
     *     <li>{@link FinishEvent}</li>
     *     <li>{@link StatusEvent}</li>
     * </ul>
     *
     */
    GENERIC,

    /**
     * Flag for work item operation progress events.
     *
     * <p>
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link com.tyron.builder.tooling.events.work.WorkItemStartEvent}</li>
     *     <li>{@link com.tyron.builder.tooling.events.work.WorkItemFinishEvent}</li>
     * </ul>
     *
     * @since 5.1
     */
    WORK_ITEM,

    /**
     * Flag for project configuration operation progress events.
     *
     * <p>
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link com.tyron.builder.tooling.events.configuration.ProjectConfigurationStartEvent}</li>
     *     <li>{@link com.tyron.builder.tooling.events.configuration.ProjectConfigurationFinishEvent}</li>
     * </ul>
     *
     * @since 5.1
     */
    PROJECT_CONFIGURATION,

    /**
     * Flag for transform operation progress events.
     *
     * <p>
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link com.tyron.builder.tooling.events.transform.TransformStartEvent}</li>
     *     <li>{@link com.tyron.builder.tooling.events.transform.TransformFinishEvent}</li>
     * </ul>
     *
     * @since 5.1
     */
    TRANSFORM,

    /**
     * Flag for test output operation progress events.
     * <p>
     * Clients must subscribe to {@link #TEST} events too if they want to receive test output events.
     * </p>
     *
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link com.tyron.builder.tooling.events.test.TestOutputEvent}</li>
     * </ul>
     *
     * @since 6.0
     */
    TEST_OUTPUT,

    /**
     * Flag for file download progress events. This includes various types of files, for example files downloaded during dependency resolution,
     * Gradle distribution downloads, and Java toolchain downloads.
     *
     * <p>
     * The following events are currently issued for this operation type.
     * <ul>
     *     <li>{@link com.tyron.builder.tooling.events.download.FileDownloadStartEvent}</li>
     *     <li>{@link com.tyron.builder.tooling.events.StatusEvent}</li>
     *     <li>{@link com.tyron.builder.tooling.events.download.FileDownloadFinishEvent}</li>
     * </ul>
     *
     * @since 7.3
     */
    @Incubating
    FILE_DOWNLOAD
}
