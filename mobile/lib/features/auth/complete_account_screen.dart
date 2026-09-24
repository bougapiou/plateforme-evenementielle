import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';

/// Turns a guest session into a full account by choosing a password.
class CompleteAccountScreen extends ConsumerStatefulWidget {
  const CompleteAccountScreen({super.key});

  @override
  ConsumerState<CompleteAccountScreen> createState() =>
      _CompleteAccountScreenState();
}

class _CompleteAccountScreenState extends ConsumerState<CompleteAccountScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  bool _saving = false;

  bool _needsEmail(UserSummary? u) {
    final e = u?.email ?? '';
    return e.isEmpty || e.endsWith('@guest.plateforme.local');
  }

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    final needsEmail = _needsEmail(ref.read(authControllerProvider).valueOrNull);
    setState(() => _saving = true);
    try {
      await ref.read(authControllerProvider.notifier).completeRegistration(
            password: _password.text,
            email: needsEmail ? _email.text.trim() : null,
          );
      if (mounted) {
        showSnack(context, 'Compte créé. Vous pouvez vous connecter partout.');
        context.pop();
      }
    } on ApiException catch (e) {
      if (mounted) showSnack(context, e.message, error: true);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(authControllerProvider).valueOrNull;
    return Scaffold(
      appBar: AppBar(title: const Text('Créer mon compte')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(
              'Choisissez un mot de passe pour retrouver vos billets, vos '
              'inscriptions et vos factures sur tous vos appareils.',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: 16),
            if (user != null) ...[
              TextFormField(
                initialValue: user.fullName,
                readOnly: true,
                decoration: const InputDecoration(labelText: 'Nom'),
              ),
              const SizedBox(height: 12),
              if (_needsEmail(user))
                TextFormField(
                  controller: _email,
                  keyboardType: TextInputType.emailAddress,
                  decoration: const InputDecoration(
                    labelText: 'Adresse e-mail',
                    helperText: 'Nécessaire pour vous connecter et recevoir vos billets.',
                  ),
                  validator: (v) => (v == null || !v.contains('@'))
                      ? 'Adresse e-mail invalide'
                      : null,
                )
              else
                TextFormField(
                  initialValue: user.email,
                  readOnly: true,
                  decoration: const InputDecoration(
                    labelText: 'Adresse e-mail',
                    helperText: 'Ce sera votre identifiant de connexion.',
                  ),
                ),
              const SizedBox(height: 12),
            ],
            PasswordField(
              controller: _password,
              decoration: const InputDecoration(
                labelText: 'Mot de passe',
                helperText: '8 caractères minimum',
              ),
              validator: (v) =>
                  (v == null || v.length < 8) ? '8 caractères minimum' : null,
            ),
            const SizedBox(height: 12),
            PasswordField(
              controller: _confirm,
              decoration:
                  const InputDecoration(labelText: 'Confirmer le mot de passe'),
              validator: (v) => v != _password.text
                  ? 'Les mots de passe ne correspondent pas'
                  : null,
            ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _saving ? null : _submit,
              child: Text(_saving ? 'Création…' : 'Créer mon compte'),
            ),
          ],
        ),
      ),
    );
  }
}
