const TABLE_SEPARATOR = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/

/** The plain text of a Markdown excerpt, which the backend cuts at a fixed length, even inside a table. */
export function excerptText(markdown: string): string {
  return markdown
    .split('\n')
    .filter((line) => !TABLE_SEPARATOR.test(line))
    .map((line) =>
      line
        .replace(/^\s*(#{1,6}|>|[-*+]|\d+\.)\s+/, '')
        .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
        .replace(/[*_`~]+/g, '')
        .replace(/\|/g, ' '),
    )
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim()
}
