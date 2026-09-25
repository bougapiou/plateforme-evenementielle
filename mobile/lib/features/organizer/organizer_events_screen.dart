import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import 'event_status_hint.dart';

/// Groups of statuses the organiser thinks in ("brouillons", "en ligne"…).
enum _Filter {
  tous('Tous', null),
  brouillons('Brouillons', {'BROUILLON', 'REFUSE'}),
  aValider('À valider', {'SOUMIS'}),
  enLigne('En ligne',
      {'VALIDE', 'PUBLIE', 'INSCRIPTIONS_OUVERTES', 'INSCRIPTIONS_FERMEES'}),
  enCours('En cours', {'EN_COURS'}),
  termines('Terminés', {'TERMINE', 'ANNULE', 'SUSPENDU'});

  final String label;
  final Set<String>? statuts;
  const _Filter(this.label, this.statuts);

  bool matches(String statut) => statuts == null || statuts!.contains(statut);
}

class OrganizerEventsScreen extends ConsumerStatefulWidget {
  const OrganizerEventsScreen({super.key});

  @override
  ConsumerState<OrganizerEventsScreen> createState() =>
      _OrganizerEventsScreenState();
}

class _OrganizerEventsScreenState extends ConsumerState<OrganizerEventsScreen> {
  late Future<Paged<EventSummary>> _future;
  _Filter _filter = _Filter.tous;

  @override
  void initState() {
    super.initState();
    _future = ref.read(organizerEventsRepositoryProvider).mine();
  }

  void _refresh() => setState(
      () => _future = ref.read(organizerEventsRepositoryProvider).mine());

  Future<void> _create() async {
    await context.push('/mes-evenements/nouveau');
    _refresh();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mes événements')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _create,
        icon: const Icon(Icons.add),
        label: const Text('Créer'),
      ),
      body: FutureView<Paged<EventSummary>>(
        future: _future,
        onRetry: _refresh,
        builder: (paged) {
          final events = paged.content;
          if (events.isEmpty) {
            return RefreshIndicator(
              onRefresh: () async => _refresh(),
              child: ListView(children: [
                const SizedBox(height: 80),
                EmptyState(
                  icon: Icons.event_note_outlined,
                  title: 'Aucun événement',
                  subtitle:
                      "Créez votre premier événement. Il restera en brouillon jusqu'à sa soumission.",
                  action: FilledButton.icon(
                    onPressed: _create,
                    icon: const Icon(Icons.add),
                    label: const Text('Créer mon premier événement'),
                  ),
                ),
              ]),
            );
          }

          final counts = {
            for (final f in _Filter.values)
              f: events.where((e) => f.matches(e.statut)).length,
          };
          // a filter that holds nothing is not worth a pill
          final current =
              counts[_filter] == 0 ? _Filter.tous : _filter;
          final visible = events.where((e) => current.matches(e.statut)).toList();

          return Column(children: [
            const SizedBox(height: 12),
            FilterPills<_Filter>(
              items: [
                for (final f in _Filter.values)
                  if (f == _Filter.tous || counts[f]! > 0)
                    (value: f, label: f.label, count: counts[f]),
              ],
              selected: current,
              onSelected: (f) => setState(() => _filter = f),
            ),
            const SizedBox(height: 4),
            Expanded(
              child: RefreshIndicator(
                onRefresh: () async => _refresh(),
                child: MaxWidth(
                  child: ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
                    itemCount: visible.length,
                    itemBuilder: (_, i) => _EventCard(
                      event: visible[i],
                      onTap: () async {
                        await context.push('/mes-evenements/${visible[i].id}');
                        _refresh();
                      },
                    ),
                  ),
                ),
              ),
            ),
          ]);
        },
      ),
    );
  }
}

class _EventCard extends StatelessWidget {
  final EventSummary event;
  final VoidCallback onTap;
  const _EventCard({required this.event, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final place = [event.ville, event.lieu]
        .whereType<String>()
        .where((s) => s.isNotEmpty)
        .join(' · ');
    final hint = organizerHint(event.statut);
    return Card(
      clipBehavior: Clip.antiAlias,
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: onTap,
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: SizedBox(
                  width: 88,
                  height: 88,
                  child: EventCover(nom: event.nom, url: event.coverUrl),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  StatusChip(event.statut),
                  const SizedBox(height: 6),
                  Text(event.nom,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 4),
                  _Meta(Icons.calendar_today_outlined,
                      Fmt.rangeShort(event.dateDebut, event.dateFin)),
                  if (place.isNotEmpty) _Meta(Icons.place_outlined, place),
                ]),
              ),
              const Icon(Icons.chevron_right, color: Brand.s400),
            ]),
          ),
          if (hint != null)
            Container(
              color: hint.tone.bg,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Row(children: [
                Icon(hint.icon, size: 16, color: hint.tone.fg),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(hint.text,
                      style: TextStyle(fontSize: 12.5, color: hint.tone.fg)),
                ),
              ]),
            ),
        ]),
      ),
    );
  }
}

class _Meta extends StatelessWidget {
  final IconData icon;
  final String text;
  const _Meta(this.icon, this.text);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 2),
        child: Row(children: [
          Icon(icon, size: 14, color: Brand.s400),
          const SizedBox(width: 5),
          Expanded(
            child: Text(text,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall),
          ),
        ]),
      );
}
