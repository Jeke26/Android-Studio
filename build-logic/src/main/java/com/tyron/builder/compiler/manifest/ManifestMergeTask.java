package com.tyron.builder.compiler.manifest;

import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.Task;
import com.tyron.builder.model.Project;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.project.api.AndroidProject;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ManifestMergeTask extends Task<AndroidProject> {

    private ManifestMerger2 mMerger;
    private File mOutputFile;
    private File mMainManifest;
    private File[] mLibraryManifestFiles;
    private String mPackageName;

    public ManifestMergeTask(AndroidProject project, ILogger logger) {
        super(project, logger);
    }

    @Override
    public String getName() {
        return "ManifestMerger";
    }

    @Override
    public void prepare(BuildType type) throws IOException {
        mPackageName = getApplicationId();

        mOutputFile = new File(getProject().getBuildDirectory(), "bin");
        if (!mOutputFile.exists()) {
            if (!mOutputFile.mkdirs()) {
                throw new IOException("Unable to create build directory");
            }
        }
        mOutputFile = new File(mOutputFile, "AndroidManifest.xml");
        if (!mOutputFile.exists()) {
            if (!mOutputFile.createNewFile()) {
                throw new IOException("Unable to create manifest file");
            }
        }

        mMainManifest = getProject().getManifestFile();
        if (!mMainManifest.exists()) {
            throw new IOException("Unable to find the main manifest file");
        }

        List<File> manifests = new ArrayList<>();
        List<File> libraries = getProject().getLibraries();
        // Filter the libraries and add all that has a AndroidManifest.xml file
        for (File library : libraries) {
            File parent = library.getParentFile();
            if (parent == null) {
                getLogger().warning("Unable to access parent directory of a library");
                continue;
            }

            File manifest = new File(parent, "AndroidManifest.xml");
            if (manifest.exists()) {
                if (manifest.length() != 0) {
                    manifests.add(manifest);
                }
            }
        }

        mLibraryManifestFiles = manifests.toArray(new File[0]);
    }


    @Override
    public void run() throws IOException, CompilationFailedException {

        if (mLibraryManifestFiles == null || mLibraryManifestFiles.length == 0) {
            // no libraries to merge, just copy the manifest file to the output
            FileUtils.copyFile(mMainManifest, mOutputFile);
            return;
        }

        ManifestMerger2.Invoker<?> invoker = ManifestMerger2.newMerger(mMainManifest,
                getLogger(), ManifestMerger2.MergeType.APPLICATION)
                .addLibraryManifests(mLibraryManifestFiles);
        invoker.setOverride(ManifestMerger2.SystemProperty.PACKAGE, mPackageName);
        invoker.setOverride(ManifestMerger2.SystemProperty.VERSION_CODE, "1");
        invoker.setOverride(ManifestMerger2.SystemProperty.VERSION_NAME, "1.0");
        invoker.setVerbose(false);
        try {
            MergingReport report = invoker.merge();
            if (report.getResult().isError()) {
                report.log(getLogger());
                throw new CompilationFailedException(report.getReportString());
            }
            if (report.getMergedDocument().isPresent()) {
                FileUtils.writeStringToFile(mOutputFile,
                        report.getMergedDocument().get().prettyPrint(),
                        Charset.defaultCharset());
            }
        } catch (ManifestMerger2.MergeFailureException e) {
            throw new CompilationFailedException(e);
        }
    }

    private String getApplicationId() throws IOException {
        String packageName = getProject().getPackageName();
        if (packageName == null) {
            throw new IOException("Failed to parse package name");
        }
        return packageName;
    }
}
