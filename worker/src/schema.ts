import { z } from "zod";

export const CATEGORIES = ["task", "finance", "shopping", "event", "reference", "unclassified"] as const;
export const ACTIONS = ["track_parcel", "add_calendar", "copy_text", "open_url", "none"] as const;

// Shape the model must produce. Key/value pairs and plain task strings keep the JSON schema simple;
// toExtractData turns them into the API contract from spec §6.
export const LlmOutput = z.object({
  category: z.enum(CATEGORIES),
  title: z.string(),
  extracted_info: z.array(z.object({ key: z.string(), value: z.string() })),
  action_type: z.enum(ACTIONS),
  action_payload: z.string(),
  tasks: z.array(z.string()),
});
export type LlmOutput = z.infer<typeof LlmOutput>;

export interface Task {
  id: number;
  description: string;
  is_completed: boolean;
}

export interface ExtractData {
  category: (typeof CATEGORIES)[number];
  title: string;
  extracted_info: Record<string, string>;
  action_type: (typeof ACTIONS)[number];
  action_payload: string;
  tasks: Task[];
}

const HTTP_URL = /^https?:\/\/\S+$/i;

export function toExtractData(o: LlmOutput): ExtractData {
  let action_type = o.action_type;
  let action_payload = o.action_payload.trim();
  // OCR text is untrusted input to the model: never hand the app a non-http link to open.
  if (action_type === "open_url" && !HTTP_URL.test(action_payload)) action_type = "none";
  if (action_type === "none") action_payload = "";
  return {
    category: o.category,
    title: o.title.trim().split(/\s+/).slice(0, 5).join(" "),
    extracted_info: Object.fromEntries(o.extracted_info.map((e) => [e.key, e.value])),
    action_type,
    action_payload,
    tasks: o.tasks.map((description, i) => ({ id: i + 1, description, is_completed: false })),
  };
}

export function trimForTier(d: ExtractData, premium: boolean): { data: ExtractData; tasks_total: number } {
  return { data: premium ? d : { ...d, tasks: d.tasks.slice(0, 1) }, tasks_total: d.tasks.length };
}
