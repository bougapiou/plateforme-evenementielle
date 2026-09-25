import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
import '../../core/format.dart';
import '../../core/manage_kit.dart';
import '../../core/media.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import 'event_status_hint.dart';

/// Statuses in which the event is on the public site (sales, live attendance and control make sense).
const _publicStatuts = {
  'PUBLIE',
  'INSCRIPTIONS_OUVERTES',
  'INSCRIPTIONS_FERMEES',
  'EN_COURS',
  'TERMINE',
};

class EventManageScreen extends ConsumerStatefulWidget {
  final String eventId;
  const EventManageScreen({super.key, required this.eventId});

  @override
  ConsumerState<EventManageScreen> createState() => _EventManageScreenState();
}

class _EventManageScreenState extends ConsumerState<EventManageScreen> {
  late Future<EventFull> _future;
  Future<EventStats?>? _stats;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<EventFull> _load() async {
    final repo = ref.read(organizerEventsRepositoryProvider);
    final e = await repo.get(widget.eventId);
    // figures only mean something once the event is public; a failure just hides them
    _stats = _publicStatuts.contains(e.statut)
        ? repo.stats(e.id).then<EventStats?>((s) => s).catchError((_) => null)
        : null;
    return e;
  }

  void _refresh() => setState(() => _future = _load());

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
        title: const Text('Supprimer ce brouillon ?'),
        content: const Text('Cette action est irréversible.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Non')),
          FilledButton(
              style: FilledButton.styleFrom(backgroundColor: Brand.red),
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Supprimer')),
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

  Future<void> _open(String path) async {
    await context.push(path);
    _refresh();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Gérer l'événement")),
      body: FutureView<EventFull>(
        future: _future,
        onRetry: _refresh,
        builder: (e) => RefreshIndicator(
          onRefresh: () async => _refresh(),
          child: MaxWidth(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
              children: [
                _Hero(event: e),
                if (e.motifRefus != null) ...[
                  const SizedBox(height: 12),
                  InfoBanner('Refusé : ${e.motifRefus}', tone: KitTone.red),
                ],
                if (_stats != null) _statsBlock(e),
                const SizedBox(height: 16),
                _lifecycle(e),
                const SizedBox(height: 16),
                _configuration(e),
                const SizedBox(height: 16),
                _onSite(e),
                if (e.statut == 'BROUILLON' || e.statut == 'REFUSE') ...[
                  const SizedBox(height: 20),
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Brand.red,
                      side: const BorderSide(color: Brand.red),
                    ),
                    onPressed: _delete,
                    icon: const Icon(Icons.delete_outline),
                    label: const Text('Supprimer le brouillon'),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _statsBlock(EventFull e) {
    return FutureBuilder<EventStats?>(
      future: _stats,
      builder: (context, snap) {
        final s = snap.data;
        if (s == null) return const SizedBox.shrink();
        return Padding(
          padding: const EdgeInsets.only(top: 12),
          child: KpiGrid(tiles: [
            KpiTile(
              icon: Icons.confirmation_number_outlined,
              label: 'Billets vendus',
              value: s.billetsTotal > 0
                  ? '${s.billetsVendus} / ${s.billetsTotal}'
                  : '${s.billetsVendus}',
              tone: KitTone.green,
              progress: s.billetsTotal > 0 ? s.billetsVendus / s.billetsTotal : null,
            ),
            KpiTile(
              icon: Icons.payments_outlined,
              label: 'Revenus',
              value: Fmt.money(s.revenus),
              tone: KitTone.amber,
            ),
            KpiTile(
              icon: Icons.how_to_reg_outlined,
              label: 'Inscriptions',
              value: '${s.inscriptionsConfirmees}',
              caption: s.inscriptionsEnAttente > 0
                  ? '+${s.inscriptionsEnAttente} en attente'
                  : null,
              tone: KitTone.blue,
            ),
            if (e.standsActifs)
              KpiTile(
                icon: Icons.storefront_outlined,
                label: 'Stands réservés',
                value: '${s.standsReserves} / ${s.standsTotal}',
                tone: KitTone.violet,
                progress: s.standsTotal > 0 ? s.standsReserves / s.standsTotal : null,
              )
            else
              KpiTile(
                icon: Icons.login,
                label: 'Entrées validées',
                value: '${s.entreesValidees}',
                tone: KitTone.slate,
              ),
          ]),
        );
      },
    );
  }

  /// What comes next for this event, with the action that gets it there.
  Widget _lifecycle(EventFull e) {
    final repo = ref.read(organizerEventsRepositoryProvider);
    final hint = organizerHint(e.statut);
    final actions = <Widget>[
      if (e.soumissible)
        FilledButton.icon(
          onPressed: _busy
              ? null
              : () => _workflow(
                  () => repo.submit(e.id), 'Événement soumis à validation.'),
          icon: const Icon(Icons.send_outlined),
          label: const Text('Soumettre à validation'),
        ),
      if (e.statut == 'VALIDE')
        FilledButton.icon(
          onPressed: _busy
              ? null
              : () => _workflow(() => repo.publish(e.id), 'Événement publié.'),
          icon: const Icon(Icons.public),
          label: const Text('Publier'),
        ),
      if (e.statut == 'PUBLIE' || e.statut == 'INSCRIPTIONS_FERMEES')
        OutlinedButton.icon(
          onPressed: _busy
              ? null
              : () => _workflow(
                  () => repo.openRegistrations(e.id), 'Inscriptions ouvertes.'),
          icon: const Icon(Icons.lock_open),
          label: const Text('Ouvrir les inscriptions'),
        ),
      if (e.statut == 'PUBLIE' || e.statut == 'INSCRIPTIONS_OUVERTES')
        OutlinedButton.icon(
          onPressed: _busy
              ? null
              : () => _workflow(
                  () => repo.closeRegistrations(e.id), 'Inscriptions fermées.'),
          icon: const Icon(Icons.lock_outline),
          label: const Text('Fermer les inscriptions'),
        ),
    ];
    if (hint == null && actions.isEmpty) return const SizedBox.shrink();
    return SectionCard(
      title: 'Prochaine étape',
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
      children: [
        Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          if (hint != null) InfoBanner(hint.text, tone: hint.tone, icon: hint.icon),
          for (final a in actions) ...[const SizedBox(height: 10), a],
        ]),
      ],
    );
  }

  Widget _configuration(EventFull e) {
    // administrators may edit an event in any status
    final locked =
        !e.modifiable && !ref.watch(hasPermissionProvider('EVENT_VALIDATE'));
    return SectionCard(title: 'Configuration', children: [
      ActionTile(
        icon: Icons.info_outline,
        title: 'Informations générales',
        subtitle: locked
            ? 'Consultation uniquement dans cet état'
            : 'Nom, dates, lieu, visuels…',
        badge: locked ? 'Verrouillé' : null,
        tone: KitTone.green,
        onTap: () => _open('/mes-evenements/${e.id}/infos'),
      ),
      if (e.hasActivities)
        ActionTile(
          icon: Icons.event_note_outlined,
          title: 'Programme',
          subtitle: 'Activités et créneaux, avec visuel',
          tone: KitTone.blue,
          onTap: () => _open('/mes-evenements/${e.id}/programme'),
        ),
      ActionTile(
        icon: Icons.confirmation_number_outlined,
        title: 'Billetterie',
        subtitle: 'Catégories de billets, quotas, prix',
        tone: KitTone.amber,
        onTap: () => _open('/mes-evenements/${e.id}/billetterie'),
      ),
      if (e.standsActifs)
        ActionTile(
          icon: Icons.storefront_outlined,
          title: 'Stands',
          subtitle: 'Types de stands et quantités',
          tone: KitTone.violet,
          onTap: () => _open('/mes-evenements/${e.id}/stands'),
        ),
      ActionTile(
        icon: Icons.record_voice_over_outlined,
        title: 'Intervenants',
        subtitle: 'Conférenciers et invités',
        tone: KitTone.slate,
        onTap: () => _open('/mes-evenements/${e.id}/intervenants'),
      ),
      ActionTile(
        icon: Icons.handshake_outlined,
        title: 'Partenaires',
        subtitle: 'Sponsors et institutions',
        tone: KitTone.slate,
        onTap: () => _open('/mes-evenements/${e.id}/partenaires'),
      ),
    ]);
  }

  Widget _onSite(EventFull e) {
    final live = _publicStatuts.contains(e.statut) && e.statut != 'TERMINE';
    return SectionCard(title: 'Sur place', children: [
      ActionTile(
        icon: Icons.badge_outlined,
        title: 'Accréditations et badges',
        subtitle: 'Conférenciers, exposants, presse, staff (QR + PDF)',
        tone: KitTone.violet,
        onTap: () => _open('/mes-evenements/${e.id}/accreditations'),
      ),
      if (live)
        ActionTile(
          icon: Icons.qr_code_scanner,
          title: 'Contrôle des accès',
          subtitle: 'Scanner les QR codes des billets',
          tone: KitTone.green,
          onTap: () =>
              context.push('/scanner/${e.id}?nom=${Uri.encodeComponent(e.nom)}&slug=${Uri.encodeComponent(e.slug)}'),
        ),
      if (_publicStatuts.contains(e.statut))
        ActionTile(
          icon: Icons.sensors,
          title: 'Présence en direct',
          subtitle: 'Entrées, sorties, présents : billets et capteurs',
          tone: KitTone.blue,
          onTap: () => context.push('/presence-en-direct/${e.slug}'),
        ),
    ]);
  }
}

/// The event's cover with its name, dates and place on a dark gradient, and its status pinned on top.
class _Hero extends StatelessWidget {
  final EventFull event;
  const _Hero({required this.event});

  @override
  Widget build(BuildContext context) {
    final place = [event.ville, event.lieu]
        .whereType<String>()
        .where((s) => s.isNotEmpty)
        .join(' · ');
    return ClipRRect(
      borderRadius: BorderRadius.circular(18),
      child: Stack(children: [
        AspectRatio(
          aspectRatio: 16 / 9,
          child: EventCover(nom: event.nom, url: event.coverUrl),
        ),
        const Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [Color(0x00000000), Color(0xD9000000)],
                stops: [.3, 1],
              ),
            ),
          ),
        ),
        Positioned(top: 12, right: 12, child: StatusChip(event.statut)),
        Positioned(
          left: 16,
          right: 16,
          bottom: 14,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(event.nom,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      height: 1.2,
                      fontWeight: FontWeight.w800)),
              const SizedBox(height: 6),
              _line(Icons.calendar_today_outlined,
                  Fmt.rangeShort(event.dateDebut, event.dateFin)),
              if (place.isNotEmpty) _line(Icons.place_outlined, place),
            ],
          ),
        ),
      ]),
    );
  }

  Widget _line(IconData icon, String text) => Padding(
        padding: const EdgeInsets.only(top: 2),
        child: Row(children: [
          Icon(icon, size: 14, color: Colors.white70),
          const SizedBox(width: 6),
          Expanded(
            child: Text(text,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: Colors.white70, fontSize: 13)),
          ),
        ]),
      );
}
