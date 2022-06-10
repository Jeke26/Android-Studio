package org.gradle.api.internal.tasks;

import org.gradle.api.BuildProject;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.BiAction;
import org.gradle.internal.Factory;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.collection.internal.BridgedCollections;
import org.gradle.model.internal.core.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.internal.core.DirectNodeNoInputsModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelMapModelProjection;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.core.NodeBackedModelMap;
import org.gradle.model.internal.core.UnmanagedModelProjection;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import static org.gradle.model.internal.core.NodePredicate.allLinks;

public class DefaultTaskContainerFactory implements Factory<TaskContainerInternal> {
    private static final ModelType<DefaultTaskContainer> DEFAULT_TASK_CONTAINER_MODEL_TYPE = ModelType.of(DefaultTaskContainer.class);
    private static final ModelType<TaskContainer> TASK_CONTAINER_MODEL_TYPE = ModelType.of(TaskContainer.class);
    private static final ModelType<Task> TASK_MODEL_TYPE = ModelType.of(Task.class);
    private static final ModelReference<Task> TASK_MODEL_REFERENCE = ModelReference.of(TASK_MODEL_TYPE);
    private static final SimpleModelRuleDescriptor COPY_TO_TASK_CONTAINER_DESCRIPTOR = new SimpleModelRuleDescriptor("copyToTaskContainer");
    private final Instantiator instantiator;
    private final ITaskFactory taskFactory;
    private final CollectionCallbackActionDecorator callbackDecorator;
    private final ProjectInternal project;
    private final TaskStatistics statistics;
    private final BuildOperationExecutor buildOperationExecutor;
    private final CrossProjectConfigurator crossProjectConfigurator;

    public DefaultTaskContainerFactory(Instantiator instantiator,
                                       ITaskFactory taskFactory,
                                       ProjectInternal project,
                                       TaskStatistics statistics,
                                       BuildOperationExecutor buildOperationExecutor,
                                       CrossProjectConfigurator crossProjectConfigurator,
                                       CollectionCallbackActionDecorator callbackDecorator) {
        this.instantiator = instantiator;
        this.taskFactory = taskFactory;
        this.project = project;
        this.statistics = statistics;
        this.buildOperationExecutor = buildOperationExecutor;
        this.crossProjectConfigurator = crossProjectConfigurator;
        this.callbackDecorator = callbackDecorator;
    }

    @Override
    public TaskContainerInternal create() {
        DefaultTaskContainer tasks = instantiator.newInstance(DefaultTaskContainer.class, project, instantiator, taskFactory, statistics, buildOperationExecutor, crossProjectConfigurator, callbackDecorator);
        bridgeIntoSoftwareModelWhenNeeded(tasks);
        return tasks;
    }

    private void bridgeIntoSoftwareModelWhenNeeded(final DefaultTaskContainer tasks) {
        project.addRuleBasedPluginListener(project -> {
            ModelReference<DefaultTaskContainer> containerReference = ModelReference.of(TaskContainerInternal.MODEL_PATH, DEFAULT_TASK_CONTAINER_MODEL_TYPE);

            ModelRegistrations.Builder registrationBuilder = BridgedCollections.bridgeTaskCollection(
                containerReference, mutableModelNode -> {
                    tasks.setModelNode(mutableModelNode);
                    return tasks;
                },
                new Task.Namer(),
                "Project.<init>.tasks()",
                new Namer()
            );

            ModelRegistry modelRegistry = ((ProjectInternal) project).getServices().get(ModelRegistry.class);
            modelRegistry.register(
                registrationBuilder
                    .withProjection(ModelMapModelProjection.unmanaged(TASK_MODEL_TYPE, ChildNodeInitializerStrategyAccessors.of(NodeBackedModelMap.createUsingParentNode(new Transformer<NamedEntityInstantiator<Task>, MutableModelNode>() {
                        @Override
                        public NamedEntityInstantiator<Task> transform(MutableModelNode modelNode) {
                            return modelNode.getPrivateData(DEFAULT_TASK_CONTAINER_MODEL_TYPE).getEntityInstantiator();
                        }
                    }))))
                    .withProjection(UnmanagedModelProjection.of(TASK_CONTAINER_MODEL_TYPE))
                    .build()
            );

            ModelNode modelNode = modelRegistry.atStateOrLater(TaskContainerInternal.MODEL_PATH, ModelNode.State.Created);

            // TODO LD use something more stable than a cast here
            MutableModelNode mutableModelNode = (MutableModelNode) modelNode;

            // Add tasks created through rules to the actual task container
            mutableModelNode.applyTo(allLinks(), ModelActionRole.Initialize, DirectNodeNoInputsModelAction.of(TASK_MODEL_REFERENCE, COPY_TO_TASK_CONTAINER_DESCRIPTOR, new BiAction<MutableModelNode, Task>() {
                @Override
                public void execute(MutableModelNode modelNode, Task task) {
                    TaskContainerInternal taskContainer = modelNode.getParent().getPrivateData(TaskContainerInternal.MODEL_TYPE);
                    taskContainer.addInternal(task);
                }
            }));
        });
    }

    private static class Namer implements Transformer<String, String> {
        @Override
        public String transform(String s) {
            return "Project.<init>.tasks." + s + "()";
        }
    }

}
