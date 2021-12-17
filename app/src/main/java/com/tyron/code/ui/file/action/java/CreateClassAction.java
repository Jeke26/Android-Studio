package com.tyron.code.ui.file.action.java;

import android.view.SubMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.R;
import com.tyron.code.template.CodeTemplate;
import com.tyron.code.template.java.AbstractTemplate;
import com.tyron.code.template.java.InterfaceTemplate;
import com.tyron.code.template.java.JavaClassTemplate;
import com.tyron.code.ui.component.tree.TreeNode;
import com.tyron.code.ui.file.dialog.CreateClassDialogFragment;
import com.tyron.code.ui.file.action.ActionContext;
import com.tyron.code.ui.file.action.FileAction;
import com.tyron.code.ui.file.tree.model.TreeFile;
import com.tyron.code.util.ProjectUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateClassAction extends FileAction {
    @Override
    public boolean isApplicable(File file) {
        if (file.isDirectory()) {
            return ProjectUtils.getPackageName(file) != null;
        }
        return false;
    }

    @Override
    public void addMenu(ActionContext context) {
        SubMenu subMenu = context.addSubMenu("new",
                context.getFragment().getString(R.string.menu_new));
        subMenu.add(R.string.menu_action_new_java_class)
                .setOnMenuItemClickListener(item -> {
                    CreateClassDialogFragment dialogFragment =
                            CreateClassDialogFragment.newInstance(getTemplates(),
                                    Collections.emptyList());
                    dialogFragment.show(context.getFragment().getChildFragmentManager(), null);
                    dialogFragment.setOnClassCreatedListener((className, template) -> {
                        try {
                            File createdFile = ProjectManager.createClass(
                                    context.getCurrentNode().getContent().getFile(),
                                    className, template
                            );
                            TreeNode<TreeFile> newNode = new TreeNode<>(
                                    TreeFile.fromFile(createdFile),
                                    context.getCurrentNode().getLevel() + 1
                            );

                            context.getTreeView().addNode(context.getCurrentNode(), newNode);
                            context.getTreeView().refreshTreeView();
                            context.getFragment().getMainViewModel()
                                    .addFile(createdFile);

                            Module currentModule = ProjectManager.getInstance()
                                    .getCurrentProject()
                                    .getModule(context.getCurrentNode().getContent().getFile());
                            if (currentModule instanceof JavaModule) {
                                ((JavaModule) currentModule).addJavaFile(createdFile);
                            }
                        } catch (IOException e) {
                            new MaterialAlertDialogBuilder(context.getFragment().requireContext())
                                    .setMessage(e.getMessage())
                                    .setPositiveButton(android.R.string.ok, null)
                                    .setTitle(R.string.error)
                                    .show();
                        }
                    });

                    return true;
                });
    }

    private List<CodeTemplate> getTemplates() {
        return Arrays.asList(
                new JavaClassTemplate(),
                new AbstractTemplate(),
                new InterfaceTemplate());
    }
}
