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

/** Input for the rename branch operation. */
public class RenameBranchInput {
  /** The name of the branch to be renamed. */
  public String sourceBranch;

  /** The new name for the branch. */
  public String destinationBranch;

  /** Whether to include closed changes during the branch rename operation. Default: false */
  public Boolean includeClosedChanges = false;

  /** Optional message to be added to change messages when moving changes. */
  public String message;
}
