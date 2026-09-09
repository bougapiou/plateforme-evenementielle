import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import '../shell/app_shell.dart';

class _Bundle {
  final EventDetail event;
  final List<EventTicketType> tickets;
  final List<StandType> standTypes;
  _Bundle(this.event, this.tickets, this.standTypes);
}

class EventDetailScreen extends ConsumerStatefulWidget {
  final String slug;
  const EventDetailScreen({super.key, required this.slug});

  @override
  ConsumerState<EventDetailScreen> createState() => _EventDetailScreenState();
}

class _EventDetailScreenState extends ConsumerState<EventDetailScreen> {
  late Future<_Bundle> _future;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<_Bundle> _load() async {
    final repo = ref.read(eventsRepositoryProvider);
    final event = await repo.bySlug(widget.slug);
    final tickets = await repo.tickets(widget.slug).catchError((_) => <EventTicketType>[]);
    final stands = event.standsActifs
        ? await repo.standTypes(widget.slug).catchError((_) => <StandType>[])
        : <StandType>[];
    return _Bundle(event, tickets, stands);
  }

  void _refresh() => setState(() => _future = _load());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Détail de l\'événement')),
      body: FutureView<_Bundle>(
        future: _future,
        onRetry: _refresh,
        builder: (b) => _Body(bundle: b, onChanged: _refresh),
      ),
    );
  }
}

class _Body extends ConsumerWidget {
  final _Bundle bundle;
  final VoidCallback onChanged;
  const _Body({required this.bundle, required this.onChanged});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final e = bundle.event;
    final payantTickets = bundle.tickets.where((t) => t.enVente).toList();
    final canRegister = e.inscriptionsOuvertes;

    return ListView(
      padding: EdgeInsets.zero,
      children: [
        if (e.coverUrl != null)
          AspectRatio(aspectRatio: 16 / 8, child: RemoteImage(url: e.coverUrl)),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
        Row(children: [
          if (e.categoryNom != null)
            Expanded(
              child: Text(e.categoryNom!.toUpperCase(),
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: Theme.of(context).colorScheme.primary,
                      )),
            ),
          StatusChip(e.statut),
        ]),
        const SizedBox(height: 6),
        Text(e.nom, style: Theme.of(context).textTheme.headlineSmall),
        if (e.organizerNom != null) ...[
          const SizedBox(height: 4),
          Text('Organisé par ${e.organizerNom}',
              style: Theme.of(context).textTheme.bodySmall),
        ],
        const SizedBox(height: 16),
        _InfoRow(Icons.calendar_today, Fmt.range(e.dateDebut, e.dateFin)),
        if (e.lieu != null || e.ville != null)
          _InfoRow(Icons.place_outlined,
              [e.lieu, e.adresse, e.ville].where((x) => x != null && x.isNotEmpty).join(', ')),
        if (e.inscriptionFin != null)
          _InfoRow(Icons.how_to_reg_outlined,
              'Inscriptions jusqu\'au ${Fmt.date(e.inscriptionFin)}'),
        if (e.contactEmail != null) _InfoRow(Icons.mail_outline, e.contactEmail!),
        if (e.contactTelephone != null)
          _InfoRow(Icons.phone_outlined, e.contactTelephone!),

        if ((e.descriptionDetaillee ?? e.descriptionCourte) != null) ...[
          const SizedBox(height: 16),
          Text(e.descriptionDetaillee ?? e.descriptionCourte!,
              style: Theme.of(context).textTheme.bodyMedium),
        ],

        if (e.conditionsParticipation != null) ...[
          const SizedBox(height: 16),
          _Section('Conditions de participation'),
          Text(e.conditionsParticipation!,
              style: Theme.of(context).textTheme.bodySmall),
        ],

        // --- Programme / activités ---
        if (e.hasActivities && e.programme.isNotEmpty) ...[
          const SizedBox(height: 20),
          _Section('Programme (${e.programme.length} activités)'),
          ...e.programme.map((a) => _ActivityTile(a)),
        ],

        // --- Intervenants ---
        if (e.intervenants.isNotEmpty) ...[
          const SizedBox(height: 20),
          _Section('Intervenants'),
          ...e.intervenants.map((s) => ListTile(
                contentPadding: EdgeInsets.zero,
                leading: CircleAvatar(
                  backgroundImage: resolveMediaUrl(s.photoUrl) != null
                      ? NetworkImage(resolveMediaUrl(s.photoUrl)!)
                      : null,
                  child: s.photoUrl == null ? const Icon(Icons.person) : null,
                ),
                title: Text(s.nom),
                subtitle: Text(
                    [s.titre, s.organisation].where((x) => x != null).join(' · ')),
              )),
        ],

        // --- Partenaires ---
        if (e.partenaires.isNotEmpty) ...[
          const SizedBox(height: 20),
          _Section('Partenaires'),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: e.partenaires.map((p) {
              final logo = resolveMediaUrl(p.logoUrl);
              return Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(
                  border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (logo != null) ...[
                      Image.network(logo,
                          height: 22,
                          errorBuilder: (_, __, ___) => const SizedBox.shrink()),
                      const SizedBox(width: 8),
                    ],
                    Text(p.nom, style: Theme.of(context).textTheme.bodySmall),
                  ],
                ),
              );
            }).toList(),
          ),
        ],

        // --- Billetterie ---
        if (payantTickets.isNotEmpty) ...[
          const SizedBox(height: 20),
          _Section('Billetterie'),
          ...payantTickets.map((t) => Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Text(t.nom),
                  subtitle: Text([
                    t.gratuit ? 'Gratuit' : t.prixFormatte,
                    if (t.portee == 'ACTIVITE' && t.activites.isNotEmpty)
                      'Activités : ${t.activites.join(', ')}',
                    '${t.quantiteRestante} place(s) restante(s)',
                  ].join('\n')),
                  isThreeLine: t.portee == 'ACTIVITE',
                ),
              )),
        ],

        // --- Stands ---
        if (e.standsActifs && bundle.standTypes.isNotEmpty) ...[
          const SizedBox(height: 20),
          _Section('Stands'),
          ...bundle.standTypes.map((s) => Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  title: Text(s.nom),
                  subtitle: Text([
                    s.prixFormatte,
                    if (s.dimensions != null) s.dimensions!,
                    '${s.quantiteRestante} disponible(s)',
                  ].join(' · ')),
                ),
              )),
        ],

        const SizedBox(height: 24),

        // --- Actions ---
        if (canRegister) ...[
          if (payantTickets.isNotEmpty)
            FilledButton.icon(
              onPressed: () => context.push('/evenements/${e.slug}/billets'),
              icon: const Icon(Icons.confirmation_number_outlined),
              label: const Text('Acheter des billets'),
            ),
          const SizedBox(height: 8),
          OutlinedButton.icon(
            onPressed: () => context.push('/evenements/${e.slug}/inscription'),
            icon: const Icon(Icons.how_to_reg_outlined),
            label: const Text('S\'inscrire à l\'événement'),
          ),
          if (e.standsActifs && bundle.standTypes.isNotEmpty) ...[
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: () => requireAuthOr(context, ref,
                  () => context.push('/evenements/${e.slug}/stands')),
              icon: const Icon(Icons.storefront_outlined),
              label: const Text('Réserver un stand'),
            ),
          ],
        ] else
          Card(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Text(e.termine
                  ? 'Cet événement est terminé.'
                  : 'Les inscriptions ne sont pas ouvertes pour cet événement.'),
            ),
          ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  const _Section(this.title);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8, top: 2),
        child: Row(
          children: [
            Container(
              width: 4,
              height: 18,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primary,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(width: 8),
            Text(title, style: Theme.of(context).textTheme.titleMedium),
          ],
        ),
      );
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String text;
  const _InfoRow(this.icon, this.text);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, size: 16, color: Theme.of(context).colorScheme.outline),
          const SizedBox(width: 10),
          Expanded(child: Text(text)),
        ]),
      );
}

class _ActivityTile extends StatelessWidget {
  final Activity a;
  const _ActivityTile(this.a);
  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      margin: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (a.imageUrl != null)
            AspectRatio(aspectRatio: 16 / 6, child: RemoteImage(url: a.imageUrl)),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(a.titre,
                    style: const TextStyle(fontWeight: FontWeight.w600)),
                const SizedBox(height: 4),
                Text([
                  if (a.dateDebut != null)
                    '${Fmt.dateTime(a.dateDebut)}${a.dateFin != null ? ' → ${Fmt.time(a.dateFin)}' : ''}',
                  if (a.salle != null) 'Salle : ${a.salle}',
                  if (a.intervenant != null) a.intervenant!,
                ].join('\n'), style: Theme.of(context).textTheme.bodySmall),
                if (a.description != null) ...[
                  const SizedBox(height: 6),
                  Text(a.description!,
                      style: Theme.of(context).textTheme.bodySmall),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
