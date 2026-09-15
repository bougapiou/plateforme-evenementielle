import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/providers.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).valueOrNull;
    final canScan = ref.watch(hasPermissionProvider('CHECKIN_SCAN'));
    final canCreateEvents = ref.watch(hasPermissionProvider('EVENT_CREATE'));

    if (user == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Profil')),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              FilledButton(
                onPressed: () => context.push('/connexion'),
                child: const Text('Se connecter'),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => context.push('/scanner-qr'),
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('Scanner un QR code'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Profil')),
      body: ListView(
        children: [
          const SizedBox(height: 12),
          Center(
            child: CircleAvatar(
              radius: 36,
              child: Text(
                user.fullName.isNotEmpty
                    ? user.fullName.substring(0, 1).toUpperCase()
                    : '?',
                style: const TextStyle(fontSize: 28),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Center(
            child: Text(user.fullName,
                style: Theme.of(context).textTheme.titleLarge),
          ),
          Center(child: Text(user.email)),
          const SizedBox(height: 4),
          Center(
            child: Text(
              user.guest ? 'Mode invité' : _typeLabel(user.type),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          const SizedBox(height: 16),
          if (user.guest)
            Card(
              color: Theme.of(context).colorScheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Créez votre compte',
                        style: Theme.of(context).textTheme.titleSmall),
                    const SizedBox(height: 4),
                    Text(
                      'Vous naviguez en invité. Choisissez un mot de passe pour '
                      'retrouver vos billets, inscriptions et factures sur tous '
                      'vos appareils.',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 10),
                    FilledButton(
                      onPressed: () => context.push('/finaliser-compte'),
                      child: const Text('Créer mon compte'),
                    ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 8),
          _sectionLabel(context, 'Mon compte'),
          if (!user.guest)
            ListTile(
              leading: const Icon(Icons.badge_outlined),
              title: const Text('Modifier mon profil'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/profil/modifier'),
            ),
          if (!user.guest)
            ListTile(
              leading: const Icon(Icons.lock_outline),
              title: const Text('Changer mon mot de passe'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/profil/mot-de-passe'),
            ),
          const Divider(height: 16),
          _sectionLabel(context, 'Mes activités'),
          ListTile(
            leading: const Icon(Icons.qr_code_scanner),
            title: const Text('Scanner un QR code'),
            subtitle: const Text('Ouvrir un événement depuis son affiche'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/scanner-qr'),
          ),
          ListTile(
            leading: const Icon(Icons.history),
            title: const Text('Mon activité'),
            subtitle: const Text('Commandes, inscriptions, réservations, paiements'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/activite'),
          ),
          ListTile(
            leading: const Icon(Icons.confirmation_number_outlined),
            title: const Text('Mes billets'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.go('/billets'),
          ),
          ListTile(
            leading: const Icon(Icons.receipt_long_outlined),
            title: const Text('Factures & reçus'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/factures'),
          ),
          if (!user.guest)
            ListTile(
              leading: const Icon(Icons.domain_outlined),
              title: const Text('Mes structures'),
              subtitle:
                  const Text('S\'inscrire / réserver au nom d\'une structure'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/structures'),
            ),
          if (!user.guest) ...[
          const Divider(height: 16),
          _sectionLabel(context, 'Organisation'),
          if (canCreateEvents)
            ListTile(
              leading: const Icon(Icons.event_available_outlined),
              title: const Text('Mes événements'),
              subtitle: const Text('Créer et gérer mes événements'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/mes-evenements'),
            )
          else
            ListTile(
              leading: const Icon(Icons.campaign_outlined),
              title: const Text('Devenir organisateur'),
              subtitle: const Text('Demander à créer des événements'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/devenir-organisateur'),
            ),
          if (canScan)
            ListTile(
              leading: const Icon(Icons.qr_code_scanner),
              title: const Text('Contrôle à l\'entrée'),
              subtitle: const Text('Scanner les QR codes des billets'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/scanner'),
            ),
          ],
          const Divider(height: 24),
          if (!user.guest && user.roles.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Rôles', style: Theme.of(context).textTheme.labelLarge),
                  const SizedBox(height: 4),
                  Text(user.roles.join(', ')),
                ],
              ),
            ),
          const SizedBox(height: 24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: OutlinedButton.icon(
              onPressed: () async {
                if (user.guest) {
                  final ok = await showDialog<bool>(
                    context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('Quitter le mode invité ?'),
                      content: const Text(
                          'Sans compte, vous ne pourrez plus retrouver vos '
                          'billets et inscriptions sur cet appareil.'),
                      actions: [
                        TextButton(
                            onPressed: () => Navigator.pop(ctx, false),
                            child: const Text('Rester')),
                        FilledButton(
                            onPressed: () => Navigator.pop(ctx, true),
                            child: const Text('Quitter')),
                      ],
                    ),
                  );
                  if (ok != true) return;
                }
                await ref.read(authControllerProvider.notifier).logout();
                if (context.mounted) context.go('/evenements');
              },
              icon: const Icon(Icons.logout),
              label: Text(user.guest ? 'Quitter le mode invité' : 'Se déconnecter'),
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _sectionLabel(BuildContext context, String text) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
        child: Text(
          text.toUpperCase(),
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: Theme.of(context).colorScheme.outline,
                letterSpacing: .6,
              ),
        ),
      );

  String _typeLabel(String type) {
    switch (type) {
      case 'STRUCTURE':
        return 'Compte structure / entreprise';
      case 'ORGANISATEUR':
        return 'Compte organisateur';
      case 'ADMIN':
      case 'SUPER_ADMIN':
        return 'Administrateur';
      default:
        return 'Compte particulier';
    }
  }
}
