// Upstream brand_name is noisy (casing variants, compound names, independent
// garage names stored as brands). This module canonicalises each raw value
// against a hand-curated list. Unrecognised values collapse to "Independent".
//
// Matching:
//   1. Normalise the raw value (trim, lowercase, collapse whitespace).
//   2. Walk BRAND_RULES in order and return the first rule whose pattern
//      matches the normalised value.
//   3. If no rule matches, return "Independent".
//
// Order matters: rules are checked top-to-bottom so that compound names like
// "BP HARVEST ENERGY" resolve to the primary customer-facing brand (BP) rather
// than the operator (Harvest Energy). Matching is whole-word so short keys
// like "bp" don't match substrings of longer words.

export const INDEPENDENT_BRAND = "Independent";

type BrandRule = { keyword: string; canonical: string };

const BRAND_RULES: BrandRule[] = [
  // Supermarket fuel chains
  { keyword: "asda", canonical: "Asda" },
  { keyword: "tesco", canonical: "Tesco" },
  { keyword: "morrisons", canonical: "Morrisons" },
  { keyword: "sainsbury's", canonical: "Sainsbury's" },
  { keyword: "sainsburys", canonical: "Sainsbury's" },
  { keyword: "sainsbury", canonical: "Sainsbury's" },
  { keyword: "costco", canonical: "Costco" },

  // Oil majors (checked before operators so "BP HARVEST ENERGY" → BP)
  { keyword: "shell", canonical: "Shell" },
  { keyword: "bp", canonical: "BP" },
  { keyword: "esso", canonical: "Esso" },
  { keyword: "texaco", canonical: "Texaco" },
  { keyword: "totalenergies", canonical: "TotalEnergies" },
  { keyword: "total energies", canonical: "TotalEnergies" },
  { keyword: "total", canonical: "TotalEnergies" },
  { keyword: "gulf", canonical: "Gulf" },
  { keyword: "valero", canonical: "Valero" },
  { keyword: "essar", canonical: "Essar" },
  { keyword: "murco", canonical: "Murco" },
  { keyword: "maxol", canonical: "Maxol" },

  // Motorway / convenience / forecourt operators
  { keyword: "welcome break", canonical: "Welcome Break" },
  { keyword: "eg on the move", canonical: "EG On The Move" },
  { keyword: "circle k", canonical: "Circle K" },
  { keyword: "applegreen", canonical: "Applegreen" },
  { keyword: "co-op", canonical: "Co-op" },
  { keyword: "co op", canonical: "Co-op" },
  { keyword: "coop", canonical: "Co-op" },
  { keyword: "spar", canonical: "Spar" },

  // Mid-tier
  { keyword: "jet", canonical: "Jet" },
  { keyword: "pace", canonical: "Pace" },
  { keyword: "solo", canonical: "Solo" },

  // Smaller/regional
  { keyword: "harvest energy", canonical: "Harvest Energy" },
  { keyword: "harvest", canonical: "Harvest Energy" },
  { keyword: "central convenience", canonical: "Central Convenience" },
  { keyword: "highland fuels", canonical: "Highland Fuels" },
  { keyword: "morgan fuels", canonical: "Morgan Fuels" },
  { keyword: "nicholls", canonical: "Nicholl" },
  { keyword: "nicholl", canonical: "Nicholl" },
  { keyword: "gleaner", canonical: "Gleaner" },
  { keyword: "emo", canonical: "Emo" },
];

export const CANONICAL_BRANDS: string[] = Array.from(
  new Set(BRAND_RULES.map((r) => r.canonical)),
).sort();

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

const COMPILED_RULES = BRAND_RULES.map(({ keyword, canonical }) => ({
  // Whole-word match so "bp" doesn't match "abp" or "bpm".
  pattern: new RegExp(`\\b${escapeRegex(keyword)}\\b`),
  canonical,
}));

function normalize(raw: string): string {
  return raw.trim().toLowerCase().replace(/\s+/g, " ");
}

export function canonicalizeBrand(raw: string | null | undefined): string {
  if (!raw) return INDEPENDENT_BRAND;
  const normalized = normalize(raw);
  if (!normalized) return INDEPENDENT_BRAND;
  for (const { pattern, canonical } of COMPILED_RULES) {
    if (pattern.test(normalized)) return canonical;
  }
  return INDEPENDENT_BRAND;
}
