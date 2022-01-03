package com.tyron.code.ui.editor.impl.text.rosemoe;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;
import com.github.javaparser.ast.observer.AstObserverAdapter;
import com.github.javaparser.ast.observer.ObservableProperty;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.tyron.code.BuildConfig;
import com.tyron.code.ui.editor.Savable;
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
import com.tyron.completion.index.CompilerService;
import com.tyron.completion.java.JavaCompilerProvider;
import com.tyron.completion.java.JavaCompilerService;
import com.tyron.completion.java.ParseTask;
import com.tyron.completion.java.Parser;
import com.tyron.completion.java.action.api.CodeActionManager;
import com.tyron.completion.java.action.api.EditorInterface;
import com.tyron.completion.java.util.ActionUtil;
import com.tyron.completion.java.util.CompilationUnitConverter;
import com.tyron.completion.model.Range;
import com.tyron.completion.model.TextEdit;
import com.tyron.completion.java.provider.CompletionEngine;
import com.tyron.completion.java.rewrite.AddImport;
import com.tyron.completion.model.CompletionItem;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import io.github.rosemoe.sora.interfaces.EditorEventListener;
import io.github.rosemoe.sora.interfaces.EditorLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;

@SuppressWarnings("FieldCanBeLocal")
public class CodeEditorFragment extends Fragment implements Savable,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LAYOUT_EDITOR_PREFIX = "editor_";

    private CodeEditor mEditor;
    private CodeEditorEventListener mEditorEventListener;

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
            ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile).getFileManager().setSnapshotContent(mCurrentFile, mEditor.getText().toString());
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        View root = inflater.inflate(R.layout.code_editor_fragment, container, false);

        mEditor = root.findViewById(R.id.code_editor);
        mEditor.setEditorLanguage(mLanguage = LanguageManager.getInstance().get(mEditor,
                mCurrentFile));
        mEditor.setColorScheme(new SchemeDarcula());
        mEditor.setOverScrollEnabled(false);
        mEditor.setTextSize(Integer.parseInt(mPreferences.getString(SharedPreferenceKeys.FONT_SIZE, "12")));
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
            mEditor.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            mEditor.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS | EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
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
                    mEditor.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
                } else {
                    mEditor.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS | EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
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

        mEditorEventListener = new CodeEditorEventListener(module, mCurrentFile);
        mEditor.setEventListener(mEditorEventListener);
        mEditor.setOnCompletionItemSelectedListener((window, item) -> {
            Cursor cursor = mEditor.getCursor();
            if (!cursor.isSelected()) {
                window.setCancelShowUp(true);

                int length = window.getLastPrefix().length();
                if (window.getLastPrefix().contains(".")) {
                    length -= window.getLastPrefix().lastIndexOf(".") + 1;
                }
                mEditor.getText().delete(cursor.getLeftLine(), cursor.getLeftColumn() - length,
                        cursor.getLeftLine(), cursor.getLeftColumn());

                window.setSelectedItem(item.commit);
                cursor.onCommitMultilineText(item.commit);

                if (item.commit != null && item.cursorOffset != item.commit.length()) {
                    int delta = (item.commit.length() - item.cursorOffset);
                    int newSel = Math.max(mEditor.getCursor().getLeft() - delta, 0);
                    CharPosition charPosition =
                            mEditor.getCursor().getIndexer().getCharPosition(newSel);
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

                if (item.item.action == CompletionItem.Kind.IMPORT) {
                    if (module instanceof JavaModule) {
                        Parser parser = Parser.parseFile(currentProject,
                                mEditor.getCurrentFile().toPath());
                        ParseTask task = new ParseTask(parser.task, parser.root);

                        boolean samePackage = false;
                        String packageName = task.root.getPackageName() == null ? "" :
                                task.root.getPackageName().toString();
                        //it's either in the same class or it's already imported
                        if (!item.item.data.contains(".") || packageName.equals(item.item.data.substring(0, item.item.data.lastIndexOf(".")))) {
                            samePackage = true;
                        }

                        if (!samePackage && !ActionUtil.hasImport(task.root, item.item.data)) {
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
            save();

            if (currentProject == null) {
                return;
            }
            Module currentModule = currentProject.getModule(mCurrentFile);
            if (!(mLanguage instanceof JavaLanguage) && !(currentModule instanceof JavaModule)) {
                return;
            }

            JavaCompilerProvider service =
                    CompilerService.getInstance().getIndex(JavaCompilerProvider.KEY);
            JavaCompilerService compiler = service.getCompiler(currentProject,
                    (JavaModule) currentModule);
            if (!compiler.isReady()) {
                return;
            }

            mEditor.setOnCreateContextMenuListener((menu, view1, contextMenuInfo) -> {
                menu.clear();
                menu.setHeaderTitle("Loading...");
                addCodeActions(menu, compiler);
                menu.clearHeader();
            });
            mEditor.showContextMenu(event.getX(), event.getY());
        });

        getChildFragmentManager().setFragmentResultListener(LayoutEditorFragment.KEY_SAVE,
                getViewLifecycleOwner(), ((requestKey, result) -> {
            String xml = result.getString("text", mEditor.getText().toString());
            xml = XmlPrettyPrinter.prettyPrint(xml, XmlFormatPreferences.defaults(),
                    XmlFormatStyle.LAYOUT, "\n");
            mEditor.setText(xml);
        }));
    }


    private void applyTextEdit(TextEdit edit) {
        int startFormat;
        int endFormat;
        Range range = edit.range;
        if (range.start.line == -1 && range.start.column == -1 || (range.end.line == -1 && range.end.column == -1)) {
            CharPosition startChar =
                    mEditor.getText().getIndexer().getCharPosition((int) range.start.start);
            CharPosition endChar =
                    mEditor.getText().getIndexer().getCharPosition((int) range.end.end);

            if (range.start.start == range.end.end) {
                mEditor.getText().insert(startChar.line, startChar.column, edit.newText);
            } else {
                mEditor.getText().replace(startChar.line, startChar.column, endChar.line,
                        endChar.column, edit.newText);
            }

            startFormat = (int) range.start.start;
            endFormat = startFormat + edit.newText.length();

            String string = mEditor.getText().toStringBuilder().substring(startFormat, endFormat);
            System.out.println(string);
        } else {
            if (range.start.equals(range.end)) {
                mEditor.getText().insert(range.start.line, range.start.column, edit.newText);
            } else {
                mEditor.getText().replace(range.start.line, range.start.column, range.end.line,
                        range.end.column, edit.newText);
            }
            startFormat = mEditor.getText().getCharIndex(range.start.line, range.start.column);
            endFormat = startFormat + edit.newText.length();
        }

        if (startFormat < endFormat) {
            if (edit.needFormat) {
                mEditor.formatCodeAsync(startFormat, endFormat);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mEditorEventListener = null;
        mEditor.setEditorLanguage(null);
        mEditor.setEventListener(null);
        mEditor.setOnLongPressListener(null);
        mEditor.setOnCompletionItemSelectedListener(null);
        mEditor.destroy();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ProjectManager.getInstance().getCurrentProject() != null) {
            ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile).getFileManager().closeFileForSnapshot(mCurrentFile);
        }
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        hideEditorWindows();

        if (ProjectManager.getInstance().getCurrentProject() != null) {
            ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile).getFileManager().setSnapshotContent(mCurrentFile, mEditor.getText().toString());
        }
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
            mEditor.getCursor().set(line, column);
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
            getChildFragmentManager().beginTransaction().add(R.id.layout_editor_container,
                    LayoutEditorFragment.newInstance(currentFile)).addToBackStack(null).commit();
        } else {
            // TODO: handle unknown files
            JavaCompilerProvider service =
                    CompilerService.getInstance().getIndex(JavaCompilerProvider.KEY);
            JavaCompilerService compiler = service.getCompiler(ProjectManager.getInstance().getCurrentProject(),
                    (JavaModule) ProjectManager.getInstance().getCurrentProject().getMainModule());
            ParseTask parse = compiler.parse(mCurrentFile.toPath(), mEditor.getText().toString());
            CompilationUnitConverter compilationUnitConverter = new CompilationUnitConverter(parse, mEditor.getText().toString(), new CompilationUnitConverter.LineColumnCallback() {
                @Override
                public int getLine(int pos) {
                    return mEditor.getText().getIndexer().getCharLine(pos);
                }

                @Override
                public int getColumn(int pos) {
                    return mEditor.getText().getIndexer().getCharColumn(pos);
                }
            });
            CompilationUnit compilationUnit = compilationUnitConverter.startScan();
            compilationUnit.register(new AstObserverAdapter() {
                @Override
                public void listChange(NodeList<?> observedNode, ListChangeType type, int index,
                                       Node node) {
                    if (type == ListChangeType.ADDITION) {
                        Optional<com.github.javaparser.Range> optionalRange = node.getRange();
                        com.github.javaparser.Range range = optionalRange.get();
                        mEditor.getText().insert(range.begin.line, range.begin.column - 1, node.toString());
                    }
                    System.out.println(observedNode + ", type: " + type + ", added: " + node.getRange());
                }

                @Override
                public void propertyChange(Node observedNode, ObservableProperty property,
                                           Object oldValue, Object newValue) {
                    Optional<com.github.javaparser.Range> optionalRange = observedNode.getRange();
                    if (oldValue instanceof NodeWithRange) {
                        optionalRange = ((NodeWithRange<?>) oldValue).getRange();
                    } else {
                        optionalRange = observedNode.getRange();
                    }
                    if (!optionalRange.isPresent()) {
                        return;
                    }
                    com.github.javaparser.Range range = optionalRange.get();
                    mEditor.getText().replace(range.begin.line, range.begin.column, range.end.line, range.end.column, "");
                    mEditor.getText().insert(range.begin.line, range.begin.column, newValue.toString());
                    mEditor.hideAutoCompleteWindow();
                }
            }, Node.ObserverRegistrationMode.THIS_NODE_AND_EXISTING_DESCENDANTS);
            System.out.println(compilationUnit);
        }
    }

    private void addCodeActions(Menu menu, JavaCompilerService compiler) {
        if (compiler == null) {
            return;
        }
        try {
            final Path current = mEditor.getCurrentFile().toPath();
            CodeActionManager.getInstance().addActions(requireContext(), menu, compiler, current, mEditor.getCursor().getLeft(), new EditorInterface() {
                @Override
                public int getCharIndex(int line , int column) {
                    return mEditor.getText().getCharIndex(line, column);
                }

                @Override
                public CharPositionWrapper getCharPosition(int index) {
                    CharPosition charPosition =
                            mEditor.getText().getIndexer().getCharPosition(index);
                    CharPositionWrapper wrapper = new CharPositionWrapper();
                    wrapper.column = charPosition.column;
                    wrapper.index = charPosition.index;
                    wrapper.line = charPosition.line;
                    return wrapper;
                }

                @Override
                public void insert(int line, int column, String string) {
                    mEditor.getText().insert(line, column, string);
                }

                @Override
                public void replace(int line, int column, int endLine, int endColumn,
                                    String string) {
                    mEditor.getText().replace(line, column, endLine, endColumn, string);
                }

                @Override
                public void formatCodeAsync(int startIndex, int endIndex) {
                    mEditor.formatCodeAsync();
                }
            });
        } catch (Throwable e) {
            compiler.close();
            if (BuildConfig.DEBUG) {
                Log.d("getCodeActions()", "Unable to get code actions", e);
            }
        }
    }

    private static final class CodeEditorEventListener implements EditorEventListener {

        private final Module mModule;
        private final File mCurrentFile;

        public CodeEditorEventListener(Module module, File currentFile) {
            mModule = module;
            mCurrentFile = currentFile;
        }

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
        public void afterDelete(@NonNull CodeEditor editor, @NonNull CharSequence content,
                                int startLine, int startColumn, int endLine, int endColumn,
                                CharSequence deletedContent) {
            updateFile(content);
        }

        @Override
        public void afterInsert(@NonNull CodeEditor editor, @NonNull CharSequence content,
                                int startLine, int startColumn, int endLine, int endColumn,
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
            if (mModule != null) {
                mModule.getFileManager().setSnapshotContent(mCurrentFile, contents.toString());
            }
        }
    }
}
