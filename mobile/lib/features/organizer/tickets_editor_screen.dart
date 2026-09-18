import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/format.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class TicketsEditorScreen extends ConsumerStatefulWidget {
  final String eventId;
  const TicketsEditorScreen({super.key, required this.eventId});

  @override
  ConsumerState<TicketsEditorScreen> createState() =>
      _TicketsEditorScreenState();
}

class _TicketsEditorScreenState extends ConsumerState<TicketsEditorScreen> {
  late Future<List<EventTicketType>> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(organizerEventsRepositoryProvider).tickets(widget.eventId);
  }

  void _refresh() => setState(() =>
      _future = ref.read(organizerEventsRepositoryProvider).tickets(widget.eventId));

  Future<void> _edit([EventTicketType? t]) async {
    final saved = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _TicketSheet(eventId: widget.eventId, ticket: t),
    );
    if (saved == true) _refresh();
  }

  Future<void> _delete(EventTicketType t) async {
    try {
      await ref
          .read(organizerEventsRepositoryProvider)
          .deleteTicket(widget.eventId, t.id);
      _refresh();
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Billetterie')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _edit(),
        icon: const Icon(Icons.add),
        label: const Text('Catégorie'),
      ),
      body: FutureView<List<EventTicketType>>(
        future: _future,
        onRetry: _refresh,
        builder: (list) {
          if (list.isEmpty) {
            return const EmptyState(
                icon: Icons.confirmation_number_outlined,
                title: 'Aucune catégorie de billet');
          }
          return ListView.builder(
            padding: const EdgeInsets.all(12),
            itemCount: list.length,
            itemBuilder: (_, i) {
              final t = list[i];
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                child: ListTile(
                  title: Text(t.nom),
                  subtitle: Text([
                    t.prixMontant == 0 ? 'Gratuit' : Fmt.money(t.prixMontant, t.devise),
                    '${t.quantiteTotale} places',
                    if (t.portee == 'ACTIVITE') 'par activité',
                    if (!t.actif) 'inactif',
                  ].join(' · ')),
                  trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                    IconButton(
                        icon: const Icon(Icons.edit_outlined),
                        onPressed: () => _edit(t)),
                    IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => _delete(t)),
                  ]),
                ),
              );
            },
          );
        },
      ),
    );
  }
}

class _TicketSheet extends ConsumerStatefulWidget {
  final String eventId;
  final EventTicketType? ticket;
  const _TicketSheet({required this.eventId, this.ticket});

  @override
  ConsumerState<_TicketSheet> createState() => _TicketSheetState();
}

class _TicketSheetState extends ConsumerState<_TicketSheet> {
  final _nom = TextEditingController();
  final _description = TextEditingController();
  final _prix = TextEditingController(text: '0');
  final _quantite = TextEditingController(text: '100');
  final _limite = TextEditingController(text: '10');
  String _portee = 'EVENEMENT';
  bool _actif = true;
  bool _formulaireRequis = true;
  bool _saving = false;
  List<Activity> _activities = const [];
  final Set<String> _activityIds = {};

  @override
  void initState() {
    super.initState();
    final t = widget.ticket;
    if (t != null) {
      _nom.text = t.nom;
      _description.text = t.description ?? '';
      _prix.text = t.prixMontant.toString();
      _quantite.text = t.quantiteTotale.toString();
      _limite.text = t.limiteParUtilisateur.toString();
      _portee = t.portee;
      _actif = t.actif;
      _formulaireRequis = t.formulaireRequis;
      _activityIds.addAll(t.activiteIds);
    }
    ref
        .read(organizerEventsRepositoryProvider)
        .activities(widget.eventId)
        .then((a) => mounted ? setState(() => _activities = a) : null)
        .catchError((_) {});
  }

  @override
  void dispose() {
    for (final c in [_nom, _description, _prix, _quantite, _limite]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _save() async {
    if (_nom.text.trim().isEmpty) {
      showSnack(context, 'Nom requis.', error: true);
      return;
    }
    if (_portee == 'ACTIVITE' && _activityIds.isEmpty) {
      showSnack(context, 'Sélectionnez au moins une activité.', error: true);
      return;
    }
    setState(() => _saving = true);
    final body = {
      'nom': _nom.text.trim(),
      if (_description.text.trim().isNotEmpty) 'description': _description.text.trim(),
      'prixMontant': num.tryParse(_prix.text.trim()) ?? 0,
      'devise': 'XOF',
      'portee': _portee,
      'quantiteTotale': int.tryParse(_quantite.text.trim()) ?? 1,
      'limiteParUtilisateur': int.tryParse(_limite.text.trim()) ?? 1,
      'actif': _actif,
      'formulaireRequis': _formulaireRequis,
      if (_portee == 'ACTIVITE') 'activityIds': _activityIds.toList(),
    };
    try {
      await ref.read(organizerEventsRepositoryProvider).saveTicket(
            widget.eventId,
            body,
            id: widget.ticket?.id,
          );
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        top: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.ticket == null ? 'Nouvelle catégorie' : 'Modifier la catégorie',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 12),
            TextField(controller: _nom, decoration: const InputDecoration(labelText: 'Nom * (ex : Standard, VIP…)')),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(
                child: TextField(
                  controller: _prix,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Prix (FCFA)'),
                  onChanged: (_) => setState(() {}),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: _quantite,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Quantité'),
                ),
              ),
            ]),
            const SizedBox(height: 10),
            TextField(
              controller: _limite,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(labelText: 'Limite par utilisateur'),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              value: _portee,
              decoration: const InputDecoration(labelText: 'Portée'),
              items: const [
                DropdownMenuItem(value: 'EVENEMENT', child: Text('Tout l\'événement')),
                DropdownMenuItem(value: 'ACTIVITE', child: Text('Activités précises')),
              ],
              onChanged: (v) => setState(() => _portee = v ?? 'EVENEMENT'),
            ),
            if (_portee == 'ACTIVITE') ...[
              const SizedBox(height: 8),
              ..._activities.map((a) => CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    dense: true,
                    value: _activityIds.contains(a.id),
                    title: Text(a.titre),
                    onChanged: (v) => setState(() {
                      if (v == true) {
                        _activityIds.add(a.id);
                      } else {
                        _activityIds.remove(a.id);
                      }
                    }),
                  )),
            ],
            const SizedBox(height: 6),
            TextField(
              controller: _description,
              maxLines: 2,
              decoration: const InputDecoration(labelText: 'Description'),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Catégorie active (en vente)'),
              value: _actif,
              onChanged: (v) => setState(() => _actif = v),
            ),
            if ((num.tryParse(_prix.text.trim()) ?? 0) == 0)
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Demander un formulaire pour obtenir ce billet'),
                subtitle: _formulaireRequis
                    ? null
                    : const Text(
                        'Sans formulaire : juste le téléphone suffit (rien du '
                        'tout si déjà connecté).'),
                value: _formulaireRequis,
                onChanged: (v) => setState(() => _formulaireRequis = v),
              ),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: _saving ? null : _save,
              child: Text(_saving ? 'Enregistrement…' : 'Enregistrer'),
            ),
          ],
        ),
      ),
    );
  }
}
