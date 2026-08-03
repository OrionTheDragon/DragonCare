# Codebase Memory MCP

This workspace has a local Codebase Memory MCP binary at:

```text
D:\codebase-memory-mcp-windows-amd64
```

The folder contains `codebase-memory-mcp.exe`, `install.ps1`, `LICENSE`, and `THIRD_PARTY_NOTICES.md`.

## Indexed Projects

The following graph indexes were created for the active mod projects:

| MCP project name | Source path | Mode | Persistence artifact |
| --- | --- | --- | --- |
| `DragonCare_1_21_1` | `D:\MyAddon\IceAndFire1.21.1\Addon` | `fast` | none |
| `DragonCare_1_20_1` | `D:\MyAddon\IceAndFire1.21.1\Addon 1.20.1` | `fast` | none |
| `D-MyAddon-IceAndFire1.21.1-MoreColorDragon` | `D:\MyAddon\IceAndFire1.21.1\MoreColorDragon` | `fast` | none |

The MoreColorDragon project name was normalized by the MCP server even though `MoreColorDragon` was requested
as the display name. Use the exact normalized name above in tool calls.

## Current Index Sizes

Last indexed on 2026-07-28:

- `DragonCare_1_21_1`: 3010 nodes, 6817 edges.
- `DragonCare_1_20_1`: 3040 nodes, 7173 edges.
- `D-MyAddon-IceAndFire1.21.1-MoreColorDragon`: 563 nodes, 1485 edges.

Indexes were created without `persistence=true`, so no `.codebase-memory/graph.db.zst` artifacts were written
into the mod folders.

## Tool Preference

Prefer graph tools for Java/code discovery:

1. `search_graph` to find classes, methods, functions, and variables.
2. `trace_path` for callers, callees, impact analysis, and data-flow-style questions.
3. `get_code_snippet` after `search_graph` returns the exact `qualified_name`.
4. `query_graph` for Cypher queries, complexity queries, and multi-hop analysis.
5. `get_architecture` for high-level summaries and hotspots.

Use ripgrep or file search for:

- localization keys and strings;
- JSON/TOML/resources/assets;
- Gradle files and shell scripts;
- exact error messages;
- cases where graph coverage is stale or insufficient.

## Reindex Commands

Use these MCP calls after substantial code changes:

```text
index_repository(repo_path="D:\MyAddon\IceAndFire1.21.1\Addon", name="DragonCare_1_21_1", mode="fast", persistence=false)
```

```text
index_repository(repo_path="D:\MyAddon\IceAndFire1.21.1\Addon 1.20.1", name="DragonCare_1_20_1", mode="fast", persistence=false)
```

```text
index_repository(repo_path="D:\MyAddon\IceAndFire1.21.1\MoreColorDragon", name="MoreColorDragon", mode="fast", persistence=false)
```

Use `mode="moderate"` or `mode="full"` only when semantic similarity or deeper cross-file discovery is worth
the extra indexing time.
