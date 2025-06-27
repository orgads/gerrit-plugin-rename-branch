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

import static com.googlesource.gerrit.plugins.renamebranch.RenameBranchCapability.RENAME_BRANCH;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** SSH command for renaming branches. */
@RequiresCapability(RENAME_BRANCH)
@CommandMetaData(name = "rename-branch", description = "Rename a branch")
public class RenameBranchCommand extends SshCommand {

  @Argument(index = 0, required = true, metaVar = "PROJECT", usage = "project name")
  private String projectName;

  @Argument(index = 1, required = true, metaVar = "SOURCE_BRANCH", usage = "source branch name")
  private String sourceBranch;

  @Argument(index = 2, required = true, metaVar = "DEST_BRANCH", usage = "destination branch name")
  private String destinationBranch;

  @Option(name = "--include-closed", usage = "include closed changes in the rename operation")
  private boolean includeClosedChanges = false;

  @Option(name = "--message", usage = "message to add to moved changes")
  private String message;

  @Inject private RenameBranch renameBranch;
  @Inject private ProjectCache projectCache;
  @Inject private Provider<CurrentUser> userProvider;

  @Override
  protected void run() throws Exception {
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = sourceBranch;
    input.destinationBranch = destinationBranch;
    input.includeClosedChanges = includeClosedChanges;
    input.message = message;

    try {
      Project.NameKey project = Project.nameKey(projectName);
      ProjectState projectState = projectCache.get(project).orElse(null);
      if (projectState == null) {
        throw new BadRequestException("Project not found: " + projectName);
      }

      CurrentUser currentUser = userProvider.get();
      if (!currentUser.isIdentifiedUser()) {
        throw new AuthException("Authentication required");
      }
      IdentifiedUser user = currentUser.asIdentifiedUser();
      ProjectResource projectResource = new ProjectResource(projectState, user);

      stdout.println("Renaming branch from " + sourceBranch + " to " + destinationBranch);
      stdout.println("Include closed changes: " + input.includeClosedChanges);

      RenameBranchResult result = renameBranch.apply(projectResource, input).value();

      stdout.println("Branch rename completed successfully:");
      stdout.println("  Source branch: " + result.sourceBranch);
      stdout.println("  Destination branch: " + result.destinationBranch);
      stdout.println("  Total changes: " + result.totalChanges);
      stdout.println("  Open changes: " + result.openChanges);
      stdout.println("  Moved changes: " + result.movedChanges);
      stdout.println("  Failed changes: " + result.failedChanges);
      stdout.println("  Branch deleted: " + result.branchDeleted);

      if (result.errors != null && !result.errors.isEmpty()) {
        stdout.println("Errors encountered:");
        for (String error : result.errors) {
          stdout.println("  - " + error);
        }
      }
    } catch (RestApiException | UpdateException | PermissionBackendException | IOException e) {
      throw die("Failed to rename branch: " + e.getMessage());
    }
  }
}
