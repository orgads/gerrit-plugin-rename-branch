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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import org.junit.Test;

@TestPlugin(
    name = "rename-branch",
    sysModule = "com.googlesource.gerrit.plugins.renamebranch.PluginModule",
    sshModule = "com.googlesource.gerrit.plugins.renamebranch.SshModule",
    httpModule = "com.googlesource.gerrit.plugins.renamebranch.HttpModule")
public class RenameBranchIT extends LightweightPluginDaemonTest {
  private static final String RENAME_BRANCH_CAPABILITY = "rename-branch-renameBranch";

  @Inject private ProjectOperations projectOperations;

  @Test
  public void renameBranchRequiresSourceBranch() throws Exception {
    RenameBranchInput input = new RenameBranchInput();
    input.destinationBranch = "new-branch";

    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertBadRequest();
    assertThat(response.getEntityContent()).contains("source branch is required");
  }

  @Test
  public void renameBranchRequiresDestinationBranch() throws Exception {
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";

    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertBadRequest();
    assertThat(response.getEntityContent()).contains("destination branch is required");
  }

  @Test
  public void renameBranchCannotUseSameName() throws Exception {
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "master";

    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertBadRequest();
    assertThat(response.getEntityContent())
        .contains("source and destination branches cannot be the same");
  }

  @Test
  public void renameBranchFailsForNonExistentSource() throws Exception {
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "non-existent";
    input.destinationBranch = "new-branch";

    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertConflict();
    assertThat(response.getEntityContent()).contains("does not exist");
  }

  @Test
  public void renameBranchFailsWhenDestinationExists() throws Exception {
    PushOneCommit.Result closedChange = createChange();
    merge(closedChange);

    // Create a test branch
    BranchInput branchInput = new BranchInput();
    branchInput.revision = "master";
    gApi.projects().name(project.get()).branch("test-branch").create(branchInput);

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "test-branch";

    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertConflict();
    assertThat(response.getEntityContent()).contains("already exists");
  }

  @Test
  @UseLocalDisk
  public void renameBranchSucceeds() throws Exception {
    // Create initial change on master
    createChange();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "main";
    input.message = "Renaming master to main";

    RenameBranchResult result =
        readContentFromJson(
            adminRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.sourceBranch).isEqualTo("master");
    assertThat(result.destinationBranch).isEqualTo("main");
    assertThat(result.branchDeleted).isTrue();
    assertThat(result.totalChanges).isEqualTo(1);
    assertThat(result.movedChanges).isEqualTo(1);
    assertThat(result.failedChanges).isEqualTo(0);

    // Verify the new branch exists
    assertThat(gApi.projects().name(project.get()).branch("main").get()).isNotNull();

    // Verify the old branch is gone
    try {
      gApi.projects().name(project.get()).branch("master").get();
      throw new AssertionError("Expected branch 'master' to be deleted");
    } catch (Exception e) {
      // Expected - the branch should not exist
      assertThat(e.getMessage()).contains("Not found");
    }
  }

  @Test
  @UseLocalDisk
  public void renameBranchMovesOpenChangesOnly() throws Exception {
    // Create two changes: one open, one merged
    PushOneCommit.Result closedChange = createChange();
    merge(closedChange);
    PushOneCommit.Result openChange = createChange();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "main";
    input.message = "Renaming master to main";
    input.includeClosedChanges = false;

    RenameBranchResult result =
        readContentFromJson(
            adminRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.sourceBranch).isEqualTo("master");
    assertThat(result.destinationBranch).isEqualTo("main");
    assertThat(result.branchDeleted).isTrue();
    assertThat(result.totalChanges).isEqualTo(1);
    assertThat(result.movedChanges).isEqualTo(1);
    assertThat(result.failedChanges).isEqualTo(0);

    // Open change should be on the new branch
    assertThat(gApi.changes().id(openChange.getChangeId()).get().branch).isEqualTo("main");
    // Closed change should remain on the old branch (which is deleted, so it should not move)
    assertThat(gApi.changes().id(closedChange.getChangeId()).get().branch).isEqualTo("master");
  }

  @Test
  @UseLocalDisk
  public void renameBranchIncludingClosedChanges() throws Exception {
    // Create and submit a change (closed)
    PushOneCommit.Result closedChange = createChange();
    merge(closedChange);
    PushOneCommit.Result openChange = createChange();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "main";
    input.message = "Renaming master to main";
    input.includeClosedChanges = true;

    RenameBranchResult result =
        readContentFromJson(
            adminRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.sourceBranch).isEqualTo("master");
    assertThat(result.destinationBranch).isEqualTo("main");
    assertThat(result.branchDeleted).isTrue();
    assertThat(result.totalChanges).isEqualTo(2);
    assertThat(result.movedChanges).isEqualTo(2);
    assertThat(result.failedChanges).isEqualTo(0);
    // Both changes should be on the new branch
    assertThat(gApi.changes().id(openChange.getChangeId()).get().branch).isEqualTo("main");
    assertThat(gApi.changes().id(closedChange.getChangeId()).get().branch).isEqualTo("main");
  }

  @Test
  public void renameBranchFailsWithEmptySourceBranch() throws Exception {
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "";
    input.destinationBranch = "new-branch";

    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertBadRequest();
    assertThat(response.getEntityContent()).contains("source branch is required");
  }

  @Test
  public void renameBranchFailsWithEmptyDestinationBranch() throws Exception {
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "";

    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertBadRequest();
    assertThat(response.getEntityContent()).contains("destination branch is required");
  }

  @Test
  public void renameBranchFailsWithoutPermission() throws Exception {
    // Use a non-admin user
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "new-branch";

    RestResponse response =
        userRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertForbidden();
    assertThat(response.getEntityContent()).contains("rename branch not permitted");
  }

  @Test
  @UseLocalDisk
  public void renameBranchWithNullMessage() throws Exception {
    // Test the message null/empty check branch
    createChange();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "main";
    input.message = null; // Test null message handling

    RenameBranchResult result =
        readContentFromJson(
            adminRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.movedChanges).isEqualTo(1);
    assertThat(result.branchDeleted).isTrue();
  }

  @Test
  @UseLocalDisk
  public void renameBranchWithEmptyMessage() throws Exception {
    // Test the message empty check branch
    createChange();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "main";
    input.message = "";

    RenameBranchResult result =
        readContentFromJson(
            adminRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.movedChanges).isEqualTo(1);
    assertThat(result.branchDeleted).isTrue();
  }

  @Test
  @UseLocalDisk
  public void renameBranchWithNoChanges() throws Exception {
    // Test branch rename with no changes on the branch
    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "main-empty";

    RenameBranchResult result =
        readContentFromJson(
            adminRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.totalChanges).isEqualTo(0);
    assertThat(result.movedChanges).isEqualTo(0);
    assertThat(result.failedChanges).isEqualTo(0);
    assertThat(result.branchDeleted).isTrue();
  }

  @Test
  @UseLocalDisk
  public void renameBranchWithoutCreate() throws Exception {
    String groupName = "rename-branch-group";
    GroupInput groupInput = new GroupInput();
    groupInput.name = groupName;
    GroupInfo groupInfo = gApi.groups().create(groupInput).get();
    gApi.groups().id(groupName).addMembers("user");

    AccountGroup.UUID groupUuid = AccountGroup.uuid(groupInfo.id);

    // Set capability on All-Projects
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(RENAME_BRANCH_CAPABILITY).group(groupUuid))
        .update();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "new-branch";
    RestResponse response =
        userRestSession.post("/projects/" + project.get() + "/rename-branch", input);
    response.assertForbidden();
    assertThat(response.getEntityContent()).contains("rename branch not permitted");
  }

  @Test
  @UseLocalDisk
  public void renameBranchWithoutAbandon() throws Exception {
    String groupName = "rename-branch-group";
    GroupInput groupInput = new GroupInput();
    groupInput.name = groupName;
    GroupInfo groupInfo = gApi.groups().create(groupInput).get();
    gApi.groups().id(groupName).addMembers("user");

    AccountGroup.UUID groupUuid = AccountGroup.uuid(groupInfo.id);

    // Set capability on All-Projects
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(RENAME_BRANCH_CAPABILITY).group(groupUuid))
        .update();

    // Set project-specific permissions on the test project
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE).ref("refs/heads/*").group(groupUuid))
        .add(allow(Permission.CREATE).ref("refs/heads/*").group(groupUuid))
        .update();

    createChange();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "new-branch";
    RenameBranchResult result =
        readContentFromJson(
            userRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.branchDeleted).isFalse();
    assertThat(result.destinationBranch).isEqualTo("new-branch");
    assertThat(result.totalChanges).isEqualTo(1);
    assertThat(result.openChanges).isEqualTo(1);
    assertThat(result.movedChanges).isEqualTo(0);
    assertThat(result.failedChanges).isEqualTo(1);
    assertThat(result.errors)
        .containsExactly(
            "Failed to move change 1: Cannot move change 1: abandon permission required");
  }

  @Test
  @UseLocalDisk
  public void renameBranchWithCapabilitySucceeds() throws Exception {
    String groupName = "rename-branch-group";
    GroupInput groupInput = new GroupInput();
    groupInput.name = groupName;
    GroupInfo groupInfo = gApi.groups().create(groupInput).get();
    gApi.groups().id(groupName).addMembers("user");

    AccountGroup.UUID groupUuid = AccountGroup.uuid(groupInfo.id);

    // Set capability on All-Projects
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(RENAME_BRANCH_CAPABILITY).group(groupUuid))
        .update();

    // Set project-specific permissions on the test project
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE).ref("refs/heads/*").group(groupUuid))
        .add(allow(Permission.CREATE).ref("refs/heads/*").group(groupUuid))
        .add(allow(Permission.ABANDON).ref("refs/*").group(groupUuid))
        .update();

    createChange();

    RenameBranchInput input = new RenameBranchInput();
    input.sourceBranch = "master";
    input.destinationBranch = "main-capability";

    RenameBranchResult result =
        readContentFromJson(
            userRestSession.post("/projects/" + project.get() + "/rename-branch", input),
            RenameBranchResult.class);

    assertThat(result.branchDeleted).isTrue();
    assertThat(result.destinationBranch).isEqualTo("main-capability");
  }

  private <T> T readContentFromJson(RestResponse r, Class<T> clazz) throws Exception {
    r.assertOK();
    try (JsonReader jsonReader = new JsonReader(r.getReader())) {
      jsonReader.setLenient(true);
      return newGson().fromJson(jsonReader, clazz);
    }
  }
}
