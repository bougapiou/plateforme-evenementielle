export type EventStatus =
  | 'BROUILLON'
  | 'SOUMIS'
  | 'REFUSE'
  | 'VALIDE'
  | 'PUBLIE'
  | 'INSCRIPTIONS_OUVERTES'
  | 'INSCRIPTIONS_FERMEES'
  | 'EN_COURS'
  | 'TERMINE'
  | 'SUSPENDU'
  | 'ANNULE';

export type ActivityType =
  | 'CEREMONIE' | 'CONFERENCE' | 'PANEL' | 'ATELIER' | 'FORMATION'
  | 'TABLE_RONDE' | 'NETWORKING' | 'PAUSE' | 'SPECTACLE' | 'AUTRE';

export type PartnerLevel =
  | 'PLATINE' | 'OR' | 'ARGENT' | 'BRONZE' | 'PARTENAIRE'
  | 'PARTENAIRE_MEDIA' | 'PARTENAIRE_INSTITUTIONNEL';

export interface EventCategory {
  id: string;
  nom: string;
  slug: string;
  description?: string;
  icone?: string;
  actif: boolean;
  ordre: number;
}

export interface EventSummary {
  id: string;
  nom: string;
  sigle?: string;
  slug: string;
  descriptionCourte?: string;
  categoryNom?: string;
  logoUrl?: string;
  coverUrl?: string;
  dateDebut: string;
  dateFin: string;
  ville?: string;
  lieu?: string;
  organizerNom: string;
  standsActifs: boolean;
  statut: EventStatus;
}

export interface EventDetail extends EventSummary {
  descriptionDetaillee?: string;
  categoryId?: string;
  adresse?: string;
  pays?: string;
  latitude?: number;
  longitude?: number;
  capaciteMax?: number;
  contactEmail?: string;
  contactTelephone?: string;
  siteWeb?: string;
  conditionsParticipation?: string;
  hasActivities: boolean;
  inscriptionDebut?: string;
  inscriptionFin?: string;
  reservationDebut?: string;
  reservationFin?: string;
  motifRefus?: string;
  organizerId: string;
  soumisLe?: string;
  valideLe?: string;
  publieLe?: string;
  createdAt: string;
}

export interface EventPayload {
  nom: string;
  sigle?: string;
  descriptionCourte?: string;
  descriptionDetaillee?: string;
  categoryId?: string;
  logoUrl?: string;
  coverUrl?: string;
  dateDebut: string;
  dateFin: string;
  lieu?: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  capaciteMax?: number;
  contactEmail?: string;
  contactTelephone?: string;
  siteWeb?: string;
  conditionsParticipation?: string;
  hasActivities: boolean;
  standsActifs: boolean;
  inscriptionDebut?: string;
  inscriptionFin?: string;
  reservationDebut?: string;
  reservationFin?: string;
}

export interface Activity {
  id: string;
  eventId: string;
  titre: string;
  description?: string;
  typeActivite?: ActivityType;
  dateDebut: string;
  dateFin?: string;
  salle?: string;
  lieu?: string;
  intervenant?: string;
  moderateur?: string;
  speakerId?: string;
  capacite?: number;
  ordre: number;
}

export interface Speaker {
  id: string;
  eventId: string;
  nom: string;
  titre?: string;
  organisation?: string;
  bio?: string;
  photoUrl?: string;
  ordre: number;
}

export interface Partner {
  id: string;
  eventId: string;
  nom: string;
  logoUrl?: string;
  siteWeb?: string;
  niveau?: PartnerLevel;
  ordre: number;
}

export interface EventPublic extends Omit<EventDetail, 'statut'> {
  statut: EventStatus;
  programme: Activity[];
  intervenants: Speaker[];
  partenaires: Partner[];
}

export const ACTIVITY_TYPES: ActivityType[] = [
  'CEREMONIE', 'CONFERENCE', 'PANEL', 'ATELIER', 'FORMATION',
  'TABLE_RONDE', 'NETWORKING', 'PAUSE', 'SPECTACLE', 'AUTRE',
];
