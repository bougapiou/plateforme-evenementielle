import 'package:dio/dio.dart';
import '../core/api_client.dart';
import '../core/format.dart';

/// Event reporting (`/api/reports/events/{id}`): day / whole-event figures,
/// laser-sensor flow per day or hour, participant lists and their export.
class ReportsRepository {
  final ApiClient api;
  ReportsRepository(this.api);

  Future<Map<String, dynamic>> _get(
    String path,
    Map<String, dynamic> query,
  ) async {
    try {
      final res = await api.dio.get(
        path,
        queryParameters: {...query}
          ..removeWhere((_, v) => v == null || v == ''),
      );
      return Map<String, dynamic>.from(res.data as Map);
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }

  Future<EventReport> stats(String eventId, {String? jour}) async =>
      EventReport.fromJson(
        await _get('/reports/events/$eventId/stats', {'jour': jour}),
      );

  Future<FluxReport> flux(
    String eventId, {
    required String granularite,
    String? jour,
    String? capteurId,
  }) async => FluxReport.fromJson(
    await _get('/reports/events/$eventId/flux', {
      'granularite': granularite,
      'jour': jour,
      'capteurId': capteurId,
    }),
  );

  Future<ParticipantPreview> participants(
    String eventId,
    ParticipantCriteria c,
  ) async => ParticipantPreview.fromJson(
    await _get('/reports/events/$eventId/participants', c.toQuery()),
  );

  /// Server path of the export, for [fetchAndPresentDocument].
  String exportPath(String eventId, ParticipantCriteria c, String format) {
    final q = {...c.toQuery(), 'format': format}
      ..removeWhere((_, v) => v == null || v == '');
    return Uri(
      path: '/api/reports/events/$eventId/participants/export',
      queryParameters: q.map((k, v) => MapEntry(k, '$v')),
    ).toString();
  }

  /// Stands of the event (id, label), for the export target picker.
  Future<List<(String, String)>> stands(String eventId) async {
    try {
      final res = await api.dio.get('/events/$eventId/stands');
      return (res.data as List)
          .map(
            (s) => (
              s['id'] as String,
              'Stand ${s['numero']} · ${s['standTypeNom'] ?? ''}',
            ),
          )
          .toList();
    } on DioException catch (e) {
      throw api.toApiException(e);
    }
  }
}

int _i(dynamic v) => (v as num?)?.toInt() ?? 0;

class ReportMetrics {
  final int billetsEmis, inscriptions, inscriptionsConfirmees, paiementsReussis;
  final int entrees,
      sorties,
      visiteursUniques,
      capteurEntrees,
      capteurSorties,
      visitesStands;
  final num revenus;

  ReportMetrics.fromJson(Map<String, dynamic> j)
    : billetsEmis = _i(j['billetsEmis']),
      inscriptions = _i(j['inscriptions']),
      inscriptionsConfirmees = _i(j['inscriptionsConfirmees']),
      paiementsReussis = _i(j['paiementsReussis']),
      entrees = _i(j['entrees']),
      sorties = _i(j['sorties']),
      visiteursUniques = _i(j['visiteursUniques']),
      capteurEntrees = _i(j['capteurEntrees']),
      capteurSorties = _i(j['capteurSorties']),
      visitesStands = _i(j['visitesStands']),
      revenus = (j['revenus'] as num?) ?? 0;
}

class EventReport {
  final String evenement;
  final String? jour;
  final List<String> jours;
  final ReportMetrics totaux;
  final List<(String, ReportMetrics)> parJour;
  final List<
    ({String titre, DateTime? debut, int entrees, int sorties, int uniques})
  >
  parActivite;

  EventReport.fromJson(Map<String, dynamic> j)
    : evenement = j['evenement'] as String,
      jour = j['jour'] as String?,
      jours = List<String>.from(j['jours'] as List),
      totaux = ReportMetrics.fromJson(
        Map<String, dynamic>.from(j['totaux'] as Map),
      ),
      parJour =
          (j['parJour'] as List)
              .map(
                (d) => (
                  d['jour'] as String,
                  ReportMetrics.fromJson(
                    Map<String, dynamic>.from(d['valeurs'] as Map),
                  ),
                ),
              )
              .toList(),
      parActivite =
          (j['parActivite'] as List)
              .map(
                (a) => (
                  titre: a['titre'] as String,
                  debut: parseDate(a['dateDebut']),
                  entrees: _i(a['entrees']),
                  sorties: _i(a['sorties']),
                  uniques: _i(a['visiteursUniques']),
                ),
              )
              .toList();
}

class FluxPoint {
  final String periode;
  final int entrees, sorties, presents, scanEntrees, scanSorties;
  FluxPoint.fromJson(Map<String, dynamic> j)
    : periode = j['periode'] as String,
      entrees = _i(j['entrees']),
      sorties = _i(j['sorties']),
      presents = _i(j['presents']),
      scanEntrees = _i(j['scanEntrees']),
      scanSorties = _i(j['scanSorties']);
}

class FluxReport {
  final String granularite;
  final String? jour;
  final List<String> jours;
  final List<FluxPoint> points;
  final int totalEntrees, totalSorties;
  final FluxPoint? pic;
  final List<({String id, String nom, bool actif, int entrees, int sorties})>
  capteurs;

  FluxReport.fromJson(Map<String, dynamic> j)
    : granularite = j['granularite'] as String,
      jour = j['jour'] as String?,
      jours = List<String>.from(j['jours'] as List),
      points =
          (j['points'] as List)
              .map(
                (p) => FluxPoint.fromJson(Map<String, dynamic>.from(p as Map)),
              )
              .toList(),
      totalEntrees = _i(j['totalEntrees']),
      totalSorties = _i(j['totalSorties']),
      pic =
          j['pic'] == null
              ? null
              : FluxPoint.fromJson(Map<String, dynamic>.from(j['pic'] as Map)),
      capteurs =
          (j['capteurs'] as List)
              .map(
                (c) => (
                  id: c['id'] as String,
                  nom: c['nom'] as String,
                  actif: c['actif'] as bool? ?? true,
                  entrees: _i(c['entrees']),
                  sorties: _i(c['sorties']),
                ),
              )
              .toList();
}

/// `du`/`au`: local date-times "yyyy-MM-ddTHH:mm", both optional.
class ParticipantCriteria {
  final String perimetre; // EVENEMENT | ACTIVITE | STAND
  final String? cibleId;
  final String source; // BILLETS | PRESENTS | INSCRITS | VISITES
  final String? du, au;
  final bool doublons;

  const ParticipantCriteria({
    this.perimetre = 'EVENEMENT',
    this.cibleId,
    this.source = 'BILLETS',
    this.du,
    this.au,
    this.doublons = true,
  });

  ParticipantCriteria copyWith({
    String? perimetre,
    String? Function()? cibleId,
    String? source,
    String? Function()? du,
    String? Function()? au,
    bool? doublons,
  }) => ParticipantCriteria(
    perimetre: perimetre ?? this.perimetre,
    cibleId: cibleId != null ? cibleId() : this.cibleId,
    source: source ?? this.source,
    du: du != null ? du() : this.du,
    au: au != null ? au() : this.au,
    doublons: doublons ?? this.doublons,
  );

  Map<String, dynamic> toQuery() => {
    'perimetre': perimetre,
    'cibleId': perimetre == 'EVENEMENT' ? null : cibleId,
    'source': source,
    'du': du,
    'au': au,
    'doublons': doublons,
  };
}

class ParticipantRow {
  final String nom, prenom, telephone, email, reference, categorie, detail;
  final DateTime? date;
  final int occurrences;
  ParticipantRow.fromJson(Map<String, dynamic> j)
    : nom = j['nom'] as String? ?? '',
      prenom = j['prenom'] as String? ?? '',
      telephone = j['telephone'] as String? ?? '',
      email = j['email'] as String? ?? '',
      reference = j['reference'] as String? ?? '',
      categorie = j['categorie'] as String? ?? '',
      detail = j['detail'] as String? ?? '',
      date = parseDate(j['date']),
      occurrences = _i(j['occurrences']);
}

class ParticipantPreview {
  final String titre, filtres;
  final int total, uniques, doublons;
  final List<ParticipantRow> lignes;
  ParticipantPreview.fromJson(Map<String, dynamic> j)
    : titre = j['titre'] as String,
      filtres = j['filtres'] as String,
      total = _i(j['total']),
      uniques = _i(j['uniques']),
      doublons = _i(j['doublons']),
      lignes =
          (j['lignes'] as List)
              .map(
                (r) => ParticipantRow.fromJson(
                  Map<String, dynamic>.from(r as Map),
                ),
              )
              .toList();
}
