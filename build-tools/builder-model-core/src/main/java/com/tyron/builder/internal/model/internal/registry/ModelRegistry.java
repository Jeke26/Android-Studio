package com.tyron.builder.internal.model.internal.registry;


import com.tyron.builder.internal.model.RuleSource;
import com.tyron.builder.internal.model.internal.core.ModelAction;
import com.tyron.builder.internal.model.internal.core.ModelNode;
import com.tyron.builder.internal.model.internal.core.ModelPath;
import com.tyron.builder.internal.model.internal.core.ModelRegistration;
import com.tyron.builder.model.internal.type.ModelType;
import com.tyron.builder.internal.model.internal.core.ModelActionRole;
import com.tyron.builder.internal.model.internal.core.ModelSpec;
import com.tyron.builder.internal.model.internal.core.MutableModelNode;

import javax.annotation.Nullable;

public interface ModelRegistry {

    /**
     * Get the fully defined model element at the given path as the given type.
     * <p>
     * No attempt to mutate the returned object should be made.
     *
     * @param path the path for the node
     * @param type the type to project the node as
     * @param <T> the type to project the node as
     * @return the node as the given type
     */
    <T> T realize(ModelPath path, ModelType<T> type);
    <T> T realize(String path, ModelType<T> type);
    <T> T realize(String path, Class<T> type);

    /**
     * Get the fully defined model element at the given path.
     * <p>
     * No attempt to mutate the returned object should be made.
     *
     * @param path the path for the node
     * @return the node.
     */
    ModelNode realizeNode(ModelPath path);

    /**
     * Get the fully defined model element at the given path as the given type, if present.
     * <p>
     * No attempt to mutate the returned object should be made.
     *
     * @param path the path for the node
     * @param type the type to project the node as
     * @param <T> the type to project the node as
     * @return the node as the given type or null if no such element.
     */
    @Nullable
    <T> T find(ModelPath path, ModelType<T> type);
    @Nullable
    <T> T find(String path, ModelType<T> type);
    @Nullable
    <T> T find(String path, Class<T> type);

    /**
     * Returns the node at the given path at the desired state or later, if it exists.
     * <p>
     * If there is no known node at that path, an {@link IllegalStateException} is thrown.
     * <p>
     * If the node is at an earlier state than desired it will be irrevocably transitioned to the desired state and returned.
     * If it is at the desired state or later it is returned.
     *
     * @param path the path for the node
     * @param state the desired node state
     * @return the node at the desired state
     */
    ModelNode atStateOrLater(ModelPath path, ModelNode.State state);
    <T> T atStateOrLater(ModelPath path, ModelType<T> type, ModelNode.State state);

    ModelNode.State state(ModelPath path);

    void remove(ModelPath path);

    /**
     * Attempts to bind the references of all model rules known at this point in time.
     * <p>
     * This method effectively validates that all references bind (i.e. all rules are executable).
     * It should be called when the model registry is at some kind of logical checkpoint, in that it is reasonable
     * to expect that all rules have been discovered.
     * <p>
     * However, it does not prevent rules from being added after being called.
     * This is necessary as mutation rules can add rules etc.
     * As such, this method can be called multiple times.
     * Subsequent invocations will bind the references of rules added since the previous invocation.
     * <p>
     * If any reference cannot successfully bind, an exception will be thrown.
     *
     * @throws UnboundModelRulesException if there are unbindable references
     */
    void bindAllReferences() throws UnboundModelRulesException;

    ModelRegistry register(ModelRegistration registration);

    /**
     * Bind the given action directly to its subject node in the given role. Calling {@link #bindAllReferences()} fails
     * if the subject of the action is not matched by any node.
     */
    ModelRegistry configure(ModelActionRole role, ModelAction action);

    /**
     * Registers a listener and binds the given action in the given role whenever a node that matches the spec is discovered.
     * Matching nodes that are already discovered when {@code configureMatching()} is called are bound directly.
     * Unlike with {@link #configure(ModelActionRole, ModelAction)}, {@link #bindAllReferences()} will <em>not</em> fail
     * if no nodes match the given spec.
     *
     * @throws IllegalArgumentException if the given action has a <code>path</code> set.
     */
    ModelRegistry configureMatching(ModelSpec spec, ModelActionRole role, ModelAction action);

    /**
     * Registers a listener and applies the given {@link RuleSource} whenever a node that matches the spec is discovered.
     * Matching nodes that are already discovered when {@code configureMatching()} is called are bound directly.
     * Unlike with {@link #configure(ModelActionRole, ModelAction)}, {@link #bindAllReferences()} will <em>not</em> fail
     * if no nodes match the given spec.
     */
    ModelRegistry configureMatching(ModelSpec spec, Class<? extends RuleSource> rules);

    MutableModelNode getRoot();
}