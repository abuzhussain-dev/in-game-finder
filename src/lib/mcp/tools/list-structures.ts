import { defineTool } from "@lovable.dev/mcp-js";
import { STRUCTURES } from "../structures";

export default defineTool({
  name: "list_structures",
  title: "List supported structures",
  description: "Return every Minecraft 1.21.1 structure the finder can locate, with their placement algorithm.",
  inputSchema: {},
  annotations: { readOnlyHint: true, idempotentHint: true, openWorldHint: false },
  handler: () => ({
    content: [
      {
        type: "text",
        text: STRUCTURES.map((s) => `${s.id} — ${s.name} (${s.placement})`).join("\n"),
      },
    ],
    structuredContent: { structures: STRUCTURES },
  }),
});