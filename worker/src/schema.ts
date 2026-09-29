import { z } from "zod";

export const CATEGORIES = ["task", "finance", "shopping", "event", "reference", "unclassified"] as const;
export const KINDS = ["checklist", "steps"] as const;
export const ROLES = ["belanja", "todo", "bawa", "lainnya"] as const;
export const ACTIVATIONS = ["masak", "beli", "kerjakan", "bayar", "ikut", "coba", "none"] as const;
export const ACTION_TYPES = [
  "add_calendar", "copy_text", "open_url", "track_parcel", "open_maps", "whatsapp", "call", "search_product",
] as const;
export const MARKETPLACES = ["shopee", "tokopedia", "other"] as const;

export type Category = (typeof CATEGORIES)[number];
export type Kind = (typeof KINDS)[number];
export type Role = (typeof ROLES)[number];
export type Activation = (typeof ACTIVATIONS)[number];
export type ActionType = (typeof ACTION_TYPES)[number];

/** Keeps the elements that parse and drops the rest, so one sloppy element does not cost the whole answer. */
const each = <T extends z.ZodType>(schema: T) =>
  z.array(z.unknown()).catch([]).transform((xs) =>
    xs.flatMap((x) => {
      const r = schema.safeParse(x);
      return r.success ? [r.data as z.output<T>] : [];
    }),
  );

// Small models often send an item as a bare string or a price as "189000"; both still count.
const LlmItem = z.preprocess(
  (x) => (typeof x === "string" ? { text: x } : x),
  z.object({
    text: z.string(),
    due: z.string().catch(""),
    minutes: z.coerce.number().catch(0),
    price: z.coerce.number().catch(0),
    size: z.string().catch(""),
  }),
);

// Shape the model must produce. An unknown category still fails, so the adapter retries once.
export const LlmOutput = z.object({
  category: z.enum(CATEGORIES),
  title: z.string(),
  info: each(z.object({ key: z.string(), value: z.string() })),
  lists: each(
    z.object({
      title: z.string().catch(""),
      kind: z.enum(KINDS).catch("checklist"),
      role: z.enum(ROLES).catch("lainnya"),
      items: each(LlmItem),
    }),
  ),
  actions: each(z.object({ type: z.string(), payload: z.string() })),
  activation: z.enum(ACTIVATIONS).catch("none"),
});
export type LlmOutput = z.infer<typeof LlmOutput>;

export interface ListItem {
  text: string;
  due: string;
  minutes: number;
  price: number;
  size: string;
}

export interface ItemList {
  title: string;
  kind: Kind;
  role: Role;
  items: ListItem[];
}

export interface Action {
  type: ActionType;
  payload: string;
}

export interface ExtractData {
  category: Category;
  title: string;
  info: Record<string, string>;
  lists: ItemList[];
  actions: Action[];
  activation: Activation;
}

const MAX_INFO = 8;
const MAX_LISTS = 6;
const MAX_ITEMS = 40;
const MAX_TEXT = 200;
const MAX_ACTIONS = 3;
const HTTP_URL = /^https?:\/\/\S+$/i;
const DUE = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?$/;

const clip = (s: string, max: number) => s.trim().slice(0, max);
const whole = (n: number, max: number) => (Number.isFinite(n) && n >= 0 && n <= max ? Math.round(n) : 0);

/** `YYYY-MM-DD` or `YYYY-MM-DDTHH:MM` on a real calendar day, else null (rejects 2026-02-30 and 24:00). */
export function validDue(s: string): string | null {
  const t = s.trim();
  const m = DUE.exec(t);
  if (!m) return null;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const day = new Date(Date.UTC(y, mo - 1, d));
  if (day.getUTCFullYear() !== y || day.getUTCMonth() !== mo - 1 || day.getUTCDate() !== d) return null;
  if (m[4] !== undefined && (Number(m[4]) > 23 || Number(m[5]) > 59)) return null;
  return t;
}

const act = (type: ActionType, payload: string): Action => ({ type, payload });

/** Validates one model action. OCR text is untrusted, so anything unexpected is dropped rather than repaired. */
export function normalizeAction(type: string, payload: string): Action | null {
  const p = payload.trim();
  const bar = p.indexOf("|");
  switch (type) {
    case "add_calendar": {
      const when = bar < 0 ? "" : p.slice(0, bar).trim();
      const title = bar < 0 ? "" : clip(p.slice(bar + 1), 100);
      return when.includes("T") && validDue(when) && title ? act(type, `${when}|${title}`) : null;
    }
    case "copy_text":
    case "open_maps":
      return p && p.length <= MAX_TEXT ? act(type, p) : null;
    case "open_url":
      return HTTP_URL.test(p) && p.length <= 2000 ? act(type, p) : null;
    case "track_parcel":
      return /^[A-Za-z0-9-]{1,40}$/.test(p) ? act(type, p) : null;
    case "whatsapp": {
      // wa.me needs the country code: 08… and 8… are Indonesian mobiles.
      const digits = p.replace(/\D/g, "");
      const n = digits.startsWith("0") ? `62${digits.slice(1)}` : digits.startsWith("8") ? `62${digits}` : digits;
      return n.length >= 8 && n.length <= 15 ? act(type, n) : null;
    }
    case "call": {
      const digits = p.replace(/\D/g, "");
      return digits.length >= 5 && digits.length <= 15 ? act(type, (p.startsWith("+") ? "+" : "") + digits) : null;
    }
    case "search_product": {
      const market = bar < 0 ? "" : p.slice(0, bar).trim().toLowerCase();
      const name = clip(bar < 0 ? p : p.slice(bar + 1), 100);
      const known = (MARKETPLACES as readonly string[]).includes(market) ? market : "other";
      return name ? act(type, `${known}|${name}`) : null;
    }
    default:
      return null;
  }
}

export function toExtractData(o: LlmOutput): ExtractData {
  const info = new Map<string, string>();
  for (const { key, value } of o.info) {
    const k = clip(key, 60);
    const v = clip(value, MAX_TEXT);
    if (k && v && !info.has(k) && info.size < MAX_INFO) info.set(k, v);
  }

  const lists: ItemList[] = [];
  for (const l of o.lists.slice(0, MAX_LISTS)) {
    const seen = new Set<string>();
    const items: ListItem[] = [];
    for (const it of l.items) {
      const text = clip(it.text, MAX_TEXT);
      if (!text || seen.has(text.toLowerCase()) || items.length >= MAX_ITEMS) continue;
      seen.add(text.toLowerCase());
      items.push({
        text,
        due: validDue(it.due) ?? "",
        minutes: whole(it.minutes, 1440),
        price: whole(it.price, 10_000_000_000),
        size: clip(it.size, 40),
      });
    }
    if (items.length > 0) lists.push({ title: clip(l.title, 60) || "Daftar", kind: l.kind, role: l.role, items });
  }

  const actions: Action[] = [];
  for (const a of o.actions) {
    const n = normalizeAction(a.type, a.payload);
    if (n && actions.length < MAX_ACTIONS && !actions.some((x) => x.type === n.type && x.payload === n.payload)) actions.push(n);
  }

  return {
    category: o.category,
    title: o.title.trim().split(/\s+/).slice(0, 5).join(" "),
    // fromEntries defines own properties, so a "__proto__" key stays plain data.
    info: Object.fromEntries(info),
    lists,
    actions,
    activation: o.activation,
  };
}
