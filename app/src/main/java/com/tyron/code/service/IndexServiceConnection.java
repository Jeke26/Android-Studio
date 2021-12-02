package com.tyron.code.service;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tyron.ProjectManager;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.log.LogViewModel;
import com.tyron.builder.model.ProjectSettings;
import com.tyron.builder.project.api.Project;
import com.tyron.code.ui.main.MainViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the communication between the Index service and the main fragment
 */
public class IndexServiceConnection implements ServiceConnection {

    private final MainViewModel mMainViewModel;
    private final LogViewModel mLogViewModel;
    private final ILogger mLogger;
    private Project mProject;

    public IndexServiceConnection(MainViewModel mainViewModel, LogViewModel logViewModel) {
        mMainViewModel = mainViewModel;
        mLogViewModel = logViewModel;
        mLogger = ILogger.wrap(logViewModel);
    }

    public void setProject(Project project) {
        mProject = project;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        IndexService.IndexBinder binder = (IndexService.IndexBinder) iBinder;
        binder.index(mProject, new TaskListener(), mLogger);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mMainViewModel.setIndexing(false);
        mMainViewModel.setCurrentState(null);
    }

    private List<File> getOpenedFiles(ProjectSettings settings) {
        String openedFilesString = settings.getString(ProjectSettings.SAVED_EDITOR_FILES, null);
        if (openedFilesString != null) {
            List<String> paths = new Gson().fromJson(openedFilesString,
                    new TypeToken<List<String>>(){}.getType());
            return paths.stream()
                    .map(File::new)
                    .filter(File::exists)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private class TaskListener implements ProjectManager.TaskListener {

        @Override
        public void onTaskStarted(String message) {
            mMainViewModel.setCurrentState(message);
        }

        @SuppressWarnings("ConstantConditions")
        @Override
        public void onComplete(boolean success, String message) {
            mMainViewModel.setIndexing(false);
            mMainViewModel.setCurrentState(null);
            if (success) {
                Project project = ProjectManager.getInstance().getCurrentProject();
                if (project != null) {
                    mMainViewModel.setToolbarTitle(project.getRootFile().getName());
                    mMainViewModel.setFiles(getOpenedFiles(project.getSettings()));
                }
            } else {
                if (mMainViewModel.getBottomSheetState().getValue()
                        != BottomSheetBehavior.STATE_EXPANDED) {
                    mMainViewModel.setBottomSheetState(BottomSheetBehavior.STATE_HALF_EXPANDED);
                }
                mLogViewModel.e(LogViewModel.BUILD_LOG, message);
            }
        }
    }
}
