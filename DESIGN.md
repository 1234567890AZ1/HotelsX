# Design

<!-- impeccable:design-schema 1 -->

## Design Tokens

### Color - White Field / 白场

A modern-minimal single-theme admin surface: paper-white ground, ink-black navigation rail, cobalt signal accent, hairline grid instead of shadows. Direction chosen over the previous Brass Night Front Desk / 黄铜夜台 world. API and business structure unchanged; only the visual world is re-dressed.

**Single theme — there is no dark mode and no theme toggle.** `body.dark` is intentionally unsupported.

| Token | Value | Usage |
|---|---|---|
| `--c-bg` | `#F4F4F2` | App ground |
| `--c-bg-grad-1` | `#F7F7F5` | Topbar fade start |
| `--c-bg-grad-2` | `#F1F1EF` | Ground fallback |
| `--c-surface` | `#FFFFFF` | Cards, panels, inputs |
| `--c-surface-2` | `#FAFAF8` | Row hover, alert bar |
| `--c-surface-3` | `#F2F2EF` | Track, secondary button hover |
| `--c-border` | `#E7E7E2` | All hairlines |
| `--c-border-strong` | `#D3D3CC` | Control outlines |
| `--c-text` | `#121211` | Ink: text, primary button, chart bars |
| `--c-text-dim` | `#63635C` | Secondary text |
| `--c-text-faint` | `#9C9C94` | Labels, placeholder, empty |
| `--c-amber` | `#1F35E0` | **Signal accent**: focus ring, active nav, key data |
| `--c-amber-deep` | `#1729B4` | Accent press |
| `--c-gold` | `#4A5FF0` | Accent bright (rail badge) |
| `--c-danger` / `--c-danger-strong` | `#C0392B` / `#AE3225` | Destructive, negative amounts |
| `--c-ok` / `--c-ok-strong` | `#16704A` / `#14603F` | Success, positive amounts |
| `--c-info` / `--c-info-strong` | `#1B6E8C` / `#175F79` | Locked / neutral status |
| `--c-warn` / `--c-warn-strong` | `#A66A00` / `#8A5800` | Maintenance / warning |
| `--c-console-bg` / `--c-console-text` | `#121211` / `#DEDED8` | Terminal block |

**Rail-only tokens:** `--c-rail` (`#121211`), `--c-rail-text` (`#EDEDE9`), `--c-rail-dim` (`#87877F`), `--c-rail-line` (`#2A2A27`), `--c-rail-hover` (`#1D1D1B`).

**Palette policy:** monochrome-first. Chromatic color appears only in three places — the cobalt accent, status dots, and money in/out. Numbers are always ink. Status never uses a filled pill; it uses a 6px dot plus a label. No gold, no brass, no purple, no gradients on data.

**Variable contract:** every `--c-*` name is a hard contract with `dashboard.html`'s inline JS and `WebServer.java`'s rendered fragments. Values may change; names must not be removed.

### Type

| Role | Stack |
|---|---|
| UI / body | `Instrument Sans`, `PingFang SC`, `HarmonyOS Sans SC`, `MiSans`, `Microsoft YaHei UI`, `Noto Sans CJK SC` |
| Data / labels | `JetBrains Mono`, `SF Mono`, `Cascadia Code`, `Consolas` |

**No web fonts, no CDN.** The panel ships and requests zero external assets, so it renders identically offline and on an isolated LAN. `Instrument Sans` and `JetBrains Mono` stay at the head of each stack purely as opportunistic upgrades — they are used only if already installed locally; otherwise the CJK/system faces take over. Distinctiveness is carried by treatment (mono micro-labels, uppercase tracking, tabular numerals, negative display tracking), not by a downloaded face.

| Level | Size | Treatment |
|---|---|---|
| h1 | 30px | weight 600, `-.02em` |
| h2 | 20px | weight 600, `-.015em` |
| h3 | 16px | weight 600 |
| h4 / panel title | 13–15px | weight 600 |
| Metric value | clamp(24–32px) | weight 600, mono, tabular, `-.035em` |
| Micro-label | 10px | mono, uppercase, `letter-spacing .14em`, faint |
| Table header | 10px | mono, uppercase, `.14em`, no fill, 1px ink bottom rule |

Rules: micro-labels are always mono + uppercase + tracked. Money and counts are always mono + tabular. Display sizes use negative tracking; micro sizes use positive tracking.

### Spacing / Radius / Shadow

- Spacing: 4px base. Panel padding 20–22px, panel gap 22px, control padding 7×14px, table cell 11×12px.
- Radius: `--r-card` 12px (panels), `--r-control` 6px (buttons, inputs), `--r-pill` 999px (badges only).
- Shadow: **none on flat surfaces.** `--shadow-card: none`. `--shadow-pop` only for modal and toast. `--shadow-focus` is a 3px cobalt ring at 16% alpha.

## Components

| Component | Form |
|---|---|
| Navigation rail | Fixed 216px ink-black column. Section labels are 10px mono uppercase `#87877F`. Items are 13.5px plain text with a 15px icon at 75% opacity; active = white text + `--c-rail-hover` fill + a 3px cobalt bar on the rail edge. |
| Topbar | Sticky, transparent, bottom hairline, fading into the ground. Title left, actions right. Live indicator = green dot + mono uppercase caption. |
| Metric grid (`.stats`) | One continuous 1px grid: `gap:1px` over a `--c-border` background, cards are borderless white cells. Numbers are the hero; the semantic color lives only on the label's dot. Optional 2px progress line pinned to the cell bottom. |
| Panel (`.section`) | White, 1px hairline, 12px radius, no shadow. Title row separated by a hairline; count badge sits on the right. |
| Table | Header: 10px mono uppercase, no background, 1px ink bottom rule. Rows: hairline separators, hover `--c-surface-2`, last row borderless. |
| Status tag | 6px dot + 12px label, no fill, no border. Colors from the semantic family only. |
| Button | Primary = solid ink with white text. Secondary = white + `--c-border-strong`. Danger = outlined red that fills on hover. No gradients, no lift on hover. |
| Input | White, 1px `--c-border-strong`, 6px radius, no inner shadow. Focus = ink border + cobalt ring. Numeric inputs use mono. |
| Modal | White, 14px radius, hairline, `--shadow-pop`, 38% ink scrim with 4px blur. Enters with 8px rise over 200ms. |
| Toast | White, hairline, 2px colored left edge (semantic), 380px max, slides down 6px. |
| Console | Ink-black terminal, mono 12px, 1.65 line-height, `#8FA2FF` command echo, `#FF8A7A` errors. |

## Principles

1. **Grid over decoration.** Structure comes from 1px hairlines and shared edges, not from shadows, gradients, or glow.
2. **One accent, earned.** Cobalt marks focus, active state, and the single most important number on a screen. Nothing else.
3. **Numbers are the interface.** Mono, tabular, tight tracking, ink color. Semantic color describes the label, never the value.
4. **Text does the hierarchy work.** Size, weight, tracking, and case — four levers, no color required.
5. **One view at a time.** `.page-section` is `display:none` by default; a hash-driven router activates exactly one, and lazily loads its data on first visit.
6. **Motion is a receipt, not a show.** 140–260ms, `cubic-bezier(.2,0,0,1)`, opacity and small translations only. Fully suppressed under `prefers-reduced-motion`.
7. **Contract before cosmetics.** `--c-*` names, `<!--HX_*-->` tokens, and every `/api/*` endpoint are frozen. Only values and form change.