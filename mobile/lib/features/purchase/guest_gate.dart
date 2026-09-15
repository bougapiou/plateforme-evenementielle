import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';

/// Makes sure a session exists before a checkout action. If the visitor is not
/// signed in, offers to continue as a guest (name + e-mail only) or to sign in.
class GuestGate {
  GuestGate._();

  /// Returns true once a session (guest or full) is available.
  static Future<bool> ensureSession(BuildContext context, WidgetRef ref) async {
    bool hasSession() => ref.read(authControllerProvider).valueOrNull != null;
    if (hasSession()) return true;

    while (true) {
      if (!context.mounted) return hasSession();
      final choice = await showModalBottomSheet<_Choice>(
        context: context,
        isScrollControlled: true,
        builder: (_) => const _GuestSheet(),
      );
      if (choice == null || choice == const _Choice.session()) {
        return choice == const _Choice.session() || hasSession();
      }
      if (!context.mounted) return hasSession();
      await context.push('/connexion');
      if (hasSession()) return true;
      // not signed in — loop back to the sheet
    }
  }
}

class _Choice {
  final String kind;
  const _Choice._(this.kind);
  const _Choice.session() : this._('session');
  const _Choice.login() : this._('login');
  @override
  bool operator ==(Object other) => other is _Choice && other.kind == kind;
  @override
  int get hashCode => kind.hashCode;
}

class _GuestSheet extends ConsumerStatefulWidget {
  const _GuestSheet();
  @override
  ConsumerState<_GuestSheet> createState() => _GuestSheetState();
}

class _GuestSheetState extends ConsumerState<_GuestSheet> {
  final _formKey = GlobalKey<FormState>();
  final _prenom = TextEditingController();
  final _nom = TextEditingController();
  final _email = TextEditingController();
  String _tel = '';
  bool _busy = false;

  @override
  void dispose() {
    _prenom.dispose();
    _nom.dispose();
    _email.dispose();
    super.dispose();
  }

  Future<void> _continueAsGuest() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _busy = true);
    try {
      await ref.read(authControllerProvider.notifier).guestSession(
            firstName: _prenom.text.trim(),
            lastName: _nom.text.trim(),
            phone: _tel.trim(),
            email: _email.text.trim().isEmpty ? null : _email.text.trim(),
          );
      if (mounted) Navigator.pop(context, const _Choice.session());
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        if (e.code == 'ACCOUNT_EXISTS') {
          _showAccountExists();
        } else {
          showSnack(context, e.message, error: true);
        }
      }
    }
  }

  void _showAccountExists() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Compte existant'),
        content: const Text(
            'Un compte existe déjà avec cet e-mail. Connectez-vous pour '
            'retrouver vos billets et vos inscriptions.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Fermer')),
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context, const _Choice.login());
            },
            child: const Text('Se connecter'),
          ),
        ],
      ),
    );
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
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('Vos coordonnées',
                  style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                'Pas besoin de compte pour continuer. Vous pourrez en créer un '
                'après le paiement pour retrouver facilement vos billets.',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 14),
              Row(children: [
                Expanded(
                  child: TextFormField(
                    controller: _prenom,
                    decoration: const InputDecoration(labelText: 'Prénom *'),
                    validator: (v) =>
                        (v == null || v.trim().isEmpty) ? 'Requis' : null,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: TextFormField(
                    controller: _nom,
                    decoration: const InputDecoration(labelText: 'Nom *'),
                    validator: (v) =>
                        (v == null || v.trim().isEmpty) ? 'Requis' : null,
                  ),
                ),
              ]),
              const SizedBox(height: 10),
              PhoneField(
                initialValue: _tel,
                required: true,
                onChanged: (v) => _tel = v,
              ),
              const SizedBox(height: 10),
              TextFormField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(
                  labelText: 'Adresse e-mail (facultatif)',
                  helperText: 'Pour recevoir vos billets et créer un compte.',
                ),
                validator: (v) => (v != null && v.trim().isNotEmpty && !v.contains('@'))
                    ? 'E-mail invalide'
                    : null,
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _busy ? null : _continueAsGuest,
                child: Text(_busy ? 'Un instant…' : 'Continuer'),
              ),
              const SizedBox(height: 4),
              TextButton(
                onPressed: _busy
                    ? null
                    : () => Navigator.pop(context, const _Choice.login()),
                child: const Text('J\'ai déjà un compte'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
