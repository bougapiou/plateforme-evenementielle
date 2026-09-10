import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';
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

  // Identity of an anonymous visitor (also used as the registration contact).
  final _prenom = TextEditingController();
  final _nom = TextEditingController();
  final _email = TextEditingController();
  // Contact when registering for a structure (the representative).
  final _contactNom = TextEditingController();
  final _contactEmail = TextEditingController();
  // Phone — optional, used in every mode.
  final _tel = TextEditingController();
  final _infos = TextEditingController();

  /// Extra attendees. Empty = the account holder is the sole participant.
  final List<_Participant> _participants = [];
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _future = ref.read(eventsRepositoryProvider).bySlug(widget.slug);
  }

  @override
  void dispose() {
    _prenom.dispose();
    _nom.dispose();
    _email.dispose();
    _contactNom.dispose();
    _contactEmail.dispose();
    _tel.dispose();
    _infos.dispose();
    for (final p in _participants) {
      p.dispose();
    }
    super.dispose();
  }

  List<Map<String, String>> _collectParticipants() => _participants
      .where((p) => p.nom.text.trim().isNotEmpty)
      .map((p) => {
            'nom': p.nom.text.trim(),
            if (p.prenom.text.trim().isNotEmpty) 'prenom': p.prenom.text.trim(),
            if (p.email.text.trim().isNotEmpty) 'email': p.email.text.trim(),
          })
      .toList();

  Future<void> _submit(EventDetail event) async {
    if (!_formKey.currentState!.validate()) return;
    if (_asStructure && _structureId == null) {
      showSnack(context, 'Choisissez la structure.', error: true);
      return;
    }
    final participants = _collectParticipants();
    if (_asStructure && participants.isEmpty) {
      showSnack(context, 'Ajoutez au moins un participant.', error: true);
      return;
    }

    final signedIn = ref.read(isSignedInProvider);
    final isGuest = ref.read(isGuestProvider);

    setState(() => _submitting = true);
    try {
      // An anonymous visitor: open a passwordless guest session from the form.
      if (!signedIn && !isGuest && !_asStructure) {
        try {
          await ref.read(authControllerProvider.notifier).guestSession(
                firstName: _prenom.text.trim(),
                lastName: _nom.text.trim(),
                phone: _tel.text.trim(),
                email: _email.text.trim().isEmpty ? null : _email.text.trim(),
              );
        } on ApiException catch (e) {
          if (!mounted) return;
          setState(() => _submitting = false);
          if (e.code == 'ACCOUNT_EXISTS') {
            _promptLogin();
          } else {
            showSnack(context, e.message, error: true);
          }
          return;
        }
      }

      final user = ref.read(authControllerProvider).valueOrNull;
      final contactNom = _asStructure
          ? _contactNom.text.trim()
          : (user?.fullName ?? '${_prenom.text.trim()} ${_nom.text.trim()}'.trim());
      final rawEmail = _asStructure
          ? _contactEmail.text.trim()
          : (user?.email ?? _email.text.trim());
      final contactEmail =
          rawEmail.endsWith('@guest.plateforme.local') ? '' : rawEmail;
      // Backend falls back to the account phone when this is empty.
      final contactTel = _tel.text.trim();

      final reg = await ref.read(registrationsRepositoryProvider).register(
            eventId: event.id,
            type: _asStructure ? 'STRUCTURE' : 'PARTICULIER',
            structureId: _asStructure ? _structureId : null,
            contactNom: contactNom,
            contactEmail: contactEmail.isEmpty ? null : contactEmail,
            contactTelephone: contactTel.isEmpty ? null : contactTel,
            informations: _infos.text.trim().isEmpty ? null : _infos.text.trim(),
            participants: participants,
          );
      if (!mounted) return;
      _showSuccess(reg);
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _promptLogin() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Compte existant'),
        content: const Text(
            'Un compte existe déjà avec cet e-mail. Connectez-vous pour vous '
            'inscrire et retrouver vos billets.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Fermer')),
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

  void _showSuccess(Registration reg) {
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
          if (isGuest) '\n\nCréez un compte pour la retrouver facilement.',
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
  }

  @override
  Widget build(BuildContext context) {
    final signedIn = ref.watch(isSignedInProvider);
    final isGuest = ref.watch(isGuestProvider);
    final user = ref.watch(authControllerProvider).valueOrNull;
    final anonymous = !signedIn && !isGuest;

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
              const SizedBox(height: 12),

              if (signedIn)
                StructureToggle(
                  label: 'S\'inscrire au nom d\'une structure',
                  asStructure: _asStructure,
                  structureId: _structureId,
                  onModeChanged: (v) => setState(() {
                    _asStructure = v;
                    if (!v) _structureId = null;
                  }),
                  onStructureChanged: (id) => setState(() => _structureId = id),
                ),

              const SizedBox(height: 8),

              // --- Identity / contact ---
              if (anonymous) ...[
                Text('Vos coordonnées',
                    style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 4),
                Text(
                  'Pas besoin de mot de passe. Elles créent votre accès et '
                  'servent à vous envoyer vos billets et votre confirmation.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton(
                    style: TextButton.styleFrom(padding: EdgeInsets.zero),
                    onPressed: () => context.push('/connexion'),
                    child: const Text('J\'ai déjà un compte'),
                  ),
                ),
                Row(children: [
                  Expanded(
                    child: TextFormField(
                      controller: _prenom,
                      decoration: const InputDecoration(labelText: 'Prénom'),
                      validator: (v) =>
                          (v == null || v.trim().isEmpty) ? 'Requis' : null,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextFormField(
                      controller: _nom,
                      decoration: const InputDecoration(labelText: 'Nom'),
                      validator: (v) =>
                          (v == null || v.trim().isEmpty) ? 'Requis' : null,
                    ),
                  ),
                ]),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _tel,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(labelText: 'Téléphone *'),
                  validator: (v) => (v == null ||
                          !RegExp(r'^\+?[0-9 ]{6,20}$').hasMatch(v.trim()))
                      ? 'Numéro de téléphone invalide'
                      : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  decoration: const InputDecoration(
                      labelText: 'Adresse e-mail (facultatif)'),
                  validator: (v) =>
                      (v != null && v.trim().isNotEmpty && !v.contains('@'))
                          ? 'E-mail invalide'
                          : null,
                ),
              ] else if (_asStructure) ...[
                Text('Personne à contacter',
                    style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _contactNom,
                  decoration: const InputDecoration(labelText: 'Nom du contact'),
                  validator: (v) =>
                      (v == null || v.trim().isEmpty) ? 'Requis' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _contactEmail,
                  keyboardType: TextInputType.emailAddress,
                  decoration:
                      const InputDecoration(labelText: 'E-mail du contact'),
                  validator: (v) =>
                      (v == null || !v.contains('@')) ? 'E-mail invalide' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _tel,
                  keyboardType: TextInputType.phone,
                  decoration:
                      const InputDecoration(labelText: 'Téléphone (facultatif)'),
                ),
              ] else ...[
                Card(
                  margin: EdgeInsets.zero,
                  child: ListTile(
                    leading: const Icon(Icons.person_outline),
                    title: Text(user?.fullName ?? ''),
                    subtitle: Text(user?.email ?? ''),
                  ),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _tel,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                      labelText: 'Téléphone de contact (facultatif)'),
                ),
              ],

              const SizedBox(height: 20),

              // --- Other attendees (optional) ---
              Row(
                children: [
                  Expanded(
                    child: Text(
                      _asStructure ? 'Participants' : 'Autres participants',
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                  ),
                  TextButton.icon(
                    onPressed: () =>
                        setState(() => _participants.add(_Participant())),
                    icon: const Icon(Icons.add),
                    label: const Text('Ajouter'),
                  ),
                ],
              ),
              Text(
                _asStructure
                    ? 'Les personnes de votre structure qui participeront.'
                    : 'Ajoutez d\'autres personnes seulement si vous les inscrivez '
                        'aussi. Sinon, laissez vide : vous êtes le participant.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 8),
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
                          validator: (v) => (v == null || v.trim().isEmpty)
                              ? 'Nom requis'
                              : null,
                        ),
                        const SizedBox(height: 8),
                        TextFormField(
                          controller: p.prenom,
                          decoration: const InputDecoration(labelText: 'Prénom'),
                        ),
                        const SizedBox(height: 8),
                        TextFormField(
                          controller: p.email,
                          decoration: const InputDecoration(labelText: 'E-mail'),
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
                child:
                    Text(_submitting ? 'Envoi…' : 'Confirmer mon inscription'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
