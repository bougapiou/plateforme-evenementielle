import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart' hide TextDirection;
import '../../core/documents.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import '../../data/reports_repository.dart';

final _dayFmt = DateFormat('EEE d MMM', 'fr');
String _dayLabel(String iso) => _dayFmt.format(DateTime.parse(iso));
String _iso(DateTime d) => DateFormat('yyyy-MM-dd').format(d);

/// Statistics (per day / whole event), laser-sensor flow (per day / per hour)
/// and participant list exports (Excel, Word, PDF) for one event.
class ReportsScreen extends StatelessWidget {
  final String eventId;
  const ReportsScreen({super.key, required this.eventId});

  @override
  Widget build(BuildContext context) => DefaultTabController(
    length: 3,
    child: Scaffold(
      appBar: AppBar(
        title: const Text('Statistiques & exports'),
        bottom: const TabBar(
          tabs: [
            Tab(text: 'Statistiques'),
            Tab(text: 'Flux capteurs'),
            Tab(text: 'Export'),
          ],
        ),
      ),
      body: TabBarView(
        children: [
          _StatsTab(eventId: eventId),
          _FluxTab(eventId: eventId),
          _ExportTab(eventId: eventId),
        ],
      ),
    ),
  );
}

// ============================================================== statistics

class _StatsTab extends ConsumerStatefulWidget {
  final String eventId;
  const _StatsTab({required this.eventId});
  @override
  ConsumerState<_StatsTab> createState() => _StatsTabState();
}

class _StatsTabState extends ConsumerState<_StatsTab>
    with AutomaticKeepAliveClientMixin {
  String? _jour;
  late Future<EventReport> _future;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() =>
      _future = ref
          .read(reportsRepositoryProvider)
          .stats(widget.eventId, jour: _jour);

  void _pick(String? jour) => setState(() {
    _jour = jour;
    _load();
  });

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return FutureView<EventReport>(
      future: _future,
      onRetry: () => setState(_load),
      builder: (r) {
        final t = r.totaux;
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                ChoiceChip(
                  label: const Text("Tout l'événement"),
                  selected: _jour == null,
                  onSelected: (_) => _pick(null),
                ),
                for (var i = 0; i < r.jours.length; i++)
                  ChoiceChip(
                    label: Text('J${i + 1} · ${_dayLabel(r.jours[i])}'),
                    selected: _jour == r.jours[i],
                    onSelected: (_) => _pick(r.jours[i]),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            _KpiGrid(
              items: [
                ('Billets émis', '${t.billetsEmis}'),
                (
                  'Inscriptions',
                  '${t.inscriptions} (${t.inscriptionsConfirmees} conf.)',
                ),
                ('Entrées QR', '${t.entrees}'),
                ('Visiteurs uniques', '${t.visiteursUniques}'),
                ('Entrées capteurs', '${t.capteurEntrees}'),
                ('Sorties capteurs', '${t.capteurSorties}'),
                ('Visites de stands', '${t.visitesStands}'),
                ('Revenus', Fmt.money(t.revenus)),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              'Jour par jour',
              style: Theme.of(context).textTheme.titleSmall,
            ),
            for (var i = 0; i < r.parJour.length; i++)
              Card(
                color:
                    _jour == r.parJour[i].$1
                        ? Theme.of(context).colorScheme.primaryContainer
                        : null,
                child: ListTile(
                  dense: true,
                  title: Text('J${i + 1} · ${_dayLabel(r.parJour[i].$1)}'),
                  subtitle: Text(
                    'Billets ${r.parJour[i].$2.billetsEmis} · Entrées QR ${r.parJour[i].$2.entrees}'
                    ' · Capteurs ↑${r.parJour[i].$2.capteurEntrees} ↓${r.parJour[i].$2.capteurSorties}'
                    ' · Stands ${r.parJour[i].$2.visitesStands}',
                  ),
                  trailing: Text(Fmt.money(r.parJour[i].$2.revenus)),
                ),
              ),
            if (r.parActivite.isNotEmpty) ...[
              const SizedBox(height: 16),
              Text(
                'Par activité',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              for (final a in r.parActivite)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: Text(a.titre),
                  subtitle: Text(Fmt.dateTime(a.debut)),
                  trailing: Text(
                    '${a.entrees} entrées\n${a.uniques} pers.',
                    textAlign: TextAlign.right,
                  ),
                ),
            ],
          ],
        );
      },
    );
  }
}

class _KpiGrid extends StatelessWidget {
  final List<(String, String)> items;
  const _KpiGrid({required this.items});

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, c) {
      final cols = c.maxWidth > 600 ? 4 : 2;
      final w = (c.maxWidth - (cols - 1) * 8) / cols;
      return Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final (label, value) in items)
            SizedBox(
              width: w,
              child: Card(
                margin: EdgeInsets.zero,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        label.toUpperCase(),
                        style: const TextStyle(
                          fontSize: 10,
                          color: Colors.black54,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        value,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      );
    },
  );
}

// ==================================================================== flux

class _FluxTab extends ConsumerStatefulWidget {
  final String eventId;
  const _FluxTab({required this.eventId});
  @override
  ConsumerState<_FluxTab> createState() => _FluxTabState();
}

class _FluxTabState extends ConsumerState<_FluxTab>
    with AutomaticKeepAliveClientMixin {
  String _granularite = 'JOUR';
  String? _jour;
  String? _capteurId;
  late Future<FluxReport> _future;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() =>
      _future = ref
          .read(reportsRepositoryProvider)
          .flux(
            widget.eventId,
            granularite: _granularite,
            jour: _granularite == 'HEURE' ? _jour : null,
            capteurId: _capteurId,
          );

  void _update(VoidCallback change) => setState(() {
    change();
    _load();
  });

  Future<void> _pickDay() async {
    final initial = _jour != null ? DateTime.parse(_jour!) : DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (d != null) _update(() => _jour = _iso(d));
  }

  String _label(FluxReport f, FluxPoint p) =>
      f.granularite == 'HEURE'
          ? '${p.periode} – ${((int.parse(p.periode.substring(0, 2)) + 1) % 24).toString().padLeft(2, '0')}h'
          : _dayLabel(p.periode);

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return FutureView<FluxReport>(
      future: _future,
      onRetry: () => setState(_load),
      builder: (f) {
        _jour ??= f.jour;
        final last = f.points.isEmpty ? 0 : f.points.last.presents;
        return RefreshIndicator(
          onRefresh: () async {
            setState(_load);
            await _future;
          },
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'JOUR', label: Text('Par jour')),
                  ButtonSegment(value: 'HEURE', label: Text('Par heure')),
                ],
                selected: {_granularite},
                onSelectionChanged:
                    (s) => _update(() => _granularite = s.first),
              ),
              if (_granularite == 'HEURE') ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final d in f.jours)
                      ChoiceChip(
                        label: Text(_dayLabel(d)),
                        selected: _jour == d,
                        onSelected: (_) => _update(() => _jour = d),
                      ),
                    ActionChip(
                      avatar: const Icon(Icons.calendar_month, size: 16),
                      label: Text(
                        _jour != null && !f.jours.contains(_jour)
                            ? _dayLabel(_jour!)
                            : 'Autre date',
                      ),
                      onPressed: _pickDay,
                    ),
                  ],
                ),
              ],
              if (f.capteurs.length > 1) ...[
                const SizedBox(height: 8),
                DropdownButtonFormField<String?>(
                  value: _capteurId,
                  decoration: const InputDecoration(labelText: 'Capteur'),
                  items: [
                    const DropdownMenuItem(
                      value: null,
                      child: Text('Tous les capteurs'),
                    ),
                    for (final c in f.capteurs)
                      DropdownMenuItem(value: c.id, child: Text(c.nom)),
                  ],
                  onChanged: (v) => _update(() => _capteurId = v),
                ),
              ],
              const SizedBox(height: 12),
              _KpiGrid(
                items: [
                  ('Entrées', '${f.totalEntrees}'),
                  ('Sorties', '${f.totalSorties}'),
                  ('Présents', '$last'),
                  (
                    'Pic',
                    f.pic == null
                        ? '—'
                        : '${_label(f, f.pic!)} (${f.pic!.entrees})',
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (f.points.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Text('Aucun passage compté sur cette période.'),
                )
              else ...[
                const Row(
                  children: [
                    _Legend(Color(0xFF173F5F), 'Entrées'),
                    SizedBox(width: 12),
                    _Legend(Color(0xFFC98F12), 'Sorties'),
                    SizedBox(width: 12),
                    _Legend(Color(0xFF4A443E), 'Présents'),
                  ],
                ),
                const SizedBox(height: 6),
                SizedBox(
                  height: 200,
                  child: CustomPaint(
                    painter: _FluxPainter(
                      f.points,
                      f.granularite == 'HEURE'
                          ? (p) => p.periode
                          : (p) => DateFormat(
                            'd/MM',
                          ).format(DateTime.parse(p.periode)),
                    ),
                    size: Size.infinite,
                  ),
                ),
              ],
              const SizedBox(height: 12),
              for (final p in f.points)
                if (f.granularite == 'JOUR' ||
                    p.entrees + p.sorties + p.scanEntrees + p.scanSorties > 0)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text(_label(f, p)),
                    subtitle: Text(
                      'QR : ${p.scanEntrees} entrées · ${p.scanSorties} sorties',
                    ),
                    trailing: Text(
                      '↑ ${p.entrees}   ↓ ${p.sorties}   = ${p.presents}',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                  ),
            ],
          ),
        );
      },
    );
  }
}

class _Legend extends StatelessWidget {
  final Color color;
  final String label;
  const _Legend(this.color, this.label);
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(width: 10, height: 10, color: color),
      const SizedBox(width: 4),
      Text(label, style: const TextStyle(fontSize: 12)),
    ],
  );
}

class _FluxPainter extends CustomPainter {
  final List<FluxPoint> points;
  final String Function(FluxPoint) tick;
  _FluxPainter(this.points, this.tick);

  @override
  void paint(Canvas canvas, Size size) {
    const bottom = 18.0;
    final h = size.height - bottom;
    final max = math.max(
      1,
      points
          .map((p) => math.max(p.presents, math.max(p.entrees, p.sorties)))
          .reduce(math.max),
    );
    final slot = size.width / points.length;
    final bar = math.max(1.5, slot / 2 - 1.5);
    double y(int v) => h - (h - 4) * v / max;
    final inPaint = Paint()..color = const Color(0xFF173F5F);
    final outPaint = Paint()..color = const Color(0xFFC98F12);
    final line =
        Paint()
          ..color = const Color(0xFF4A443E)
          ..strokeWidth = 2
          ..style = PaintingStyle.stroke;
    final path = Path();
    final every = (points.length / 8).ceil();
    for (var i = 0; i < points.length; i++) {
      final p = points[i];
      final x = i * slot;
      canvas.drawRect(
        Rect.fromLTRB(x + 1, y(p.entrees), x + 1 + bar, h),
        inPaint,
      );
      canvas.drawRect(
        Rect.fromLTRB(x + 1 + bar, y(p.sorties), x + 1 + 2 * bar, h),
        outPaint,
      );
      final cx = x + slot / 2;
      i == 0 ? path.moveTo(cx, y(p.presents)) : path.lineTo(cx, y(p.presents));
      if (i % every == 0) {
        final tp = TextPainter(
          text: TextSpan(
            text: tick(p),
            style: const TextStyle(fontSize: 9, color: Colors.black54),
          ),
          textDirection: TextDirection.ltr,
        )..layout();
        tp.paint(canvas, Offset(cx - tp.width / 2, h + 4));
      }
    }
    canvas.drawPath(path, line);
    canvas.drawLine(
      Offset(0, h),
      Offset(size.width, h),
      Paint()..color = const Color(0xFFE2DDD6),
    );
  }

  @override
  bool shouldRepaint(_FluxPainter old) => old.points != points;
}

// ================================================================== export

class _ExportTab extends ConsumerStatefulWidget {
  final String eventId;
  const _ExportTab({required this.eventId});
  @override
  ConsumerState<_ExportTab> createState() => _ExportTabState();
}

class _ExportTabState extends ConsumerState<_ExportTab>
    with AutomaticKeepAliveClientMixin {
  static const _sources = {
    'EVENEMENT': [
      ('BILLETS', 'Détenteurs de billets'),
      ('PRESENTS', 'Présents (entrées contrôlées)'),
      ('INSCRITS', 'Participants inscrits'),
      ('VISITES', 'Visiteurs de tous les stands'),
    ],
    'ACTIVITE': [
      ('BILLETS', "Détenteurs d'un billet pour l'activité"),
      ('PRESENTS', "Présents à l'activité (scans)"),
    ],
    'STAND': [('VISITES', 'Visiteurs du stand')],
  };

  ParticipantCriteria _c = const ParticipantCriteria();
  List<Activity> _activities = [];
  List<(String, String)> _stands = [];
  List<String> _jours = [];
  ParticipantPreview? _preview;
  String? _error;
  bool _busy = false;
  int _seq = 0;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    final org = ref.read(organizerEventsRepositoryProvider);
    final reports = ref.read(reportsRepositoryProvider);
    org
        .activities(widget.eventId)
        .then((a) {
          if (mounted) setState(() => _activities = a);
        })
        .catchError((_) {});
    reports
        .stands(widget.eventId)
        .then((s) {
          if (mounted) setState(() => _stands = s);
        })
        .catchError((_) {});
    reports
        .stats(widget.eventId)
        .then((r) {
          if (mounted) setState(() => _jours = r.jours);
        })
        .catchError((_) {});
    _refresh();
  }

  Future<void> _refresh() async {
    if (_c.perimetre != 'EVENEMENT' && _c.cibleId == null) {
      setState(() => _preview = null);
      return;
    }
    final seq = ++_seq;
    try {
      final p = await ref
          .read(reportsRepositoryProvider)
          .participants(widget.eventId, _c);
      if (mounted && seq == _seq)
        setState(() {
          _preview = p;
          _error = null;
        });
    } on ApiException catch (e) {
      if (mounted && seq == _seq) setState(() => _error = e.message);
    }
  }

  void _set(ParticipantCriteria c) {
    setState(() => _c = c);
    _refresh();
  }

  void _setPerimetre(String p) {
    String? cible;
    if (p == 'ACTIVITE' && _activities.isNotEmpty) cible = _activities.first.id;
    if (p == 'STAND' && _stands.isNotEmpty) cible = _stands.first.$1;
    _set(
      _c.copyWith(
        perimetre: p,
        cibleId: () => cible,
        source: _sources[p]!.first.$1,
      ),
    );
  }

  String _local(DateTime d) => DateFormat("yyyy-MM-dd'T'HH:mm").format(d);

  Future<void> _pickBound(bool start) async {
    final current = (start ? _c.du : _c.au);
    final init = current != null ? DateTime.parse(current) : DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: init,
      firstDate: DateTime(2020),
      lastDate: DateTime(2100),
    );
    if (d == null || !mounted) return;
    final t = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(init),
    );
    if (!mounted) return;
    final v = _local(
      DateTime(d.year, d.month, d.day, t?.hour ?? 0, t?.minute ?? 0),
    );
    _set(start ? _c.copyWith(du: () => v) : _c.copyWith(au: () => v));
  }

  bool _isDay(String d) {
    final next = _iso(DateTime.parse(d).add(const Duration(days: 1)));
    return _c.du == '${d}T00:00' && _c.au == '${next}T00:00';
  }

  Future<void> _export(String format) async {
    setState(() => _busy = true);
    try {
      final repo = ref.read(reportsRepositoryProvider);
      final ext = format.toLowerCase();
      await fetchAndPresentDocument(
        ref.read(apiClientProvider),
        path: repo.exportPath(widget.eventId, _c, format),
        filename: 'participants-${_c.source.toLowerCase()}.$ext',
      );
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } catch (_) {
      if (mounted) showSnack(context, "L'export a échoué.", error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final p = _preview;
    final count = p == null ? 0 : (_c.doublons ? p.total : p.uniques);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        DropdownButtonFormField<String>(
          value: _c.perimetre,
          decoration: const InputDecoration(labelText: 'Liste de'),
          items: [
            const DropdownMenuItem(
              value: 'EVENEMENT',
              child: Text("Tout l'événement"),
            ),
            if (_activities.isNotEmpty)
              const DropdownMenuItem(
                value: 'ACTIVITE',
                child: Text('Une activité'),
              ),
            if (_stands.isNotEmpty)
              const DropdownMenuItem(value: 'STAND', child: Text('Un stand')),
          ],
          onChanged: (v) => _setPerimetre(v!),
        ),
        if (_c.perimetre == 'ACTIVITE')
          DropdownButtonFormField<String>(
            value: _c.cibleId,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Activité'),
            items: [
              for (final a in _activities)
                DropdownMenuItem(
                  value: a.id,
                  child: Text(
                    '${Fmt.time(a.dateDebut)} · ${a.titre}',
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            ],
            onChanged: (v) => _set(_c.copyWith(cibleId: () => v)),
          ),
        if (_c.perimetre == 'STAND')
          DropdownButtonFormField<String>(
            value: _c.cibleId,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Stand'),
            items: [
              for (final s in _stands)
                DropdownMenuItem(value: s.$1, child: Text(s.$2)),
            ],
            onChanged: (v) => _set(_c.copyWith(cibleId: () => v)),
          ),
        DropdownButtonFormField<String>(
          value: _c.source,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Personnes'),
          items: [
            for (final s in _sources[_c.perimetre]!)
              DropdownMenuItem(value: s.$1, child: Text(s.$2)),
          ],
          onChanged: (v) => _set(_c.copyWith(source: v)),
        ),
        const SizedBox(height: 16),
        Text('Période', style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 6),
        Wrap(
          spacing: 6,
          runSpacing: 6,
          children: [
            ChoiceChip(
              label: const Text('Toute la période'),
              selected: _c.du == null && _c.au == null,
              onSelected:
                  (_) => _set(_c.copyWith(du: () => null, au: () => null)),
            ),
            for (var i = 0; i < _jours.length; i++)
              ChoiceChip(
                label: Text('J${i + 1} · ${_dayLabel(_jours[i])}'),
                selected: _isDay(_jours[i]),
                onSelected: (_) {
                  final d = _jours[i];
                  final next = _iso(
                    DateTime.parse(d).add(const Duration(days: 1)),
                  );
                  _set(
                    _c.copyWith(
                      du: () => '${d}T00:00',
                      au: () => '${next}T00:00',
                    ),
                  );
                },
              ),
          ],
        ),
        const SizedBox(height: 6),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.schedule, size: 16),
                label: Text(
                  _c.du == null
                      ? 'Du : début'
                      : 'Du ${_c.du!.replaceFirst('T', ' ')}',
                ),
                onPressed: () => _pickBound(true),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.schedule, size: 16),
                label: Text(
                  _c.au == null
                      ? 'Au : fin'
                      : 'Au ${_c.au!.replaceFirst('T', ' ')}',
                ),
                onPressed: () => _pickBound(false),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        Text('Doublons', style: Theme.of(context).textTheme.titleSmall),
        RadioListTile<bool>(
          dense: true,
          contentPadding: EdgeInsets.zero,
          value: true,
          groupValue: _c.doublons,
          title: const Text('Garder les doublons'),
          onChanged: (v) => _set(_c.copyWith(doublons: v)),
        ),
        RadioListTile<bool>(
          dense: true,
          contentPadding: EdgeInsets.zero,
          value: false,
          groupValue: _c.doublons,
          title: const Text('Supprimer les doublons (une ligne par personne)'),
          subtitle: const Text(
            'Reconnue au téléphone, sinon à l\'e-mail, sinon au nom.',
          ),
          onChanged: (v) => _set(_c.copyWith(doublons: v)),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(_error!, style: const TextStyle(color: Color(0xFFA2040E))),
          ),
        if (p != null) ...[
          const Divider(height: 24),
          Text(p.titre, style: const TextStyle(fontWeight: FontWeight.w700)),
          Text(p.filtres, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 6),
          Text(
            '${p.total} lignes · ${p.uniques} personnes distinctes · '
            '${p.doublons == 0 ? 'aucun doublon' : '${p.doublons} doublons'}',
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              for (final (f, label) in [
                ('XLSX', 'Excel'),
                ('DOCX', 'Word'),
                ('PDF', 'PDF'),
              ]) ...[
                Expanded(
                  child: FilledButton(
                    onPressed: _busy || count == 0 ? null : () => _export(f),
                    child: Text(label),
                  ),
                ),
                if (f != 'PDF') const SizedBox(width: 8),
              ],
            ],
          ),
          const SizedBox(height: 10),
          for (final l in p.lignes)
            ListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              title: Text(
                [
                  l.nom,
                  l.prenom,
                ].where((s) => s.isNotEmpty).join(' ').ifEmpty('—'),
              ),
              subtitle: Text(
                [
                  l.telephone,
                  l.email,
                  l.reference,
                  l.categorie,
                ].where((s) => s.isNotEmpty).join(' · '),
              ),
              trailing:
                  !_c.doublons && l.occurrences > 1
                      ? Text(
                        '×${l.occurrences}',
                        style: const TextStyle(
                          color: Color(0xFFB07800),
                          fontWeight: FontWeight.w700,
                        ),
                      )
                      : null,
            ),
          if (count > p.lignes.length)
            Text(
              'Aperçu des ${p.lignes.length} premières lignes — le fichier contient les $count.',
              style: Theme.of(context).textTheme.bodySmall,
            ),
        ],
      ],
    );
  }
}

extension on String {
  String ifEmpty(String other) => isEmpty ? other : this;
}
