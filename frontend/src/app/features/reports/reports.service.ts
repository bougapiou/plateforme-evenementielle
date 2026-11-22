import { Injectable } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiBase } from '../../core/api';

export interface ReportMetrics {
  billetsEmis: number;
  inscriptions: number;
  inscriptionsConfirmees: number;
  revenus: number;
  paiementsReussis: number;
  entrees: number;
  sorties: number;
  visiteursUniques: number;
  capteurEntrees: number;
  capteurSorties: number;
  visitesStands: number;
}

export interface EventReport {
  eventId: string;
  evenement: string;
  dateDebut: string;
  dateFin: string;
  /** null = the whole event. */
  jour: string | null;
  jours: string[];
  totaux: ReportMetrics;
  parJour: { jour: string; valeurs: ReportMetrics }[];
  parActivite: ActivityMetrics[];
}

/** One line of the « Par activité » table. */
export interface ActivityMetrics {
  id: string;
  titre: string;
  dateDebut: string;
  dateFin?: string;
  salle?: string;
  entrees: number;
  sorties: number;
  visiteursUniques: number;
  inscrits: number;
  inscritsSurPlace: number;
  autoEnregistres: number;
  /** Unique people let in / tickets issued for the session, in %. */
  tauxPresence?: number | null;
  capacite?: number | null;
  tauxRemplissage?: number | null;
  dureeMoyenneMinutes?: number | null;
}

export type Granularite = 'JOUR' | 'HEURE';

export interface FluxPoint {
  periode: string;
  debut: string;
  entrees: number;
  sorties: number;
  presents: number;
  scanEntrees: number;
  scanSorties: number;
}

export interface FluxReport {
  granularite: Granularite;
  jour: string | null;
  jours: string[];
  points: FluxPoint[];
  totalEntrees: number;
  totalSorties: number;
  pic: FluxPoint | null;
  capteurs: {
    id: string; nom: string; actif: boolean; derniereActivite?: string;
    entrees: number; sorties: number;
  }[];
}

export type Perimetre = 'EVENEMENT' | 'ACTIVITE' | 'STAND';
export type Source = 'BILLETS' | 'PRESENTS' | 'INSCRITS' | 'VISITES';
export type ExportFormat = 'XLSX' | 'DOCX' | 'PDF' | 'CSV';

export interface ParticipantCriteria {
  perimetre: Perimetre;
  cibleId?: string | null;
  source: Source;
  /** Local date-times (yyyy-MM-ddTHH:mm), both optional. */
  du?: string | null;
  au?: string | null;
  doublons: boolean;
}

export interface ParticipantRow {
  nom: string;
  prenom: string;
  telephone: string;
  email: string;
  reference: string;
  categorie: string;
  detail: string;
  date: string;
  occurrences: number;
}

export interface ParticipantPreview {
  titre: string;
  filtres: string;
  total: number;
  uniques: number;
  doublons: number;
  lignes: ParticipantRow[];
}

@Injectable({ providedIn: 'root' })
export class ReportsService extends ApiBase {
  stats(eventId: string, jour: string | null): Observable<EventReport> {
    return this.get<EventReport>(`/reports/events/${eventId}/stats`, { jour });
  }

  flux(eventId: string, granularite: Granularite, jour: string | null, capteurId: string | null):
    Observable<FluxReport> {
    return this.get<FluxReport>(`/reports/events/${eventId}/flux`, { granularite, jour, capteurId });
  }

  participants(eventId: string, c: ParticipantCriteria): Observable<ParticipantPreview> {
    return this.get<ParticipantPreview>(`/reports/events/${eventId}/participants`, { ...c });
  }

  bilan(eventId: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/reports/events/${eventId}/bilan.pdf`, {
      responseType: 'blob',
      observe: 'response',
    });
  }

  exportActivities(eventId: string, jour: string | null, format: 'CSV' | 'XLSX'):
    Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/reports/events/${eventId}/stats/activites/export`, {
      params: this.params({ format, jour: jour ?? undefined }),
      responseType: 'blob',
      observe: 'response',
    });
  }

  exportParticipants(eventId: string, c: ParticipantCriteria, format: ExportFormat):
    Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/reports/events/${eventId}/participants/export`, {
      params: this.params({ ...c, format }),
      responseType: 'blob',
      observe: 'response',
    });
  }
}
