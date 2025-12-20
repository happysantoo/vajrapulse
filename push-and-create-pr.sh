#!/bin/bash

# Script to push 0.9.9 branch and create PR
# Usage: ./push-and-create-pr.sh

set -e

echo "🚀 Pushing 0.9.9 branch to origin..."

# Check if branch exists
if ! git rev-parse --verify 0.9.9 >/dev/null 2>&1; then
    echo "❌ Error: Branch 0.9.9 does not exist"
    exit 1
fi

# Check if there are uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo "⚠️  Warning: You have uncommitted changes. Commit them first."
    exit 1
fi

# Push branch
echo "📤 Pushing branch 0.9.9 to origin..."
git push origin 0.9.9

echo "✅ Branch pushed successfully!"

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    echo "⚠️  GitHub CLI (gh) not found. Install it or create PR manually:"
    echo "   https://github.com/happysantoo/vajrapulse/compare/main...0.9.9"
    exit 0
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
    echo "⚠️  GitHub CLI not authenticated. Run: gh auth login"
    echo "   Then create PR manually or run this script again"
    exit 1
fi

# Create PR
echo "📝 Creating Pull Request..."
gh pr create \
  --base main \
  --head 0.9.9 \
  --title "Release 0.9.9: Code Quality Improvements and Refactoring" \
  --body-file PR_0.9.9_DESCRIPTION.md

echo "✅ Pull Request created successfully!"
echo ""
echo "🔗 View PR: https://github.com/happysantoo/vajrapulse/pulls"
