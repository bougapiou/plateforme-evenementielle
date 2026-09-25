import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class OrganizerEventsScreen extends ConsumerStatefulWidget {
  const OrganizerEventsScreen({super.key});

  @override
  ConsumerState<OrganizerEventsScreen> createState() =>
      _OrganizerEventsScreenState();
}

class _OrganizerEventsScreenState extends ConsumerState<OrganizerEventsScreen> {
  late Future<Paged<EventSummary>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(organizerEventsRepositoryProvider).mine();
  }

  void _refresh() => setState(
      () => _future = ref.read(organizerEventsRepositoryProvider).mine());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mes événements')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          await context.push('/mes-evenements/nouveau');
          _refresh();
        },
        icon: const Icon(Icons.add),
        label: const Text('Créer'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => _refresh(),
        child: FutureView<Paged<EventSummary>>(
          future: _future,
          onRetry: _refresh,
          builder: (paged) {
            if (paged.content.isEmpty) {
              return ListView(children: const [
                SizedBox(height: 100),
                EmptyState(
                  icon: Icons.event_note_outlined,
                  title: 'Aucun événement',
                  subtitle:
                      'Créez votre premier événement. Il restera en brouillon jusqu\'à sa soumission.',
                ),
              ]);
            }
            return ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: paged.content.length,
              itemBuilder: (_, i) {
                final e = paged.content[i];
                return Card(
                  clipBehavior: Clip.antiAlias,
                  margin: const EdgeInsets.only(bottom: 10),
                  child: ListTile(
                    onTap: () async {
                      await context.push('/mes-evenements/${e.id}');
                      _refresh();
                    },
                    leading: SizedBox(
                      width: 56,
                      height: 56,
                      child: EventCover(nom: e.nom, url: e.coverUrl),
                    ),
                    title: Text(e.nom,
                        maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(Fmt.range(e.dateDebut, e.dateFin)),
                    trailing: StatusChip(e.statut),
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }
}
