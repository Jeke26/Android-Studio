package com.tyron.code.ui.file;

import android.os.Environment;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.tyron.code.ui.component.tree.TreeNode;
import com.tyron.code.ui.file.tree.TreeUtil;
import com.tyron.code.ui.file.tree.model.TreeFile;

import java.io.File;
import java.util.concurrent.Executors;

public class FileViewModel extends ViewModel {

    private MutableLiveData<File> mRoot =
            new MutableLiveData<>(Environment.getExternalStorageDirectory());
    private MutableLiveData<TreeNode<TreeFile>> mNode = new MutableLiveData<>();

    public LiveData<TreeNode<TreeFile>> getNodes() {
        return mNode;
    }

    public LiveData<File> getRootFile() {
        return mRoot;
    }

    public void setRootFile(File root) {
        mRoot.setValue(root);
        refreshNode(root);
    }

    public void refreshNode(File root) {
        Executors.newSingleThreadExecutor().execute(() -> {
            TreeNode<TreeFile> node = TreeNode.root(TreeUtil.getNodes(root));
            mNode.postValue(node);
        });
    }
}
