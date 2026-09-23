export type RegistrationType = 'PARTICULIER' | 'STRUCTURE';
export type RegistrationStatus = 'EN_ATTENTE' | 'CONFIRMEE' | 'ANNULEE' | 'REFUSEE';

export interface ParticipantView {
  id: string;
  nom: string;
  prenom?: string;
  email?: string;
  telephone?: string;
  fonction?: string;
}

export interface Registration {
  id: string;
  reference: string;
  eventId: string;
  eventNom: string;
  type: RegistrationType;
  statut: RegistrationStatus;
  structureId?: string;
  structureNom?: string;
  contactNom?: string;
  contactEmail?: string;
  contactTelephone?: string;
  nombreParticipants: number;
  informations?: string;
  motifRefus?: string;
  ticketOrderId?: string;
  ticketOrderReference?: string;
  ticketOrderStatut?: string;
  participants: ParticipantView[];
  confirmeeLe?: string;
  createdAt: string;
}

export interface RegisterPayload {
  type: RegistrationType;
  structureId?: string;
  contactNom?: string;
  contactEmail?: string;
  contactTelephone?: string;
  informations?: string;
  participants?: {
    /** L'un des deux au moins doit être renseigné (voir IdentiteRequise). */
    nom?: string;
    prenom?: string;
    email?: string;
    telephone?: string;
    fonction?: string;
  }[];
  tickets?: { eventTicketId: string; quantite: number }[];
}

export interface EventDocument {
  id: string;
  nom: string;
  typeDocument?: string;
  url: string;
  mime?: string;
  taille?: number;
  createdAt: string;
}
