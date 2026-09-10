import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class EventManageScreen extends ConsumerStatefulWidget {
  final String eventId;
  const EventManageScreen({super.key, required this.eventId});

  @override
  ConsumerState<EventManageScreen> createState() => _EventManageScreenState();
}

class _EventManageScreenState extends ConsumerState<EventManageScreen> {
  late Future<EventFull> _future;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _future = ref.read(organizerEventsRepositoryProvider).get(widget.eventId);
  }

  void _refresh() => setState(() =>
      _future = ref.read(organizerEventsRepositoryProvider).get(widget.eventId));

  Future<void> _workflow(Future<EventFull> Function() action, String ok) async {
    setState(() => _busy = true);
    try {
      await action();
      if (mounted) showSnack(context, ok);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        content: const Text('Supprimer ce brouillon ? Action irréversible.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Non')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Supprimer')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await ref.read(organizerEventsRepositoryProvider).delete(widget.eventId);
      if (mounted) {
        showSnack(context, 'Événement supprimé.');
        context.pop();
      }
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final repo = ref.read(organizerEventsRepositoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Gérer l\'événement')),
      body: FutureView<EventFull>(
        future: _future,
        onRetry: _refresh,
        builder: (e) => ListView(
          padding: EdgeInsets.zero,
          children: [
            if (e.coverUrl != null)
              AspectRatio(aspectRatio: 16 / 7, child: RemoteImage(url: e.coverUrl)),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Expanded(
                      child: Text(e.nom,
                          style: Theme.of(context).textTheme.titleLarge),
                    ),
                    StatusChip(e.statut),
                  ]),
                  const SizedBox(height: 4),
                  Text(Fmt.range(e.dateDebut, e.dateFin),
                      style: Theme.of(context).textTheme.bodySmall),
                  if (e.motifRefus != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Card(
                        color: const Color(0xFFFEE2E2),
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Text('Refusé : ${e.motifRefus}'),
                        ),
                      ),
                    ),
                  const SizedBox(height: 16),

                  _section('Configuration'),
                  _tile(Icons.info_outline, 'Informations générales',
                      e.modifiable
                          ? 'Modifier nom, dates, lieu, image…'
                          : 'Non modifiable dans cet état',
                      onTap: () async {
                    await context.push('/mes-evenements/${e.id}/infos');
                    _refresh();
                  }),
                  if (e.hasActivities)
                    _tile(Icons.event_note_outlined, 'Programme (activités)',
                        'Ajouter des créneaux avec visuel', onTap: () async {
                      await context.push('/mes-evenements/${e.id}/programme');
                      _refresh();
                    }),
                  _tile(Icons.confirmation_number_outlined, 'Billetterie',
                      'Catégories de billets, quotas, prix', onTap: () async {
                    await context.push('/mes-evenements/${e.id}/billetterie');
                    _refresh();
                  }),
                  if (e.standsActifs)
                    _tile(Icons.storefront_outlined, 'Stands',
                        'Types de stands et quantités', onTap: () async {
                      await context.push('/mes-evenements/${e.id}/stands');
                      _refresh();
                    }),
                  _tile(Icons.record_voice_over_outlined, 'Intervenants', null,
                      onTap: () async {
                    await context.push('/mes-evenements/${e.id}/intervenants');
                    _refresh();
                  }),
                  _tile(Icons.handshake_outlined, 'Partenaires', null,
                      onTap: () async {
                    await context.push('/mes-evenements/${e.id}/partenaires');
                    _refresh();
                  }),
                  _tile(Icons.badge_outlined, 'Accréditations / badges',
                      'Conférenciers, exposants, presse, staff… (QR + PDF)',
                      onTap: () async {
                    await context.push('/mes-evenements/${e.id}/accreditations');
                    _refresh();
                  }),

                  const SizedBox(height: 20),
                  _section('Cycle de vie'),
                  const SizedBox(height: 4),
                  if (e.soumissible)
                    FilledButton.icon(
                      onPressed: _busy
                          ? null
                          : () => _workflow(
                              () => repo.submit(e.id), 'Événement soumis à validation.'),
                      icon: const Icon(Icons.send_outlined),
                      label: const Text('Soumettre à validation'),
                    ),
                  if (e.statut == 'SOUMIS')
                    const _InfoBox('En attente de validation par un administrateur.'),
                  if (e.statut == 'VALIDE') ...[
                    const _InfoBox('Validé. Vous pouvez publier l\'événement.'),
                    const SizedBox(height: 8),
                    FilledButton.icon(
                      onPressed: _busy
                          ? null
                          : () => _workflow(
                              () => repo.publish(e.id), 'Événement publié.'),
                      icon: const Icon(Icons.public),
                      label: const Text('Publier'),
                    ),
                  ],
                  if (e.statut == 'PUBLIE' || e.statut == 'INSCRIPTIONS_FERMEES') ...[
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: _busy
                          ? null
                          : () => _workflow(() => repo.openRegistrations(e.id),
                              'Inscriptions ouvertes.'),
                      icon: const Icon(Icons.lock_open),
                      label: const Text('Ouvrir les inscriptions'),
                    ),
                  ],
                  if (e.statut == 'PUBLIE' || e.statut == 'INSCRIPTIONS_OUVERTES') ...[
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: _busy
                          ? null
                          : () => _workflow(() => repo.closeRegistrations(e.id),
                              'Inscriptions fermées.'),
                      icon: const Icon(Icons.lock_outline),
                      label: const Text('Fermer les inscriptions'),
                    ),
                  ],
                  if (e.statut == 'PUBLIE' ||
                      e.statut == 'INSCRIPTIONS_OUVERTES' ||
                      e.statut == 'INSCRIPTIONS_FERMEES' ||
                      e.statut == 'EN_COURS')
                    Padding(
                      padding: const EdgeInsets.only(top: 12),
                      child: OutlinedButton.icon(
                        onPressed: () => context.push('/scanner/${e.id}?nom=${Uri.encodeComponent(e.nom)}'),
                        icon: const Icon(Icons.qr_code_scanner),
                        label: const Text('Contrôle à l\'entrée'),
                      ),
                    ),
                  if (e.statut == 'BROUILLON' || e.statut == 'REFUSE') ...[
                    const SizedBox(height: 16),
                    TextButton(
                      onPressed: _delete,
                      child: const Text('Supprimer le brouillon'),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _section(String t) => Text(t.toUpperCase(),
      style: Theme.of(context).textTheme.labelSmall);

  Widget _tile(IconData icon, String title, String? subtitle,
          {required VoidCallback onTap}) =>
      ListTile(
        contentPadding: EdgeInsets.zero,
        leading: Icon(icon),
        title: Text(title),
        subtitle: subtitle != null ? Text(subtitle) : null,
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      );
}

class _InfoBox extends StatelessWidget {
  final String text;
  const _InfoBox(this.text);
  @override
  Widget build(BuildContext context) => Card(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Padding(padding: const EdgeInsets.all(12), child: Text(text)),
      );
}
