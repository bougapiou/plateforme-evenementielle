import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/providers.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  ConsumerState<ForgotPasswordScreen> createState() =>
      _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  bool _loading = false;
  bool _sent = false;

  @override
  void dispose() {
    _email.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      await ref
          .read(authControllerProvider.notifier)
          .requestPasswordReset(_email.text.trim());
    } catch (_) {
      // Same result whether or not the account exists.
    }
    if (mounted) {
      setState(() {
        _loading = false;
        _sent = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Mot de passe oublié')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: _sent
            ? _SentView(email: _email.text.trim())
            : Form(
                key: _formKey,
                child: ListView(
                  children: [
                    const SizedBox(height: 8),
                    Text(
                      'Indiquez l\'adresse e-mail de votre compte. Si elle est '
                      'connue, vous recevrez un e-mail avec un lien et un code de '
                      'réinitialisation.',
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _email,
                      decoration:
                          const InputDecoration(labelText: 'Adresse e-mail'),
                      keyboardType: TextInputType.emailAddress,
                      autofillHints: const [AutofillHints.email],
                      validator: (v) => (v == null || !v.contains('@'))
                          ? 'E-mail invalide'
                          : null,
                    ),
                    const SizedBox(height: 20),
                    FilledButton(
                      onPressed: _loading ? null : _submit,
                      child: Text(_loading ? 'Envoi…' : 'Envoyer le lien'),
                    ),
                  ],
                ),
              ),
      ),
    );
  }
}

class _SentView extends StatelessWidget {
  final String email;
  const _SentView({required this.email});

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        const SizedBox(height: 24),
        Icon(Icons.mark_email_read_outlined,
            size: 56, color: Theme.of(context).colorScheme.primary),
        const SizedBox(height: 16),
        Text('Vérifiez votre boîte mail',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        Text(
          'Si un compte existe pour $email, un e-mail vient de partir. '
          'Le lien est valable 1 heure. Ouvrez-le, ou copiez le code qu\'il '
          'contient dans l\'écran suivant.',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 24),
        FilledButton(
          onPressed: () => context.push('/mot-de-passe/reinitialiser'),
          child: const Text('J\'ai reçu le code'),
        ),
        TextButton(
          onPressed: () => context.go('/connexion'),
          child: const Text('Retour à la connexion'),
        ),
      ],
    );
  }
}
