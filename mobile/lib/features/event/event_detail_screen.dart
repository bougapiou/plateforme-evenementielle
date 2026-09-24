import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/documents.dart';
import '../../core/format.dart';
import '../../core/media.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import '../purchase/guest_gate.dart';
import '../shell/app_shell.dart';

class _Bundle {
  final EventDetail event;
  final List<EventTicketType> tickets;
  final List<StandType> standTypes;
  _Bundle(this.event, this.tickets, this.standTypes);
}

class EventDetailScreen extends ConsumerStatefulWidget {
  final String slug;

  /// Arrivée depuis « S'inscrire et prendre un billet » : on descend au formulaire.
  final bool participer;
  const EventDetailScreen({super.key, required this.slug, this.participer = false});

  @override
  ConsumerState<EventDetailScreen> createState() => _EventDetailScreenState();
}

class _EventDetailScreenState extends ConsumerState<EventDetailScreen> {
  late Future<_Bundle> _future;
  final _participateKey = GlobalKey();
  bool _scrolled = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  void _scrollToParticipation() {
    if (!widget.participer || _scrolled) return;
    _scrolled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final ctx = _participateKey.currentContext;
      if (ctx != null) {
        Scrollable.ensureVisible(ctx,
            duration: const Duration(milliseconds: 450), curve: Curves.easeInOut);
      }
    });
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
        builder: (b) {
          _scrollToParticipation();
          return _Body(
              bundle: b, onChanged: _refresh, participateKey: _participateKey);
        },
      ),
    );
  }
}

class _Body extends ConsumerWidget {
  final _Bundle bundle;
  final VoidCallback onChanged;
  final GlobalKey participateKey;
  const _Body(
      {required this.bundle,
      required this.onChanged,
      required this.participateKey});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final e = bundle.event;
    final payantTickets = bundle.tickets.where((t) => t.enVente).toList();
    final canRegister = e.inscriptionsOuvertes;

    return ListView(
      padding: EdgeInsets.zero,
      // Tout est construit d'avance pour que le défilement vers la billetterie fonctionne.
      cacheExtent: 100000,
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
          ...e.programme.map((a) => _ActivityTile(a, slug: e.slug)),
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
          SizedBox(key: participateKey, height: 20),
          _Section('Billetterie'),
          if (canRegister)
            _TicketPurchaseSection(event: e, tickets: payantTickets)
          else
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
                    Fmt.price(s.prixMontant, s.devise),
                    if (s.dimensions != null) s.dimensions!,
                    '${s.quantiteRestante} disponible(s)',
                  ].join(' · ')),
                ),
              )),
        ],

        SizedBox(
            key: payantTickets.isEmpty ? participateKey : null, height: 24),

        // --- Actions ---
        if (canRegister) ...[
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

class _ActivityTile extends ConsumerStatefulWidget {
  final Activity a;
  final String slug;
  const _ActivityTile(this.a, {required this.slug});
  @override
  ConsumerState<_ActivityTile> createState() => _ActivityTileState();
}

class _ActivityTileState extends ConsumerState<_ActivityTile> {
  bool _busy = false;
  bool _joined = false;
  String? _error;

  Future<void> _participate() async {
    final signedIn = ref.read(authControllerProvider).valueOrNull != null;
    if (!signedIn) {
      context.push('/connexion');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(ticketsRepositoryProvider).attendActivity(widget.a.id);
      if (mounted) setState(() => _joined = true);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.a;
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
                Row(
                  children: [
                    Expanded(
                      child: Text(a.titre,
                          style: const TextStyle(fontWeight: FontWeight.w600)),
                    ),
                    if (a.gratuit)
                      const _AccessChip('Gratuit', Color(0xFF16A34A))
                    else if (a.payant)
                      const _AccessChip('Payant', Color(0xFFB45309)),
                  ],
                ),
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
                if (a.gratuit) ...[
                  const SizedBox(height: 8),
                  if (_joined)
                    Text('Vous participez — billet dans « Mes billets ».',
                        style: Theme.of(context)
                            .textTheme
                            .bodySmall
                            ?.copyWith(color: const Color(0xFF16A34A)))
                  else
                    OutlinedButton(
                      onPressed: _busy ? null : _participate,
                      child: Text(_busy ? 'Un instant…' : 'Participer (billet gratuit)'),
                    ),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(_error!,
                          style: const TextStyle(color: Colors.red, fontSize: 12)),
                    ),
                ] else if (a.payant) ...[
                  const SizedBox(height: 6),
                  Text('Billet requis — voir la billetterie ci-dessous.',
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

class _AccessChip extends StatelessWidget {
  final String label;
  final Color color;
  const _AccessChip(this.label, this.color);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(label,
            style: TextStyle(
                color: color, fontSize: 11, fontWeight: FontWeight.w600)),
      );
}

/// Buy tickets right on the event page — no separate screen. A single
/// category is auto-selected (and its quantity hidden if free, since a free
/// category is capped at 1 per person); with several categories, the list
/// below acts as the picker. On success: free tickets are issued and their
/// PDF downloaded immediately; paid ones go through the payment screen,
/// which downloads them automatically once payment is confirmed.
class _TicketPurchaseSection extends ConsumerStatefulWidget {
  final EventDetail event;
  final List<EventTicketType> tickets;
  const _TicketPurchaseSection({required this.event, required this.tickets});

  @override
  ConsumerState<_TicketPurchaseSection> createState() =>
      _TicketPurchaseSectionState();
}

class _TicketPurchaseSectionState
    extends ConsumerState<_TicketPurchaseSection> {
  final Map<String, int> _qty = {};
  bool _submitting = false;
  String? _error;
  List<Ticket>? _issued;

  bool get _single => widget.tickets.length == 1;
  bool get _singleFree => _single && widget.tickets.first.gratuit;

  @override
  void initState() {
    super.initState();
    if (_single) _qty[widget.tickets.first.id] = 1;
  }

  num get _total {
    num sum = 0;
    for (final t in widget.tickets) {
      sum += t.prixMontant * (_qty[t.id] ?? 0);
    }
    return sum;
  }

  int get _count => _qty.values.fold(0, (a, b) => a + b);

  /// The organizer opted out of a form for this free category: only a phone
  /// prompt (or nothing at all if already signed in) stands between a scan
  /// and the ticket.
  bool get _noForm => _singleFree && !widget.tickets.first.formulaireRequis;

  Future<String?> _promptPhone() {
    String phone = '';
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Votre téléphone'),
        content: PhoneField(
          required: true,
          onChanged: (v) => phone = v,
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Annuler')),
          FilledButton(
            onPressed: () {
              if (phone.trim().isEmpty) return;
              Navigator.pop(ctx, phone.trim());
            },
            child: const Text('Continuer'),
          ),
        ],
      ),
    );
  }

  Future<void> _submit() async {
    final lignes = {
      for (final e in _qty.entries)
        if (e.value > 0) e.key: e.value,
    };
    if (lignes.isEmpty) return;

    final signedIn = ref.read(authControllerProvider).valueOrNull != null;
    String? quickPhone;
    if (!signedIn) {
      if (_noForm) {
        quickPhone = await _promptPhone();
        if (quickPhone == null) return; // cancelled
      } else if (!await GuestGate.ensureSession(context, ref)) {
        return;
      }
    }
    if (!mounted) return;

    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      if (quickPhone != null) {
        await ref.read(authControllerProvider.notifier).guestSessionQuick(quickPhone);
      }
      final order = await ref
          .read(ticketsRepositoryProvider)
          .createOrder(eventId: widget.event.id, lignes: lignes);
      if (!mounted) return;
      if (order.montantTotal == 0) {
        await _downloadIssued(order.reference);
      } else {
        context.push('/paiement/TICKET_ORDER/${order.id}');
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _downloadIssued(String orderReference) async {
    final all = await ref.read(ticketsRepositoryProvider).myTickets();
    final mine =
        all.where((t) => t.orderReference == orderReference).toList();
    if (mounted) setState(() => _issued = mine);
    final api = ref.read(apiClientProvider);
    for (final t in mine) {
      try {
        await fetchAndPresentDocument(api,
            path: '/api/tickets/${t.id}/pdf',
            filename: 'billet-${t.numero}.pdf');
      } catch (_) {
        // best-effort auto-download — the button below covers a failure
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_issued != null) {
      return Card(
        color: const Color(0xFFE8F5E9),
        margin: const EdgeInsets.only(bottom: 8),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Billet obtenu',
                  style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF15803D))),
              const SizedBox(height: 4),
              Text(
                'Téléchargé automatiquement. Pas besoin de l\'imprimer, la '
                'version numérique suffit.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              for (final t in _issued!)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Row(
                    children: [
                      Expanded(child: Text('Billet ${t.numero}')),
                      TextButton(
                        onPressed: () => fetchAndPresentDocument(
                          ref.read(apiClientProvider),
                          path: '/api/tickets/${t.id}/pdf',
                          filename: 'billet-${t.numero}.pdf',
                        ),
                        child: const Text('Retélécharger'),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_singleFree)
          Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(widget.tickets.first.nom,
                      style: const TextStyle(fontWeight: FontWeight.w600)),
                  const SizedBox(height: 2),
                  const Text('Gratuit'),
                  if (_noForm) ...[
                    const SizedBox(height: 4),
                    Text(
                      'Juste votre téléphone suffit — pas de compte, pas '
                      'd\'autre champ à remplir.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ],
              ),
            ),
          )
        else
          ...widget.tickets.map((t) => _TicketRow(
                ticket: t,
                quantity: _qty[t.id] ?? 0,
                onChanged: (v) => setState(() => _qty[t.id] = v),
              )),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(_error!, style: const TextStyle(color: Colors.red)),
          ),
        FilledButton(
          onPressed: _submitting || _count == 0 ? null : _submit,
          child: Text(_submitting
              ? 'Un instant…'
              : _singleFree
                  ? 'Obtenir mon billet (Gratuit)'
                  : 'Commander ($_count — ${Fmt.price(_total)})'),
        ),
      ],
    );
  }
}

class _TicketRow extends StatelessWidget {
  final EventTicketType ticket;
  final int quantity;
  final ValueChanged<int> onChanged;
  const _TicketRow({
    required this.ticket,
    required this.quantity,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final max = ticket.limiteParUtilisateur > 0
        ? ticket.limiteParUtilisateur
        : ticket.quantiteRestante;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(ticket.nom,
                style: const TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            Text(ticket.gratuit ? 'Gratuit' : ticket.prixFormatte,
                style: Theme.of(context).textTheme.bodyMedium),
            if (ticket.description != null) ...[
              const SizedBox(height: 4),
              Text(ticket.description!,
                  style: Theme.of(context).textTheme.bodySmall),
            ],
            if (ticket.portee == 'ACTIVITE' && ticket.activites.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text('Activités : ${ticket.activites.join(', ')}',
                  style: Theme.of(context).textTheme.bodySmall),
            ],
            const SizedBox(height: 8),
            Row(
              children: [
                Text('${ticket.quantiteRestante} restant(s)',
                    style: Theme.of(context).textTheme.bodySmall),
                const Spacer(),
                IconButton.outlined(
                  onPressed:
                      quantity > 0 ? () => onChanged(quantity - 1) : null,
                  icon: const Icon(Icons.remove),
                ),
                SizedBox(
                  width: 36,
                  child: Text('$quantity', textAlign: TextAlign.center),
                ),
                IconButton.outlined(
                  onPressed:
                      quantity < max ? () => onChanged(quantity + 1) : null,
                  icon: const Icon(Icons.add),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
