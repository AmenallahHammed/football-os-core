export type PlayerPosition = 'GK' | 'DEF' | 'MID' | 'FWD';

export interface PlayerProfile {
  id: string;
  name: string;
  position: PlayerPosition;
  shirtNumber: number;
  team: string;
  photoUrl: string;
  documents: string[];
  events: string[];
}
