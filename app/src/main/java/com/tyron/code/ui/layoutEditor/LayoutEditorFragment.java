package com.tyron.code.ui.layoutEditor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.TransitionManager;

import com.flipkart.android.proteus.ProteusContext;
import com.flipkart.android.proteus.ProteusView;
import com.flipkart.android.proteus.ViewTypeParser;
import com.flipkart.android.proteus.exceptions.ProteusInflateException;
import com.flipkart.android.proteus.toolbox.Attributes;
import com.flipkart.android.proteus.toolbox.ProteusHelper;
import com.flipkart.android.proteus.value.Dimension;
import com.flipkart.android.proteus.value.Layout;
import com.flipkart.android.proteus.value.ObjectValue;
import com.flipkart.android.proteus.value.Primitive;
import com.flipkart.android.proteus.value.Value;
import com.flipkart.android.proteus.view.UnknownViewGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.ImmutableMap;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.R;
import com.tyron.code.ui.layoutEditor.attributeEditor.AttributeEditorDialogFragment;
import com.tyron.code.ui.layoutEditor.model.ViewPalette;
import com.tyron.completion.java.provider.CompletionEngine;
import com.tyron.layoutpreview.BoundaryDrawingFrameLayout;
import com.tyron.layoutpreview.convert.LayoutToXmlConverter;
import com.tyron.layoutpreview.inflate.PreviewLayoutInflater;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Pair;

public class LayoutEditorFragment extends Fragment implements ProjectManager.OnProjectOpenListener {

    public static final String KEY_SAVE = "KEY_SAVE";

    /**
     * Creates a new LayoutEditorFragment instance for a layout xml file.
     * Make sure that the file exists and is a valid layout file and that
     * {@code ProjectManager#getCurrentProject} is not null
     */
    public static LayoutEditorFragment newInstance(File file) {
        Bundle args = new Bundle();
        args.putSerializable("file", file);
        LayoutEditorFragment fragment = new LayoutEditorFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private final ExecutorService mService = Executors.newSingleThreadExecutor();
    private LayoutEditorViewModel mEditorViewModel;

    private File mCurrentFile;
    private PreviewLayoutInflater mInflater;
    private BoundaryDrawingFrameLayout mEditorRoot;
    private EditorDragListener mDragListener;

    private LinearLayout mLoadingLayout;
    private TextView mLoadingText;

    private boolean isDumb;

    private final View.OnLongClickListener mOnLongClickListener = v -> {
        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
        ViewCompat.startDragAndDrop(v, null, shadowBuilder, v, 0);
        return true;
    };

    private final View.OnClickListener mOnClickListener = v -> {
        if (v instanceof ProteusView) {
            ProteusView view = (ProteusView) v;
            ProteusView.Manager manager = view.getViewManager();
            ProteusContext context = manager.getContext();
            Layout layout = manager.getLayout();
            ArrayList<Pair<String, String>> attributes = new ArrayList<>();
            for (Layout.Attribute attribute :
                    layout.getAttributes()) {
                String name = ProteusHelper.getAttributeName(view, attribute.id);
                attributes.add(new Pair<>(name, attribute.value.toString()));
            }
            if (layout.extras != null) {
                layout.extras.entrySet().forEach(entry -> {
                    ViewTypeParser<View> parser;
                    int id = ProteusHelper.getAttributeId(view, entry.getKey());
                    if (id == -1) {
                        parser = context.getParser("android.view.View");
                        if (parser != null) {
                            id = parser.getAttributeId(entry.getKey());
                        }
                    }
                    if (id != -1) {
                        String name = ProteusHelper.getAttributeName(view, id);
                        attributes.add(new Pair<>(name, entry.getValue().toString()));
                    }
                });
            }

            ArrayList<Pair<String, String>> availableAttributes = new ArrayList<>();
            manager.getAvailableAttributes().forEach((key, value) -> {
                availableAttributes.add(new Pair<>(key, ""));
            });

            AttributeEditorDialogFragment.newInstance(availableAttributes, attributes)
                    .show(getChildFragmentManager(), null);

            getChildFragmentManager().setFragmentResultListener(
                    AttributeEditorDialogFragment.KEY_ATTRIBUTE_CHANGED,
                    getViewLifecycleOwner(),
                    (requestKey, result) -> {
                manager.updateAttribute(result.getString("key"), result.getString("value"));
                getChildFragmentManager()
                        .clearFragmentResult(AttributeEditorDialogFragment.KEY_ATTRIBUTE_CHANGED);
            });
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCurrentFile = (File) requireArguments().getSerializable("file");
        isDumb = ProjectManager.getInstance().getCurrentProject() == null ||
                CompletionEngine.isIndexing();
        mEditorViewModel = new ViewModelProvider(this)
                .get(LayoutEditorViewModel.class);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Save to xml")
                        .setMessage("Do you want to save the layout?")
                        .setPositiveButton(android.R.string.yes, (d, w) -> {
                            String converted = convertLayoutToXml();
                            if (converted == null) {
                                new MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("Error")
                                        .setMessage("An unknown error has occurred during layout conversion")
                                        .show();
                            } else {
                                Bundle args = new Bundle();
                                args.putString("text", converted);
                                getParentFragmentManager().setFragmentResult(KEY_SAVE,
                                        args);
                            }
                            getParentFragmentManager().popBackStack();
                        })
                        .setNegativeButton(android.R.string.no, (d, w) -> {
                            getParentFragmentManager().popBackStack();
                        })
                        .show();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.layout_editor_fragment, container, false);

        mLoadingLayout = root.findViewById(R.id.loading_root);
        mLoadingText = root.findViewById(R.id.loading_text);

        mEditorRoot = root.findViewById(R.id.editor_root);
        mDragListener = new EditorDragListener(mEditorRoot);
        mDragListener.setInflateCallback((parent, palette) -> {
            Layout layout = new Layout(palette.getClassName());
            ProteusView inflated = mInflater.getContext()
                    .getInflater()
                    .inflate(layout, new ObjectValue(), parent, 0);
            palette.getDefaultValues().forEach((key, value) ->
                    inflated.getViewManager().updateAttribute(key, value.toString()));
            return inflated;
        });
        mDragListener.setDelegate(new EditorDragListener.Delegate() {
            @Override
            public void onAddView(ViewGroup parent, View view) {
                if (view instanceof ViewGroup) {
                    setDragListeners(((ViewGroup) view));
                }
                setClickListeners(view);
                mEditorRoot.postDelayed(() -> mEditorRoot.requestLayout(), 100);

                if (parent instanceof ProteusView && view instanceof ProteusView) {
                    ProteusView proteusParent = (ProteusView) parent;
                    ProteusView proteusChild = (ProteusView) view;
                    ProteusHelper.addChildToLayout(proteusParent, proteusChild);
                }
            }

            @Override
            public void onRemoveView(ViewGroup parent, View view) {
                if (parent instanceof ProteusView && view instanceof ProteusView) {
                    ProteusView proteusParent = (ProteusView) parent;
                    ProteusView proteusChild = (ProteusView) view;
                    ProteusHelper.removeChildFromLayout(proteusParent, proteusChild);
                }
            }
        });
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mEditorViewModel.setPalettes(populatePalettes());
        if (isDumb) {
            ProjectManager.getInstance().addOnProjectOpenListener(this);
            setLoadingText("Indexing");
        } else {
            createInflater();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        ProjectManager.getInstance().removeOnProjectOpenListener(this);
    }

    private void exit(String title, String message) {
        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        getParentFragmentManager().popBackStack();
    }

    private void createInflater() {
        Project currentProject = ProjectManager.getInstance().getCurrentProject();
        if (currentProject == null) {
            exit(getString(R.string.error), "No project opened.");
            return;
        }
        Module module = currentProject.getModule(mCurrentFile);
        if (!(module instanceof AndroidModule)) {
            exit(getString(R.string.error), "Layout preview is only for android projects.");
            return;
        }
        mInflater = new PreviewLayoutInflater(requireContext(), (AndroidModule) module);

        setLoadingText("Parsing xml files");
        mInflater.parseResources(mService).whenComplete((inflater, exception) ->
                requireActivity().runOnUiThread(() -> {
                    if (inflater == null) {
                        exit(getString(R.string.error),
                                "Unable to inflate layout: " + exception.getMessage());
                    } else {
                        afterParse(inflater);
                    }
                }));
    }

    private void afterParse(PreviewLayoutInflater inflater) {
        mInflater = inflater;
        setLoadingText("Inflating xml");
        inflateFile(mCurrentFile);
    }

    private void inflateFile(File file) {
        Optional<ProteusView> optionalView;

        try {
            optionalView = mInflater.inflateLayout(file.getName()
                    .replace(".xml", ""));
        } catch (ProteusInflateException e) {
            optionalView = Optional.empty();
        }
        setLoadingText(null);

        if (optionalView.isPresent()) {
            mEditorRoot.removeAllViews();
            mEditorRoot.addView(optionalView.get().getAsView());
            setDragListeners(mEditorRoot);
            setClickListeners(mEditorRoot);
        } else {
            exit(getString(R.string.error), "Unable to inflate layout.");
        }
    }

    private void setDragListeners(ViewGroup viewGroup) {
        viewGroup.setOnDragListener(mDragListener);

        try {
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            transition.disableTransitionType(LayoutTransition.DISAPPEARING);
            transition.setDuration(180L);
            viewGroup.setLayoutTransition(new LayoutTransition());
        } catch (Throwable e) {
            // ignored, some ViewGroup's may not allow layout transitions
        }
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                setDragListeners(((ViewGroup) child));
            }
        }
    }

    private void setClickListeners(View view) {
        view.setOnLongClickListener(mOnLongClickListener);
        view.setOnClickListener(mOnClickListener);
        if (view instanceof ViewGroup && !(view instanceof UnknownViewGroup)) {
            ViewGroup parent = (ViewGroup) view;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                setClickListeners(child);
            }
        }
    }


    /**
     * Show a loading text to the editor
     *
     * @param message The message to be displayed, pass null to remove the loading text
     */
    private void setLoadingText(@Nullable String message) {
        TransitionManager.beginDelayedTransition((ViewGroup) mLoadingLayout.getParent());
        if (null == message) {
            mLoadingLayout.setVisibility(View.GONE);
        } else {
            mLoadingLayout.setVisibility(View.VISIBLE);
            mLoadingText.setText(message);
        }
    }

    private List<ViewPalette> populatePalettes() {
        List<ViewPalette> palettes = new ArrayList<>();

        palettes.add(createPalette("android.widget.LinearLayout", R.drawable.ic_baseline_vertical_24));
        palettes.add(createPalette("android.widget.FrameLayout", R.drawable.ic_baseline_frame_24));
        palettes.add(createPalette("android.widget.ScrollView", R.drawable.ic_baseline_format_line_spacing_24));
        palettes.add(createPalette("android.widget.HorizontalScrollView", R.drawable.ic_baseline_format_line_spacing_24));

        palettes.add(createPalette("androidx.cardview.widget.CardView", R.drawable.ic_baseline_style_24));

        palettes.add(createPalette("android.widget.Button",
                R.drawable.ic_baseline_crop_16_9_24,
                ImmutableMap.of(Attributes.TextView.Text, new Primitive("Button"))));
        palettes.add(createPalette("android.widget.TextView",
                R.drawable.ic_baseline_text_fields_24,
                ImmutableMap.of(Attributes.TextView.Text, new Primitive("TextView"))));
        palettes.add(createPalette("android.widget.EditText",
                R.drawable.ic_baseline_edit_24,
                ImmutableMap.of(Attributes.TextView.Hint, new Primitive("EditText"))));
        palettes.add(createPalette("android.widget.CheckBox",
                R.drawable.ic_baseline_check_box_24,
                ImmutableMap.of(Attributes.TextView.Text, new Primitive("CheckBox"))));
        palettes.add(createPalette("android.widget.Switch",
                R.drawable.ic_baseline_edit_attributes_24,
                ImmutableMap.of(Attributes.TextView.Text, new Primitive("Switch"))));
        palettes.add(createPalette("android.widget.SeekBar", R.drawable.ic_baseline_swipe_right_alt_24));

        return palettes;
    }

    private ViewPalette createPalette(@NonNull String className, @DrawableRes int icon) {
        return createPalette(className, icon, Collections.emptyMap());
    }

    private ViewPalette createPalette(@NonNull String className,
                                      @DrawableRes int icon,
                                      Map<String, Value> attributes) {
        String name = className.substring(className.lastIndexOf('.') + 1);
        ViewPalette.Builder builder = ViewPalette.builder()
                .setClassName(className)
                .setName(name)
                .setIcon(icon)
                .addDefaultValue(Attributes.View.MinHeight, Dimension.valueOf("25dp"))
                .addDefaultValue(Attributes.View.MinWidth, Dimension.valueOf("50dp"));

        attributes.forEach(builder::addDefaultValue);
        return builder.build();
    }

    @Override
    public void onProjectOpen(Project module) {
        if (isDumb) {
            isDumb = false;
            createInflater();
        }
    }

    private String convertLayoutToXml() {
        if (mInflater != null) {
            LayoutToXmlConverter converter =
                    new LayoutToXmlConverter(mInflater.getContext());
            ProteusView view = (ProteusView) mEditorRoot.getChildAt(0);
            if (view != null) {
                Layout layout = view.getViewManager().getLayout();
                try {
                    return converter.convert(layout.getAsLayout());
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}