import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class BecomeOrganizerScreen extends ConsumerStatefulWidget {
  const BecomeOrganizerScreen({super.key});

  @override
  ConsumerState<BecomeOrganizerScreen> createState() =>
      _BecomeOrganizerScreenState();
}

class _BecomeOrganizerScreenState extends ConsumerState<BecomeOrganizerScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nom = TextEditingController();
  final _description = TextEditingController();
  final _email = TextEditingController();
  final _tel = TextEditingController();
  final _site = TextEditingController();
  String? _structureId;
  bool _saving = false;
  late Future<Organizer?> _existing;

  @override
  void initState() {
    super.initState();
    _existing = ref.read(organizersRepositoryProvider).me();
    final user = ref.read(authControllerProvider).valueOrNull;
    if (user != null) {
      _nom.text = user.fullName;
      _email.text = user.email;
    }
  }

  @override
  void dispose() {
    _nom.dispose();
    _description.dispose();
    _email.dispose();
    _tel.dispose();
    _site.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      await ref.read(organizersRepositoryProvider).apply(
            nomAffichage: _nom.text.trim(),
            description:
                _description.text.trim().isEmpty ? null : _description.text.trim(),
            structureId: _structureId,
            contactEmail: _email.text.trim().isEmpty ? null : _email.text.trim(),
            contactTelephone: _tel.text.trim().isEmpty ? null : _tel.text.trim(),
            siteWeb: _site.text.trim().isEmpty ? null : _site.text.trim(),
          );
      if (mounted) {
        showDialog(
          context: context,
          builder: (_) => AlertDialog(
            icon: const Icon(Icons.verified_outlined,
                color: Color(0xFF16A34A), size: 44),
            title: const Text('Demande envoyée'),
            content: const Text(
                'Votre demande pour devenir organisateur a été transmise. '
                'Un administrateur l\'examinera. La création d\'événements se '
                'fait ensuite depuis le portail web.'),
            actions: [
              FilledButton(
                onPressed: () {
                  Navigator.pop(context);
                  context.pop();
                },
                child: const Text('OK'),
              ),
            ],
          ),
        );
      }
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final structures = ref.watch(myStructuresProvider).valueOrNull ?? const [];
    return Scaffold(
      appBar: AppBar(title: const Text('Devenir organisateur')),
      body: FutureBuilder<Organizer?>(
        future: _existing,
        builder: (context, snap) {
          final existing = snap.data;
          if (existing != null) {
            return Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.badge_outlined, size: 48),
                  const SizedBox(height: 12),
                  Text('Vous avez déjà un profil organisateur',
                      style: Theme.of(context).textTheme.titleMedium,
                      textAlign: TextAlign.center),
                  const SizedBox(height: 8),
                  Text('« ${existing.nomAffichage} »',
                      textAlign: TextAlign.center),
                  const SizedBox(height: 8),
                  StatusChip(existing.statut),
                  const SizedBox(height: 16),
                  Text(
                    'La gestion de vos événements se fait depuis le portail web.',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            );
          }
          return Form(
            key: _formKey,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(
                  'Demandez à pouvoir créer et gérer des événements sur la '
                  'plateforme. Un administrateur valide la demande.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _nom,
                  decoration: const InputDecoration(
                      labelText: 'Nom d\'affichage de l\'organisateur *'),
                  validator: (v) =>
                      (v == null || v.trim().isEmpty) ? 'Requis' : null,
                ),
                const SizedBox(height: 12),
                if (structures.isNotEmpty)
                  DropdownButtonFormField<String?>(
                    value: _structureId,
                    decoration: const InputDecoration(
                        labelText: 'Rattacher à une structure (optionnel)'),
                    items: [
                      const DropdownMenuItem(value: null, child: Text('Aucune')),
                      ...structures.map((s) => DropdownMenuItem(
                          value: s.id, child: Text(s.raisonSociale))),
                    ],
                    onChanged: (v) => setState(() => _structureId = v),
                  ),
                if (structures.isNotEmpty) const SizedBox(height: 12),
                TextFormField(
                  controller: _description,
                  decoration:
                      const InputDecoration(labelText: 'Présentation (optionnel)'),
                  maxLines: 3,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _email,
                  decoration:
                      const InputDecoration(labelText: 'E-mail de contact'),
                  keyboardType: TextInputType.emailAddress,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _tel,
                  decoration:
                      const InputDecoration(labelText: 'Téléphone de contact'),
                  keyboardType: TextInputType.phone,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _site,
                  decoration: const InputDecoration(labelText: 'Site web'),
                ),
                const SizedBox(height: 20),
                FilledButton(
                  onPressed: _saving ? null : _submit,
                  child: Text(_saving ? 'Envoi…' : 'Envoyer ma demande'),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
