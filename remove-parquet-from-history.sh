#!/bin/bash
# Remove parquet files from Git history and push

echo "Removing parquet files from Git history..."
echo "This will rewrite history and may take 10-20 minutes"

# Create a backup of current HEAD
git branch backup-before-cleanup

# Remove all parquet files from history
git filter-repo --invert-paths --path-glob '*.parquet' --force

if [ $? -eq 0 ]; then
    echo "Successfully removed parquet files from history!"
    echo "New repository size:"
    du -sh .git
    
    echo "Force pushing to remote..."
    echo "WARNING: This will rewrite history on remote"
    git push --force --prune origin all-broker-support-and-moduler
    echo "Done!"
else
    echo "filter-repo failed"
    exit 1
fi
