import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../data/domain.dart';
import 'file_download.dart' as dl;

/// « Mon programme » (PART-02): the sessions the visitor starred, kept on the
/// device and exportable as an iCalendar file (opened by the phone's calendar).
class Agenda {
  Agenda._();

  static final ValueNotifier<Set<String>> starred = ValueNotifier(<String>{});
  static bool _loaded = false;

  static Future<File> _file() async =>
      File('${(await getApplicationDocumentsDirectory()).path}/mon_programme.json');

  static Future<void> load() async {
    if (_loaded || kIsWeb) return;
    _loaded = true;
    try {
      final f = await _file();
      if (await f.exists()) {
        starred.value = (jsonDecode(await f.readAsString()) as List<dynamic>).cast<String>().toSet();
      }
    } catch (_) {
      // start empty
    }
  }

  static Future<void> toggle(String activityId) async {
    final next = {...starred.value};
    if (!next.remove(activityId)) next.add(activityId);
    starred.value = next;
    if (kIsWeb) return;
    await (await _file()).writeAsString(jsonEncode(next.toList()));
  }

  static String _date(DateTime d) =>
      d.toUtc().toIso8601String().replaceAll(RegExp('[-:]'), '').replaceAll(RegExp(r'\.\d+'), '');

  static String _esc(String s) => s
      .replaceAll('\\', '\\\\')
      .replaceAll(';', '\\;')
      .replaceAll(',', '\\,')
      .replaceAll('\n', '\\n');

  /// RFC 5545 calendar of the given sessions, with a reminder 30 minutes before.
  static String ics(String eventNom, String? lieu, List<Activity> sessions) {
    final b = StringBuffer()
      ..write('BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//PNE Burkina Faso//Mon programme//FR\r\n')
      ..write('X-WR-CALNAME:${_esc(eventNom)}\r\n');
    final stamp = _date(DateTime.now());
    for (final a in sessions.where((a) => a.dateDebut != null)) {
      final end = a.dateFin ?? a.dateDebut!.add(const Duration(hours: 1));
      final where = [a.salle ?? a.lieu, lieu].whereType<String>().join(', ');
      b
        ..write('BEGIN:VEVENT\r\nUID:${a.id}@pne.gov.bf\r\nDTSTAMP:$stamp\r\n')
        ..write('DTSTART:${_date(a.dateDebut!)}\r\nDTEND:${_date(end)}\r\n')
        ..write('SUMMARY:${_esc(a.titre)}\r\n');
      if (where.isNotEmpty) b.write('LOCATION:${_esc(where)}\r\n');
      if (a.description != null) b.write('DESCRIPTION:${_esc(a.description!)}\r\n');
      b.write('BEGIN:VALARM\r\nTRIGGER:-PT30M\r\nACTION:DISPLAY\r\nDESCRIPTION:${_esc(a.titre)}\r\n'
          'END:VALARM\r\nEND:VEVENT\r\n');
    }
    b.write('END:VCALENDAR\r\n');
    return b.toString();
  }

  /// Saves the .ics and opens it with the calendar app (download on the web).
  static Future<void> export(String filename, String content) async {
    final bytes = Uint8List.fromList(utf8.encode(content));
    if (kIsWeb) {
      dl.saveBytes(bytes, filename);
      return;
    }
    final f = File('${(await getTemporaryDirectory()).path}/$filename');
    await f.writeAsBytes(bytes, flush: true);
    await OpenFilex.open(f.path, type: 'text/calendar');
  }
}
