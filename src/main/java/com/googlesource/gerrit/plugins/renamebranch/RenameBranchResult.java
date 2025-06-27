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

import java.util.List;

/** Result of a branch rename operation. */
public class RenameBranchResult {
  /** The original source branch name. */
  public String sourceBranch;

  /** The new destination branch name. */
  public String destinationBranch;

  /** Total number of changes that were found on the source branch. */
  public int totalChanges;

  /** Number of open changes on the source branch. */
  public int openChanges;

  /** Number of changes successfully moved to the destination branch. */
  public int movedChanges;

  /** Number of changes that failed to move. */
  public int failedChanges;

  /** Whether the source branch was successfully deleted. */
  public boolean branchDeleted;

  /** List of error messages if any operations failed. */
  public List<String> errors;
}
