package com.tyron.completion.provider;

import static com.google.common.truth.Truth.assertThat;
import static com.tyron.completion.TestUtil.resolveBasePath;

import com.tyron.builder.project.mock.MockAndroidProject;
import com.tyron.builder.project.mock.MockFileManager;
import com.tyron.completion.CompletionModule;
import com.tyron.completion.TestUtil;
import com.tyron.completion.model.CompletionList;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, resourceDir = Config.NONE)
public abstract class CompletionBase {

    public static final String COMPLETE_IDENTIFIER = "/** @complete */";
    public static final String INSERT_IDENTIFIER = "/** @insert */";

    private File mRoot;
    private MockFileManager mFileManager;
    private MockAndroidProject mProject;
    private CompletionEngine mCompletionEngine;

    @Before
    public void setup() throws IOException {
        CompletionModule.setAndroidJar(new File(resolveBasePath(), "classpath/rt.jar"));
        CompletionModule.setLambdaStubs(new File(resolveBasePath(), "classpath/core-lambda-stubs.jar"));

        mRoot = new File(TestUtil.resolveBasePath(), "EmptyProject");
        mFileManager = new MockFileManager(mRoot);
        mProject = new MockAndroidProject(mRoot, mFileManager);
        mProject.open();

        File[] testFiles = new File(mRoot, "completion").listFiles(c ->
                c.getName().endsWith(".java"));
        if (testFiles != null) {
            for (File testFile : testFiles) {
                mProject.addJavaFile(testFile);
            }
        }

        mCompletionEngine = CompletionEngine.getInstance();
    }

    protected CompletionList complete(File file, String contents, long cursor) {
        try {
            return mCompletionEngine.complete(mProject,
                    file,
                    contents,
                    cursor);
        } catch (InterruptedException e) {
            return CompletionList.EMPTY;
        }
    }
    protected CompletionList complete(String fileName, long cursor) {
        File file = new File(mProject.getRootFile(), "completion/" + fileName);
        CharSequence contents = mFileManager.getFileContent(file).get();
        return complete(file, contents.toString(), cursor);
    }

    protected CompletionList completeHandle(String fileName, String contents) {
        File file = new File(mProject.getRootFile(), "completion/" + fileName);
        assertThat(contents)
                .contains(COMPLETE_IDENTIFIER);
        long cursor = (long) contents.indexOf(COMPLETE_IDENTIFIER);
        String newContents = contents.replace(COMPLETE_IDENTIFIER, "");
        return complete(file, newContents, cursor);
    }

    /**
     * Method to replace the {@code \\/** @insert *\\/ } and then complete the file
     * automatically appends the COMPLETE_IDENTIFIER to the replace string
     */
    protected CompletionList completeInsertHandle(String fileName, String replace) {
        File file = new File(mProject.getRootFile(), "completion/" + fileName);
        CharSequence contents = mFileManager.getFileContent(file).get();
        assertThat(contents.toString())
                .contains(INSERT_IDENTIFIER);
        String newContents = contents.toString().replace(INSERT_IDENTIFIER, replace + COMPLETE_IDENTIFIER);
        return completeHandle(fileName, newContents);
    }

    public void assertCompletion(CompletionList list, String... expectedItems) {
        assertThat(list.items.stream().map(it -> it.label).collect(Collectors.toList()))
                .containsAtLeastElementsIn(expectedItems);
    }
}
