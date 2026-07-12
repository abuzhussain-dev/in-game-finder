import { defineTool } from "@lovable.dev/mcp-js";
import { waypointStore } from "../structures";

export default defineTool({
  name: "clear_waypoints",
  title: "Clear waypoints",
  description: "Delete every saved waypoint. Destructive: cannot be undone.",
  inputSchema: {},
  annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true },
  handler: () => {
    const n = waypointStore.clear();
    return { content: [{ type: "text", text: `Cleared ${n} waypoint(s).` }] };
  },
});