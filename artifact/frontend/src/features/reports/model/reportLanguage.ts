/**
 * Upstream writes some fixed English text into every report whatever the output language:
 * structured-output labels ("**Rating**:", "**Executive Summary**:"), the rating scale words
 * and debate prefixes ("Bull Analyst:"). The model's own text is already in the chosen
 * language, so only these are translated, at display time.
 */
const TR_LABELS: Record<string, string> = {
  Rating: 'Karar',
  'Executive Summary': 'Yönetici Özeti',
  'Investment Thesis': 'Yatırım Tezi',
  'Price Target': 'Hedef Fiyat',
  'Time Horizon': 'Zaman Ufku',
  Recommendation: 'Öneri',
  Rationale: 'Gerekçe',
  'Strategic Actions': 'Stratejik Adımlar',
  Action: 'İşlem',
  Reasoning: 'Gerekçe',
  'Entry Price': 'Giriş Fiyatı',
  'Stop Loss': 'Zarar Durdur',
  'Position Sizing': 'Pozisyon Büyüklüğü',
}

const TR_RATINGS: Record<string, string> = {
  Buy: 'Al',
  Overweight: 'Ağırlık Artır',
  Hold: 'Tut',
  Underweight: 'Ağırlık Azalt',
  Sell: 'Sat',
}

/** Imported runs don't know their language; Turkish-only letters give it away. */
function looksTurkish(text: string): boolean {
  return (text.match(/[ğışĞİŞ]/g)?.length ?? 0) >= 10
}

/** The language a run's reports are written in; for imported runs the text itself decides. */
export function reportLanguage(
  recorded: string,
  source: 'PLATFORM' | 'EXTERNAL',
  sample: string | undefined,
): string {
  return source === 'EXTERNAL' && sample && looksTurkish(sample) ? 'Turkish' : recorded
}

export function localizeReport(markdown: string, language?: string | null): string {
  // Imported runs are recorded as "English" whatever they contain, so the text decides too.
  if (language !== 'Turkish' && !looksTurkish(markdown)) return markdown
  const labels = Object.keys(TR_LABELS).join('|')
  const ratings = Object.keys(TR_RATINGS).join('|')
  return markdown
    .replace(
      new RegExp(`\\*\\*(${labels})\\*\\*`, 'g'),
      (_, label: string) => `**${TR_LABELS[label]}**`,
    )
    .replace(
      new RegExp(`(\\*\\*(?:Karar|Öneri|İşlem)\\*\\*\\s*:\\s*\\**)(${ratings})\\b`, 'g'),
      (_, prefix: string, rating: string) => `${prefix}${TR_RATINGS[rating]}`,
    )
    .replace(/\b(Overweight|Underweight)\b/g, (rating) => TR_RATINGS[rating]!)
    .replace(/:\s*not provided\b/gi, ': belirtilmedi')
    .replace(/^\s*(Bull|Bear|Aggressive|Conservative|Neutral) Analyst:\s*/gm, '')
}
