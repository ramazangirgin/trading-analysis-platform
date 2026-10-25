const FALLBACK_KEY = 'preview.locked.other'

/** The label key of a locked section; `exists` tells whether the translation for `key` is known. */
export function lockedTitleKey(key: string, exists: (labelKey: string) => boolean): string {
  const labelKey = `preview.locked.${key}`
  return exists(labelKey) ? labelKey : FALLBACK_KEY
}
