import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import 'structure_toggle.dart';

class ReserveStandScreen extends ConsumerStatefulWidget {
  final String slug;
  const ReserveStandScreen({super.key, required this.slug});

  @override
  ConsumerState<ReserveStandScreen> createState() => _ReserveStandScreenState();
}

class _Data {
  final EventDetail event;
  final List<StandType> types;
  final List<Stand> stands;
  _Data(this.event, this.types, this.stands);
}

class _ReserveStandScreenState extends ConsumerState<ReserveStandScreen> {
  late Future<_Data> _future;
  String? _busyStandId;
  bool _asStructure = false;
  String? _structureId;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_Data> _load() async {
    final repo = ref.read(eventsRepositoryProvider);
    final event = await repo.bySlug(widget.slug);
    final types = await repo.standTypes(widget.slug).catchError((_) => <StandType>[]);
    final stands = await repo.stands(widget.slug).catchError((_) => <Stand>[]);
    return _Data(event, types, stands);
  }

  Future<void> _reserve(EventDetail event, Stand stand) async {
    if (_asStructure && _structureId == null) {
      showSnack(context, 'Choisissez la structure.', error: true);
      return;
    }
    setState(() => _busyStandId = stand.id);
    try {
      final r = await ref.read(standsRepositoryProvider).reserve(
            eventId: event.id,
            standId: stand.id,
            structureId: _asStructure ? _structureId : null,
          );
      if (!mounted) return;
      showSnack(context,
          'Stand ${stand.numero} bloqué. Finalisez le paiement sous 15 min.');
      context.push('/paiement/STAND_RESERVATION/${r.id}');
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busyStandId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Réserver un stand')),
      body: FutureView<_Data>(
        future: _future,
        onRetry: () => setState(() => _future = _load()),
        builder: (d) {
          final available = d.stands.where((s) => s.disponible).toList();
          if (available.isEmpty) {
            return const EmptyState(
              icon: Icons.storefront_outlined,
              title: 'Aucun stand disponible',
              subtitle: 'Tous les stands de cet événement sont réservés.',
            );
          }
          // group by type name
          final byType = <String, List<Stand>>{};
          for (final s in available) {
            byType.putIfAbsent(s.standTypeNom, () => []).add(s);
          }
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(d.event.nom,
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                'Sélectionnez un stand. Il sera bloqué 15 minutes, le temps du paiement.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 8),
              StructureToggle(
                label: 'Réserver au nom d\'une structure',
                asStructure: _asStructure,
                structureId: _structureId,
                onModeChanged: (v) => setState(() {
                  _asStructure = v;
                  if (!v) _structureId = null;
                }),
                onStructureChanged: (id) => setState(() => _structureId = id),
              ),
              const SizedBox(height: 16),
              ...byType.entries.map((entry) {
                final type = d.types.firstWhere(
                  (t) => t.nom == entry.key,
                  orElse: () => StandType(
                    id: '',
                    nom: entry.key,
                    prixMontant: entry.value.first.prixMontant,
                    devise: 'XOF',
                    prixFormatte: entry.value.first.prixFormatte,
                    quantiteRestante: entry.value.length,
                  ),
                );
                return Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(type.nom,
                            style: const TextStyle(
                                fontWeight: FontWeight.w600, fontSize: 16)),
                        Text(type.prixFormatte),
                        if (type.dimensions != null)
                          Text(type.dimensions!,
                              style: Theme.of(context).textTheme.bodySmall),
                        if (type.equipements != null)
                          Text(type.equipements!,
                              style: Theme.of(context).textTheme.bodySmall),
                        const SizedBox(height: 10),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: entry.value.map((s) {
                            final busy = _busyStandId == s.id;
                            return OutlinedButton(
                              onPressed: _busyStandId != null
                                  ? null
                                  : () => _reserve(d.event, s),
                              child: busy
                                  ? const SizedBox(
                                      width: 16,
                                      height: 16,
                                      child: CircularProgressIndicator(
                                          strokeWidth: 2))
                                  : Text('Stand ${s.numero}'),
                            );
                          }).toList(),
                        ),
                      ],
                    ),
                  ),
                );
              }),
            ],
          );
        },
      ),
    );
  }
}
