---
name: context7
description: Fetches up-to-date documentation and code examples for libraries, frameworks, APIs, and SDKs using Context7. Use this when researching library APIs, framework features, migrations, or troubleshooting library methods.
---

# Context7 Documentation Skill

Context7 provides up-to-date, version-specific documentation and code examples directly from official library documentation and source repositories.

## Workflow

1. **Resolve Library ID**:
   - Use `resolve-library-id` with the library name to obtain the exact `/org/project` identifier.
2. **Fetch Documentation**:
   - Use `get-library-docs` with the resolved library ID and specific topic to get API references, usage patterns, and snippets.
3. **Pagination & Refinement**:
   - For broad topics, paginate with `page=2`, `page=3` or narrow down the `topic` query.
