package com.tyron.builder.compiler;

import com.tyron.builder.compiler.aab.AabTask;
import com.tyron.builder.compiler.dex.R8Task;
import com.tyron.builder.compiler.firebase.GenerateFirebaseConfigTask;
import com.tyron.builder.compiler.incremental.dex.IncrementalD8Task;
import com.tyron.builder.compiler.incremental.java.IncrementalJavaTask;
import com.tyron.builder.compiler.incremental.kotlin.IncrementalKotlinCompiler;
import com.tyron.builder.compiler.incremental.resource.IncrementalAapt2Task;
import com.tyron.builder.compiler.manifest.ManifestMergeTask;
import com.tyron.builder.compiler.symbol.MergeSymbolsTask;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.model.ProjectSettings;
import com.tyron.builder.project.api.AndroidProject;

import java.util.ArrayList;
import java.util.List;

public class AndroidAppBundleBuilder extends BuilderImpl<AndroidProject> {

    public AndroidAppBundleBuilder(AndroidProject project, ILogger logger) {
        super(project, logger);
    }

    @Override
    public List<Task<? super AndroidProject>> getTasks(BuildType type) {
        List<Task<? super AndroidProject>> tasks = new ArrayList<>();
        tasks.add(new CleanTask(getProject(), getLogger()));
        tasks.add(new ManifestMergeTask(getProject(), getLogger()));
        tasks.add(new GenerateFirebaseConfigTask(getProject(), getLogger()));
        tasks.add(new IncrementalAapt2Task(getProject(), getLogger(), true));
        tasks.add(new MergeSymbolsTask(getProject(), getLogger()));
        tasks.add(new IncrementalKotlinCompiler(getProject(), getLogger()));
        tasks.add(new IncrementalJavaTask(getProject(), getLogger()));
        if (getProject().getSettings().getBoolean(ProjectSettings.USE_R8, false)) {
            tasks.add(new R8Task(getProject(), getLogger()));
        } else {
            tasks.add(new IncrementalD8Task(getProject(), getLogger()));
        }
        tasks.add(new AabTask(getProject(), getLogger()));
        return tasks;
    }
}
