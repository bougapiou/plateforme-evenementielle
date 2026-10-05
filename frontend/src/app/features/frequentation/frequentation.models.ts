/** Fréquentation des stands — module indépendant du contrôle d'accès (billets) et des capteurs. */

export interface StandInfo {
  id: string;
  numero: string;
  standTypeNom: string;
  exposantNom?: string;
  eventNom: string;
  eventSlug: string;
}

export interface SignalerPassagePayload {
  nom?: string;
  prenom?: string;
  telephone?: string;
}

export interface StandFrequentation {
  standId: string;
  numero: string;
  standTypeNom: string;
  exposantNom?: string;
  visites: number;
}

export interface Frequentation {
  eventNom: string;
  totalVisites: number;
  stands: StandFrequentation[];
}
