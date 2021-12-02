package com.tyron.builder.project.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableMap;
import com.tyron.builder.compiler.manifest.xml.AndroidManifestParser;
import com.tyron.builder.compiler.manifest.xml.ManifestData;
import com.tyron.builder.project.api.AndroidProject;
import com.tyron.common.util.StringSearch;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AndroidProjectImpl extends JavaProjectImpl implements AndroidProject {

    private ManifestData mManifestData;
    private final Map<String, File> mKotlinFiles;

    public AndroidProjectImpl(File root) {
        super(root);

        mKotlinFiles = new HashMap<>();
    }

    @Override
    public void open() throws IOException {
        super.open();

        mManifestData = AndroidManifestParser.parse(getManifestFile());
    }

    @Override
    public void index() {
        super.index();

        Consumer<File> kotlinConsumer = this::addKotlinFile;

        if (getJavaDirectory().exists()) {
            FileUtils.iterateFiles(getJavaDirectory(),
                    FileFilterUtils.suffixFileFilter(".kt"),
                    TrueFileFilter.INSTANCE
            ).forEachRemaining(kotlinConsumer);
        }

        if (getKotlinDirectory().exists()) {
            FileUtils.iterateFiles(getKotlinDirectory(),
                    FileFilterUtils.suffixFileFilter(".kt"),
                    TrueFileFilter.INSTANCE
            ).forEachRemaining(kotlinConsumer);
        }

        // R.java files
        File gen = new File(getBuildDirectory(), "gen");
        if (gen.exists()) {
            FileUtils.iterateFiles(gen,
                    FileFilterUtils.suffixFileFilter(".java"),
                    TrueFileFilter.INSTANCE
            ).forEachRemaining(this::addJavaFile);
        }
    }

    @Override
    public File getAndroidResourcesDirectory() {
        File custom = getPathSetting("android_resources_directory");
        if (custom.exists()) {
            return custom;
        }
        return new File(getRootFile(), "app/src/main/res");
    }

    @Override
    public List<String> getAllClasses() {
        List<String> classes = super.getAllClasses();
        classes.addAll(mKotlinFiles.keySet());
        return classes;
    }

    @Override
    public File getNativeLibrariesDirectory() {
        File custom = getPathSetting("native_libraries_directory");
        if (custom.exists()) {
            return custom;
        }
        return new File(getRootFile(), "app/src/main/jniLibs");
    }

    @Override
    public File getAssetsDirectory() {
        File custom = getPathSetting("assets_directory");
        if (custom.exists()) {
            return custom;
        }
        return new File(getRootFile(), "app/src/main/assets");
    }

    @Override
    public String getPackageName() {
        return mManifestData.getPackage();
    }

    @Override
    public File getManifestFile() {
        File custom = getPathSetting("android_manifest_file");
        if (custom.exists()) {
            return custom;
        }
        return new File(getRootFile(), "app/src/main/AndroidManifest.xml");
    }

    @Override
    public int getTargetSdk() {
        return mManifestData.getTargetSdkVersion();
    }

    @Override
    public int getMinSdk() {
        return mManifestData.getMinSdkVersion();
    }

    @NonNull
    @Override
    public Map<String, File> getKotlinFiles() {
        return ImmutableMap.copyOf(mKotlinFiles);
    }

    @NonNull
    @Override
    public File getKotlinDirectory() {
        File custom = getPathSetting("kotlin_directory");
        if (custom.exists()) {
            return custom;
        }
        return new File(getRootFile(), "app/src/main/kotlin");
    }

    @Nullable
    @Override
    public File getKotlinFile(String packageName) {
        return mKotlinFiles.get(packageName);
    }

    @Override
    public void addKotlinFile(File file) {
        String packageName = StringSearch.packageName(file);
        if (packageName == null) {
            packageName = "";
        }
        String fqn = packageName + "." + file.getName().replace(".kt", "");
        mKotlinFiles.put(fqn, file);
    }
}
