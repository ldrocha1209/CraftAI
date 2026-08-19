export function formatMinecraftResponse(response: string): string {
    return response
        .replace(/```(?:[a-zA-Z0-9_-]+)?\s*/g, "")
        .replace(/`/g, "")
        .replace(/\*\*/g, "")
        .replace(/^\s*#{1,6}\s+/gm, "")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}
