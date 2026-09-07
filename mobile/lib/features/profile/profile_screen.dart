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

    if (user == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Profil')),
        body: Center(
          child: FilledButton(
            onPressed: () => context.push('/connexion'),
            child: const Text('Se connecter'),
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
              _typeLabel(user.type),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          const SizedBox(height: 20),
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
          if (canScan)
            ListTile(
              leading: const Icon(Icons.qr_code_scanner),
              title: const Text('Contrôle à l\'entrée'),
              subtitle: const Text('Scanner les QR codes des billets'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.push('/scanner'),
            ),
          const Divider(height: 24),
          if (user.roles.isNotEmpty)
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
                await ref.read(authControllerProvider.notifier).logout();
                if (context.mounted) context.go('/evenements');
              },
              icon: const Icon(Icons.logout),
              label: const Text('Se déconnecter'),
            ),
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

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
