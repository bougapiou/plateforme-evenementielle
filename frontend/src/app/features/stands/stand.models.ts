export type StandStatus = 'DISPONIBLE' | 'INDISPONIBLE';
export type StandReservationStatus =
  | 'EN_ATTENTE' | 'RESERVE_TEMP' | 'ATTENTE_PAIEMENT' | 'PAYE' | 'CONFIRME' | 'ANNULE' | 'EXPIRE';

export interface StandType {
  id: string;
  eventId: string;
  nom: string;
  description?: string;
  dimensions?: string;
  prixMontant: number;
  devise: string;
  prixFormatte: string;
  quantiteTotale: number;
  quantiteReservee: number;
  quantiteRestante: number;
  equipements?: string;
  conditions?: string;
  ordre: number;
}

export interface StandTypePayload {
  nom: string;
  description?: string;
  dimensions?: string;
  prixMontant: number;
  devise?: string;
  quantiteTotale: number;
  equipements?: string;
  conditions?: string;
}

export interface Stand {
  id: string;
  eventId: string;
  standTypeId: string;
  standTypeNom: string;
  prixMontant: number;
  prixFormatte: string;
  numero: string;
  positionX?: number;
  positionY?: number;
  statut: StandStatus;
  disponible: boolean;
}

export interface StandReservation {
  id: string;
  reference: string;
  numeroReservation: string;
  eventId: string;
  eventNom: string;
  standId: string;
  standNumero: string;
  standTypeNom: string;
  structureId?: string;
  structureNom?: string;
  montant: number;
  devise: string;
  montantFormatte: string;
  statut: StandReservationStatus;
  informations?: string;
  holdExpireLe?: string;
  dateLimitePaiement?: string;
  payeLe?: string;
  createdAt: string;
}
