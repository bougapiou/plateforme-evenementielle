export type StructureType =
  | 'ENTREPRISE'
  | 'INSTITUTION'
  | 'ADMINISTRATION_PUBLIQUE'
  | 'ONG_ASSOCIATION'
  | 'ETABLISSEMENT_SCOLAIRE'
  | 'AUTRE';

export type StructureStatus = 'EN_ATTENTE' | 'VERIFIEE' | 'SUSPENDUE';
export type MemberRole = 'PROPRIETAIRE' | 'ADMINISTRATEUR' | 'MEMBRE';

export interface StructureSummary {
  id: string;
  raisonSociale: string;
  sigle?: string;
  typeStructure: StructureType;
  ville?: string;
  statut: StructureStatus;
}

export interface Structure extends StructureSummary {
  secteurActivite?: string;
  rccm?: string;
  ifu?: string;
  adresse?: string;
  pays?: string;
  telephone?: string;
  email?: string;
  siteWeb?: string;
  logoUrl?: string;
  description?: string;
  ownerId: string;
  memberCount: number;
  myRole?: MemberRole;
  createdAt: string;
}

export interface StructureMember {
  id: string;
  userId: string;
  fullName: string;
  email: string;
  roleInterne: MemberRole;
  fonction?: string;
  active: boolean;
}

export interface StructurePayload {
  raisonSociale: string;
  sigle?: string;
  typeStructure: StructureType;
  secteurActivite?: string;
  rccm?: string;
  ifu?: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  telephone?: string;
  email?: string;
  siteWeb?: string;
  logoUrl?: string;
  description?: string;
}

export const STRUCTURE_TYPES: { value: StructureType; label: string }[] = [
  { value: 'ENTREPRISE', label: 'Entreprise' },
  { value: 'INSTITUTION', label: 'Institution' },
  { value: 'ADMINISTRATION_PUBLIQUE', label: 'Administration publique' },
  { value: 'ONG_ASSOCIATION', label: 'ONG / Association' },
  { value: 'ETABLISSEMENT_SCOLAIRE', label: 'Établissement scolaire' },
  { value: 'AUTRE', label: 'Autre' },
];
