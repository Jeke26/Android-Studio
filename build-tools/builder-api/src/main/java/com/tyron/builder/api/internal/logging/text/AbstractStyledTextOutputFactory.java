package com.tyron.builder.api.internal.logging.text;

import com.tyron.builder.api.internal.graph.StyledTextOutput;
import com.tyron.builder.api.logging.LogLevel;

public abstract class AbstractStyledTextOutputFactory implements StyledTextOutputFactory {
    @Override
    public StyledTextOutput create(Class<?> logCategory) {
        return create(logCategory.getName());
    }

    @Override
    public StyledTextOutput create(String logCategory) {
        return create(logCategory, null);
    }

    @Override
    public StyledTextOutput create(Class<?> logCategory, LogLevel logLevel) {
        return create(logCategory.getName(), logLevel);
    }
}