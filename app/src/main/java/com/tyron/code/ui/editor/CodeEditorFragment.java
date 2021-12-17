package com.tyron.code.ui.editor;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.builder.compiler.manifest.xml.XmlFormatPreferences;
import com.tyron.builder.compiler.manifest.xml.XmlFormatStyle;
import com.tyron.builder.compiler.manifest.xml.XmlPrettyPrinter;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.R;
import com.tyron.code.ui.editor.language.LanguageManager;
import com.tyron.code.ui.editor.language.java.JavaLanguage;
import com.tyron.code.ui.editor.shortcuts.ShortcutAction;
import com.tyron.code.ui.editor.shortcuts.ShortcutItem;
import com.tyron.code.ui.layoutEditor.LayoutEditorFragment;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.code.util.ProjectUtils;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.completion.ParseTask;
import com.tyron.completion.Parser;
import com.tyron.completion.action.CodeActionProvider;
import com.tyron.completion.model.CodeAction;
import com.tyron.completion.model.CodeActionList;
import com.tyron.completion.model.Range;
import com.tyron.completion.model.TextEdit;
import com.tyron.completion.provider.CompletionEngine;
import com.tyron.completion.provider.CompletionProvider;
import com.tyron.completion.rewrite.AddImport;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import io.github.rosemoe.sora.interfaces.EditorEventListener;
import io.github.rosemoe.sora.interfaces.EditorLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;

@SuppressWarnings("FieldCanBeLocal")
public class CodeEditorFragment extends Fragment
        implements Savable, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LAYOUT_EDITOR_PREFIX = "editor_";

    private CodeEditor mEditor;

    private EditorLanguage mLanguage;
    private File mCurrentFile = new File("");
    private MainViewModel mMainViewModel;
    private SharedPreferences mPreferences;

    public static CodeEditorFragment newInstance(File file) {
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putString("path", file.getAbsolutePath());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCurrentFile = new File(requireArguments().getString("path", ""));
        mMainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (ProjectManager.getInstance().getCurrentProject() != null) {
            ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile)
                    .getFileManager()
                    .setSnapshotContent(mCurrentFile, mEditor.getText().toString());
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        hideEditorWindows();

        if (ProjectManager.getInstance().getCurrentProject() != null) {
            ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile)
                    .getFileManager()
                    .setSnapshotContent(mCurrentFile, mEditor.getText().toString());
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!CompletionEngine.isIndexing()) {
            mEditor.analyze();
        }
        if (BottomSheetBehavior.STATE_HIDDEN == mMainViewModel.getBottomSheetState().getValue()) {
            mMainViewModel.setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    public void hideEditorWindows() {
        mEditor.getTextActionPresenter().onExit();
        mEditor.hideAutoCompleteWindow();
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!CompletionEngine.isIndexing()) {
            mEditor.analyze();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        View root = inflater.inflate(R.layout.code_editor_fragment, container, false);

        mEditor = root.findViewById(R.id.code_editor);
        mEditor.setEditorLanguage(mLanguage =
                LanguageManager.getInstance().get(mEditor, mCurrentFile));
        mEditor.setColorScheme(new SchemeDarcula());
        mEditor.setOverScrollEnabled(false);
        mEditor.setTextSize(
                Integer.parseInt(mPreferences.getString(SharedPreferenceKeys.FONT_SIZE, "12")));
        mEditor.setCurrentFile(mCurrentFile);
        mEditor.setTextActionMode(CodeEditor.TextActionMode.POPUP_WINDOW);
        mEditor.setTypefaceText(ResourcesCompat.getFont(requireContext(),
                R.font.jetbrains_mono_regular));
        mEditor.setLigatureEnabled(true);
        mEditor.setHighlightCurrentBlock(true);
        mEditor.setAllowFullscreen(false);
        mEditor.setEdgeEffectColor(Color.TRANSPARENT);
        mEditor.setWordwrap(mPreferences.getBoolean(SharedPreferenceKeys.EDITOR_WORDWRAP, false));
        mEditor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if (mPreferences.getBoolean(SharedPreferenceKeys.KEYBOARD_ENABLE_SUGGESTIONS, false)) {
            mEditor.setInputType(EditorInfo.TYPE_CLASS_TEXT |
                    EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            mEditor.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                    EditorInfo.TYPE_CLASS_TEXT |
                    EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE |
                    EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        }
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        return root;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
        if (mEditor == null) {
            return;
        }
        switch (key) {
            case SharedPreferenceKeys.FONT_SIZE:
                mEditor.setTextSize(Integer.parseInt(pref.getString(key, "14")));
                break;
            case SharedPreferenceKeys.KEYBOARD_ENABLE_SUGGESTIONS:
                if (pref.getBoolean(key, false)) {
                    mEditor.setInputType(EditorInfo.TYPE_CLASS_TEXT |
                            EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
                } else {
                    mEditor.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                            EditorInfo.TYPE_CLASS_TEXT |
                            EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE |
                            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                }
                break;
            case SharedPreferenceKeys.EDITOR_WORDWRAP:
                mEditor.setWordwrap(pref.getBoolean(key, false));
                break;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Module module;
        Project currentProject = ProjectManager.getInstance().getCurrentProject();
        if (currentProject != null) {
            module = currentProject.getModule(mCurrentFile);
        } else {
            module = null;
        }
        if (mCurrentFile.exists()) {
            String text;
            try {
                text = FileUtils.readFileToString(mCurrentFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = "File does not exist: " + e.getMessage();
            }
            if (module != null) {
                module.getFileManager().openFileForSnapshot(mCurrentFile, text);
            }
            mEditor.setText(text);
        }

        mEditor.setOnCompletionItemSelectedListener((window, item) -> {
            Cursor cursor = mEditor.getCursor();
            if (!cursor.isSelected()) {
                window.setCancelShowUp(true);

                int length = window.getLastPrefix().length();
                if (window.getLastPrefix().contains(".")) {
                    length -= window.getLastPrefix().lastIndexOf(".") + 1;
                }
                mEditor.getText().delete(cursor.getLeftLine(),
                        cursor.getLeftColumn() - length,
                        cursor.getLeftLine(), cursor.getLeftColumn());

                window.setSelectedItem(item.commit);
                cursor.onCommitMultilineText(item.commit);

                if (item.commit != null && item.cursorOffset != item.commit.length()) {
                    int delta = (item.commit.length() - item.cursorOffset);
                    int newSel = Math.max(mEditor.getCursor().getLeft() - delta, 0);
                    CharPosition charPosition = mEditor.getCursor()
                            .getIndexer().getCharPosition(newSel);
                    mEditor.setSelection(charPosition.line, charPosition.column);
                }

                if (item.item == null) {
                    return;
                }

                if (item.item.additionalTextEdits != null) {
                    for (TextEdit edit : item.item.additionalTextEdits) {
                        window.applyTextEdit(edit);
                    }
                }

                if (item.item.action == com.tyron.completion.model.CompletionItem.Kind.IMPORT) {
                    if (module instanceof JavaModule) {
                        Parser parser = Parser.parseFile(currentProject,
                                mEditor.getCurrentFile().toPath());
                        ParseTask task = new ParseTask(parser.task, parser.root);

                        boolean samePackage = false;
                        //it's either in the same class or it's already imported
                        if (!item.item.data.contains(".") || task.root.getPackageName().toString()
                                .equals(item.item.data.substring(0, item.item.data.lastIndexOf(".")))) {
                            samePackage = true;
                        }

                        if (!samePackage && !CompletionProvider.hasImport(task.root, item.item.data)) {
                            AddImport imp = new AddImport(new File(""), item.item.data);
                            Map<File, TextEdit> edits = imp.getText(task);
                            TextEdit edit = edits.values().iterator().next();
                            window.applyTextEdit(edit);
                        }
                    }
                }
                window.setCancelShowUp(false);
            }
            mEditor.postHideCompletionWindow();
        });
        mEditor.setOnLongPressListener((start, end, event) -> {
            ProgressDialog dialog = new ProgressDialog(requireContext());
            dialog.setMessage("Analyzing");
            dialog.show();

            Executors.newSingleThreadExecutor().execute(() -> {
                save();

                List<CodeActionList> actions = getCodeActions();
                if (getActivity() != null && mEditor != null) {
                    mEditor.postDelayed(() -> {
                        dialog.dismiss();
                        mEditor.setOnCreateContextMenuListener((menu, view1, contextMenuInfo) -> {
                            for (final CodeActionList action : actions) {
                                if (action.getActions().isEmpty()) {
                                    continue;
                                }
                                menu.add(action.getTitle()).setOnMenuItemClickListener(menuItem -> {
                                    new MaterialAlertDialogBuilder(CodeEditorFragment.this.requireContext())
                                            .setTitle(action.getTitle())
                                            .setItems(action.getActions().stream()
                                                            .map(CodeAction::getTitle)
                                                            .toArray(String[]::new),
                                                    ((dialogInterface, i) -> {
                                                        CodeAction codeAction = action.getActions().get(i);
                                                        Map<Path, List<TextEdit>> rewrites =
                                                                codeAction.getEdits();
                                                        List<TextEdit> edits = rewrites.values()
                                                                .iterator().next();
                                                        for (TextEdit edit : edits) {
                                                            Range range = edit.range;
                                                            if (range.start.equals(range.end)) {
                                                                mEditor.getText()
                                                                        .insert(range.start.line,
                                                                                range.start.column,
                                                                                edit.newText);
                                                            } else {
                                                                mEditor.getText()
                                                                        .replace(range.start.line,
                                                                                range.start.column,
                                                                                range.end.line,
                                                                                range.end.column,
                                                                                edit.newText);
                                                            }
                                                            int startFormat = mEditor.getText()
                                                                    .getCharIndex(range.start.line,
                                                                            range.start.column);
                                                            int endFormat = startFormat +
                                                                    edit.newText.length();
                                                            mEditor.formatCodeAsync(startFormat, endFormat);
                                                        }
                                                    })).show();
                                    return true;
                                });
                            }
                        });
                        mEditor.showContextMenu(event.getX(), event.getY());
                    }, 300);
                }
            });
        });
        mEditor.setEventListener(new EditorEventListener() {
            @Override
            public boolean onRequestFormat(@NonNull CodeEditor editor) {
                return false;
            }

            @Override
            public boolean onFormatFail(@NonNull CodeEditor editor, Throwable cause) {
                ApplicationLoader.showToast("Unable to format: " + cause.getMessage());
                return false;
            }

            @Override
            public void onFormatSucceed(@NonNull CodeEditor editor) {

            }

            @Override
            public void onNewTextSet(@NonNull CodeEditor editor) {
                updateFile(editor.getText().toString());
            }

            @Override
            public void afterDelete(@NonNull CodeEditor editor,
                                    @NonNull CharSequence content,
                                    int startLine, int startColumn,
                                    int endLine, int endColumn,
                                    CharSequence deletedContent) {
                updateFile(content);
            }

            @Override
            public void afterInsert(@NonNull CodeEditor editor,
                                    @NonNull CharSequence content,
                                    int startLine, int startColumn,
                                    int endLine, int endColumn,
                                    CharSequence insertedContent) {
                updateFile(content);
            }

            @Override
            public void beforeReplace(@NonNull CodeEditor editor, @NonNull CharSequence content) {
                updateFile(content);
            }

            @Override
            public void onSelectionChanged(@NonNull CodeEditor editor, @NonNull Cursor cursor) {

            }

            private void updateFile(CharSequence contents) {
                if (module != null) {
                    module.getFileManager().setSnapshotContent(mCurrentFile, contents.toString());
                }
            }
        });
        getChildFragmentManager().setFragmentResultListener(LayoutEditorFragment.KEY_SAVE,
                getViewLifecycleOwner(), ((requestKey, result) -> {
                    String xml = result.getString("text", mEditor.getText().toString());
                    xml = XmlPrettyPrinter.prettyPrint(xml, XmlFormatPreferences.defaults(),
                            XmlFormatStyle.LAYOUT, "\n");
                    mEditor.setText(xml);
                }));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ProjectManager.getInstance().getCurrentProject() != null) {
            ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile)
                    .getFileManager()
                    .closeFileForSnapshot(mCurrentFile);
        }
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void save() {
        if (mCurrentFile.exists()) {
            String oldContents = "";
            try {
                oldContents = FileUtils.readFileToString(mCurrentFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (oldContents.equals(mEditor.getText().toString())) {
                return;
            }

            try {
                FileUtils.writeStringToFile(mCurrentFile, mEditor.getText().toString());
            } catch (IOException e) {
                // ignored
            }
        }
    }

    public void undo() {
        if (mEditor == null) {
            return;
        }
        if (mEditor.canUndo()) {
            mEditor.undo();
        }
    }

    public void redo() {
        if (mEditor == null) {
            return;
        }
        if (mEditor.canRedo()) {
            mEditor.redo();
        }
    }

    public void setCursorPosition(int line, int column) {
        if (mEditor != null) {
            mEditor.getCursor()
                    .set(line, column);
        }
    }

    public void performShortcut(ShortcutItem item) {
        for (ShortcutAction action : item.actions) {
            if (action.isApplicable(item.kind)) {
                action.apply(mEditor, item);
            }
        }
    }

    public void format() {
        if (mEditor != null) {
            mEditor.formatCodeAsync();
        }
    }

    public void analyze() {
        if (mEditor != null) {
            mEditor.analyze();
        }
    }

    public void preview() {

        File currentFile = mEditor.getCurrentFile();
        if (ProjectUtils.isLayoutXMLFile(currentFile)) {
            getChildFragmentManager().beginTransaction()
                    .add(R.id.layout_editor_container, LayoutEditorFragment.newInstance(currentFile))
                    .addToBackStack(null)
                    .commit();
        } else {
            // TODO: handle unknown files
        }
    }

    private List<CodeActionList> getCodeActions() {
        Project project = ProjectManager.getInstance().getCurrentProject();
        if (project == null) {
            return Collections.emptyList();
        }
        Module module = project.getModule(mCurrentFile);
        if (mLanguage instanceof JavaLanguage && module != null) {
            final Path current = mEditor.getCurrentFile().toPath();
            CodeActionProvider provider =
                    new CodeActionProvider(CompletionEngine.getInstance()
                            .getCompiler(project, (JavaModule) module));
            return provider.codeActionsForCursor(current, mEditor.getCursor().getLeft());
        }
        return Collections.emptyList();
    }
}
