#!/bin/bash
#
# JNode Local Build and Deploy Script
# Builds locally but can upload to VM
# Usage: ./build-local.sh [h2_version] [target_host]
#

set -e

H2_VERSION=${1:-"2.3.232"}
TARGET_HOST=${2:-""}

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log "Building JNode with H2 version: $H2_VERSION"

# Build with Maven
log "Running Maven build..."
mvn clean package -Pall -q

# Extract lib directory
log "Extracting lib directory..."
cd jnode-assembly/target
ZIPFILE=$(ls jnode-*.zip | head -1)
unzip -j "$ZIPFILE" "jnode/lib/*" -d ../lib-temp/

# Replace H2 version
log "Setting H2 version to $H2_VERSION"
rm -f ../lib-temp/h2-*.jar
case "$H2_VERSION" in
    "1.3.174") cp ../../jdbc-drivers/old-drivers/h2-1.3.174.jar ../lib-temp/ ;;
    "1.4.200") cp ../../jdbc-drivers/old-drivers/h2-1.4.200.jar ../lib-temp/ ;;
    *) cp ../../jdbc-drivers/h2-2.3.232.jar ../lib-temp/ ;;
esac

if [ -n "$TARGET_HOST" ]; then
    log "Uploading to $TARGET_HOST..."
    
    # Create deployment directory in user home (avoids /tmp permission issues)
    ssh "$TARGET_HOST" 'rm -rf ~/jnode-deploy && mkdir -p ~/jnode-deploy'
    
    # Upload lib files
    scp ../lib-temp/* "$TARGET_HOST:~/jnode-deploy/"
    
    # Deploy on target
    ssh "$TARGET_HOST" << 'EOF'
sudo systemctl stop jnode || true
sudo mkdir -p /opt/jnode/lib-backup-$(date +%Y%m%d-%H%M%S)
sudo cp -r /opt/jnode/lib/* /opt/jnode/lib-backup-$(date +%Y%m%d-%H%M%S)/ 2>/dev/null || true
sudo cp ~/jnode-deploy/* /opt/jnode/lib/
sudo systemctl start jnode
sudo systemctl status jnode --no-pager
rm -rf ~/jnode-deploy
EOF
    
    success "Deployed to $TARGET_HOST"
else
    success "Build complete. Lib files in jnode-assembly/lib-temp/"
    log "To deploy manually: scp jnode-assembly/lib-temp/* your-vm:/opt/jnode/lib/"
fi

# Cleanup
rm -rf ../lib-temp