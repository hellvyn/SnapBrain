// Placeholder so wrangler's `main` resolves; the real HTTP handler lands in a later task.
export default {
  fetch: () => new Response("not implemented", { status: 501 }),
} satisfies ExportedHandler;
