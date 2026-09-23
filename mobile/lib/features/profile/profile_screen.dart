import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/brand.dart';
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
          child: Padding(
            padding: const EdgeInsets.all(24),
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
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  onPressed: () => context.push('/retrouver-billet'),
                  icon: const Icon(Icons.confirmation_number_outlined),
                  label: const Text('Retrouver mon billet'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final role = _role(user.type, user.guest);
    final isAgent = canScan && !canCreateEvents;

    return Scaffold(
      appBar: AppBar(title: const Text('Profil')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  CircleAvatar(
                    radius: 34,
                    backgroundColor: role.color.withValues(alpha: .12),
                    foregroundColor: role.color,
                    child: Text(
                      user.fullName.isNotEmpty
                          ? user.fullName.substring(0, 1).toUpperCase()
                          : '?',
                      style: const TextStyle(
                          fontSize: 26, fontWeight: FontWeight.w700),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Text(user.fullName,
                      style: Theme.of(context).textTheme.titleLarge,
                      textAlign: TextAlign.center),
                  const SizedBox(height: 2),
                  Text(user.email,
                      style: Theme.of(context).textTheme.bodySmall,
                      textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  Wrap(
                    alignment: WrapAlignment.center,
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      _RoleChip(icon: role.icon, label: role.label, color: role.color),
                      if (isAgent)
                        const _RoleChip(
                          icon: Icons.qr_code_scanner,
                          label: 'Agent de contrôle',
                          color: Brand.b600,
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          if (user.guest) ...[
            const SizedBox(height: 12),
            Card(
              color: Theme.of(context).colorScheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(16),
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
                    const SizedBox(height: 12),
                    FilledButton(
                      onPressed: () => context.push('/finaliser-compte'),
                      child: const Text('Créer mon compte'),
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 20),
          if (!user.guest)
            _SectionCard(title: 'Mon compte', children: [
              _tile(context,
                  icon: Icons.badge_outlined,
                  title: 'Modifier mon profil',
                  onTap: () => context.push('/profil/modifier')),
              _tile(context,
                  icon: Icons.lock_outline,
                  title: 'Changer mon mot de passe',
                  onTap: () => context.push('/profil/mot-de-passe')),
            ]),
          const SizedBox(height: 16),
          _SectionCard(title: 'Mes activités', children: [
            _tile(context,
                icon: Icons.qr_code_scanner,
                title: 'Scanner un QR code',
                subtitle: "Ouvrir un événement depuis son affiche",
                onTap: () => context.push('/scanner-qr')),
            _tile(context,
                icon: Icons.history,
                title: 'Mon activité',
                subtitle: 'Commandes, inscriptions, réservations, paiements',
                onTap: () => context.push('/activite')),
            _tile(context,
                icon: Icons.confirmation_number_outlined,
                title: 'Mes billets',
                onTap: () => context.go('/billets')),
            _tile(context,
                icon: Icons.receipt_long_outlined,
                title: 'Factures & reçus',
                onTap: () => context.push('/factures')),
            if (!user.guest)
              _tile(context,
                  icon: Icons.domain_outlined,
                  title: 'Mes structures',
                  subtitle: "S'inscrire / réserver au nom d'une structure",
                  onTap: () => context.push('/structures')),
          ]),
          if (!user.guest) ...[
            const SizedBox(height: 16),
            _SectionCard(
              title: 'Organisation',
              accentColor: Brand.b600,
              children: [
                canCreateEvents
                    ? _tile(context,
                        icon: Icons.event_available_outlined,
                        title: 'Mes événements',
                        subtitle: 'Créer et gérer mes événements',
                        onTap: () => context.push('/mes-evenements'))
                    : _tile(context,
                        icon: Icons.campaign_outlined,
                        title: 'Devenir organisateur',
                        subtitle: 'Demander à créer des événements',
                        onTap: () => context.push('/devenir-organisateur')),
                if (canScan)
                  _tile(context,
                      icon: Icons.qr_code_scanner,
                      title: 'Contrôle à la porte',
                      subtitle: 'Scanner les QR codes des billets',
                      onTap: () => context.push('/scanner')),
              ],
            ),
          ],
          if (!user.guest && user.roles.isNotEmpty) ...[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(Icons.verified_user_outlined,
                        size: 18, color: Theme.of(context).colorScheme.outline),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(user.roles.join(' · '),
                          style: Theme.of(context).textTheme.bodySmall),
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 24),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: Brand.red,
              side: const BorderSide(color: Brand.red),
            ),
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
        ],
      ),
    );
  }

  Widget _tile(
    BuildContext context, {
    required IconData icon,
    required String title,
    String? subtitle,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: subtitle != null ? Text(subtitle) : null,
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }

  _RoleInfo _role(String type, bool guest) {
    if (guest) return _RoleInfo('Mode invité', Icons.person_outline, Brand.s500);
    switch (type) {
      case 'STRUCTURE':
        return _RoleInfo(
            'Compte structure / entreprise', Icons.domain, Brand.s700);
      case 'ORGANISATEUR':
        return _RoleInfo('Organisateur', Icons.event_note, Brand.b600);
      case 'ADMIN':
      case 'SUPER_ADMIN':
        return _RoleInfo('Administrateur', Icons.shield_outlined, Brand.red);
      default:
        return _RoleInfo('Compte particulier', Icons.person_outline, Brand.s500);
    }
  }
}

class _RoleInfo {
  final String label;
  final IconData icon;
  final Color color;
  _RoleInfo(this.label, this.icon, this.color);
}

/// Bordered card grouping a set of tiles, with a small colored accent bar
/// on the section title for the "power user" sections (organisateur,
/// personnel de contrôle) so they stand out from the plain account settings.
class _SectionCard extends StatelessWidget {
  final String title;
  final List<Widget> children;
  final Color? accentColor;
  const _SectionCard({required this.title, required this.children, this.accentColor});

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 6),
            child: Row(
              children: [
                if (accentColor != null) ...[
                  Container(
                    width: 4,
                    height: 14,
                    decoration: BoxDecoration(
                      color: accentColor,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Text(
                  title.toUpperCase(),
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: accentColor ?? Theme.of(context).colorScheme.outline,
                        letterSpacing: .6,
                      ),
                ),
              ],
            ),
          ),
          for (int i = 0; i < children.length; i++) ...[
            children[i],
            if (i < children.length - 1) const Divider(height: 1),
          ],
        ],
      ),
    );
  }
}

class _RoleChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  const _RoleChip({required this.icon, required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .10),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 5),
          Text(label,
              style: TextStyle(
                  fontSize: 12, fontWeight: FontWeight.w600, color: color)),
        ],
      ),
    );
  }
}
