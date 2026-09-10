import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';

class ResetPasswordScreen extends ConsumerStatefulWidget {
  /// Prefilled from the e-mail link (`?token=`); otherwise pasted by the user.
  final String? initialToken;
  const ResetPasswordScreen({super.key, this.initialToken});

  @override
  ConsumerState<ResetPasswordScreen> createState() =>
      _ResetPasswordScreenState();
}

class _ResetPasswordScreenState extends ConsumerState<ResetPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _token =
      TextEditingController(text: widget.initialToken ?? '');
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _token.dispose();
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_password.text != _confirm.text) {
      setState(() => _error = 'Les deux mots de passe ne correspondent pas.');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).resetPassword(
            token: _token.text.trim(),
            password: _password.text,
          );
      if (!mounted) return;
      showSnack(context, 'Mot de passe mis à jour. Vous pouvez vous connecter.');
      context.go('/connexion');
    } on ApiException catch (e) {
      setState(() {
        _loading = false;
        _error = e.message;
      });
    } catch (_) {
      setState(() {
        _loading = false;
        _error = 'Réinitialisation impossible.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Nouveau mot de passe')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: ListView(
            children: [
              const SizedBox(height: 8),
              Text(
                'Collez le code reçu par e-mail, puis choisissez un nouveau mot '
                'de passe (8 caractères minimum).',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _token,
                decoration: const InputDecoration(
                  labelText: 'Code de réinitialisation',
                ),
                maxLines: 2,
                minLines: 1,
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Code requis' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _password,
                decoration: const InputDecoration(labelText: 'Mot de passe'),
                obscureText: true,
                validator: (v) => (v == null || v.length < 8)
                    ? '8 caractères minimum'
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _confirm,
                decoration:
                    const InputDecoration(labelText: 'Confirmer le mot de passe'),
                obscureText: true,
                validator: (v) =>
                    (v == null || v.isEmpty) ? 'Confirmation requise' : null,
              ),
              const SizedBox(height: 16),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(_error!, style: const TextStyle(color: Colors.red)),
                ),
              FilledButton(
                onPressed: _loading ? null : _submit,
                child: Text(_loading ? 'Enregistrement…' : 'Mettre à jour'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
