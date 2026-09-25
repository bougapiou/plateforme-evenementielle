import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/documents.dart';
import '../../core/manage_kit.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class _EventGroup {
  final String eventId;
  final String eventNom;
  final DateTime? eventDateDebut;
  final String? lieu;
  final List<Ticket> tickets;
  _EventGroup(
    this.eventId,
    this.eventNom,
    this.eventDateDebut,
    this.lieu,
    this.tickets,
  );
}

/// Lets a guest visitor get their tickets back with just their phone number —
/// no name, no password. Enter the phone → pick the event (if they have
/// tickets for more than one) → the ticket(s) for that event download right
/// away.
class FindTicketScreen extends ConsumerStatefulWidget {
  const FindTicketScreen({super.key});

  @override
  ConsumerState<FindTicketScreen> createState() => _FindTicketScreenState();
}

class _FindTicketScreenState extends ConsumerState<FindTicketScreen> {
  final _formKey = GlobalKey<FormState>();
  String _tel = '';
  bool _busy = false;
  String? _error;
  List<_EventGroup>? _events;
  String? _downloadingEventId;
  String? _selectedEventId;

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).guestLookup(_tel.trim());
      final tickets = await ref.read(ticketsRepositoryProvider).myTickets();
      final byEvent = <String, _EventGroup>{};
      for (final t in tickets) {
        final g = byEvent[t.eventId];
        if (g != null) {
          g.tickets.add(t);
        } else {
          byEvent[t.eventId] = _EventGroup(
            t.eventId,
            t.eventNom,
            t.eventDateDebut,
            t.lieu,
            [t],
          );
        }
      }
      if (!mounted) return;
      if (byEvent.isEmpty) {
        setState(() {
          _busy = false;
          _error = 'Aucun billet trouvé pour ce numéro.';
        });
        return;
      }
      setState(() {
        _busy = false;
        _events = byEvent.values.toList();
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        if (e.code == 'ACCOUNT_EXISTS') {
          _promptLogin();
        } else {
          setState(() => _error = e.message);
        }
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = 'Aucun billet trouvé pour ce numéro.';
        });
      }
    }
  }

  Future<void> _chooseEvent(_EventGroup e) async {
    setState(() => _downloadingEventId = e.eventId);
    final api = ref.read(apiClientProvider);
    for (final t in e.tickets) {
      try {
        await fetchAndPresentDocument(
          api,
          path: '/api/tickets/${t.id}/pdf',
          filename: 'billet-${t.numero}.pdf',
        );
      } catch (_) {
        // best-effort — l'utilisateur peut retrouver ses billets via /billets
      }
    }
    if (mounted) setState(() => _downloadingEventId = null);
  }

  void _restart() {
    setState(() {
      _events = null;
      _selectedEventId = null;
      _error = null;
      _tel = '';
    });
  }

  void _promptLogin() {
    showDialog(
      context: context,
      builder:
          (ctx) => AlertDialog(
            title: const Text('Compte existant'),
            content: const Text(
              'Un compte existe déjà avec ce numéro. Connectez-vous pour '
              'retrouver vos billets.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Fermer'),
              ),
              FilledButton(
                onPressed: () {
                  Navigator.pop(ctx);
                  context.push('/connexion');
                },
                child: const Text('Se connecter'),
              ),
            ],
          ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final signedIn = ref.watch(authControllerProvider).valueOrNull != null;

    return Scaffold(
      appBar: AppBar(title: const Text('Retrouver mon billet')),
      body: MaxWidth(
        width: 520,
        child:
            signedIn
                ? EmptyState(
                  icon: Icons.verified_user_outlined,
                  title: 'Vous êtes déjà connecté',
                  subtitle: 'Vos billets se trouvent dans « Mes billets ».',
                  action: FilledButton(
                    onPressed: () => context.go('/billets'),
                    child: const Text('Voir mes billets'),
                  ),
                )
                : _events != null
                ? _buildEventList(_events!)
                : _buildPhoneForm(),
      ),
    );
  }

  Widget _buildEventList(List<_EventGroup> events) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: [
            const IconBubble(Icons.event_available_outlined, size: 44),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                "Choisissez l'événement",
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          "Votre billet se télécharge dès que vous choisissez l'événement.",
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                DropdownButtonFormField<String>(
                  value: _selectedEventId,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: 'Événement'),
                  hint: const Text('Choisir un événement'),
                  items: [
                    for (final e in events)
                      DropdownMenuItem(
                        value: e.eventId,
                        child: Text(
                          '${e.eventNom} (${e.tickets.length} billet'
                          '${e.tickets.length > 1 ? 's' : ''})',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                  onChanged:
                      _downloadingEventId != null
                          ? null
                          : (id) {
                            if (id == null) return;
                            setState(() => _selectedEventId = id);
                            _chooseEvent(
                              events.firstWhere((e) => e.eventId == id),
                            );
                          },
                ),
                if (_downloadingEventId != null) ...[
                  const SizedBox(height: 14),
                  const Row(
                    children: [
                      SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                      SizedBox(width: 10),
                      Expanded(child: Text('Téléchargement en cours…')),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        TextButton(
          onPressed: _restart,
          child: const Text('← Utiliser un autre numéro'),
        ),
      ],
    );
  }

  Widget _buildPhoneForm() {
    return Form(
      key: _formKey,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              const IconBubble(Icons.confirmation_number_outlined, size: 44),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  'Retrouvez vos billets avec votre numéro',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Indiquez le numéro de téléphone utilisé lors de votre achat ou '
            'inscription : vous retrouvez directement vos billets, sans mot '
            'de passe.',
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  PhoneField(required: true, onChanged: (v) => _tel = v),
                  if (_error != null) ...[
                    const SizedBox(height: 14),
                    InfoBanner(_error!, tone: KitTone.red),
                  ],
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: Text(_busy ? 'Recherche…' : 'Retrouver mes billets'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          TextButton(
            onPressed: () => context.push('/connexion'),
            child: const Text("J'ai déjà un compte avec mot de passe"),
          ),
        ],
      ),
    );
  }
}
