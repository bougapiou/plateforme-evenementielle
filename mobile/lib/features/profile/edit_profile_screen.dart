import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/models.dart';
import '../../core/phone_field.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';
import '../../data/domain.dart';

class EditProfileScreen extends ConsumerStatefulWidget {
  const EditProfileScreen({super.key});

  @override
  ConsumerState<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends ConsumerState<EditProfileScreen> {
  final _formKey = GlobalKey<FormState>();
  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  String _phone = '';
  late Future<Me> _future;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  Future<Me> _load() async {
    final me = await ref.read(usersRepositoryProvider).me();
    _firstName.text = me.firstName;
    _lastName.text = me.lastName;
    _phone = me.phone ?? '';
    return me;
  }

  @override
  void dispose() {
    _firstName.dispose();
    _lastName.dispose();
    super.dispose();
  }

  Future<void> _save(Me me) async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final updated = await ref.read(usersRepositoryProvider).updateProfile(
            firstName: _firstName.text.trim(),
            lastName: _lastName.text.trim(),
            phone: _phone.trim().isEmpty ? null : _phone.trim(),
          );
      final current = ref.read(authControllerProvider).valueOrNull;
      await ref.read(authControllerProvider.notifier).setUser(UserSummary(
            id: updated.id,
            email: updated.email,
            fullName: updated.fullName,
            type: updated.type,
            status: updated.status,
            roles: updated.roles.isNotEmpty
                ? updated.roles
                : (current?.roles ?? const []),
            permissions: updated.permissions.isNotEmpty
                ? updated.permissions
                : (current?.permissions ?? const []),
          ));
      if (mounted) {
        showSnack(context, 'Profil mis à jour.');
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
    return Scaffold(
      appBar: AppBar(title: const Text('Modifier mon profil')),
      body: FutureView<Me>(
        future: _future,
        onRetry: () => setState(() => _future = _load()),
        builder: (me) => Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              TextFormField(
                initialValue: me.email,
                readOnly: true,
                decoration: const InputDecoration(
                  labelText: 'Adresse e-mail',
                  helperText: 'L\'e-mail ne peut pas être modifié ici.',
                ),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _firstName,
                decoration: const InputDecoration(labelText: 'Prénom'),
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Requis' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _lastName,
                decoration: const InputDecoration(labelText: 'Nom'),
                validator: (v) =>
                    (v == null || v.trim().isEmpty) ? 'Requis' : null,
              ),
              const SizedBox(height: 12),
              PhoneField(
                initialValue: _phone,
                onChanged: (v) => _phone = v,
              ),
              const SizedBox(height: 20),
              FilledButton(
                onPressed: _saving ? null : () => _save(me),
                child: Text(_saving ? 'Enregistrement…' : 'Enregistrer'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
