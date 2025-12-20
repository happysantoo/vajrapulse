# Manual PR Creation Instructions

Since automated push/PR creation requires authentication, here are the manual steps:

## Step 1: Push Branch

```bash
git push origin 0.9.9
```

**If you encounter authentication issues:**
- Use SSH: `git remote set-url origin git@github.com:YOUR_USERNAME/vajrapulse.git`
- Or configure GitHub CLI: `gh auth login`

## Step 2: Create PR

### Option A: Using GitHub CLI (Recommended)

```bash
# Make sure you're authenticated
gh auth login

# Create PR
gh pr create \
  --base main \
  --head 0.9.9 \
  --title "Release 0.9.9: Code Quality Improvements and Refactoring" \
  --body-file PR_0.9.9_DESCRIPTION.md
```

### Option B: Using Script

```bash
./push-and-create-pr.sh
```

### Option C: Using GitHub Web Interface

1. Go to: https://github.com/YOUR_USERNAME/vajrapulse/compare/main...0.9.9
2. Click "Create Pull Request"
3. **Title**: `Release 0.9.9: Code Quality Improvements and Refactoring`
4. **Description**: Copy content from `PR_0.9.9_DESCRIPTION.md`

## Step 3: Verify PR

After creating the PR, verify:
- ✅ All commits are included
- ✅ PR description is complete
- ✅ CI/CD checks are running
- ✅ Branch is targeting `main`

## Commits Ready to Push

The following commits are ready to be pushed:

1. `1ac5410` - docs: Completely rewrite README for 0.9.9 release
2. `7c9eb14` - docs: Add What's New section to README for 0.9.9 release
3. `c6c90f9` - docs: Update CHANGELOG and create detailed 0.9.9 release notes
4. `5388b17` - docs: Add Principal Engineer review for 0.9.9 release
5. `8d06c1e` - docs: Add PR description and creation instructions for 0.9.9 release
6. `ed67451` - Release 0.9.9: Code Quality Improvements and Refactoring

**Total**: 6 commits ready for PR

---

## Quick Reference

**Branch**: `0.9.9`  
**Target**: `main`  
**PR Title**: `Release 0.9.9: Code Quality Improvements and Refactoring`  
**PR Description**: Use content from `PR_0.9.9_DESCRIPTION.md`

---

**Status**: ✅ Ready to push and create PR
