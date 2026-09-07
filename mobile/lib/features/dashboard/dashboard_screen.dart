import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../core/providers.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).valueOrNull;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mon espace'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              await ref.read(authControllerProvider.notifier).logout();
              if (context.mounted) context.go('/');
            },
          ),
        ],
      ),
      body: user == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text('Bonjour ${user.fullName}',
                    style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 4),
                Text(user.email),
                const SizedBox(height: 16),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Rôles',
                            style: TextStyle(fontWeight: FontWeight.bold)),
                        Text(user.roles.join(', ')),
                        const SizedBox(height: 12),
                        const Text('Permissions',
                            style: TextStyle(fontWeight: FontWeight.bold)),
                        Wrap(
                          spacing: 6,
                          runSpacing: 6,
                          children: user.permissions
                              .map((p) => Chip(label: Text(p)))
                              .toList(),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                const Card(
                  child: ListTile(
                    leading: Icon(Icons.confirmation_number_outlined),
                    title: Text('Mes billets'),
                    subtitle: Text('Disponible avec le module Billetterie.'),
                  ),
                ),
              ],
            ),
    );
  }
}
