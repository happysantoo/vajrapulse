# Release 0.9.8 Plan

**Date**: 2025-01-XX  
**Status**: Planning  
**Target Release**: 0.9.8  
**Estimated Timeline**: 2-3 weeks

## Executive Summary

Release 0.9.8 focuses on **nice-to-have enhancements** identified in the PR #27 principal engineer review. These improvements enhance developer experience, add missing assertion types, and make stability detection configurable. All items are **low priority** and can be deferred if needed.

---

## 🎯 Phase 3: Nice-to-Have Enhancements

### 1. Make Stability Detection Configurable ⭐

**Priority**: 🟢 **LOW**  
**Estimated Effort**: 1-2 days  
**Source**: PR #27 Review - Section 1.3

#### Problem

The stability detection algorithm uses hardcoded constants:
- `TPS_TOLERANCE = 50.0` (TPS can vary by this amount and still be considered stable)
- `STABLE_INTERVALS_REQUIRED = 3` (number of consecutive stable intervals required)

These values may not be suitable for all use cases.

#### Proposed Solution

Add constructor parameters with sensible defaults:

```java
public AdaptiveLoadPattern(
    // ... existing parameters ...
    double stabilityTolerance,  // Default: 50.0
    int stableIntervalsRequired  // Default: 3
) {
    // ...
}
```

#### Implementation Details

1. **Add Parameters**:
   - `stabilityTolerance` (default: 50.0)
   - `stableIntervalsRequired` (default: 3)

2. **Update Constructors**:
   - Add overloaded constructors with new parameters
   - Maintain backward compatibility with default values

3. **Update Logic**:
   - Replace hardcoded constants with instance fields
   - Update `isStableAtCurrentTps()` to use instance fields

4. **Tests**:
   - Test with custom tolerance values
   - Test with custom interval requirements
   - Verify backward compatibility

#### Benefits

- More flexible for different use cases
- Allows fine-tuning of stability detection
- Maintains backward compatibility

#### Files to Modify

- `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AdaptiveLoadPatternSpec.groovy`

---

### 2. Improve Assertion Error Messages ⭐

**Priority**: 🟢 **LOW**  
**Estimated Effort**: 1 day  
**Source**: PR #27 Review - Section 4.1

#### Problem

Current error messages are informative but could be more actionable. They don't provide context or suggestions for resolution.

#### Current Implementation

```java
return AssertionResult.failure(
    "P%.0f latency %.2fms exceeds maximum %.2fms",
    percentile * 100, latencyMs, maxLatencyMs
);
```

#### Proposed Solution

Enhance error messages with:
- **Context**: How much over/under the limit
- **Suggestions**: Actionable recommendations
- **Relative metrics**: Percentage over limit, etc.

#### Implementation Details

1. **Latency Assertions**:
   ```java
   return AssertionResult.failure(
       "P%.0f latency %.2fms exceeds maximum %.2fms (%.1f%% over limit). " +
       "Consider: reducing load, optimizing server, or increasing timeout.",
       percentile * 100, latencyMs, maxLatencyMs,
       ((latencyMs - maxLatencyMs) / maxLatencyMs) * 100
   );
   ```

2. **Error Rate Assertions**:
   ```java
   return AssertionResult.failure(
       "Error rate %.2f%% exceeds maximum %.2f%% (%.1f%% over limit). " +
       "Consider: investigating server errors, reducing load, or checking dependencies.",
       actualErrorRate, maxErrorRate, ((actualErrorRate - maxErrorRate) / maxErrorRate) * 100
   );
   ```

3. **Throughput Assertions**:
   ```java
   return AssertionResult.failure(
       "Throughput %.2f TPS is below minimum required %.2f TPS (%.1f%% under limit). " +
       "Consider: increasing load, optimizing server, or checking bottlenecks.",
       actualTps, minTps, ((minTps - actualTps) / minTps) * 100
   );
   ```

#### Benefits

- More actionable error messages
- Better developer experience
- Easier debugging

#### Files to Modify

- `vajrapulse-api/src/main/java/com/vajrapulse/api/Assertions.java`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AssertionsSpec.groovy`

---

### 3. Add Missing Assertion Types ⭐⭐⭐

**Priority**: 🟢 **LOW**  
**Estimated Effort**: 2-3 days  
**Source**: PR #27 Review - Section 4.2

#### Problem

Some common assertion types are missing:
- **Rate of change**: "Latency should not increase by more than X%"
- **Trend**: "Error rate should be decreasing"
- **Relative**: "P95 should be within 2x of P50"

#### Proposed Solution

Add new assertion types to `Assertions` factory:

1. **Rate of Change Assertion**:
   ```java
   public static Assertion latencyRateOfChange(
       double percentile, 
       double maxIncreasePercentage,
       Metrics previousMetrics
   );
   ```

2. **Trend Assertion**:
   ```java
   public enum Trend {
       INCREASING, DECREASING, STABLE
   }
   
   public static Assertion errorRateTrend(Trend expectedTrend, Metrics previousMetrics);
   ```

3. **Relative Assertion**:
   ```java
   public static Assertion relativeLatency(
       double lowerPercentile, 
       double upperPercentile, 
       double maxRatio
   );
   ```

#### Implementation Details

1. **Rate of Change**:
   - Compare current metrics with previous metrics
   - Calculate percentage change
   - Fail if change exceeds threshold

2. **Trend**:
   - Compare current metrics with previous metrics
   - Determine trend (increasing, decreasing, stable)
   - Fail if trend doesn't match expected

3. **Relative**:
   - Compare two percentiles from same metrics
   - Calculate ratio
   - Fail if ratio exceeds threshold

#### Challenges

- **Previous Metrics Storage**: Need to store previous metrics for comparison
- **State Management**: Assertions are stateless, need to handle state externally
- **API Design**: How to pass previous metrics?

#### Proposed API Design

**Option 1: Context Object**
```java
public class AssertionContext {
    private Metrics previousMetrics;
    
    public AssertionContext(Metrics initialMetrics) {
        this.previousMetrics = initialMetrics;
    }
    
    public AssertionResult evaluate(Assertion assertion, Metrics currentMetrics) {
        // Store previous, evaluate, update
        AssertionResult result = assertion.evaluate(currentMetrics, previousMetrics);
        this.previousMetrics = currentMetrics;
        return result;
    }
}
```

**Option 2: Stateful Assertion**
```java
public interface StatefulAssertion {
    AssertionResult evaluate(Metrics currentMetrics);
    void update(Metrics metrics);
}
```

**Option 3: Functional with Previous Metrics**
```java
public static Assertion latencyRateOfChange(
    double percentile,
    double maxIncreasePercentage
) {
    return (current, previous) -> {
        // Compare current with previous
    };
}
```

**Recommendation**: Use **Option 1** (Context Object) for cleaner API and better state management.

#### Benefits

- More comprehensive assertion coverage
- Better validation capabilities
- Supports advanced testing scenarios

#### Files to Create/Modify

- `vajrapulse-api/src/main/java/com/vajrapulse/api/AssertionContext.java` (new)
- `vajrapulse-api/src/main/java/com/vajrapulse/api/Assertions.java` (modify)
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AssertionsSpec.groovy` (modify)

---

## 📊 Feature Priority Matrix

| Feature | Priority | Effort | Impact | Recommendation |
|---------|----------|--------|--------|----------------|
| Configurable Stability Detection | 🟢 LOW | 1-2 days | Medium | ✅ Include if time permits |
| Improved Error Messages | 🟢 LOW | 1 day | Low-Medium | ✅ Quick win |
| Missing Assertion Types | 🟢 LOW | 2-3 days | Medium-High | ✅ High value, consider |

---

## 🎯 Recommended 0.9.8 Release Scope

### Must-Have Features (0.9.8)

1. ✅ **Improved Error Messages** - Quick win, improves developer experience
2. ✅ **Configurable Stability Detection** - Low effort, good value

### Nice-to-Have (If Time Permits)

3. ⚠️ **Missing Assertion Types** - Higher effort, but high value

### Defer to 0.9.9 or Later

- Additional assertion types can be added incrementally
- Other nice-to-have features from future reviews

---

## 📅 Estimated Timeline

**0.9.8 Release**: 2-3 weeks

- **Week 1**: Improved Error Messages, Configurable Stability Detection
- **Week 2**: Missing Assertion Types (if included)
- **Week 3**: Testing, Documentation, Release

---

## 🎓 Success Criteria

- [ ] All selected features implemented
- [ ] ≥90% test coverage maintained
- [ ] All tests pass
- [ ] Documentation updated
- [ ] CHANGELOG updated
- [ ] Examples updated (if applicable)
- [ ] Backward compatibility maintained

---

## 📝 Implementation Notes

### Backward Compatibility

- All changes must maintain backward compatibility
- Use default parameters where possible
- Add overloaded constructors/methods

### Testing Strategy

- Unit tests for all new features
- Integration tests for assertion types
- Performance tests for assertion evaluation

### Documentation

- Update JavaDoc for all new APIs
- Add usage examples
- Update README.md with new features

---

## 🔄 Dependencies

### Prerequisites

- 0.9.7 release must be complete
- All Phase 1 and Phase 2 fixes from PR #27 must be merged

### Blocking Issues

- None identified

---

## 📚 Related Documents

- `documents/releases/PR_27_PRINCIPAL_ENGINEER_REVIEW.md` - Source of requirements
- `documents/releases/RELEASE_0.9.7_SUMMARY.md` - Previous release summary
- `documents/roadmap/TASK_PLAN_VAJRAPULSE.md` - Overall task planning

---

## 🚀 Next Steps

1. **Review and Prioritize**: Review this plan and prioritize features
2. **Create GitHub Issues**: Create issues for selected features
3. **Begin Implementation**: Start with quick wins (error messages)
4. **Regular Progress Reviews**: Weekly progress reviews

---

**Status**: 📋 **PLANNING**  
**Last Updated**: 2025-01-XX

