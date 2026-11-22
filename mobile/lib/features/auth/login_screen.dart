import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/faso.dart';
import '../../core/models.dart';
import '../../core/providers.dart';
import '../../core/widgets.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _code = TextEditingController();
  bool _loading = false;
  String? _error;

  /// Second-factor step: null while on the password step.
  String? _mfaPurpose;
  String? _mfaToken;
  MfaSetup? _setup;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _startMfa(MfaRequiredException e) async {
    setState(() {
      _mfaPurpose = e.purpose;
      _mfaToken = e.mfaToken;
      _code.clear();
      _setup = null;
    });
    if (e.purpose == 'SETUP') {
      try {
        final st = await ref.read(authControllerProvider.notifier).mfaSetup(e.mfaToken);
        if (mounted) setState(() => _setup = st);
      } on ApiException catch (err) {
        if (mounted) setState(() => _error = err.message);
      }
    }
  }

  Future<void> _submitCode() async {
    final code = _code.text.replaceAll(' ', '');
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      setState(() => _error = 'Saisissez le code à 6 chiffres.');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier)
          .completeMfa(_mfaToken!, code, setup: _mfaPurpose == 'SETUP');
      if (mounted) context.go('/evenements');
    } on ApiException catch (e) {
      setState(() {
        _error = e.status == 401
            ? "Code incorrect. Vérifiez l'heure de votre téléphone."
            : e.message;
        if (e.code == 'MFA_TOKEN_EXPIRED') _mfaPurpose = null;
      });
    } catch (_) {
      setState(() => _error = 'Vérification impossible.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _mfaStep() {
    final setup = _mfaPurpose == 'SETUP';
    final qr = _setup?.qrDataUri ?? '';
    final comma = qr.indexOf(',');
    return ListView(
      children: [
        const SizedBox(height: 8),
        if (setup) ...[
          Card(
            color: Colors.amber.shade50,
            child: const Padding(
              padding: EdgeInsets.all(12),
              child: Text(
                "Double authentification obligatoire pour votre rôle. Installez une application "
                "d'authentification (Google Authenticator, Microsoft Authenticator, FreeOTP…), "
                'ajoutez le compte avec ce QR code ou la clé ci-dessous, puis saisissez le code affiché.',
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (_setup == null)
            const Center(child: CircularProgressIndicator())
          else ...[
            if (comma > 0)
              Center(
                child: Image.memory(base64Decode(qr.substring(comma + 1)), width: 200, height: 200),
              ),
            const SizedBox(height: 8),
            SelectableText('Clé : ${_setup!.secret}',
                textAlign: TextAlign.center, style: const TextStyle(fontFamily: 'monospace')),
          ],
        ] else
          const Text("Saisissez le code à 6 chiffres affiché par votre application d'authentification."),
        const SizedBox(height: 16),
        TextField(
          controller: _code,
          keyboardType: TextInputType.number,
          autofillHints: const [AutofillHints.oneTimeCode],
          maxLength: 7,
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 24, letterSpacing: 8, fontFamily: 'monospace'),
          decoration: const InputDecoration(labelText: 'Code de vérification', counterText: ''),
          onSubmitted: (_) => _submitCode(),
        ),
        const SizedBox(height: 12),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Text(_error!, style: const TextStyle(color: Colors.red)),
          ),
        FilledButton(
          onPressed: _loading ? null : _submitCode,
          child: Text(_loading ? 'Vérification…' : (setup ? 'Activer et se connecter' : 'Valider')),
        ),
        TextButton(
          onPressed: () => setState(() {
            _mfaPurpose = null;
            _error = null;
            _password.clear();
          }),
          child: const Text('← Recommencer'),
        ),
      ],
    );
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref
          .read(authControllerProvider.notifier)
          .login(_email.text.trim(), _password.text);
      if (mounted) context.go('/evenements');
    } on MfaRequiredException catch (e) {
      await _startMfa(e);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Connexion impossible.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Connexion')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: _mfaPurpose != null ? _mfaStep() : Form(
          key: _formKey,
          child: ListView(
            children: [
              const SizedBox(height: 8),
              const FasoHeader(),
              const SizedBox(height: 20),
              TextFormField(
                controller: _email,
                decoration: const InputDecoration(labelText: 'Adresse e-mail'),
                keyboardType: TextInputType.emailAddress,
                autofillHints: const [AutofillHints.email],
                validator: (v) =>
                    (v == null || !v.contains('@')) ? 'E-mail invalide' : null,
              ),
              const SizedBox(height: 12),
              PasswordField(
                controller: _password,
                decoration: const InputDecoration(labelText: 'Mot de passe'),
                validator: (v) =>
                    (v == null || v.isEmpty) ? 'Mot de passe requis' : null,
              ),
              const SizedBox(height: 16),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(_error!, style: const TextStyle(color: Colors.red)),
                ),
              FilledButton(
                onPressed: _loading ? null : _submit,
                child: Text(_loading ? 'Connexion…' : 'Se connecter'),
              ),
              TextButton(
                onPressed: () => context.push('/mot-de-passe-oublie'),
                child: const Text('Mot de passe oublié ?'),
              ),
              const Divider(height: 8),
              TextButton(
                onPressed: () => context.push('/inscription'),
                child: const Text('Créer un compte'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
