import { defineTool } from "@lovable.dev/mcp-js";
import { z } from "zod";
import { waypointStore } from "../structures";

export default defineTool({
  name: "add_waypoint",
  title: "Add waypoint",
  description: "Save a labeled waypoint at the given block coordinates.",
  inputSchema: {
    label: z.string().min(1).describe("Human-readable label, e.g. 'Village near spawn'."),
    x: z.number().int(),
    y: z.number().int().default(64),
    z: z.number().int(),
    color: z.string().default("#FFAA00").describe("Hex color for the waypoint marker."),
  },
  annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
  handler: ({ label, x, y, z, color }) => {
    const wp = waypointStore.add({ label, x, y, z, color });
    return {
      content: [{ type: "text", text: `Added waypoint '${label}' at ${x}, ${y}, ${z}.` }],
      structuredContent: { waypoint: wp },
    };
  },
});