export type ListTab = "my" | "saved" | "other";

const listTabs: ListTab[] = ["my", "saved", "other"];

export function parseListSearchParams(searchParams: {
  get(name: string): string | null;
}): {
  selectedId: number | null;
  tab: ListTab;
} {
  const selected = Number(searchParams.get("selected"));
  const tab = searchParams.get("tab");

  return {
    selectedId:
      Number.isSafeInteger(selected) && selected > 0 ? selected : null,
    tab: listTabs.includes(tab as ListTab) ? (tab as ListTab) : "my",
  };
}

export function buildListHref(selectedId: number, tab: ListTab): string {
  return `/lists?selected=${selectedId}&tab=${tab}`;
}
