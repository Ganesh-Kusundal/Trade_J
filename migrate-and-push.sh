#!/bin/bash
# Script to migrate parquet files to Git LFS and push

echo "Starting Git LFS migration..."
echo "This may take a while (30+ minutes for 9.4GB repository)"

# Set Git config for large operations
git config --global http.postBuffer 2147483648  # 2GB
git config --global http.lowSpeedTime 600
git config --global http.lowSpeedLimit 1000

# Run LFS migration
echo "Migrating parquet files to LFS..."
git lfs migrate import --include='*.parquet' --everything --yes

if [ $? -eq 0 ]; then
    echo "LFS migration completed successfully!"
    echo "Pushing to remote..."
    git push --force origin all-broker-support-and-moduler
    echo "Done!"
else
    echo "LFS migration failed or was interrupted"
    exit 1
fi
