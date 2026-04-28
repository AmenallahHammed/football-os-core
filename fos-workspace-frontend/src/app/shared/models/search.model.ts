export interface SearchResult {
  id: string;
  type: 'Document' | 'Event';
  title: string;
  context: string;
}
