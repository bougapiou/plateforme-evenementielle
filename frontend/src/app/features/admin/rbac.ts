/** Libellés français des rôles, types de compte et permissions (codes techniques du serveur). */

import { UserType } from '../../core/models';

export interface RoleInfo {
  id: string;
  name: string;
  description?: string;
  systemRole: boolean;
  permissions: string[];
  utilisateurs: number;
}

export interface UserDetail {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  phone?: string;
  type: UserType;
  status: 'ACTIF' | 'EN_ATTENTE' | 'DESACTIVE';
  guest: boolean;
  roles: string[];
  permissions: string[];
  lastLoginAt?: string;
  createdAt: string;
}

export const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Administrateur',
  ORGANISATEUR: 'Organisateur',
  PARTICIPANT: 'Participant',
  STRUCTURE: 'Structure',
  PERSONNEL_CONTROLE: 'Personnel de contrôle',
};

export const roleLabel = (name: string) =>
  ROLE_LABELS[name] ?? name.charAt(0) + name.slice(1).toLowerCase().replace(/_/g, ' ');

export const TYPES: { value: UserType; label: string; aide: string; role: string }[] = [
  { value: 'PARTICULIER', label: 'Particulier', aide: 'Visiteur ou participant : billets et inscriptions.', role: 'PARTICIPANT' },
  { value: 'STRUCTURE', label: 'Structure', aide: 'Entreprise ou institution : inscriptions et stands.', role: 'STRUCTURE' },
  { value: 'ORGANISATEUR', label: 'Organisateur', aide: 'Crée et gère ses événements (profil organisateur actif).', role: 'ORGANISATEUR' },
  { value: 'PERSONNEL', label: 'Personnel de contrôle', aide: 'Scanne les billets à l’entrée.', role: 'PERSONNEL_CONTROLE' },
  { value: 'ADMIN', label: 'Administrateur', aide: 'Accès complet à la plateforme.', role: 'SUPER_ADMIN' },
];

export const typeLabel = (t: string) => TYPES.find((x) => x.value === t)?.label ?? t;

export const PERMISSION_LABELS: Record<string, string> = {
  USER_READ: 'Consulter les utilisateurs',
  USER_MANAGE: 'Gérer les comptes (créer, modifier, mot de passe, suspendre)',
  ROLE_MANAGE: 'Gérer les rôles et autorisations',
  AUDIT_READ: 'Consulter le journal d’audit',
  SETTINGS_MANAGE: 'Paramètres de la plateforme',
  NOTIFICATION_MANAGE: 'Gérer les notifications',
  ORGANIZER_MANAGE: 'Valider ou suspendre les organisateurs',
  STRUCTURE_READ: 'Consulter les structures',
  STRUCTURE_MANAGE: 'Gérer une structure',
  EVENT_READ: 'Consulter les événements',
  EVENT_CREATE: 'Créer des événements',
  EVENT_UPDATE: 'Modifier ses événements',
  EVENT_DELETE: 'Supprimer des événements',
  EVENT_PUBLISH: 'Publier et ouvrir les inscriptions',
  EVENT_VALIDATE: 'Valider ou refuser les événements soumis',
  EVENT_CATEGORY_MANAGE: 'Gérer les catégories d’événements',
  TICKET_MANAGE: 'Gérer la billetterie',
  TICKET_PURCHASE: 'Obtenir ou acheter des billets',
  REGISTRATION_CREATE: 'S’inscrire aux événements',
  REGISTRATION_MANAGE: 'Gérer les inscriptions',
  STAND_MANAGE: 'Gérer les stands',
  STAND_RESERVE: 'Réserver des stands',
  PAYMENT_READ: 'Consulter les paiements',
  PAYMENT_MANAGE: 'Gérer les paiements',
  COMMISSION_MANAGE: 'Gérer les commissions',
  CHECKIN_SCAN: 'Scanner les billets (contrôle d’accès)',
  POINTAGE_MANAGE: 'Pointage des visiteurs',
  STATS_GLOBAL_READ: 'Statistiques de toute la plateforme',
  STATS_OWN_READ: 'Statistiques de ses événements',
};

export const permissionLabel = (p: string) => PERMISSION_LABELS[p] ?? p;

export const PERMISSION_GROUPS: { label: string; permissions: string[] }[] = [
  { label: 'Comptes et sécurité', permissions: ['USER_READ', 'USER_MANAGE', 'ROLE_MANAGE', 'AUDIT_READ', 'SETTINGS_MANAGE', 'NOTIFICATION_MANAGE'] },
  { label: 'Organisateurs et structures', permissions: ['ORGANIZER_MANAGE', 'STRUCTURE_READ', 'STRUCTURE_MANAGE'] },
  { label: 'Événements', permissions: ['EVENT_READ', 'EVENT_CREATE', 'EVENT_UPDATE', 'EVENT_DELETE', 'EVENT_PUBLISH', 'EVENT_VALIDATE', 'EVENT_CATEGORY_MANAGE'] },
  { label: 'Billetterie, inscriptions et stands', permissions: ['TICKET_MANAGE', 'TICKET_PURCHASE', 'REGISTRATION_CREATE', 'REGISTRATION_MANAGE', 'STAND_MANAGE', 'STAND_RESERVE'] },
  { label: 'Paiements', permissions: ['PAYMENT_READ', 'PAYMENT_MANAGE', 'COMMISSION_MANAGE'] },
  { label: 'Opérations et statistiques', permissions: ['CHECKIN_SCAN', 'POINTAGE_MANAGE', 'STATS_GLOBAL_READ', 'STATS_OWN_READ'] },
];

/** Couleur de pastille par rôle (système), neutre pour les rôles personnalisés. */
export const roleChip = (name: string) =>
  ({
    SUPER_ADMIN: 'bg-rouge-50 text-rouge-700 ring-1 ring-rouge-200',
    ORGANISATEUR: 'bg-vert-50 text-vert-800 ring-1 ring-vert-200',
    PERSONNEL_CONTROLE: 'bg-bleu-50 text-bleu-800 ring-1 ring-bleu-100',
    STRUCTURE: 'bg-ocre-50 text-ocre-800 ring-1 ring-ocre-100',
    PARTICIPANT: 'bg-neutre-100 text-neutre-700 ring-1 ring-neutre-200',
  })[name] ?? 'bg-or-50 text-or-800 ring-1 ring-or-200';
