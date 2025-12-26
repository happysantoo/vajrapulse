#!/usr/bin/env bash
#
# VajraPulse Release Script
# 
# This script automates the release process for Maven Central publishing.
# It performs validation, builds, tests, and optionally publishes.
#
# Usage:
#   ./scripts/release.sh [version] [--dry-run] [--skip-tests] [--publish]
#
# Examples:
#   ./scripts/release.sh 0.9.3 --dry-run          # Validate release without publishing
#   ./scripts/release.sh 0.9.3 --publish           # Full release with publishing
#   ./scripts/release.sh 0.9.3 --skip-tests        # Skip tests (use with caution)
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
VERSION="${1:-}"
DRY_RUN=false
SKIP_TESTS=false
PUBLISH=false
SKIP_BUNDLE=false

# Parse arguments
shift || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --publish)
            PUBLISH=true
            shift
            ;;
        --skip-bundle)
            SKIP_BUNDLE=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Usage: $0 [version] [--dry-run] [--skip-tests] [--publish] [--skip-bundle]"
            exit 1
            ;;
    esac
done

# Validate version
if [[ -z "${VERSION}" ]]; then
    echo -e "${RED}Error: Version is required${NC}"
    echo "Usage: $0 [version] [options]"
    exit 1
fi

# Validate version format (semantic versioning)
if ! [[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9-]+)?$ ]]; then
    echo -e "${RED}Error: Invalid version format: ${VERSION}${NC}"
    echo "Expected format: X.Y.Z or X.Y.Z-SNAPSHOT"
    exit 1
fi

# Load Gradle properties from ~/.gradle/gradle.properties
load_gradle_properties() {
    local gradle_props="${HOME}/.gradle/gradle.properties"
    if [[ -f "${gradle_props}" ]]; then
        # Read properties file line by line
        while IFS= read -r line || [[ -n "${line}" ]]; do
            # Skip comments and empty lines
            [[ "${line}" =~ ^[[:space:]]*# ]] && continue
            [[ -z "${line// }" ]] && continue
            
            # Split on first '=' sign
            if [[ "${line}" =~ ^([^=]+)=(.*)$ ]]; then
                local key="${BASH_REMATCH[1]}"
                local value="${BASH_REMATCH[2]}"
                
                # Remove leading/trailing whitespace
                key="${key#"${key%%[![:space:]]*}"}"
                key="${key%"${key##*[![:space:]]}"}"
                value="${value#"${value%%[![:space:]]*}"}"
                value="${value%"${value##*[![:space:]]}"}"
                
                # Export as environment variable if not already set (env vars take precedence)
                if [[ -n "${key}" ]] && [[ -z "${!key:-}" ]]; then
                    export "${key}=${value}"
                fi
            fi
        done < "${gradle_props}"
    fi
}

# Load Gradle properties before checking prerequisites
load_gradle_properties

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}VajraPulse Release Process${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Version: ${GREEN}${VERSION}${NC}"
echo -e "Dry Run: ${GREEN}${DRY_RUN}${NC}"
echo -e "Skip Tests: ${GREEN}${SKIP_TESTS}${NC}"
echo -e "Publish: ${GREEN}${PUBLISH}${NC}"
echo ""

# Check prerequisites
check_prerequisites() {
    echo -e "${BLUE}[1/8] Checking prerequisites...${NC}"
    
    # Check Git
    if ! command -v git &> /dev/null; then
        echo -e "${RED}Error: git is not installed${NC}"
        exit 1
    fi
    
    # Check if we're on a clean working directory
    if [[ -n "$(git status --porcelain)" ]]; then
        echo -e "${YELLOW}Warning: Working directory is not clean${NC}"
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    
    # Check if version is already tagged (only warn, don't exit)
    if git rev-parse "v${VERSION}" &>/dev/null; then
        echo -e "${YELLOW}Warning: Tag v${VERSION} already exists locally${NC}"
        # Check if tag exists on remote
        if git ls-remote --tags origin "v${VERSION}" | grep -q "v${VERSION}"; then
            echo -e "${YELLOW}Tag v${VERSION} also exists on remote. Will skip tag creation.${NC}"
        else
            echo -e "${YELLOW}Tag exists locally but not on remote. Will push existing tag.${NC}"
        fi
    fi
    
    # Check Gradle properties
    if [[ "${PUBLISH}" == "true" ]]; then
        if [[ -z "${mavenCentralUsername:-}" ]] || [[ -z "${mavenCentralPassword:-}" ]]; then
            echo -e "${YELLOW}Warning: mavenCentralUsername or mavenCentralPassword not set${NC}"
            echo "These should be in ~/.gradle/gradle.properties or as environment variables"
        fi
        
        if [[ -z "${signingKey:-}" ]] || [[ -z "${signingPassword:-}" ]]; then
            echo -e "${YELLOW}Warning: signingKey or signingPassword not set${NC}"
            echo "These should be in ~/.gradle/gradle.properties or as environment variables"
        fi
    fi
    
    echo -e "${GREEN}✓ Prerequisites check passed${NC}"
    echo ""
}

# Update version
update_version() {
    echo -e "${BLUE}[2/8] Updating version to ${VERSION}...${NC}"
    
    # Update build.gradle.kts
    if [[ "$(uname)" == "Darwin" ]]; then
        sed -i '' "s/version = \".*\"/version = \"${VERSION}\"/" build.gradle.kts
    else
        sed -i "s/version = \".*\"/version = \"${VERSION}\"/" build.gradle.kts
    fi
    
    # Update jreleaser.yml
    if [[ "$(uname)" == "Darwin" ]]; then
        sed -i '' "s/version: .*/version: ${VERSION}/" jreleaser.yml
    else
        sed -i "s/version: .*/version: ${VERSION}/" jreleaser.yml
    fi
    
    echo -e "${GREEN}✓ Version updated${NC}"
    echo ""
}

# Run tests
run_tests() {
    if [[ "${SKIP_TESTS}" == "true" ]]; then
        echo -e "${YELLOW}[3/8] Skipping tests...${NC}"
        echo ""
        return
    fi
    
    echo -e "${BLUE}[3/8] Running tests...${NC}"
    ./gradlew clean test
    echo -e "${GREEN}✓ Tests passed${NC}"
    echo ""
}

# Check coverage
check_coverage() {
    if [[ "${SKIP_TESTS}" == "true" ]]; then
        echo -e "${YELLOW}[4/8] Skipping coverage check...${NC}"
        echo ""
        return
    fi
    
    echo -e "${BLUE}[4/8] Checking test coverage...${NC}"
    ./gradlew jacocoTestReport jacocoTestCoverageVerification
    echo -e "${GREEN}✓ Coverage check passed${NC}"
    echo ""
}

# Build project
build_project() {
    echo -e "${BLUE}[5/8] Building project...${NC}"
    ./gradlew clean build -x test
    echo -e "${GREEN}✓ Build successful${NC}"
    echo ""
}

# Prepare release
prepare_release() {
    echo -e "${BLUE}[6/8] Preparing release artifacts...${NC}"
    ./gradlew prepareRelease
    echo -e "${GREEN}✓ Release artifacts prepared${NC}"
    echo ""
}

# Publish to Maven Local
publish_local() {
    echo -e "${BLUE}[7/8] Publishing to Maven Local...${NC}"
    ./gradlew publishToMavenLocal
    echo -e "${GREEN}✓ Published to Maven Local${NC}"
    echo ""
}

# Create bundle and publish
publish_central() {
    if [[ "${DRY_RUN}" == "true" ]]; then
        echo -e "${YELLOW}[8/8] DRY RUN: Would publish to Maven Central${NC}"
        echo ""
        echo "To actually publish, run:"
        echo "  ./scripts/release.sh ${VERSION} --publish"
        return
    fi
    
    if [[ "${PUBLISH}" != "true" ]]; then
        echo -e "${YELLOW}[8/8] Skipping Maven Central publish (use --publish to enable)${NC}"
        echo ""
        return
    fi
    
    echo -e "${BLUE}[8/8] Publishing to Maven Central...${NC}"
    
    # Create bundle
    echo "Creating Maven Central bundle..."
    BUNDLE_PATH="/tmp/vajrapulse-${VERSION}-central.zip"
    ./scripts/create-central-bundle.sh "${VERSION}" "${BUNDLE_PATH}"
    
    if [[ ! -f "${BUNDLE_PATH}" ]]; then
        echo -e "${RED}Error: Bundle file not created: ${BUNDLE_PATH}${NC}"
        exit 1
    fi
    
    # Upload to Maven Central Portal
    echo "Uploading bundle to Maven Central Portal..."
    
    if [[ -z "${mavenCentralUsername:-}" ]] || [[ -z "${mavenCentralPassword:-}" ]]; then
        echo -e "${RED}Error: mavenCentralUsername and mavenCentralPassword must be set${NC}"
        echo "Set them in ~/.gradle/gradle.properties or as environment variables"
        exit 1
    fi
    
    RESPONSE=$(curl -s -w "\n%{http_code}" -u "${mavenCentralUsername}:${mavenCentralPassword}" \
        -F "bundle=@${BUNDLE_PATH}" \
        "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC")
    
    HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
    BODY=$(echo "${RESPONSE}" | sed '$d')
    
    if [[ "${HTTP_CODE}" == "201" ]] || [[ "${HTTP_CODE}" == "200" ]]; then
        echo -e "${GREEN}✓ Bundle uploaded successfully${NC}"
        echo "Response: ${BODY}"
        
        # Extract transaction ID if present
        if echo "${BODY}" | grep -q "transactionId"; then
            TRANSACTION_ID=$(echo "${BODY}" | grep -o '"transactionId":"[^"]*"' | cut -d'"' -f4)
            echo "Transaction ID: ${TRANSACTION_ID}"
        fi
        
        echo ""
        echo -e "${YELLOW}Note: Maven Central sync typically takes 10-120 minutes${NC}"
        echo "Monitor at: https://central.sonatype.com/"
    else
        echo -e "${RED}Error: Upload failed with HTTP ${HTTP_CODE}${NC}"
        echo "Response: ${BODY}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Published to Maven Central${NC}"
    echo ""
}

# Create Git tag
create_tag() {
    if [[ "${DRY_RUN}" == "true" ]]; then
        echo -e "${YELLOW}DRY RUN: Would create Git tag v${VERSION}${NC}"
        return
    fi
    
    if [[ "${PUBLISH}" != "true" ]]; then
        echo -e "${YELLOW}Skipping Git tag (use --publish to enable)${NC}"
        return
    fi
    
    # Check if tag already exists locally
    if git rev-parse "v${VERSION}" &>/dev/null; then
        echo -e "${YELLOW}Tag v${VERSION} already exists locally${NC}"
        
        # Check if tag exists on remote
        if git ls-remote --tags origin "v${VERSION}" | grep -q "v${VERSION}"; then
            echo -e "${YELLOW}Tag v${VERSION} already exists on remote, skipping tag push${NC}"
        else
            echo -e "${BLUE}Pushing existing tag v${VERSION} to remote...${NC}"
            git push origin "v${VERSION}"
            echo -e "${GREEN}✓ Tag pushed${NC}"
        fi
    else
        echo -e "${BLUE}Creating Git tag v${VERSION}...${NC}"
        git tag -a "v${VERSION}" -m "Release v${VERSION}: Code Quality Improvements and Refactoring

- AdaptiveLoadPattern refactoring (23.5% code reduction)
- ExecutionEngine improvements (3.4% code reduction)
- Test reliability improvements (100% timeout coverage, 0% flakiness)
- Polymorphism over type checking
- Comprehensive test utilities and best practices guide

See CHANGELOG.md for complete release notes."
        echo -e "${GREEN}✓ Tag created${NC}"
        
        # Push tag to remote
        echo -e "${BLUE}Pushing tag to remote...${NC}"
        git push origin "v${VERSION}"
        echo -e "${GREEN}✓ Tag pushed${NC}"
    fi
    echo ""
}

# Create GitHub release
create_github_release() {
    if [[ "${DRY_RUN}" == "true" ]]; then
        echo -e "${YELLOW}DRY RUN: Would create GitHub release v${VERSION}${NC}"
        return
    fi
    
    if [[ "${PUBLISH}" != "true" ]]; then
        echo -e "${YELLOW}Skipping GitHub release (use --publish to enable)${NC}"
        return
    fi
    
    # Check if GitHub CLI is available
    if ! command -v gh &> /dev/null; then
        echo -e "${YELLOW}Warning: GitHub CLI (gh) not found. Skipping GitHub release creation.${NC}"
        echo "Install GitHub CLI: https://cli.github.com/"
        echo "Or create release manually: https://github.com/happysantoo/vajrapulse/releases/new"
        return
    fi
    
    # Check if already authenticated
    if ! gh auth status &>/dev/null; then
        echo -e "${YELLOW}Warning: GitHub CLI not authenticated. Skipping GitHub release creation.${NC}"
        echo "Authenticate with: gh auth login"
        return
    fi
    
    echo -e "${BLUE}Creating GitHub release v${VERSION}...${NC}"
    
    # Extract release notes from CHANGELOG.md
    local release_notes=""
    if [[ -f "CHANGELOG.md" ]]; then
        # Extract the section for this version from CHANGELOG
        release_notes=$(awk "/^## \[${VERSION}\]/,/^## \[/" CHANGELOG.md | sed '$d')
        if [[ -z "${release_notes}" ]]; then
            # Fallback to simple message
            release_notes="Release ${VERSION}

See CHANGELOG.md for details."
        fi
    else
        release_notes="Release ${VERSION}"
    fi
    
    # Create GitHub release
    if gh release create "v${VERSION}" \
        --title "Release v${VERSION}" \
        --notes "${release_notes}" \
        --repo "happysantoo/vajrapulse" 2>&1; then
        echo -e "${GREEN}✓ GitHub release created${NC}"
        echo ""
        echo "Release URL: https://github.com/happysantoo/vajrapulse/releases/tag/v${VERSION}"
    else
        # Check if release already exists
        if gh release view "v${VERSION}" --repo "happysantoo/vajrapulse" &>/dev/null; then
            echo -e "${YELLOW}GitHub release v${VERSION} already exists${NC}"
        else
            echo -e "${RED}Failed to create GitHub release${NC}"
            echo "Create manually: https://github.com/happysantoo/vajrapulse/releases/new"
        fi
    fi
    echo ""
}

# Main execution
main() {
    check_prerequisites
    update_version
    run_tests
    check_coverage
    build_project
    prepare_release
    publish_local
    create_tag
    create_github_release
    publish_central
    
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Release process completed!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    
    if [[ "${DRY_RUN}" == "true" ]]; then
        echo -e "${YELLOW}This was a dry run. No changes were published.${NC}"
    elif [[ "${PUBLISH}" == "true" ]]; then
        echo -e "${GREEN}Version ${VERSION} release completed!${NC}"
        echo ""
        echo "Completed steps:"
        echo "  ✓ Git tag created and pushed: v${VERSION}"
        echo "  ✓ GitHub release created (if GitHub CLI available)"
        echo "  ✓ Maven Central bundle uploaded"
        echo ""
        echo "Next steps:"
        echo "  1. Monitor Maven Central sync (10-120 minutes):"
        echo "     https://central.sonatype.com/"
        echo "  2. Verify artifacts after sync:"
        echo "     https://repo1.maven.org/maven2/com/vajrapulse/"
        echo "  3. Check GitHub release:"
        echo "     https://github.com/happysantoo/vajrapulse/releases/tag/v${VERSION}"
    else
        echo -e "${YELLOW}Release artifacts prepared but not published${NC}"
        echo "To publish, run: ./scripts/release.sh ${VERSION} --publish"
    fi
}

# Run main
main

