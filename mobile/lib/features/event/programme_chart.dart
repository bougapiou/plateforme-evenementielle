import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../data/domain.dart';

/// Display label and accent colour of an activity type (`typeActivite`).
({String label, Color color}) activityTypeStyle(String? type) => switch (type) {
      'CEREMONIE' => (label: 'Cérémonie', color: const Color(0xFFB45309)),
      'CONFERENCE' => (label: 'Conférence', color: const Color(0xFF1D4ED8)),
      'PANEL' => (label: 'Panel', color: const Color(0xFF7C3AED)),
      'ATELIER' => (label: 'Atelier', color: const Color(0xFF0F766E)),
      'FORMATION' => (label: 'Formation', color: const Color(0xFF0369A1)),
      'TABLE_RONDE' => (label: 'Table ronde', color: const Color(0xFF9333EA)),
      'NETWORKING' => (label: 'Networking', color: const Color(0xFFC2410C)),
      'PAUSE' => (label: 'Pause', color: const Color(0xFF64748B)),
      'SPECTACLE' => (label: 'Spectacle', color: const Color(0xFFBE123C)),
      _ => (label: 'Activité', color: const Color(0xFF475569)),
    };

const _line = Color(0xFFCBD5E1);

/// Event programme drawn as a tree (arborescence): the event at the root,
/// one collapsible branch per day, that day's activities as leaves in time
/// order. [cardBuilder] renders each activity (it carries the participation
/// actions, which live in the event screen).
class ProgrammeChart extends StatefulWidget {
  final String title;
  final String? subtitle;
  final List<Activity> activities;
  final Widget Function(Activity) cardBuilder;

  const ProgrammeChart({
    super.key,
    required this.title,
    this.subtitle,
    required this.activities,
    required this.cardBuilder,
  });

  @override
  State<ProgrammeChart> createState() => _ProgrammeChartState();
}

class _ProgrammeChartState extends State<ProgrammeChart> {
  static final _dayFmt = DateFormat('EEEE d MMMM', 'fr');
  final _collapsed = <String>{};

  List<(String, DateTime?, List<Activity>)> _days() {
    final sorted = [...widget.activities]..sort((a, b) {
        if (a.dateDebut == null || b.dateDebut == null) return 0;
        return a.dateDebut!.compareTo(b.dateDebut!);
      });
    final days = <String, (String, DateTime?, List<Activity>)>{};
    for (final a in sorted) {
      final d = a.dateDebut?.toLocal();
      final key = d == null ? '-' : '${d.year}-${d.month}-${d.day}';
      days.putIfAbsent(key, () => (key, d, <Activity>[])).$3.add(a);
    }
    return days.values.toList();
  }

  @override
  Widget build(BuildContext context) {
    final days = _days();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _RootNode(title: widget.title, subtitle: widget.subtitle),
        Padding(
          padding: const EdgeInsets.only(left: 16),
          child: Column(children: [
            for (var i = 0; i < days.length; i++)
              _Branch(
                last: i == days.length - 1,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _DayNode(
                      jour: days.length > 1 ? i + 1 : null,
                      label: days[i].$2 == null
                          ? 'Date à préciser'
                          : _capitalize(_dayFmt.format(days[i].$2!)),
                      count: days[i].$3.length,
                      collapsed: _collapsed.contains(days[i].$1),
                      onTap: () => setState(() {
                        final k = days[i].$1;
                        if (!_collapsed.remove(k)) _collapsed.add(k);
                      }),
                    ),
                    if (!_collapsed.contains(days[i].$1))
                      Padding(
                        padding: const EdgeInsets.only(left: 18),
                        child: Column(children: [
                          for (var j = 0; j < days[i].$3.length; j++)
                            _Branch(
                              last: j == days[i].$3.length - 1,
                              child: widget.cardBuilder(days[i].$3[j]),
                            ),
                        ]),
                      ),
                  ],
                ),
              ),
          ]),
        ),
      ],
    );
  }

  static String _capitalize(String s) =>
      s.isEmpty ? s : s[0].toUpperCase() + s.substring(1);
}

/// One tree node: an elbow from the parent's rail into [child], and the rail
/// continuing down to the next sibling unless this is the [last] one.
class _Branch extends StatelessWidget {
  final bool last;
  final Widget child;
  const _Branch({required this.last, required this.child});

  @override
  Widget build(BuildContext context) => IntrinsicHeight(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SizedBox(width: 20, child: CustomPaint(painter: _ElbowPainter(last))),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(top: 10),
                child: child,
              ),
            ),
          ],
        ),
      );
}

class _ElbowPainter extends CustomPainter {
  final bool last;
  _ElbowPainter(this.last);

  static const _elbowY = 28.0;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = _line
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke;
    const r = 6.0;
    final path = Path()
      ..moveTo(1, 0)
      ..lineTo(1, _elbowY - r)
      ..quadraticBezierTo(1, _elbowY, 1 + r, _elbowY)
      ..lineTo(size.width, _elbowY);
    canvas.drawPath(path, p);
    if (!last) canvas.drawLine(const Offset(1, 0), Offset(1, size.height), p);
  }

  @override
  bool shouldRepaint(_ElbowPainter old) => old.last != last;
}

class _RootNode extends StatelessWidget {
  final String title;
  final String? subtitle;
  const _RootNode({required this.title, this.subtitle});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
        decoration: BoxDecoration(
          gradient: const LinearGradient(
              colors: [Color(0xFF0F172A), Color(0xFF334155)]),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          const Icon(Icons.account_tree_outlined, color: Colors.white70, size: 18),
          const SizedBox(width: 10),
          Flexible(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(
                        color: Colors.white, fontWeight: FontWeight.w700)),
                if (subtitle != null)
                  Text(subtitle!,
                      style: const TextStyle(color: Colors.white70, fontSize: 12)),
              ],
            ),
          ),
        ]),
      );
}

class _DayNode extends StatelessWidget {
  final int? jour;
  final String label;
  final int count;
  final bool collapsed;
  final VoidCallback onTap;

  const _DayNode({
    required this.jour,
    required this.label,
    required this.count,
    required this.collapsed,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => Material(
        color: const Color(0xFFEEF2FF),
        shape: const StadiumBorder(side: BorderSide(color: Color(0xFFC7D2FE))),
        child: InkWell(
          customBorder: const StadiumBorder(),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              AnimatedRotation(
                turns: collapsed ? -0.25 : 0,
                duration: const Duration(milliseconds: 150),
                child: const Icon(Icons.expand_more, size: 18, color: Color(0xFF4338CA)),
              ),
              const SizedBox(width: 4),
              if (jour != null) ...[
                Text('JOUR $jour',
                    style: const TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1,
                        color: Color(0xFF4338CA))),
                const SizedBox(width: 6),
              ],
              Flexible(
                child: Text(label,
                    style: const TextStyle(
                        fontWeight: FontWeight.w600, color: Color(0xFF1E293B))),
              ),
              const SizedBox(width: 6),
              Text('$count',
                  style: const TextStyle(fontSize: 12, color: Color(0xFF64748B))),
            ]),
          ),
        ),
      );
}
