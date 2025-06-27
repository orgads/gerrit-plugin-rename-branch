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

import static com.google.gerrit.server.permissions.RefPermission.CREATE;
import static com.google.gerrit.server.permissions.RefPermission.DELETE;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.googlesource.gerrit.plugins.renamebranch.RenameBranchCapability.RENAME_BRANCH;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.access.PluginPermission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

/**
 * Renames a branch and optionally moves all associated changes to the new branch.
 *
 * <p>This REST endpoint handles the complete branch rename operation including:
 *
 * <ul>
 *   <li>Creating the new branch pointing to the same commit as the source
 *   <li>Moving all changes from the source branch to the destination branch
 *   <li>Deleting the source branch if all operations succeed
 *   <li>Providing detailed results and error information
 * </ul>
 *
 * <p>The operation requires appropriate permissions including DELETE on the source branch, CREATE
 * on the destination branch, and ABANDON permission on changes to be moved.
 */
@Singleton
public class RenameBranch implements RestModifyView<ProjectResource, RenameBranchInput> {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ProjectCache projectCache;
  private final BatchUpdate.Factory updateFactory;
  private final ChangeMessagesUtil cmUtil;

  @Inject
  RenameBranch(
      PermissionBackend permissionBackend,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      ProjectCache projectCache,
      BatchUpdate.Factory updateFactory,
      ChangeMessagesUtil cmUtil) {
    this.permissionBackend = permissionBackend;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.projectCache = projectCache;
    this.updateFactory = updateFactory;
    this.cmUtil = cmUtil;
  }

  @Override
  public Response<RenameBranchResult> apply(ProjectResource rsrc, RenameBranchInput input)
      throws RestApiException, UpdateException, PermissionBackendException, IOException {

    log.atInfo().log(
        "Renaming branch in project %s from %s to %s",
        rsrc.getNameKey().get(), input.sourceBranch, input.destinationBranch);
    validateInput(input);

    Project.NameKey project = rsrc.getNameKey();
    IdentifiedUser user = rsrc.getUser().asIdentifiedUser();

    BranchNameKey sourceBranch = BranchNameKey.create(project, input.sourceBranch);
    BranchNameKey destinationBranch = BranchNameKey.create(project, input.destinationBranch);

    // Validate project state
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    projectState.checkStatePermitsWrite();

    try (Repository repo = repoManager.openRepository(project)) {
      return performRename(repo, sourceBranch, destinationBranch, input, user);
    }
  }

  private void validateInput(RenameBranchInput input) throws BadRequestException {
    if (Strings.isNullOrEmpty(input.sourceBranch)) {
      throw new BadRequestException("source branch is required");
    }
    if (Strings.isNullOrEmpty(input.destinationBranch)) {
      throw new BadRequestException("destination branch is required");
    }
    if (input.sourceBranch.equals(input.destinationBranch)) {
      throw new BadRequestException("source and destination branches cannot be the same");
    }
  }

  protected boolean canRenameBranch(IdentifiedUser user) throws PermissionBackendException {
    try {
      permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (AuthException denied) {
      // If not admin, check for the specific capability
      try {
        permissionBackend.user(user).check(new PluginPermission("rename-branch", RENAME_BRANCH));
      } catch (AuthException e) {
        return false;
      }
    }
    return true;
  }

  private Response<RenameBranchResult> performRename(
      Repository repo,
      BranchNameKey sourceBranch,
      BranchNameKey destinationBranch,
      RenameBranchInput input,
      IdentifiedUser user)
      throws RestApiException, UpdateException, PermissionBackendException, IOException {

    // Check if source branch exists
    Ref sourceRef = repo.findRef(sourceBranch.branch());
    if (sourceRef == null) {
      throw new ResourceConflictException(
          "Source branch " + sourceBranch.shortName() + " does not exist");
    }

    // Check if destination branch already exists
    Ref destRef = repo.findRef(destinationBranch.branch());
    if (destRef != null) {
      throw new ResourceConflictException(
          "Destination branch " + destinationBranch.shortName() + " already exists");
    }

    if (!canRenameBranch(user)) {
      throw new AuthException("rename branch not permitted");
    }

    // Check permissions
    try {
      permissionBackend.user(user).ref(sourceBranch).check(DELETE);
      permissionBackend.user(user).ref(destinationBranch).check(CREATE);
    } catch (AuthException denied) {
      throw new AuthException("rename branch not permitted", denied);
    }

    // Get all changes on the source branch
    List<ChangeData> openChangeData = queryProvider.get().byBranchOpen(sourceBranch);
    List<ChangeData> allChangeData;
    if (input.includeClosedChanges) {
      allChangeData =
          queryProvider.get().byProject(sourceBranch.project()).stream()
              .filter(cd -> cd.change().getDest().equals(sourceBranch))
              .toList();
    } else {
      allChangeData = openChangeData;
    }

    RenameBranchResult result = new RenameBranchResult();
    result.sourceBranch = sourceBranch.shortName();
    result.destinationBranch = destinationBranch.shortName();
    result.totalChanges = allChangeData.size();
    result.openChanges = openChangeData.size();

    // Create the new branch pointing to the same commit as source
    ObjectId sourceCommit = sourceRef.getObjectId();
    try (RefUpdateContext ctx =
        RefUpdateContext.open(RefUpdateContext.RefUpdateType.BRANCH_MODIFICATION)) {
      RefUpdate createUpdate = repo.updateRef(destinationBranch.branch());
      createUpdate.setNewObjectId(sourceCommit);
      createUpdate.setExpectedOldObjectId(ObjectId.zeroId());

      RefUpdate.Result createResult = createUpdate.update();
      if (createResult != RefUpdate.Result.NEW) {
        throw new ResourceConflictException("Failed to create destination branch: " + createResult);
      }
    }

    try {
      // Move all changes to the new branch
      for (ChangeData changeData : allChangeData) {
        try {
          moveChangeToNewBranch(changeData, destinationBranch, input.message, user);
          result.movedChanges++;
        } catch (Exception e) {
          result.failedChanges++;
          if (result.errors == null) {
            result.errors = new ArrayList<>();
          }
          result.errors.add("Failed to move change " + changeData.getId() + ": " + e.getMessage());
        }
      }
      // Delete the source branch only if all changes were moved successfully
      if (result.failedChanges == 0) {
        try (RefUpdateContext ctx =
            RefUpdateContext.open(RefUpdateContext.RefUpdateType.BRANCH_MODIFICATION)) {
          RefUpdate deleteUpdate = repo.updateRef(sourceBranch.branch());
          deleteUpdate.setExpectedOldObjectId(sourceCommit);
          deleteUpdate.setNewObjectId(ObjectId.zeroId());
          deleteUpdate.setForceUpdate(true); // Force the branch deletion
          RefUpdate.Result deleteResult = deleteUpdate.delete();
          if (deleteResult == RefUpdate.Result.FORCED
              || deleteResult == RefUpdate.Result.FAST_FORWARD) {
            result.branchDeleted = true;
          } else {
            result.branchDeleted = false;
            if (result.errors == null) {
              result.errors = new ArrayList<>();
            }
            result.errors.add("Failed to delete source branch: " + deleteResult);
          }
        }
      }
    } catch (Exception e) {
      // If something goes wrong, try to clean up the destination branch
      try (RefUpdateContext ctx =
          RefUpdateContext.open(RefUpdateContext.RefUpdateType.BRANCH_MODIFICATION)) {
        RefUpdate rollbackUpdate = repo.updateRef(destinationBranch.branch());
        rollbackUpdate.setExpectedOldObjectId(sourceCommit);
        rollbackUpdate.setNewObjectId(ObjectId.zeroId());
        rollbackUpdate.delete();
      } catch (IOException rollbackEx) {
        // Log but don't throw - original exception is more important
      }
      throw new ResourceConflictException("Branch rename failed: " + e.getMessage(), e);
    }
    return Response.ok(result);
  }

  /** Moves a single change to the new branch. */
  private void moveChangeToNewBranch(
      ChangeData changeData, BranchNameKey destinationBranch, String message, IdentifiedUser user)
      throws RestApiException, UpdateException, PermissionBackendException, IOException {

    Change change = changeData.change();

    // Check if user has permission to abandon this change (required for move)
    try {
      permissionBackend.user(user).change(changeData).check(ChangePermission.ABANDON);
    } catch (AuthException e) {
      throw new AuthException(
          "Cannot move change " + change.getId() + ": abandon permission required", e);
    }

    // Use a custom batch update operation that doesn't check change status
    MoveOp moveOp = new MoveOp(change, destinationBranch, message);
    try (BatchUpdate u = updateFactory.create(change.getProject(), user, TimeUtil.now())) {
      u.addOp(change.getId(), moveOp);
      u.execute();
    }
  }

  /**
   * Custom batch operation for moving changes, based on Move.Op but without status restrictions.
   */
  private class MoveOp implements BatchUpdateOp {
    private final Change originalChange;
    private final BranchNameKey newDestKey;
    private final String message;

    MoveOp(Change change, BranchNameKey destinationBranch, String message) {
      this.originalChange = change;
      this.newDestKey = destinationBranch;
      this.message = message;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws ResourceConflictException, IOException {
      Change change = ctx.getChange();
      BranchNameKey changePrevDest = change.getDest();

      if (changePrevDest.equals(newDestKey)) {
        throw new ResourceConflictException("Change is already destined for the specified branch");
      }

      // Update the change destination
      PatchSet.Id psId = change.currentPatchSetId();
      ChangeUpdate update = ctx.getUpdate(psId);
      update.setBranch(newDestKey.branch());
      change.setDest(newDestKey);

      // Create the change message
      StringBuilder msgBuf = new StringBuilder();
      msgBuf.append("Branch renamed from ");
      msgBuf.append(changePrevDest.shortName());
      msgBuf.append(" to ");
      msgBuf.append(newDestKey.shortName());
      if (!Strings.isNullOrEmpty(message)) {
        msgBuf.append("\n\n");
        msgBuf.append(message);
      }
      cmUtil.setChangeMessage(ctx, msgBuf.toString(), ChangeMessagesUtil.TAG_MOVE);

      return true;
    }
  }
}
