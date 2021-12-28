package com.tyron.completion.java;

import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.index.CompilerProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaCompilerProvider extends CompilerProvider<JavaCompilerService> {
    public static final String KEY = JavaCompilerProvider.class.getSimpleName();

    private volatile JavaCompilerService mProvider;
    private final Set<File> mCachedPaths;

    public JavaCompilerProvider() {
        mCachedPaths = new HashSet<>();
    }

    @Override
    public JavaCompilerService get(Project project, Module module) {
        if (module instanceof JavaModule) {
            return getCompiler(project, (JavaModule) module);
        }
        return null;
    }

    public void destroy() {
        mCachedPaths.clear();
        mProvider = null;
    }

    public JavaCompilerService getCompiler(Project project, JavaModule module) {

        List<Module> dependencies = new ArrayList<>();
        if (project != null) {
            dependencies.addAll(project.getDependencies(module));
        }

        Set<File> paths = new HashSet<>();
        paths.addAll(module.getJavaFiles().values());
        paths.addAll(module.getLibraries());

        for (Module dependency : dependencies) {
            if (dependency instanceof JavaModule) {
                paths.addAll(((JavaModule) dependency).getJavaFiles().values());
                paths.addAll(((JavaModule) dependency).getLibraries());
            }
        }

        String target =
                CompletionModule.getPreferences().getString(SharedPreferenceKeys.JAVA_COMPLETIONS_TARGET_VERSION, "8");
        String source =
                CompletionModule.getPreferences().getString(SharedPreferenceKeys.JAVA_COMPLETIONS_SOURCE_VERSION, "8");

        if (mProvider == null || changed(mCachedPaths, paths)) {
            mProvider = new JavaCompilerService(project, paths, Collections.emptySet(),
                    Collections.emptySet());

            mCachedPaths.clear();
            mCachedPaths.addAll(paths);
            mProvider.setCurrentModule(module);
        }

        return mProvider;
    }

    private boolean changed(Set<File> oldFiles, Set<File> newFiles) {
        if (oldFiles.size() != newFiles.size()) {
            return true;
        }

        for (File oldFile : oldFiles) {
            if (!newFiles.contains(oldFile)) {
                return true;
            }
        }

        for (File newFile : newFiles) {
            if (!oldFiles.contains(newFile)) {
                return true;
            }
        }

        return false;
    }
}
