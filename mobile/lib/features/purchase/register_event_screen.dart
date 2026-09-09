import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
import 'guest_gate.dart';
import 'structure_toggle.dart';

class RegisterEventScreen extends ConsumerStatefulWidget {
  final String slug;
  const RegisterEventScreen({super.key, required this.slug});

  @override
  ConsumerState<RegisterEventScreen> createState() =>
      _RegisterEventScreenState();
}

class _Participant {
  final nom = TextEditingController();
  final prenom = TextEditingController();
  final email = TextEditingController();
  void dispose() {
    nom.dispose();
    prenom.dispose();
    email.dispose();
  }
}

class _RegisterEventScreenState extends ConsumerState<RegisterEventScreen> {
  final _formKey = GlobalKey<FormState>();
  late Future<EventDetail> _future;
  bool _asStructure = false;
  String? _structureId;
  final _contactNom = TextEditingController();
  final _contactEmail = TextEditingController();
  final _contactTel = TextEditingController();
  final _infos = TextEditingController();
  final List<_Participant> _participants = [_Participant()];
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _future = ref.read(eventsRepositoryProvider).bySlug(widget.slug);
    final user = ref.read(authControllerProvider).valueOrNull;
    if (user != null) {
      _contactNom.text = user.fullName;
      _contactEmail.text = user.email;
    }
  }

  @override
  void dispose() {
    _contactNom.dispose();
    _contactEmail.dispose();
    _contactTel.dispose();
    _infos.dispose();
    for (final p in _participants) {
      p.dispose();
    }
    super.dispose();
  }

  Future<void> _submit(EventDetail event) async {
    if (!_formKey.currentState!.validate()) return;
    if (_asStructure && _structureId == null) {
      showSnack(context, 'Choisissez la structure.', error: true);
      return;
    }
    if (!_asStructure) {
      if (!await GuestGate.ensureSession(context, ref)) return;
      if (!mounted) return;
      final u = ref.read(authControllerProvider).valueOrNull;
      if (u != null) {
        if (_contactNom.text.trim().isEmpty) _contactNom.text = u.fullName;
        if (_contactEmail.text.trim().isEmpty) _contactEmail.text = u.email;
      }
    }
    setState(() => _submitting = true);
    try {
      final participants = _participants
          .where((p) => p.nom.text.trim().isNotEmpty)
          .map((p) => {
                'nom': p.nom.text.trim(),
                if (p.prenom.text.trim().isNotEmpty)
                  'prenom': p.prenom.text.trim(),
                if (p.email.text.trim().isNotEmpty) 'email': p.email.text.trim(),
              })
          .toList();
      final reg = await ref.read(registrationsRepositoryProvider).register(
            eventId: event.id,
            type: _asStructure ? 'STRUCTURE' : 'PARTICULIER',
            structureId: _asStructure ? _structureId : null,
            contactNom: _contactNom.text.trim(),
            contactEmail: _contactEmail.text.trim(),
            contactTelephone:
                _contactTel.text.trim().isEmpty ? null : _contactTel.text.trim(),
            informations: _infos.text.trim().isEmpty ? null : _infos.text.trim(),
            participants: participants,
          );
      if (!mounted) return;
      final isGuest = ref.read(isGuestProvider);
      showDialog(
        context: context,
        builder: (_) => AlertDialog(
          icon: const Icon(Icons.how_to_reg, color: Color(0xFF16A34A), size: 44),
          title: const Text('Inscription enregistrée'),
          content: Text([
            reg.statut == 'CONFIRMEE'
                ? 'Votre inscription (réf. ${reg.reference}) est confirmée.'
                : 'Votre inscription (réf. ${reg.reference}) est en attente de validation par l\'organisateur.',
            if (isGuest)
              '\nCréez un compte pour la retrouver facilement.',
          ].join('')),
          actions: [
            if (isGuest)
              FilledButton(
                onPressed: () {
                  Navigator.pop(context);
                  context.push('/finaliser-compte');
                },
                child: const Text('Créer un compte'),
              ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
                context.go('/activite');
              },
              child: Text(isGuest ? 'Plus tard' : 'Voir mes inscriptions'),
            ),
          ],
        ),
      );
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('S\'inscrire')),
      body: FutureView<EventDetail>(
        future: _future,
        onRetry: () => setState(() =>
            _future = ref.read(eventsRepositoryProvider).bySlug(widget.slug)),
        builder: (event) => Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(event.nom, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              if (ref.watch(isSignedInProvider))
                StructureToggle(
                  label: 'S\'inscrire au nom d\'une structure',
                  asStructure: _asStructure,
                  structureId: _structureId,
                  onModeChanged: (v) => setState(() {
                    _asStructure = v;
                    if (!v) _structureId = null;
                  }),
                  onStructureChanged: (id) => setState(() => _structureId = id),
                )
              else
                Text(
                  'Aucun compte requis. Vous pourrez en créer un après votre '
                  'inscription pour la retrouver facilement.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _contactNom,
                decoration: const InputDecoration(labelText: 'Nom du contact'),
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Requis' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _contactEmail,
                decoration: const InputDecoration(labelText: 'E-mail du contact'),
                keyboardType: TextInputType.emailAddress,
                validator: (v) =>
                    (v == null || !v.contains('@')) ? 'E-mail invalide' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _contactTel,
                decoration:
                    const InputDecoration(labelText: 'Téléphone (optionnel)'),
                keyboardType: TextInputType.phone,
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: Text('Participants',
                        style: Theme.of(context).textTheme.titleSmall),
                  ),
                  TextButton.icon(
                    onPressed: () =>
                        setState(() => _participants.add(_Participant())),
                    icon: const Icon(Icons.add),
                    label: const Text('Ajouter'),
                  ),
                ],
              ),
              ..._participants.asMap().entries.map((entry) {
                final i = entry.key;
                final p = entry.value;
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      children: [
                        Row(
                          children: [
                            Text('Participant ${i + 1}'),
                            const Spacer(),
                            if (_participants.length > 1)
                              IconButton(
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () => setState(() {
                                  p.dispose();
                                  _participants.removeAt(i);
                                }),
                              ),
                          ],
                        ),
                        TextFormField(
                          controller: p.nom,
                          decoration: const InputDecoration(labelText: 'Nom'),
                        ),
                        const SizedBox(height: 8),
                        TextFormField(
                          controller: p.prenom,
                          decoration: const InputDecoration(labelText: 'Prénom'),
                        ),
                        const SizedBox(height: 8),
                        TextFormField(
                          controller: p.email,
                          decoration:
                              const InputDecoration(labelText: 'E-mail'),
                          keyboardType: TextInputType.emailAddress,
                        ),
                      ],
                    ),
                  ),
                );
              }),
              const SizedBox(height: 12),
              TextFormField(
                controller: _infos,
                decoration: const InputDecoration(
                  labelText: 'Informations complémentaires (optionnel)',
                ),
                maxLines: 3,
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _submitting ? null : () => _submit(event),
                child: Text(
                    _submitting ? 'Envoi…' : 'Confirmer mon inscription'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
