
## Finding code

You have the `LSP` tool. Look a symbol up with it before you read files, then `Read` only those
lines with `offset` and `limit`:

- `workspaceSymbol`: a class or function by name.
- `findReferences`: its callers and users.
- `goToDefinition`: from a use to the definition.
- `documentSymbol`: the outline of a file with line numbers.

Use `Grep` for text that is not a symbol (configuration keys, SQL, Markdown), and when the tool
answers nothing.
