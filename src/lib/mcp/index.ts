import { defineMcp } from "@lovable.dev/mcp-js";
import listStructures from "./tools/list-structures";
import findStructure from "./tools/find-structure";
import listWaypoints from "./tools/list-waypoints";
import addWaypoint from "./tools/add-waypoint";
import clearWaypoints from "./tools/clear-waypoints";

export default defineMcp({
  name: "seedfinder-mcp",
  title: "SeedFinder MCP",
  version: "0.1.0",
  instructions:
    "Tools for locating Minecraft 1.21.1 structures from a known world seed and managing shared waypoints. " +
    "Use list_structures to discover ids, then find_nearest_structure with the seed + player XZ. " +
    "Save results with add_waypoint; list_waypoints and clear_waypoints manage the shared list.",
  tools: [listStructures, findStructure, listWaypoints, addWaypoint, clearWaypoints],
});