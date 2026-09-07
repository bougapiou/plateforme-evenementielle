import 'package:intl/intl.dart';

/// Shared formatting helpers (dates, money) — French locale, FCFA currency.
class Fmt {
  static final _day = DateFormat('EEE d MMM yyyy', 'fr');
  static final _dayTime = DateFormat('d MMM yyyy · HH:mm', 'fr');
  static final _time = DateFormat('HH:mm', 'fr');

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

  static String money(num? amount, [String devise = 'XOF']) {
    if (amount == null) return '—';
    final n = NumberFormat.decimalPattern('fr').format(amount);
    final label = devise == 'XOF' ? 'FCFA' : devise;
    return '$n $label';
  }
}

DateTime? parseDate(dynamic v) {
  if (v == null) return null;
  return DateTime.tryParse(v.toString());
}
