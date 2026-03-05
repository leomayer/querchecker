export interface FilterNode {
  id: string;
  name: string;
  children?: FilterNode[];
  parentName?: string; // Optional für den Breadcrumb-Kontext
  level: number; // Hilfreich für Styling (Einrückung)
}
