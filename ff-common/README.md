# FF Common Module

Shared data models, rule definitions, constants, and common exceptions for the Feature Toggle Service.

## Overview

This module provides the foundational data structures used across all components of the feature toggle system:
- **ff-server** (Admin Backend)
- **ff-sdk-java** (Server-side SDK)
- **ff-sdk-js** (Web SDK)
- **ff-simulator** (Load Testing)

## Key Components

### Data Models

#### 1. FeatureFlag
Represents a toggleable feature with metadata and evaluation rules.

**Key Fields:**
- `flagKey`: Unique identifier (e.g., "new_checkout_flow")
- `environment`: Deployment environment (dev/staging/prod)
- `status`: Enabled/disabled state
- `rules`: List of evaluation rules ordered by priority
- `experimentGroup`: A/B test group for mutual exclusion

#### 2. Rule
Defines evaluation logic with support for multiple rule types.

**Rule Types:**
- `KILL_SWITCH`: Emergency disable for all users
- `WHITELIST`: Specific users always get the action value
- `BLACKLIST`: Specific users always get default value
- `TARGETING`: Attribute-based conditions (region, VIP level, etc.)
- `PERCENTAGE_ROLLOUT`: Deterministic percentage-based rollout

**Example:**
```json
{
  "id": "rule_001",
  "priority": 1,
  "type": "TARGETING",
  "conditions": [
    {"attribute": "region", "operator": "in", "values": ["cn-beijing"]},
    {"attribute": "vipLevel", "operator": "gte", "values": [2]}
  ],
  "actionValue": "v2",
  "description": "VIP users in Beijing"
}
```

#### 3. Condition
Represents a single condition within a rule.

**Supported Operators:**
- Comparison: `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`
- Set operations: `IN`, `NOT_IN`
- String operations: `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `REGEX`
- Boolean: `IS_TRUE`, `IS_FALSE`

**Example JSON Structures:**

```json
// User ID in list
{"attribute": "uid", "operator": "in", "values": [1001, 1002, 1003]}

// Region equals
{"attribute": "region", "operator": "eq", "values": ["cn-beijing"]}

// VIP level >= 2
{"attribute": "vipLevel", "operator": "gte", "values": [2]}

// Email contains domain
{"attribute": "email", "operator": "contains", "values": ["@company.com"]}
```

#### 4. UserContext
Contains user attributes for rule evaluation.

**Common Attributes:**
- `uid`: User ID (numeric)
- `region`: Geographic region
- `vipLevel`: VIP level
- `deviceType`: Device type (ios/android/web)
- `email`: Email address
- `accountAge`: Account age in days
- `isEmployee`: Employee status (boolean)

#### 5. EvaluationDetail
Provides full explainability for flag evaluation decisions.

**Answers:**
- Is it enabled? → `enabled`, `value`
- For whom? → `userContextSnapshot`
- In which region? → `region`
- Associated with which release? → `releaseVersion`
- Why? → `reason`, `matchedConditions`

**Example:**
```json
{
  "flagKey": "new_checkout",
  "enabled": true,
  "value": "v2",
  "reason": "MATCHED_RULE",
  "matchedRuleId": "rule_001",
  "environment": "prod",
  "region": "cn-beijing",
  "userContextSnapshot": {"uid": 123, "vipLevel": 2},
  "matchedConditions": ["uid in [100,200]", "vipLevel >= 2"],
  "evaluatedAt": 1713849600000
}
```

### DTOs

- **BatchEvaluationRequest**: Request for evaluating multiple flags
- **EvaluationResponse**: Response containing evaluation results

### Constants

- **CommonConstants**: System-wide constants (Redis channels, HTTP headers, etc.)
- **UserAttributes**: Standard user attribute names
- **Environments**: Supported environment names

### Exceptions

- **FeatureToggleException**: Base exception
- **FlagNotFoundException**: Flag not found
- **InvalidRuleException**: Invalid rule configuration
- **AuthenticationException**: Authentication/authorization failure

## Usage

### Maven Dependency

```xml
<dependency>
    <groupId>com.featuretoggle</groupId>
    <artifactId>ff-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Example: Creating a Feature Flag

```java
FeatureFlag flag = FeatureFlag.builder()
    .flagKey("new_checkout_flow")
    .name("New Checkout Flow")
    .environment("prod")
    .status(1)
    .defaultValue("v1")
    .rules(Arrays.asList(
        Rule.builder()
            .id("rule_001")
            .priority(1)
            .type(Rule.RuleType.TARGETING)
            .conditions(Arrays.asList(
                Condition.builder()
                    .attribute("region")
                    .operator(Condition.Operator.IN)
                    .values(Arrays.asList("cn-beijing"))
                    .build()
            ))
            .actionValue("v2")
            .description("Users in Beijing")
            .build()
    ))
    .build();
```

### Example: Evaluating a Flag

```java
UserContext user = UserContext.builder()
    .userId("12345")
    .attributes(Map.of(
        "uid", 12345,
        "region", "cn-beijing",
        "vipLevel", 2
    ))
    .build();

// Evaluation logic is implemented in ff-sdk-core or ff-sdk-java
EvaluationDetail result = evaluator.evaluate(flag, user);
```

## Design Principles

1. **Immutability**: Models use Lombok's `@Builder` for immutable instances
2. **Serialization**: All models implement `Serializable` for distributed caching
3. **Validation**: Rules and conditions include `validate()` methods
4. **Explainability**: Conditions provide `toReadableString()` for debugging
5. **Extensibility**: `metadata` field in EvaluationDetail for future extensions

## See Also

- [Example Rule Configuration](src/main/resources/example-rule-config.json)
- [Design Document V1](../../设计文档V1.md)
