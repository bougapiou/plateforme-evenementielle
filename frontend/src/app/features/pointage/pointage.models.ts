/** Pointage des visiteurs — module indépendant des événements (pas de slug, pas de billetterie). */

export interface PointageStand {
  id: string;
  nom: string;
}

export interface SignalerPointagePassagesPayload {
  standIds: string[];
  nom?: string;
  prenom?: string;
  telephone?: string;
}

export interface SignalerPointagePassagesResult {
  enregistres: number;
}

export interface PointageStandStat {
  standId: string;
  nom: string;
  visites: number;
  visitesIdentifiees: number;
  visitesAnonymes: number;
}

export interface PointageStats {
  totalVisites: number;
  totalIdentifiees: number;
  totalAnonymes: number;
  stands: PointageStandStat[];
}
