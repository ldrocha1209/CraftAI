const WIKI_API_URL = "https://minecraft.wiki/api.php";

export async function searchMinecraftWiki(
    searchTerm: string
): Promise<string | null> {

    const searchUrl = new URL(WIKI_API_URL);

    searchUrl.searchParams.set("action", "query");
    searchUrl.searchParams.set("list", "search");
    searchUrl.searchParams.set("srsearch", searchTerm);
    searchUrl.searchParams.set("srlimit", "1");
    searchUrl.searchParams.set("format", "json");

    const searchResponse = await fetch(searchUrl);

    if (!searchResponse.ok) {
        throw new Error(
            `Minecraft Wiki search failed: ${searchResponse.status}`
        );
    }

    const searchData = await searchResponse.json();

    const results = searchData.query?.search;

    if (!results || results.length === 0) {
        return null;
    }

    const pageTitle = results[0].title;

    const pageUrl = new URL(WIKI_API_URL);

    pageUrl.searchParams.set("action", "query");
    pageUrl.searchParams.set("prop", "extracts");
    pageUrl.searchParams.set("explaintext", "1");
    pageUrl.searchParams.set("exsectionformat", "plain");
    pageUrl.searchParams.set("titles", pageTitle);
    pageUrl.searchParams.set("format", "json");

    const pageResponse = await fetch(pageUrl);

    if (!pageResponse.ok) {
        throw new Error(
            `Minecraft Wiki page request failed: ${pageResponse.status}`
        );
    }

    const pageData = await pageResponse.json();

    const pages = pageData.query?.pages;

    if (!pages) {
        return null;
    }

    const page = Object.values(pages)[0] as {
        extract?: string;
    };

    const extract = page.extract ?? null;

    if (!extract) {
        return null;
    }

    const maxLength = 12000;

    if (extract.length <= maxLength) {
        return extract;
    }

    return extract.substring(0, maxLength) + "\n[Wiki content truncated]";
  }