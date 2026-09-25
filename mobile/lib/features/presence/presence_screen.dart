import 'dart:async';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../core/brand.dart';
import '../../core/manage_kit.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import 'presence_flow.dart';

/// Source picked on this device; kept while the app runs, so a display left open keeps its setting.
final presenceSourceProvider = StateProvider<PresenceSource?>((ref) => null);

final _number = NumberFormat.decimalPattern('fr');
const _tabular = [FontFeature.tabularFigures()];

/// Live presence of an event — entries, exits, people inside, re-entries — with the choice of where the
/// numbers come from: ticket scans (QR), laser sensors, or both added up. Works without an account, and
/// grows to the whole screen (immersive, dark) for a display at the venue.
class PresenceScreen extends ConsumerStatefulWidget {
  final String slug;
  final PresenceSource? initialSource;
  final bool startFullscreen;

  const PresenceScreen({
    super.key,
    required this.slug,
    this.initialSource,
    this.startFullscreen = false,
  });

  @override
  ConsumerState<PresenceScreen> createState() => _PresenceScreenState();
}

class _PresenceScreenState extends ConsumerState<PresenceScreen> {
  static const _refresh = Duration(seconds: 4);

  Timer? _timer;
  AttendanceView? _data;
  DateTime? _updatedAt;
  bool _loading = true;
  bool _gone = false; // not found / not public yet
  bool _offline =
      false; // the last refresh failed: the numbers on screen are the previous ones
  bool _fullscreen = false;

  @override
  void initState() {
    super.initState();
    final initial = widget.initialSource;
    if (initial != null) {
      Future.microtask(
        () => ref.read(presenceSourceProvider.notifier).state = initial,
      );
    }
    if (widget.startFullscreen) _setFullscreen(true);
    _load();
    _timer = Timer.periodic(_refresh, (_) => _load());
  }

  @override
  void dispose() {
    _timer?.cancel();
    if (_fullscreen) _systemUi(false);
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final view = await ref
          .read(checkinRepositoryProvider)
          .publicAttendance(widget.slug);
      if (!mounted) return;
      setState(() {
        _data = view;
        _updatedAt = DateTime.now();
        _loading = false;
        _gone = false;
        _offline = false;
      });
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        if (e.status == 404 || e.status == 403) {
          _gone = true;
        } else {
          _offline = true;
        }
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _offline = true;
      });
    }
  }

  void _systemUi(bool immersive) {
    if (kIsWeb) return;
    SystemChrome.setEnabledSystemUIMode(
      immersive ? SystemUiMode.immersiveSticky : SystemUiMode.edgeToEdge,
    );
  }

  void _setFullscreen(bool on) {
    _systemUi(on);
    if (mounted) {
      setState(() => _fullscreen = on);
    } else {
      _fullscreen = on;
    }
  }

  PresenceSource _source() {
    final auto =
        hasPhysicalCount(_data?.comptagePhysique)
            ? PresenceSource.combine
            : PresenceSource.qr;
    return ref.watch(presenceSourceProvider) ?? auto;
  }

  void _pick(PresenceSource s) =>
      ref.read(presenceSourceProvider.notifier).state = s;

  List<_Counter> _counters(PresenceSource source) {
    final d = _data!;
    final f = flowOf(source, d.event, d.comptagePhysique);
    final combined = source == PresenceSource.combine;
    String? detail(String key) {
      if (!combined) return null;
      final c = contributions(key, d.event, d.comptagePhysique);
      return 'Billets ${_number.format(c.qr)}\nCapteurs ${_number.format(c.physique)}';
    }

    return [
      _Counter(
        'Entrées',
        Icons.login,
        f.entrees,
        _Tone.entrees,
        detail('entrees'),
      ),
      _Counter(
        'Sorties',
        Icons.logout,
        f.sorties,
        _Tone.sorties,
        detail('sorties'),
      ),
      _Counter(
        'Présents',
        Icons.groups_outlined,
        f.presents,
        _Tone.presents,
        detail('presents'),
      ),
      if (f.reentrees != null)
        _Counter(
          'Ré-entrées',
          Icons.repeat,
          f.reentrees!,
          _Tone.reentrees,
          combined ? 'Billets uniquement' : null,
        ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final source = _source();
    return PopScope(
      canPop: !_fullscreen,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _setFullscreen(false);
      },
      child:
          _fullscreen && _data != null
              ? _buildDark(source)
              : _buildLight(source),
    );
  }

  // --- normal mode ---------------------------------------------------------

  Widget _buildLight(PresenceSource source) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Présence en direct'),
        actions: [
          if (_data != null)
            IconButton(
              icon: const Icon(Icons.fullscreen),
              tooltip: 'Plein écran',
              onPressed: () => _setFullscreen(true),
            ),
        ],
      ),
      body: _lightBody(source),
    );
  }

  Widget _lightBody(PresenceSource source) {
    if (_data == null) {
      if (_loading) return const Center(child: CircularProgressIndicator());
      if (_gone) {
        return const EmptyState(
          icon: Icons.event_busy,
          title: 'Pas de présence à afficher',
          subtitle: "Cet événement est introuvable ou n'est pas encore publié.",
        );
      }
      return EmptyState(
        icon: Icons.cloud_off,
        title: 'Impossible de charger',
        subtitle: 'Vérifiez votre connexion.',
        action: OutlinedButton.icon(
          onPressed: _load,
          icon: const Icon(Icons.refresh),
          label: const Text('Réessayer'),
        ),
      );
    }

    final d = _data!;
    final counters = _counters(source);
    final noSensor =
        source != PresenceSource.qr && !hasPhysicalCount(d.comptagePhysique);
    final width = MediaQuery.sizeOf(context).width;
    final side = width > 760 ? (width - 728) / 2 : 16.0;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: EdgeInsets.fromLTRB(side, 16, side, 32),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    d.eventNom,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 6),
                  Row(
                    children: [
                      const _LiveDot(),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          _offline
                              ? 'Connexion perdue · dernières valeurs affichées'
                              : 'Mis à jour à ${DateFormat.Hms('fr').format(_updatedAt!)}',
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: _offline ? Brand.amber : null),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          _SourcePicker(value: source, onChanged: _pick),
          const SizedBox(height: 4),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
            child: Text(
              source.description,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          if (noSensor)
            const Padding(
              padding: EdgeInsets.only(bottom: 10),
              child: InfoBanner(
                "Aucun passage n'a encore été compté par un capteur pour cet événement.",
                tone: KitTone.amber,
              ),
            ),
          const SizedBox(height: 4),
          _CounterGrid(counters: counters),
          if (source == PresenceSource.combine)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(
                'Combiné : chaque compteur additionne les scans de billets et les passages '
                'comptés par les capteurs. À utiliser quand les deux ne comptent pas les mêmes '
                'accès — sinon une personne serait comptée deux fois.',
                style: Theme.of(
                  context,
                ).textTheme.bodySmall?.copyWith(color: Brand.s400),
              ),
            ),
          if (d.activites.isNotEmpty) ...[
            const SizedBox(height: 24),
            Text(
              'Par activité',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            if (source == PresenceSource.physique)
              Text(
                "Les capteurs comptent les passages à l'entrée de l'événement, pas activité par "
                'activité : choisissez « Billets QR » ou « Combiné » pour voir le détail des activités.',
                style: Theme.of(context).textTheme.bodySmall,
              )
            else ...[
              if (source == PresenceSource.combine)
                Text(
                  'Détail par activité : scans de billets uniquement.',
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: Brand.s400),
                ),
              const SizedBox(height: 6),
              for (final a in d.activites) _ActivityCard(activity: a),
            ],
          ],
        ],
      ),
    );
  }

  // --- full screen ---------------------------------------------------------

  Widget _buildDark(PresenceSource source) {
    final d = _data!;
    return Scaffold(
      backgroundColor: const Color(0xFF020617),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const _LiveDot(),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'PRÉSENCE EN DIRECT',
                      style: TextStyle(
                        color: Brand.s400,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 2,
                      ),
                    ),
                  ),
                  if (_updatedAt != null)
                    Text(
                      _offline
                          ? 'Connexion perdue'
                          : DateFormat.Hms('fr').format(_updatedAt!),
                      style: TextStyle(
                        color: _offline ? const Color(0xFFF59E0B) : Brand.s500,
                        fontSize: 11,
                      ),
                    ),
                  IconButton(
                    icon: const Icon(
                      Icons.fullscreen_exit,
                      color: Colors.white,
                    ),
                    tooltip: 'Quitter le plein écran',
                    onPressed: () => _setFullscreen(false),
                  ),
                ],
              ),
              Text(
                d.eventNom,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                source.description,
                style: const TextStyle(color: Brand.s400, fontSize: 13),
              ),
              const SizedBox(height: 12),
              Expanded(child: _DarkBoard(counters: _counters(source))),
              const SizedBox(height: 12),
              _SourcePicker(value: source, onChanged: _pick, dark: true),
            ],
          ),
        ),
      ),
    );
  }
}

// --- building blocks -------------------------------------------------------

enum _Tone {
  entrees(
    light: Brand.b50,
    lightFg: Brand.b700,
    dark: Color(0xFF06251A),
    darkBorder: Color(0xFF14532D),
    darkValue: Color(0xFF6EE7B7),
    darkLabel: Color(0xFF10B981),
  ),
  sorties(
    light: Brand.s100,
    lightFg: Brand.s700,
    dark: Color(0xFF0B1226),
    darkBorder: Color(0xFF334155),
    darkValue: Color(0xFFF1F5F9),
    darkLabel: Color(0xFF94A3B8),
  ),
  presents(
    light: Color(0xFFEFF6FF),
    lightFg: Color(0xFF1D4ED8),
    dark: Color(0xFF082238),
    darkBorder: Color(0xFF075985),
    darkValue: Color(0xFF7DD3FC),
    darkLabel: Color(0xFF0EA5E9),
  ),
  reentrees(
    light: Color(0xFFFFFBEB),
    lightFg: Color(0xFFB45309),
    dark: Color(0xFF241205),
    darkBorder: Color(0xFF7C2D12),
    darkValue: Color(0xFFFCD34D),
    darkLabel: Color(0xFFF59E0B),
  );

  final Color light;
  final Color lightFg;
  final Color dark;
  final Color darkBorder;
  final Color darkValue;
  final Color darkLabel;
  const _Tone({
    required this.light,
    required this.lightFg,
    required this.dark,
    required this.darkBorder,
    required this.darkValue,
    required this.darkLabel,
  });
}

class _Counter {
  final String label;
  final IconData icon;
  final int value;
  final _Tone tone;

  /// What each source adds to a combined counter.
  final String? detail;
  const _Counter(this.label, this.icon, this.value, this.tone, this.detail);
}

class _LiveDot extends StatelessWidget {
  const _LiveDot();

  @override
  Widget build(BuildContext context) => Container(
    width: 10,
    height: 10,
    decoration: BoxDecoration(
      color: const Color(0xFF10B981),
      shape: BoxShape.circle,
      boxShadow: [
        BoxShadow(
          color: const Color(0xFF10B981).withValues(alpha: .45),
          blurRadius: 6,
          spreadRadius: 2,
        ),
      ],
    ),
  );
}

/// Segmented control: tickets (QR) / sensors / both added up.
class _SourcePicker extends StatelessWidget {
  final PresenceSource value;
  final ValueChanged<PresenceSource> onChanged;
  final bool dark;
  const _SourcePicker({
    required this.value,
    required this.onChanged,
    this.dark = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: dark ? const Color(0xCC1E293B) : Brand.s100,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: [
          for (final s in PresenceSource.values)
            Expanded(
              child: Semantics(
                button: true,
                selected: s == value,
                label: '${s.label} : ${s.description}',
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () => onChanged(s),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 150),
                    padding: const EdgeInsets.symmetric(
                      vertical: 11,
                      horizontal: 4,
                    ),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: s == value ? Colors.white : Colors.transparent,
                      borderRadius: BorderRadius.circular(10),
                      boxShadow:
                          s == value && !dark
                              ? [
                                BoxShadow(
                                  color: Colors.black.withValues(alpha: .08),
                                  blurRadius: 4,
                                  offset: const Offset(0, 1),
                                ),
                              ]
                              : null,
                    ),
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        s.label,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w700,
                          color:
                              s == value
                                  ? (dark ? Brand.s900 : Brand.b700)
                                  : (dark ? Brand.s300 : Brand.s500),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Two columns of counters (three on wide screens when there are three), the last one stretched if alone.
class _CounterGrid extends StatelessWidget {
  final List<_Counter> counters;
  const _CounterGrid({required this.counters});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) {
        const gap = 10.0;
        final cols = counters.length == 3 && c.maxWidth >= 560 ? 3 : 2;
        final tile = (c.maxWidth - gap * (cols - 1)) / cols;
        final lonely = counters.length % cols == 1;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (int i = 0; i < counters.length; i++)
              SizedBox(
                width: lonely && i == counters.length - 1 ? c.maxWidth : tile,
                child: _LightTile(counter: counters[i]),
              ),
          ],
        );
      },
    );
  }
}

class _LightTile extends StatelessWidget {
  final _Counter counter;
  const _LightTile({required this.counter});

  @override
  Widget build(BuildContext context) {
    final t = counter.tone;
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 14, 10, 12),
      decoration: BoxDecoration(
        color: t.light,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(counter.icon, size: 22, color: t.lightFg),
          const SizedBox(height: 4),
          FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(
              _number.format(counter.value),
              style: TextStyle(
                fontSize: 34,
                fontWeight: FontWeight.w800,
                color: t.lightFg,
                fontFeatures: _tabular,
              ),
            ),
          ),
          Text(
            counter.label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: t.lightFg.withValues(alpha: .85),
            ),
          ),
          if (counter.detail != null)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                counter.detail!,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 11, color: Brand.s500),
              ),
            ),
        ],
      ),
    );
  }
}

class _ActivityCard extends StatelessWidget {
  final ActivityFlow activity;
  const _ActivityCard({required this.activity});

  @override
  Widget build(BuildContext context) {
    final f = activity.flux;
    Widget stat(String value, String label, Color color) => Expanded(
      child: Column(
        children: [
          Text(
            value,
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w800,
              color: color,
              fontFeatures: _tabular,
            ),
          ),
          Text(label, style: const TextStyle(fontSize: 11, color: Brand.s500)),
        ],
      ),
    );
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(activity.titre, style: Theme.of(context).textTheme.titleSmall),
            if (activity.dateDebut != null || activity.acces != null)
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Text(
                  [
                    if (activity.dateDebut != null)
                      DateFormat(
                        "d MMM · HH:mm",
                        'fr',
                      ).format(activity.dateDebut!.toLocal()),
                    if (activity.acces == 'PAYANT') 'payant',
                    if (activity.acces == 'GRATUIT') 'gratuit',
                  ].join(' · '),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ),
            const SizedBox(height: 12),
            Row(
              children: [
                stat('${f['entrees'] ?? 0}', 'Entrées', Brand.b700),
                stat('${f['sorties'] ?? 0}', 'Sorties', Brand.s700),
                stat(
                  '${f['presents'] ?? 0}',
                  'Présents',
                  const Color(0xFF1D4ED8),
                ),
                stat(
                  '${f['reentrees'] ?? 0}',
                  'Ré-entrées',
                  const Color(0xFFB45309),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// The full-screen board: as big as the screen allows, one tile per counter.
class _DarkBoard extends StatelessWidget {
  final List<_Counter> counters;
  const _DarkBoard({required this.counters});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, c) {
        const gap = 12.0;
        final wide = c.maxWidth / c.maxHeight > 1.8;
        List<Widget> spaced(List<Widget> items, Widget space) => [
          for (int i = 0; i < items.length; i++) ...[
            if (i > 0) space,
            items[i],
          ],
        ];
        Widget tile(_Counter counter) =>
            Expanded(child: _DarkTile(counter: counter));

        // `stretch` gives every tile the full height (or width) of its slot;
        // without it the tiles shrink around their content and drift apart.
        if (wide || counters.length == 3 && c.maxWidth > c.maxHeight) {
          return Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: spaced([
              for (final k in counters) tile(k),
            ], const SizedBox(width: gap)),
          );
        }
        if (counters.length == 4) {
          Widget row(List<_Counter> two) => Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: spaced([
                for (final k in two) tile(k),
              ], const SizedBox(width: gap)),
            ),
          );
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              row(counters.sublist(0, 2)),
              const SizedBox(height: gap),
              row(counters.sublist(2)),
            ],
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: spaced([
            for (final k in counters) tile(k),
          ], const SizedBox(height: gap)),
        );
      },
    );
  }
}

class _DarkTile extends StatelessWidget {
  final _Counter counter;
  const _DarkTile({required this.counter});

  @override
  Widget build(BuildContext context) {
    final t = counter.tone;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: t.dark,
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: t.darkBorder),
      ),
      child: FittedBox(
        fit: BoxFit.contain,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(counter.icon, size: 34, color: t.darkLabel),
            const SizedBox(height: 4),
            Text(
              _number.format(counter.value),
              style: TextStyle(
                fontSize: 96,
                height: 1,
                fontWeight: FontWeight.w900,
                color: t.darkValue,
                fontFeatures: _tabular,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              counter.label.toUpperCase(),
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                letterSpacing: 2,
                color: t.darkLabel,
              ),
            ),
            if (counter.detail != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  counter.detail!,
                  style: const TextStyle(fontSize: 14, color: Brand.s400),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
