import { defineTool } from "@lovable.dev/mcp-js";
import { waypointStore } from "../structures";

export default defineTool({
  name: "list_waypoints",
  title: "List saved waypoints",
  description: "Return every waypoint currently saved on the server.",
  inputSchema: {},
  annotations: { readOnlyHint: true, idempotentHint: true, openWorldHint: false },
  handler: () => {
    const items = waypointStore.list();
    return {
      content: [
        {
          type: "text",
          text:
            items.length === 0
              ? "No waypoints saved."
              : items.map((w) => `${w.label}: ${w.x}, ${w.y}, ${w.z} (${w.color})`).join("\n"),
        },
      ],
      structuredContent: { waypoints: items },
    };
  },
});