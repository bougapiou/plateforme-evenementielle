import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

/// « Présence en direct » entry of the public menu: the events happening now, each opening its live
/// counters (tickets scanned, sensors, or both added up) — no account needed.
class LiveEventsScreen extends ConsumerStatefulWidget {
  const LiveEventsScreen({super.key});

  @override
  ConsumerState<LiveEventsScreen> createState() => _LiveEventsScreenState();
}

class _LiveEventsScreenState extends ConsumerState<LiveEventsScreen> {
  late Future<List<EventSummary>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(eventsRepositoryProvider).liveEvents();
  }

  void _reload() =>
      setState(() => _future = ref.read(eventsRepositoryProvider).liveEvents());

  String _when(EventSummary e) {
    final s = e.dateDebut?.toLocal();
    final f = e.dateFin?.toLocal();
    if (s == null) return '—';
    final sameDay =
        f != null && s.year == f.year && s.month == f.month && s.day == f.day;
    if (sameDay) return '${Fmt.time(s)} – ${Fmt.time(f)}';
    final short = DateFormat('d MMM', 'fr');
    return f == null ? short.format(s) : '${short.format(s)} → ${short.format(f)}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Présence en direct')),
      body: RefreshIndicator(
        onRefresh: () async => _reload(),
        child: FutureView<List<EventSummary>>(
          future: _future,
          onRetry: _reload,
          builder: (events) {
            if (events.isEmpty) {
              return ListView(
                children: const [
                  SizedBox(height: 80),
                  EmptyState(
                    icon: Icons.sensors,
                    title: 'Aucun événement en cours',
                    subtitle:
                        "La présence en direct apparaît ici dès qu'un événement commence.",
                    action: DiscoverEventsButton(),
                  ),
                ],
              );
            }
            return MaxWidth(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                children: [
                  Text(
                    "Combien de personnes sont entrées, sorties ou présentes en ce moment : billets "
                    "scannés à l'entrée et passages comptés par les capteurs, séparément ou additionnés.",
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 12),
                  for (final e in events) _LiveCard(event: e, when: _when(e)),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _LiveCard extends StatelessWidget {
  final EventSummary event;
  final String when;
  const _LiveCard({required this.event, required this.when});

  @override
  Widget build(BuildContext context) {
    final place = [
      event.ville,
      event.lieu,
    ].whereType<String>().where((s) => s.isNotEmpty).join(' · ');
    return Card(
      clipBehavior: Clip.antiAlias,
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: () => context.push('/presence-en-direct/${event.slug}'),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: SizedBox(
                      width: 84,
                      height: 84,
                      child: EventCover(nom: event.nom, url: event.coverUrl),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const _EnCoursPill(),
                        const SizedBox(height: 6),
                        Text(
                          event.nom,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        if (place.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Row(
                              children: [
                                const Icon(
                                  Icons.place_outlined,
                                  size: 15,
                                  color: Brand.s400,
                                ),
                                const SizedBox(width: 4),
                                Expanded(
                                  child: Text(
                                    place,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style:
                                        Theme.of(context).textTheme.bodySmall,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        Padding(
                          padding: const EdgeInsets.only(top: 2),
                          child: Row(
                            children: [
                              const Icon(
                                Icons.schedule,
                                size: 15,
                                color: Brand.s400,
                              ),
                              const SizedBox(width: 4),
                              Expanded(
                                child: Text(
                                  when,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed:
                          () =>
                              context.push('/presence-en-direct/${event.slug}'),
                      icon: const Icon(Icons.groups_outlined),
                      label: const Text('Voir la présence'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.outlined(
                    tooltip: 'Plein écran',
                    onPressed:
                        () => context.push(
                          '/presence-en-direct/${event.slug}?plein=1',
                        ),
                    icon: const Icon(Icons.fullscreen),
                    style: IconButton.styleFrom(
                      minimumSize: const Size(48, 48),
                      side: const BorderSide(color: Brand.s300),
                      foregroundColor: Brand.b700,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EnCoursPill extends StatelessWidget {
  const _EnCoursPill();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color: const Color(0xFFECFDF5),
      borderRadius: BorderRadius.circular(99),
    ),
    child: const Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        CircleAvatar(radius: 3, backgroundColor: Color(0xFF10B981)),
        SizedBox(width: 5),
        Text(
          'En cours',
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w700,
            color: Color(0xFF047857),
          ),
        ),
      ],
    ),
  );
}
