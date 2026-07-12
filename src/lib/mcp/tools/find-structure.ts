import { defineTool } from "@lovable.dev/mcp-js";
import { z } from "zod";
import { findNearestStructure } from "../structures";

export default defineTool({
  name: "find_nearest_structure",
  title: "Find nearest structure",
  description:
    "Locate the nearest instance of a Minecraft structure using a known world seed and the player's XZ coordinates. Returns block coordinates, distance, and compass bearing.",
  inputSchema: {
    seed: z.string().describe("Minecraft world seed as a signed 64-bit integer, e.g. '-1234567890'."),
    structure: z.string().describe("Structure id (see list_structures), e.g. 'village', 'ancient_city'."),
    playerX: z.number().int().describe("Player X block coordinate."),
    playerZ: z.number().int().describe("Player Z block coordinate."),
    radiusChunks: z.number().int().positive().optional().describe("Search radius in chunks (default 3200)."),
  },
  annotations: { readOnlyHint: true, idempotentHint: true, openWorldHint: false },
  handler: ({ seed, structure, playerX, playerZ, radiusChunks }) => {
    let seedBig: bigint;
    try {
      seedBig = BigInt(seed);
    } catch {
      return { content: [{ type: "text", text: `Invalid seed: ${seed}` }], isError: true };
    }
    const result = findNearestStructure(seedBig, structure, playerX, playerZ, radiusChunks);
    if (!result) {
      return {
        content: [{ type: "text", text: `No ${structure} found within search radius.` }],
        isError: true,
      };
    }
    return {
      content: [
        {
          type: "text",
          text: `Nearest ${structure}: X=${result.x} Z=${result.z} (${result.distance} blocks ${result.bearing})`,
        },
      ],
      structuredContent: result,
    };
  },
});