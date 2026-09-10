import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

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
  String? _structureId;
  bool? _asStructure; // null until the event is known

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
    final asStructure = _asStructure ?? true;
    if (asStructure && _structureId == null) {
      showSnack(context, 'Choisissez d\'abord une structure.', error: true);
      return;
    }
    setState(() => _busyStandId = stand.id);
    try {
      final r = await ref.read(standsRepositoryProvider).reserve(
            eventId: event.id,
            standId: stand.id,
            structureId: asStructure ? _structureId : null,
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
    final structuresAsync = ref.watch(myStructuresProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Réserver un stand')),
      body: FutureView<_Data>(
        future: _future,
        onRetry: () => setState(() => _future = _load()),
        builder: (d) => structuresAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ErrorRetry(
              error: e, onRetry: () => ref.invalidate(myStructuresProvider)),
          data: (all) {
            final verified = all.where((s) => s.verifiee).toList();
            // Default: individual only when the event opens stands to particuliers.
            _asStructure ??= !d.event.standsParticuliers;
            final asStructure = _asStructure!;
            if (asStructure && verified.isEmpty && !d.event.standsParticuliers) {
              return _NoVerifiedStructure(pending: all.isNotEmpty);
            }
            if (asStructure) {
              _structureId ??= verified.length == 1 ? verified.first.id : null;
            }
            return _body(context, d, verified);
          },
        ),
      ),
    );
  }

  Widget _body(BuildContext context, _Data d, List<StructureSummary> verified) {
    final available = d.stands.where((s) => s.disponible).toList();
    final byType = <String, List<Stand>>{};
    for (final s in available) {
      byType.putIfAbsent(s.standTypeNom, () => []).add(s);
    }

    final asStructure = _asStructure ?? true;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(d.event.nom, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 4),
        Text(
          'Le stand est bloqué 15 minutes, le temps du paiement.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 12),
        if (d.event.standsParticuliers)
          SegmentedButton<bool>(
            segments: const [
              ButtonSegment(value: false, label: Text('En mon nom')),
              ButtonSegment(value: true, label: Text('Structure')),
            ],
            selected: {asStructure},
            onSelectionChanged: (s) => setState(() => _asStructure = s.first),
          ),
        if (asStructure) ...[
          const SizedBox(height: 12),
          if (verified.isEmpty)
            Text(
              'Aucune structure vérifiée. Choisissez « En mon nom » ou gérez vos '
              'structures depuis Profil → Mes structures.',
              style: Theme.of(context).textTheme.bodySmall,
            )
          else
            DropdownButtonFormField<String>(
              value: _structureId,
              decoration: const InputDecoration(labelText: 'Réserver au nom de'),
              items: verified
                  .map((s) => DropdownMenuItem(
                        value: s.id,
                        child: Text(s.raisonSociale),
                      ))
                  .toList(),
              onChanged: (v) => setState(() => _structureId = v),
            ),
        ],
        const SizedBox(height: 16),
        if (available.isEmpty)
          const EmptyState(
            icon: Icons.storefront_outlined,
            title: 'Aucun stand disponible',
            subtitle: 'Tous les stands de cet événement sont réservés.',
          )
        else
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
                    Text(Fmt.price(type.prixMontant, type.devise)),
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
                        final blocked = asStructure && _structureId == null;
                        return OutlinedButton(
                          onPressed: (_busyStandId != null || blocked)
                              ? null
                              : () => _reserve(d.event, s),
                          child: busy
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child:
                                      CircularProgressIndicator(strokeWidth: 2))
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
  }
}

/// Shown when the user has no verified structure to book a stand with.
class _NoVerifiedStructure extends StatelessWidget {
  final bool pending;
  const _NoVerifiedStructure({required this.pending});

  @override
  Widget build(BuildContext context) {
    return EmptyState(
      icon: Icons.domain_add_outlined,
      title: pending
          ? 'Structure en attente de vérification'
          : 'Créez d\'abord une structure',
      subtitle: pending
          ? 'Un administrateur doit vérifier votre structure avant que vous '
              'puissiez réserver un stand. Vous serez notifié·e.'
          : 'La réservation de stands est réservée aux entreprises et '
              'institutions. Créez votre structure ; elle sera ensuite vérifiée '
              'par un administrateur.',
      action: Column(
        children: [
          FilledButton.icon(
            onPressed: () => context.push('/structures'),
            icon: const Icon(Icons.domain_outlined),
            label: const Text('Mes structures'),
          ),
          if (!pending)
            TextButton(
              onPressed: () => context.push('/structures/nouvelle'),
              child: const Text('Créer une structure'),
            ),
        ],
      ),
    );
  }
}
