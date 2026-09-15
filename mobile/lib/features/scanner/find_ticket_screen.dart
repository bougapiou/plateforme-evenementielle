import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';

/// Lets a guest visitor get back the session tied to a phone number they used
/// at checkout, without an account or a password — same lookup the guest
/// checkout sheet already does when it recognises a returning phone number,
/// just exposed as its own entry point next to the public QR scanner.
class FindTicketScreen extends ConsumerStatefulWidget {
  const FindTicketScreen({super.key});

  @override
  ConsumerState<FindTicketScreen> createState() => _FindTicketScreenState();
}

class _FindTicketScreenState extends ConsumerState<FindTicketScreen> {
  final _formKey = GlobalKey<FormState>();
  final _prenom = TextEditingController();
  final _nom = TextEditingController();
  String _tel = '';
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _prenom.dispose();
    _nom.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).guestSession(
            firstName: _prenom.text.trim(),
            lastName: _nom.text.trim(),
            phone: _tel.trim(),
          );
      if (mounted) context.go('/billets');
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

  void _promptLogin() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Compte existant'),
        content: const Text(
            'Un compte existe déjà avec cet e-mail. Connectez-vous pour '
            'retrouver vos billets.'),
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

  @override
  Widget build(BuildContext context) {
    final signedIn = ref.watch(authControllerProvider).valueOrNull != null;

    return Scaffold(
      appBar: AppBar(title: const Text('Retrouver mon billet')),
      body: signedIn
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('Vous êtes déjà connecté.'),
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: () => context.go('/billets'),
                      child: const Text('Voir mes billets'),
                    ),
                  ],
                ),
              ),
            )
          : Form(
              key: _formKey,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text(
                    'Indiquez le nom et le numéro de téléphone utilisés lors '
                    'de votre achat ou inscription : vous retrouvez '
                    'directement vos billets, sans mot de passe.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 16),
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
                  PhoneField(
                    required: true,
                    onChanged: (v) => _tel = v,
                  ),
                  const SizedBox(height: 16),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_error!,
                          style: const TextStyle(color: Colors.red)),
                    ),
                  FilledButton(
                    onPressed: _busy ? null : _submit,
                    child: Text(_busy ? 'Recherche…' : 'Retrouver mon billet'),
                  ),
                  const SizedBox(height: 8),
                  TextButton(
                    onPressed: () => context.push('/connexion'),
                    child: const Text('J\'ai déjà un compte avec mot de passe'),
                  ),
                ],
              ),
            ),
    );
  }
}
