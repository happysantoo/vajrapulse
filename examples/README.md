# VajraPulse Examples

> **📚 User Examples**: These are production-ready examples for end users learning VajraPulse.  
> For internal framework testing, see [`internal-tests/`](../internal-tests/).

This directory contains real-world examples demonstrating how to use VajraPulse for load testing.

## Available Examples

### `http-load-test/`
A complete HTTP API load testing example with:
- Real HTTP client implementation using Java 21 virtual threads
- Full observability stack (Docker Compose, Grafana, OpenTelemetry)
- Multiple load patterns (static, ramp, step, sine, spike)
- Both CLI and programmatic usage examples
- Comprehensive documentation and quick start guides

**Perfect for**: Learning how to test HTTP APIs, setting up observability, understanding load patterns.

## Purpose

These examples are designed to:
- ✅ Show real-world usage patterns
- ✅ Provide copy-paste ready code
- ✅ Demonstrate best practices
- ✅ Include complete setup instructions
- ✅ Help users get started quickly

## Getting Started

1. **Choose an example** that matches your use case
2. **Read the example's README** for specific instructions
3. **Copy and adapt** the code for your own testing needs
4. **Follow the quick start guide** to run it

## Examples vs Internal Tests

| Directory | Purpose | Audience |
|-----------|---------|----------|
| **`examples/`** | User education, real-world scenarios | End users |
| **`internal-tests/`** | Framework validation, automated testing | Testing agent, developers |

**Examples** are production-ready, well-documented, and designed for users to learn from.  
**Internal tests** are minimal, focused test cases used by the automated testing framework.

## Contributing Examples

When adding new examples:
- Include comprehensive README with setup instructions
- Provide both CLI and programmatic usage
- Include observability setup if applicable
- Make it copy-paste ready for users
- Document all configuration options

