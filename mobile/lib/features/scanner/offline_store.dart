import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../../core/sha256.dart';
import '../../data/domain.dart';

/// One scan recorded without network, waiting to be synchronised.
class PendingScan {
  final String clientRef;
  final String token;
  final String eventId;
  final String? activityId;
  final String sens;
  final DateTime scannedAt;

  PendingScan(this.clientRef, this.token, this.eventId, this.activityId, this.sens, this.scannedAt);

  Map<String, dynamic> toJson() => {
        'clientRef': clientRef,
        'token': token,
        'eventId': eventId,
        if (activityId != null) 'activityId': activityId,
        'sens': sens,
        'scannedAt': scannedAt.toUtc().toIso8601String(),
      };

  factory PendingScan.fromJson(Map<String, dynamic> j) => PendingScan(
        j['clientRef'] as String,
        j['token'] as String,
        j['eventId'] as String,
        j['activityId'] as String?,
        j['sens'] as String? ?? 'ENTREE',
        DateTime.parse(j['scannedAt'] as String),
      );
}

/// Offline control for one event (CTRL-01): the list of valid tickets (SHA-256
/// of their QR tokens, from `GET /checkins/events/{id}/offline-pack`), who is
/// currently inside, and the queue of scans to synchronise with
/// `POST /checkins/sync` (each carries a `clientRef`, so a replay is ignored).
///
/// Not available on the web build (no file system).
class OfflineStore {
  final String eventId;
  Map<String, Map<String, dynamic>> _billets = {};
  final Set<String> _inside = {};
  final List<PendingScan> _queue = [];
  DateTime? genereLe;

  OfflineStore(this.eventId);

  static bool get supported => !kIsWeb;

  bool get hasPack => _billets.isNotEmpty;
  int get pending => _queue.length;
  List<PendingScan> get queue => List.unmodifiable(_queue);

  Future<File> _file() async {
    final dir = await getApplicationDocumentsDirectory();
    return File('${dir.path}/controle_$eventId.json');
  }

  static Future<OfflineStore> load(String eventId) async {
    final s = OfflineStore(eventId);
    if (!supported) return s;
    try {
      final f = await s._file();
      if (await f.exists()) {
        final j = jsonDecode(await f.readAsString()) as Map<String, dynamic>;
        s._billets = (j['billets'] as Map<String, dynamic>? ?? {})
            .map((k, v) => MapEntry(k, Map<String, dynamic>.from(v as Map)));
        s._inside.addAll((j['inside'] as List<dynamic>? ?? []).cast<String>());
        s._queue.addAll((j['queue'] as List<dynamic>? ?? [])
            .map((e) => PendingScan.fromJson(e as Map<String, dynamic>)));
        s.genereLe = j['genereLe'] == null ? null : DateTime.tryParse(j['genereLe'] as String);
      }
    } catch (_) {
      // corrupted cache: start afresh
    }
    return s;
  }

  Future<void> _save() async {
    if (!supported) return;
    final f = await _file();
    await f.writeAsString(jsonEncode({
      'billets': _billets,
      'inside': _inside.toList(),
      'queue': _queue.map((e) => e.toJson()).toList(),
      'genereLe': genereLe?.toIso8601String(),
    }), flush: true);
  }

  /// Replaces the ticket list with a fresh pack downloaded from the server.
  Future<void> applyPack(Map<String, dynamic> pack) async {
    _billets = {
      for (final b in (pack['billets'] as List<dynamic>? ?? []))
        (b as Map<String, dynamic>)['h'] as String: {
          'numero': b['numero'],
          'nom': b['nom'],
          'categorie': b['categorie'],
          'activites': (b['activites'] as List<dynamic>? ?? []).cast<String>(),
        },
    };
    _inside
      ..clear()
      ..addAll((pack['presents'] as List<dynamic>? ?? []).map((p) {
        final m = p as Map<String, dynamic>;
        return '${m['h']}|${m['activityId'] ?? ''}';
      }));
    // Scans still waiting keep their effect on who is inside.
    for (final q in _queue) {
      final key = '${sha256Hex(q.token)}|${q.activityId ?? ''}';
      if (q.sens == 'ENTREE') {
        _inside.add(key);
      } else {
        _inside.remove(key);
      }
    }
    genereLe = DateTime.tryParse(pack['genereLe'] as String? ?? '') ?? DateTime.now();
    await _save();
  }

  /// Decides a scan locally and queues it for synchronisation.
  Future<ScanOutcome> scan(String token, {String? activityId, String? activiteNom, required String sens}) async {
    final h = sha256Hex(token.trim());
    final b = _billets[h];
    if (b == null) {
      return ScanOutcome(resultat: 'INVALIDE', sens: sens, message: 'Billet inconnu (hors ligne)');
    }
    final acts = (b['activites'] as List).cast<String>();
    if (activityId != null && acts.isNotEmpty && !acts.contains(activityId)) {
      return ScanOutcome(
          resultat: 'INVALIDE',
          sens: sens,
          message: 'Billet non valable pour cette activité',
          participantNom: b['nom'] as String?,
          categorieNom: b['categorie'] as String?,
          numeroBillet: b['numero'] as String?,
          activiteNom: activiteNom);
    }
    final key = '$h|${activityId ?? ''}';
    final inside = _inside.contains(key);
    if (sens == 'ENTREE' && inside) {
      return ScanOutcome(
          resultat: 'DEJA_UTILISE',
          sens: sens,
          message: "Déjà à l'intérieur (hors ligne)",
          participantNom: b['nom'] as String?,
          categorieNom: b['categorie'] as String?,
          numeroBillet: b['numero'] as String?,
          activiteNom: activiteNom);
    }
    if (sens == 'SORTIE' && !inside) {
      return ScanOutcome(
          resultat: 'DEJA_UTILISE',
          sens: sens,
          message: 'Pas à l\'intérieur (hors ligne)',
          participantNom: b['nom'] as String?,
          numeroBillet: b['numero'] as String?,
          activiteNom: activiteNom);
    }
    if (sens == 'ENTREE') {
      _inside.add(key);
    } else {
      _inside.remove(key);
    }
    _queue.add(PendingScan(_ref(), token.trim(), eventId, activityId, sens, DateTime.now()));
    await _save();
    return ScanOutcome(
        resultat: 'VALIDE',
        sens: sens,
        message: sens == 'ENTREE' ? 'Bienvenue — enregistré hors ligne' : 'Sortie enregistrée hors ligne',
        participantNom: b['nom'] as String?,
        categorieNom: b['categorie'] as String?,
        numeroBillet: b['numero'] as String?,
        activiteNom: activiteNom);
  }

  /// Removes the scans the server acknowledged (recorded now or earlier).
  Future<void> acknowledge(Iterable<String> clientRefs) async {
    final done = clientRefs.toSet();
    _queue.removeWhere((q) => done.contains(q.clientRef));
    await _save();
  }

  static final _rnd = Random.secure();
  static String _ref() =>
      '${DateTime.now().millisecondsSinceEpoch.toRadixString(36)}-${List.generate(10, (_) => _rnd.nextInt(36).toRadixString(36)).join()}';
}
