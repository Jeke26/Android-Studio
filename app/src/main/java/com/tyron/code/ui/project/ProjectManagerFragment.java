package com.tyron.code.ui.project;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.transition.MaterialFade;
import com.google.android.material.transition.MaterialSharedAxis;
import com.tyron.builder.project.api.Project;
import com.tyron.builder.project.impl.AndroidProjectImpl;
import com.tyron.code.R;
import com.tyron.code.ui.file.FilePickerDialogFixed;
import com.tyron.code.ui.main.MainFragment;
import com.tyron.code.ui.project.adapter.ProjectManagerAdapter;
import com.tyron.code.ui.wizard.WizardFragment;
import com.tyron.common.SharedPreferenceKeys;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;

public class ProjectManagerFragment extends Fragment {

    public static final String TAG = ProjectManagerFragment.class.getSimpleName();

    private SharedPreferences mPreferences;
    private RecyclerView mRecyclerView;
    private ProjectManagerAdapter mAdapter;
    private ExtendedFloatingActionButton mCreateProjectFab;
    private boolean mShowDialogOnPermissionGrant;
    private ActivityResultLauncher<String[]> mPermissionLauncher;
    private final ActivityResultContracts.RequestMultiplePermissions mPermissionsContract =
            new ActivityResultContracts.RequestMultiplePermissions();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
        mPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        mPermissionLauncher = registerForActivityResult(mPermissionsContract, isGranted -> {
            if (isGranted.containsValue(false)) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Permission denied")
                        .setMessage("Projects will be saved to the app's internal storage directory. " +
                                "Backup your projects before uninstalling the app.")
                        .setPositiveButton("Request again", (d, which) -> {
                            mShowDialogOnPermissionGrant = true;
                            requestPermissions();
                        })
                        .setNegativeButton("Continue", (d, which) -> {
                            mShowDialogOnPermissionGrant = false;
                            setSavePath(Environment.getExternalStorageDirectory().getAbsolutePath());
                        })
                        .show();
                setSavePath(Environment.getExternalStorageDirectory().getAbsolutePath());
            } else {
                if (mShowDialogOnPermissionGrant) {
                    mShowDialogOnPermissionGrant = false;
                    showDirectorySelectDialog();
                }
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        NestedScrollView scrollView = view.findViewById(R.id.scrolling_view);
        scrollView.setNestedScrollingEnabled(false);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);

        mCreateProjectFab = view.findViewById(R.id.create_project_fab);
        mCreateProjectFab.setOnLongClickListener(v -> {
            setSavePath(null);
            checkSavePath();
            return true;
        });
        mCreateProjectFab.setOnClickListener(v -> {
            WizardFragment wizardFragment = new WizardFragment();
            wizardFragment.setOnProjectCreatedListener(this::openProject);
            getParentFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, wizardFragment)
                    .addToBackStack(null)
                    .commit();
        });
        mAdapter = new ProjectManagerAdapter();
        mAdapter.setOnProjectSelectedListener(this::openProject);
        mRecyclerView = view.findViewById(R.id.projects_recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mAdapter);

        checkSavePath();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.project_manager_fragment, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        checkSavePath();
    }

    private void checkSavePath() {
        String path = mPreferences.getString(SharedPreferenceKeys.PROJECT_SAVE_PATH, null);
        if (path == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (permissionsGranted()) {
                showDirectorySelectDialog();
            } else if (shouldShowRequestPermissionRationale()) {
                if (shouldShowRequestPermissionRationale()) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setMessage("The application needs storage permissions in order to " +
                                    "save project files that will not be deleted when you uninstall " +
                                    "the app. Alternatively you can choose to " +
                                    "save project files into the app's internal storage.")
                            .setPositiveButton("Allow", (d, which) -> {
                                mShowDialogOnPermissionGrant = true;
                                requestPermissions();
                            })
                            .setNegativeButton("Use internal storage", (d, which) -> {
                                setSavePath(Environment.getExternalStorageDirectory().getAbsolutePath());
                            })
                            .setTitle("Storage permissions")
                            .show();
                }
            } else {
                requestPermissions();
            }
        } else {
            loadProjects();
        }
    }

    private void setSavePath(String path) {
        mPreferences.edit()
                .putString(SharedPreferenceKeys.PROJECT_SAVE_PATH, path)
                .apply();
        loadProjects();
    }

    private void showDirectorySelectDialog() {
        DialogProperties properties = new DialogProperties();
        properties.selection_type = DialogConfigs.DIR_SELECT;
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.root = Environment.getExternalStorageDirectory();
        FilePickerDialogFixed dialogFixed = new FilePickerDialogFixed(requireContext(), properties);
        dialogFixed.setTitle("Select save location");
        dialogFixed.setDialogSelectionListener(files -> {
            setSavePath(files[0]);
            loadProjects();
        });
        dialogFixed.show();
    }

    private boolean permissionsGranted() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean shouldShowRequestPermissionRationale() {
        return shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermissions() {
        mPermissionLauncher.launch(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE});
    }

    private void openProject(Project project) {
        MainFragment fragment = MainFragment.newInstance(project.getRootFile().getAbsolutePath());
        getParentFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void loadProjects() {
        toggleLoading(true);

        Executors.newSingleThreadExecutor().execute(() -> {
            String path;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                path = requireContext().getExternalFilesDir("Projects").getAbsolutePath();
            } else {
                path = mPreferences.getString(SharedPreferenceKeys.PROJECT_SAVE_PATH,
                        requireContext().getExternalFilesDir("Projects").getAbsolutePath());
            }
            File projectDir = new File(path);
            File[] directories = projectDir.listFiles(File::isDirectory);

            List<Project> projects = new ArrayList<>();
            if (directories != null) {
                Arrays.sort(directories, Comparator.comparingLong(File::lastModified));
                for (File directory : directories) {
                    Project project = new AndroidProjectImpl(new File(directory.getAbsolutePath()
                            .replaceAll("%20", " ")));
                   // if (project.isValidProject()) {
                        projects.add(project);
                   // }
                }
            }

            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    toggleLoading(false);
                    mAdapter.submitList(projects);
                });
            }
        });
    }

    private void toggleLoading(boolean show) {
        if (getActivity() == null || isDetached()) {
            return;
        }

        View recycler = requireView().findViewById(R.id.projects_recycler);
        View empty = requireView().findViewById(R.id.empty_container);

        TransitionManager.beginDelayedTransition((ViewGroup) recycler.getParent(),
                new MaterialFade());
        if (show) {
            recycler.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            empty.setVisibility(View.GONE);
        }
    }
}
