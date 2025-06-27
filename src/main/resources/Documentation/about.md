# Rename Branch Plugin

This plugin provides functionality to rename Git branches in Gerrit projects while automatically handling all associated changes.

## Description

The Rename Branch plugin allows administrators and users with appropriate permissions to rename branches and optionally move all associated changes (both open and closed) to the new branch name. This is useful for:

- Renaming default branches (e.g., master → main)
- Reorganizing branch naming schemes
- Consolidating development activities from one branch to another

## REST API

### Rename Branch

Renames a branch and optionally moves all associated changes.

**Request:**
```
POST /projects/{project-name}/rename-branch~rename-branch
```

**Input:**
```json
{
  "source_branch": "old-branch-name",
  "destination_branch": "new-branch-name",
  "include_closed_changes": true,
  "message": "Optional message for change updates"
}
```

## SSH API

### rename-branch Command

```bash
ssh -p 29418 user@gerrit.example.com gerrit rename-branch rename-branch PROJECT SOURCE_BRANCH DEST_BRANCH [OPTIONS]
```

**Arguments:**
- `PROJECT`: The name of the project
- `SOURCE_BRANCH`: The name of the branch to be renamed
- `DEST_BRANCH`: The new name for the branch

**Options:**
- `--include-closed`: Include closed changes (default: false)
- `--message MESSAGE`: Custom message for change updates

**Examples:**
```bash
# Basic rename
ssh -p 29418 user@gerrit.example.com gerrit rename-branch rename-branch my-project master main

# Rename with custom message
ssh -p 29418 user@gerrit.example.com gerrit rename-branch rename-branch my-project feature-old feature-new '--message="Renaming for clarity"'

# Include closed changes
ssh -p 29418 user@gerrit.example.com gerrit rename-branch rename-branch my-project topic new-topic --include-closed
```

**Parameters:**

- `source_branch`: *(required)* The name of the branch to be renamed
- `destination_branch`: *(required)* The new name for the branch
- `include_closed_changes`: *(optional, default: true)* Whether to include closed changes during the branch rename operation
- `message`: *(optional)* Custom message to be added to change messages when moving changes
already exists

**Response:**
```json
{
  "source_branch": "old-branch-name",
  "destination_branch": "new-branch-name",
  "total_changes": 5,
  "open_changes": 2,
  "moved_changes": 5,
  "failed_changes": 0,
  "branch_deleted": true,
  "errors": []
}
```

**Response Fields:**

- `source_branch`: The original source branch name
- `destination_branch`: The new destination branch name
- `total_changes`: Total number of changes found on the source branch
- `open_changes`: Number of open changes on the source branch
- `moved_changes`: Number of changes successfully moved to the destination branch
- `failed_changes`: Number of changes that failed to move
- `branch_deleted`: Whether the source branch was successfully deleted
- `errors`: List of error messages if any operations failed

## Examples

### Basic branch rename:
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "source_branch": "master",
    "destination_branch": "main"
  }' \
  'https://gerrit.example.com/a/projects/my-project/rename-branch~rename-branch'
```

### Rename with custom message:
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "source_branch": "feature-old",
    "destination_branch": "feature-new",
    "message": "Renaming feature branch for better clarity",
    "include_closed_changes": false
  }' \
  'https://gerrit.example.com/a/projects/my-project/rename-branch~rename-branch'
```

## Permissions

To rename a branch, users must have:

1. **DELETE** permission on the source branch
2. **CREATE** permission on the destination branch  
3. **ABANDON** permission on changes that need to be moved

## Error Handling

The plugin implements robust error handling for various edge cases:

- **Source branch doesn't exist**: Returns `ResourceConflictException`
- **Destination branch already exists**: Returns `ResourceConflictException`
- **Insufficient permissions**: Returns `AuthException`
- **Invalid input parameters**: Returns `BadRequestException`
- **Change movement failures**: Continues with other changes and reports errors in the response

If any changes fail to move during the rename operation, the plugin will:
1. Continue attempting to move remaining changes
2. Report failed changes in the response
3. Only delete the source branch if all changes moved successfully

## Best Practices

1. **Backup**: Always ensure you have proper backups before performing branch renames
2. **Timing**: Perform renames during low-activity periods to minimize conflicts
3. **Communication**: Inform team members about branch renames in advance
4. **Testing**: Test the rename operation on a non-critical branch first
5. **Permissions**: Ensure proper permissions are set up before the operation

## Limitations

- The plugin cannot rename branches that are currently checked out by active Git operations
- Very large repositories with thousands of changes may experience longer processing times
- The operation is not atomic across all changes (individual change moves can fail independently)

## Configuration

The plugin does not require additional configuration and uses Gerrit's existing permission system.
