export type OrganizerStatus = 'EN_ATTENTE' | 'ACTIF' | 'SUSPENDU';

export interface Organizer {
  id: string;
  userId: string;
  userFullName: string;
  userEmail: string;
  structureId?: string;
  structureName?: string;
  nomAffichage: string;
  description?: string;
  logoUrl?: string;
  contactEmail?: string;
  contactTelephone?: string;
  siteWeb?: string;
  statut: OrganizerStatus;
  approuveLe?: string;
  createdAt: string;
}

export interface OrganizerApplyPayload {
  nomAffichage: string;
  description?: string;
  structureId?: string;
  contactEmail?: string;
  contactTelephone?: string;
  siteWeb?: string;
}
