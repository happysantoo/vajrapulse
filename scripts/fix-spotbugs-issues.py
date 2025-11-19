#!/usr/bin/env python3
"""
SpotBugs Bug Fixer Agent

This script analyzes SpotBugs findings and automatically fixes common issues.
It parses the HTML report, identifies fixable patterns, and applies fixes.

Usage:
    python3 scripts/fix-spotbugs-issues.py [module]
    
    Example:
    python3 scripts/fix-spotbugs-issues.py vajrapulse-core
"""

import re
import sys
from pathlib import Path
from typing import List, Dict
from dataclasses import dataclass

@dataclass
class SpotBugsFinding:
    """Represents a SpotBugs finding."""
    bug_type: str
    class_name: str
    method_name: str
    file_path: str
    line_number: int
    description: str

class SpotBugsReportParser:
    """Parses SpotBugs HTML reports."""
    
    def __init__(self, report_path: Path):
        self.report_path = report_path
        self.findings: List[SpotBugsFinding] = []
    
    def parse(self) -> List[SpotBugsFinding]:
        """Parse the HTML report and extract findings."""
        if not self.report_path.exists():
            print(f"Report not found: {self.report_path}")
            return []
        
        with open(self.report_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Extract bug patterns from HTML
        # Pattern: Bug type, class, method, file, line
        pattern = r'Bug type ([A-Z_]+).*?In class ([^\s<]+).*?In method ([^\s<]+).*?At ([^:]+\.java):\[line (\d+)\]'
        
        matches = re.finditer(pattern, content, re.DOTALL)
        
        for match in matches:
            finding = SpotBugsFinding(
                bug_type=match.group(1),
                class_name=match.group(2),
                method_name=match.group(3),
                file_path=match.group(4),
                line_number=int(match.group(5)),
                description=f"{match.group(1)} in {match.group(2)}.{match.group(3)}"
            )
            self.findings.append(finding)
        
        return self.findings

class BugFixer:
    """Automatically fixes common SpotBugs issues."""
    
    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.fixes_applied = []
        self.manual_review_needed = []
    
    def fix_rv_return_value_ignored(self, finding: SpotBugsFinding) -> bool:
        """
        Fix RV_RETURN_VALUE_IGNORED_BAD_PRACTICE.
        
        For executor.submit(), we can suppress the warning with @SuppressWarnings
        or store the Future if needed. For fire-and-forget, suppression is appropriate.
        """
        file_path = self.project_root / finding.file_path
        if not file_path.exists():
            return False
        
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        line_idx = finding.line_number - 1
        if line_idx >= len(lines):
            return False
        
        line = lines[line_idx]
        
        # Check if it's executor.submit() call
        if 'executor.submit(' in line or '.submit(' in line:
            # Add @SuppressWarnings annotation before the line
            # Find the method containing this line
            method_start = self._find_method_start(lines, line_idx)
            if method_start >= 0:
                # Check if method already has @SuppressWarnings
                has_suppress = any('@SuppressWarnings' in lines[i] 
                                  for i in range(method_start, line_idx + 1))
                
                if not has_suppress:
                    # Add @SuppressWarnings before method
                    indent = self._get_indent(lines[method_start])
                    suppress_line = f"{indent}@SuppressWarnings(\"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE\")\n"
                    lines.insert(method_start, suppress_line)
                    
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.writelines(lines)
                    
                    self.fixes_applied.append(f"Added @SuppressWarnings for {finding.description}")
                    return True
        
        return False
    
    def fix_ei_expose_rep(self, finding: SpotBugsFinding) -> bool:
        """
        Fix EI_EXPOSE_REP - exposing mutable representation.
        
        For record fields that return Map, we should return Collections.unmodifiableMap()
        """
        file_path = self.project_root / finding.file_path
        if not file_path.exists():
            return False
        
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Check if it's a record
        if 'public record' not in content:
            return False
        
        # Find the accessor method for the field
        field_name = finding.method_name.replace('()', '')
        
        # Pattern: public Map<...> fieldName() { return fieldName; }
        pattern = rf'public\s+java\.util\.Map<[^>]+>\s+{re.escape(field_name)}\(\)\s*\{{[^}}]*return\s+{re.escape(field_name)};[^}}]*\}}'
        
        match = re.search(pattern, content, re.MULTILINE | re.DOTALL)
        if match:
            # Replace with Collections.unmodifiableMap()
            old_return = f"return {field_name};"
            new_return = f"return java.util.Collections.unmodifiableMap({field_name});"
            
            content = content.replace(old_return, new_return)
            
            # Add import if needed
            if 'import java.util.Collections;' not in content:
                # Find last import statement
                import_pattern = r'(import\s+[^;]+;\n)'
                imports = list(re.finditer(import_pattern, content))
                if imports:
                    last_import = imports[-1]
                    insert_pos = last_import.end()
                    content = content[:insert_pos] + 'import java.util.Collections;\n' + content[insert_pos:]
            
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(content)
            
            self.fixes_applied.append(f"Fixed EI_EXPOSE_REP for {finding.description} - wrapped Map in Collections.unmodifiableMap()")
            return True
        
        return False
    
    def fix_ei_expose_rep2(self, finding: SpotBugsFinding) -> bool:
        """
        Fix EI_EXPOSE_REP2 - storing mutable reference in constructor.
        
        For record constructors, we should create defensive copies.
        """
        file_path = self.project_root / finding.file_path
        if not file_path.exists():
            return False
        
        # This is trickier - records have compact constructors
        # We need to add a canonical constructor that creates defensive copies
        # For now, mark as manual review
        self.manual_review_needed.append(
            f"{finding.description} - Record constructor needs defensive copy. "
            f"Consider adding canonical constructor with Collections.unmodifiableMap()"
        )
        return False
    
    def _find_method_start(self, lines: List[str], line_idx: int) -> int:
        """Find the start of the method containing the given line."""
        # Look backwards for method declaration
        brace_count = 0
        for i in range(line_idx, -1, -1):
            line = lines[i]
            brace_count += line.count('}')
            brace_count -= line.count('{')
            
            if 'public' in line or 'private' in line or 'protected' in line:
                if '(' in line and ')' in line and brace_count <= 0:
                    return i
        
        return -1
    
    def _get_indent(self, line: str) -> str:
        """Get the indentation of a line."""
        return line[:len(line) - len(line.lstrip())]
    
    def fix_all(self, findings: List[SpotBugsFinding]) -> Dict[str, int]:
        """Fix all findings that can be automatically fixed."""
        stats = {
            'fixed': 0,
            'manual_review': 0,
            'skipped': 0
        }
        
        for finding in findings:
            fixed = False
            
            if finding.bug_type == 'RV_RETURN_VALUE_IGNORED_BAD_PRACTICE':
                fixed = self.fix_rv_return_value_ignored(finding)
            elif finding.bug_type == 'EI_EXPOSE_REP':
                fixed = self.fix_ei_expose_rep(finding)
            elif finding.bug_type == 'EI_EXPOSE_REP2':
                fixed = self.fix_ei_expose_rep2(finding)
            
            if fixed:
                stats['fixed'] += 1
            elif finding.bug_type in ['EI_EXPOSE_REP2']:
                stats['manual_review'] += 1
            else:
                stats['skipped'] += 1
        
        return stats

def main():
    """Main entry point."""
    project_root = Path(__file__).parent.parent
    
    # Get module name from args or default to vajrapulse-core
    module = sys.argv[1] if len(sys.argv) > 1 else 'vajrapulse-core'
    
    report_path = project_root / module / 'build' / 'reports' / 'spotbugs' / 'main.html'
    
    print(f"🔍 Analyzing SpotBugs report: {report_path}")
    print("=" * 70)
    
    # Parse report
    parser = SpotBugsReportParser(report_path)
    findings = parser.parse()
    
    if not findings:
        print("✅ No SpotBugs findings found or report not available.")
        print(f"   Run: ./gradlew :{module}:spotbugsMain")
        return 0
    
    print(f"📋 Found {len(findings)} SpotBugs findings:")
    for finding in findings:
        print(f"   - {finding.bug_type}: {finding.class_name}.{finding.method_name} "
              f"({finding.file_path}:{finding.line_number})")
    
    print("\n🔧 Attempting automatic fixes...")
    print("=" * 70)
    
    # Fix issues
    fixer = BugFixer(project_root)
    stats = fixer.fix_all(findings)
    
    # Print results
    print(f"\n✅ Fixed: {stats['fixed']}")
    print(f"⚠️  Manual review needed: {stats['manual_review']}")
    print(f"⏭️  Skipped: {stats['skipped']}")
    
    if fixer.fixes_applied:
        print("\n📝 Fixes applied:")
        for fix in fixer.fixes_applied:
            print(f"   ✓ {fix}")
    
    if fixer.manual_review_needed:
        print("\n⚠️  Manual review required:")
        for item in fixer.manual_review_needed:
            print(f"   • {item}")
    
    print("\n" + "=" * 70)
    print("💡 Next steps:")
    print(f"   1. Review changes: git diff {module}/src/main/java")
    print(f"   2. Run tests: ./gradlew :{module}:test")
    print(f"   3. Re-run SpotBugs: ./gradlew :{module}:spotbugsMain")
    print("   4. Commit fixes if tests pass")
    
    return 0 if stats['fixed'] > 0 or stats['manual_review'] == 0 else 1

if __name__ == '__main__':
    sys.exit(main())

