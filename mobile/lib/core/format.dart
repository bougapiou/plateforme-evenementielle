import 'package:intl/intl.dart';

/// Shared formatting helpers (dates, money) — French locale, FCFA currency.
class Fmt {
  static final _day = DateFormat('EEE d MMM yyyy', 'fr');
  static final _dayTime = DateFormat('d MMM yyyy · HH:mm', 'fr');
  static final _time = DateFormat('HH:mm', 'fr');

  static final _dm = DateFormat('d MMM', 'fr');
  static final _dmy = DateFormat('d MMM yyyy', 'fr');

  static String date(DateTime? d) => d == null ? '—' : _day.format(d.toLocal());

  static String dateTime(DateTime? d) =>
      d == null ? '—' : _dayTime.format(d.toLocal());

  static String time(DateTime? d) => d == null ? '' : _time.format(d.toLocal());

  /// "12 déc. → 18 déc. 2027" style range.
  static String range(DateTime? start, DateTime? end) {
    if (start == null) return '—';
    final s = _day.format(start.toLocal());
    if (end == null) return s;
    return '$s → ${_day.format(end.toLocal())}';
  }

  /// "25 oct. → 3 nov. 2026" (the year once when both dates share it), or one date when it is a single day.
  static String rangeShort(DateTime? start, DateTime? end) {
    if (start == null) return '—';
    final s = start.toLocal();
    final e = end?.toLocal();
    if (e == null || (s.year == e.year && s.month == e.month && s.day == e.day)) {
      return _dmy.format(s);
    }
    if (s.year == e.year) return '${_dm.format(s)} → ${_dmy.format(e)}';
    return '${_dmy.format(s)} → ${_dmy.format(e)}';
  }

  /// "TABLE_RONDE" -> "Table ronde": an enum code made readable.
  static String humanize(String? code) {
    if (code == null || code.isEmpty) return '';
    final t = code.replaceAll('_', ' ').toLowerCase();
    return t[0].toUpperCase() + t.substring(1);
  }

  static String money(num? amount, [String devise = 'XOF']) {
    if (amount == null) return '—';
    final n = NumberFormat.decimalPattern('fr').format(amount);
    final label = devise == 'XOF' ? 'FCFA' : devise;
    return '$n $label';
  }

  /// Price for display: "Gratuit" when the amount is null or zero.
  static String price(num? amount, [String devise = 'XOF']) {
    if (amount == null || amount <= 0) return 'Gratuit';
    return money(amount, devise);
  }
}

DateTime? parseDate(dynamic v) {
  if (v == null) return null;
  return DateTime.tryParse(v.toString());
}
