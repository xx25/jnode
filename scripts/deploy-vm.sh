#!/bin/bash
#
# JNode VM Deployment Script - Pull-based deployment
# Usage: ./deploy-vm.sh [h2_version] [release_tag]
# Examples:
#   ./deploy-vm.sh                    # Latest artifacts with default H2 2.3.232
#   ./deploy-vm.sh 1.3.174           # Latest artifacts with H2 1.3.174
#   ./deploy-vm.sh 2.3.232 v2.0.1    # Specific release with H2 2.3.232
#

set -e  # Exit on error

# Configuration
GITHUB_REPO="xx25/jnode"
JNODE_HOME="/opt/jnode"
JNODE_SERVICE="jnode"
LOG_FILE="/var/log/jnode-deploy.log"

# Default values
H2_VERSION=${1:-"2.3.232"}
RELEASE_TAG=${2:-""}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log() {
    echo -e "${BLUE}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
    exit 1
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

# Check if H2 version is valid
validate_h2_version() {
    case "$H2_VERSION" in
        "2.3.232"|"1.4.200"|"1.3.174")
            log "Using H2 version: $H2_VERSION"
            ;;
        *)
            error "Invalid H2 version: $H2_VERSION. Valid options: 2.3.232, 1.4.200, 1.3.174"
            ;;
    esac
}

# Create necessary directories
setup_directories() {
    log "Setting up directories..."
    sudo mkdir -p "$JNODE_HOME"
    sudo mkdir -p "$JNODE_HOME/lib"
    sudo mkdir -p "$JNODE_HOME/backups"
    mkdir -p /tmp/jnode-deploy
}

# Download artifacts from GitHub
download_artifacts() {
    log "Downloading JNode artifacts..."
    
    cd /tmp/jnode-deploy
    
    if [ -n "$RELEASE_TAG" ]; then
        # Download from specific release
        log "Downloading from release: $RELEASE_TAG"
        DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/download/$RELEASE_TAG/jnode-lib-h2-$H2_VERSION.tar.gz"
        
        if ! wget -O "jnode-lib-h2-$H2_VERSION.tar.gz" "$DOWNLOAD_URL"; then
            error "Failed to download from release $RELEASE_TAG"
        fi
    else
        # Try latest release first
        log "Trying latest release..."
        DOWNLOAD_URL=$(curl -s "https://api.github.com/repos/$GITHUB_REPO/releases/latest" | \
                       grep "browser_download_url.*jnode-lib-h2-$H2_VERSION.tar.gz" | \
                       cut -d '"' -f 4)
        
        if [ -n "$DOWNLOAD_URL" ]; then
            log "Found in latest release: $DOWNLOAD_URL"
            if ! wget -O "jnode-lib-h2-$H2_VERSION.tar.gz" "$DOWNLOAD_URL"; then
                error "Failed to download from latest release"
            fi
        else
            # Fallback: Download from CI artifacts (latest build)
            log "No release found, downloading from latest CI build..."
            download_from_ci_artifacts
        fi
    fi
    
    # Extract the archive
    if ! tar -xzf "jnode-lib-h2-$H2_VERSION.tar.gz"; then
        error "Failed to extract artifacts"
    fi
    
    success "Downloaded and extracted artifacts"
}

# Download from CI build artifacts (when no release exists)
download_from_ci_artifacts() {
    log "Downloading from CI build artifacts..."
    
    # Download the full distribution from CI
    log "Getting latest CI build..."
    LATEST_RUN=$(curl -s "https://api.github.com/repos/$GITHUB_REPO/actions/workflows/ci.yml/runs?status=success&per_page=1" | \
                 grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ -z "$LATEST_RUN" ]; then
        error "No successful CI runs found. Please check GitHub Actions."
    fi
    
    log "Latest successful CI run: $LATEST_RUN"
    
    # Download the distribution zip from CI artifacts
    ARTIFACT_URL="https://github.com/$GITHUB_REPO/suites/$LATEST_RUN/artifacts"
    log "Trying alternative: download full distribution and extract lib..."
    
    # Alternative approach: use GitHub's raw file access
    log "Downloading latest build from GitHub repository..."
    
    # Download the built distribution directly from the latest successful run
    DIST_URL="https://api.github.com/repos/$GITHUB_REPO/actions/artifacts"
    if wget -O "jnode-distribution.zip" "$DIST_URL"; then
        log "Downloaded CI distribution, extracting lib directory..."
        
        # Extract and process the distribution
        unzip -q "jnode-distribution.zip"
        INNER_ZIP=$(ls jnode-*.zip 2>/dev/null | head -1)
        
        if [ -n "$INNER_ZIP" ]; then
            log "Found inner zip: $INNER_ZIP"
            mkdir -p temp-extract
            unzip -j "$INNER_ZIP" "jnode/lib/*" -d temp-extract/
            
            # Create the lib directory with selected H2 version
            mkdir -p "lib-h2-$H2_VERSION"
            cp temp-extract/* "lib-h2-$H2_VERSION/"
            
            # Replace H2 jar with the requested version
            rm -f "lib-h2-$H2_VERSION"/h2-*.jar
            case "$H2_VERSION" in
                "1.3.174")
                    wget -O "lib-h2-$H2_VERSION/h2-1.3.174.jar" "https://github.com/$GITHUB_REPO/raw/master/jdbc-drivers/old-drivers/h2-1.3.174.jar" ;;
                "1.4.200")
                    wget -O "lib-h2-$H2_VERSION/h2-1.4.200.jar" "https://github.com/$GITHUB_REPO/raw/master/jdbc-drivers/old-drivers/h2-1.4.200.jar" ;;
                *)
                    wget -O "lib-h2-$H2_VERSION/h2-2.3.232.jar" "https://github.com/$GITHUB_REPO/raw/master/jdbc-drivers/h2-2.3.232.jar" ;;
            esac
            
            # Create the expected tar.gz structure
            tar -czf "jnode-lib-h2-$H2_VERSION.tar.gz" "lib-h2-$H2_VERSION/"
            
            log "Created lib archive with H2 version $H2_VERSION"
        else
            error "Could not find distribution zip inside artifact"
        fi
    else
        error "Failed to download from CI artifacts. Please create a release or check if CI build exists."
    fi
}

# Backup current installation
backup_current() {
    if [ -d "$JNODE_HOME/lib" ] && [ "$(ls -A $JNODE_HOME/lib)" ]; then
        BACKUP_DIR="$JNODE_HOME/backups/lib-backup-$(date +%Y%m%d-%H%M%S)"
        log "Creating backup: $BACKUP_DIR"
        sudo mkdir -p "$BACKUP_DIR"
        sudo cp -r "$JNODE_HOME/lib/"* "$BACKUP_DIR/" || warn "Backup failed, continuing anyway"
        success "Backup created: $BACKUP_DIR"
    else
        log "No existing lib directory to backup"
    fi
}

# Stop JNode service
stop_service() {
    log "Stopping JNode service..."
    if sudo systemctl is-active --quiet "$JNODE_SERVICE"; then
        sudo systemctl stop "$JNODE_SERVICE"
        success "JNode service stopped"
    else
        log "JNode service was not running"
    fi
}

# Deploy new files
deploy_files() {
    log "Deploying new lib files..."
    
    # Remove old lib files
    sudo rm -rf "$JNODE_HOME/lib/"*
    
    # Copy new files
    sudo cp -r "/tmp/jnode-deploy/lib-h2-$H2_VERSION/"* "$JNODE_HOME/lib/"
    
    # Set proper permissions
    sudo chown -R jnode:jnode "$JNODE_HOME/lib/" 2>/dev/null || warn "Could not set jnode:jnode ownership"
    sudo chmod 755 "$JNODE_HOME/lib/"*.jar
    
    success "Files deployed successfully"
}

# Start JNode service
start_service() {
    log "Starting JNode service..."
    sudo systemctl start "$JNODE_SERVICE"
    
    # Wait a moment and check status
    sleep 3
    if sudo systemctl is-active --quiet "$JNODE_SERVICE"; then
        success "JNode service started successfully"
    else
        error "Failed to start JNode service. Check logs: sudo journalctl -u $JNODE_SERVICE -f"
    fi
}

# Cleanup temporary files
cleanup() {
    log "Cleaning up temporary files..."
    rm -rf /tmp/jnode-deploy
    success "Cleanup completed"
}

# Show deployment info
show_info() {
    echo ""
    echo "=========================="
    echo "   DEPLOYMENT SUMMARY"
    echo "=========================="
    echo "H2 Version: $H2_VERSION"
    echo "Release: ${RELEASE_TAG:-"latest"}"
    echo "Deployed to: $JNODE_HOME/lib"
    echo "Service: $JNODE_SERVICE"
    echo "Log file: $LOG_FILE"
    echo ""
    echo "Files deployed:"
    ls -la "$JNODE_HOME/lib/" | grep -E "\.(jar)$" || echo "No JAR files found"
    echo "=========================="
}

# Main deployment function
main() {
    log "Starting JNode deployment..."
    log "H2 Version: $H2_VERSION"
    log "Release Tag: ${RELEASE_TAG:-"latest"}"
    
    validate_h2_version
    setup_directories
    download_artifacts
    backup_current
    stop_service
    deploy_files
    start_service
    cleanup
    show_info
    
    success "JNode deployment completed successfully!"
}

# Script help
show_help() {
    echo "JNode VM Deployment Script"
    echo ""
    echo "Usage: $0 [h2_version] [release_tag]"
    echo ""
    echo "Arguments:"
    echo "  h2_version    H2 database version (2.3.232, 1.4.200, 1.3.174)"
    echo "                Default: 2.3.232"
    echo "  release_tag   Specific GitHub release tag (e.g., v2.0.1)"
    echo "                Default: latest release"
    echo ""
    echo "Examples:"
    echo "  $0                    # Latest with H2 2.3.232"
    echo "  $0 1.3.174           # Latest with H2 1.3.174"
    echo "  $0 2.3.232 v2.0.1    # Release v2.0.1 with H2 2.3.232"
    echo ""
}

# Check arguments
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    show_help
    exit 0
fi

# Run main function
main