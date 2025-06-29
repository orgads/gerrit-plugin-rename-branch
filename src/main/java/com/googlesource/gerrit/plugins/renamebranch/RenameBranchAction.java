// Copyright (C) 2025 AudioCodes Ltd.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.renamebranch;

import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class RenameBranchAction extends RenameBranch implements UiAction<ProjectResource> {
  private final Provider<CurrentUser> userProvider;

  @Inject
  RenameBranchAction(
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      ProjectCache projectCache,
      BatchUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil,
      Provider<CurrentUser> userProvider) {
    super(permissionBackend, repoManager, queryProvider, projectCache, updateFactory, cmUtil);
    this.userProvider = userProvider;
  }

  @Override
  public UiAction.Description getDescription(ProjectResource rsrc) {
    CurrentUser user = userProvider.get();
    boolean canRename = false;
    try {
      if (user.isIdentifiedUser()) {
        canRename = canRenameBranch(user.asIdentifiedUser());
      }
    } catch (PermissionBackendException e) {
    }
    return new UiAction.Description()
        .setLabel("Rename Branch")
        .setTitle(String.format("Rename branches in project %s", rsrc.getName()))
        .setEnabled(canRename)
        .setVisible(canRename);
  }
}
