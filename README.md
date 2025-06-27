# Gerrit Rename Branch Plugin

A comprehensive Gerrit plugin for renaming Git branches while automatically handling all associated changes.

## Overview

The Rename Branch plugin provides a safe and efficient way to rename branches in Gerrit projects. It automatically moves all changes (both open and closed) from the source branch to the destination branch, maintaining change history and permissions.

## Features

- **Branch Renaming**: Rename any branch in a Gerrit project
- **Change Migration**: Automatically move all associated changes to the new branch
- **Flexible Options**: Choose to include or exclude closed changes
- **Permission Control**: Granular permission system using Gerrit capabilities
- **Robust Error Handling**: Comprehensive error reporting and recovery
- **Multiple Interfaces**: Both REST API and SSH command support

## Installation

1. Build the plugin:
   ```bash
   bazel build plugins/rename-branch
   ```

2. Copy the plugin jar to your Gerrit plugins directory:
   ```bash
   cp bazel-bin/plugins/rename-branch/rename-branch.jar $GERRIT_SITE/plugins/
   ```

3. Restart Gerrit or reload plugins:
   ```bash
   ssh -p 29418 admin@gerrit.example.com gerrit plugin reload rename-branch
   ```

## Configuration

### Permissions

Grant the `renameBranch` capability to groups that should be able to rename branches:

1. Go to the All-Projects access control
2. Add the `renameBranch` capability to desired groups (typically Administrators and Project Owners)

### Project-level Permissions

Users also need:
- DELETE permission on the source branch
- CREATE permission on the destination branch
- ABANDON permission on changes (to move them)

## Usage Examples

### REST API
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "source_branch": "master",
    "destination_branch": "main",
    "message": "Renaming default branch"
  }' \
  'https://gerrit.example.com/a/projects/my-project/rename-branch~rename-branch'
```

### SSH Command
```bash
ssh -p 29418 user@gerrit.example.com gerrit rename-branch rename-branch my-project master main --message "Renaming default branch"
```

## Development

### Building
```bash
bazel build plugins/rename-branch
```

### Testing
```bash
bazel test plugins/rename-branch:rename-branch_tests
```

### Formatting
```bash
java -jar google-java-format.jar --replace $(find plugins/rename-branch -name "*.java")
```

## Best Practices

1. **Communication**: Always notify team members before renaming branches
2. **Timing**: Perform renames during low-activity periods
3. **Backup**: Ensure you have proper backups before major operations
4. **Testing**: Test on non-critical branches first
5. **Permissions**: Set up proper permissions before deployment

## Limitations

- Cannot rename branches currently being used in active Git operations
- Large repositories may experience longer processing times
- Operations are not fully atomic (individual change moves can fail)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## License

Licensed under the Apache License, Version 2.0.
