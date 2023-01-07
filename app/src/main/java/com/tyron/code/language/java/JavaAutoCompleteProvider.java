package com.tyron.code.language.java;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.language.AbstractAutoCompleteProvider;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.main.CompletionEngine;
import com.tyron.completion.model.CompletionList;
import com.tyron.editor.Content;
import com.tyron.editor.Editor;

import java.util.Optional;

import io.github.rosemoe.sora.lang.completion.CompletionHelper;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.CodeEditor;

public class JavaAutoCompleteProvider extends AbstractAutoCompleteProvider {

    private final Editor mEditor;
    private final SharedPreferences mPreferences;

    public JavaAutoCompleteProvider(Editor editor) {
        mEditor = editor;
        mPreferences = ApplicationLoader.getDefaultPreferences();
    }


    @Nullable
    @Override
    public CompletionList getCompletionList(String prefix, int line, int column) {
        if (!mPreferences.getBoolean(SharedPreferenceKeys.JAVA_CODE_COMPLETION, true)) {
            return null;
        }

        Project project = ProjectManager.getInstance().getCurrentProject();

        if (project == null) {
            return null;
        }

        Module currentModule = project.getModule(mEditor.getCurrentFile());

        if (currentModule instanceof JavaModule) {
            Content content = mEditor.getContent();
            return CompletionEngine.getInstance()
                    .complete(project,
                            currentModule,
                            mEditor,
                            mEditor.getCurrentFile(),
                            content.toString(),
                            prefix,
                            line,
                            column,
                            mEditor.getCaret().getStart());
        }
        return null;
    }

    @Override
    public String getPrefix(Editor editor, int line, int column) {
        Content content = editor.getContent();
        int end = editor.getCharIndex(line, column);
        int start = end;
        while (start > 0 && Character.isJavaIdentifierPart(content.charAt(start - 1))) {
            start--;
        }
        return content.subSequence(start, end).toString();
    }
}
