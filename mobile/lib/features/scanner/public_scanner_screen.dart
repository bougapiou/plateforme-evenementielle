import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:url_launcher/url_launcher.dart';

/// Camera scanner open to any visitor, signed in or not — no permission
/// required. Meant for scanning an event's printed QR (poster, flyer): it
/// lands you on that event's page to register or take a ticket, right in
/// the app. Distinct from [../checkin/…] which is staff-only ticket control.
class PublicScannerScreen extends StatefulWidget {
  const PublicScannerScreen({super.key});

  @override
  State<PublicScannerScreen> createState() => _PublicScannerScreenState();
}

class _PublicScannerScreenState extends State<PublicScannerScreen> {
  final _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
  );
  final _linkCtrl = TextEditingController();
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _controller.dispose();
    _linkCtrl.dispose();
    super.dispose();
  }

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_busy) return;
    final raw = capture.barcodes
        .map((b) => b.rawValue)
        .firstWhere((v) => v != null && v.isNotEmpty, orElse: () => null);
    if (raw == null) return;
    _busy = true;
    await _go(raw);
    _busy = false;
  }

  Future<void> _go(String raw) async {
    final value = raw.trim();
    if (value.isEmpty) return;
    setState(() => _error = null);

    final uri = Uri.tryParse(value);
    if (uri != null && uri.path.startsWith('/evenements/')) {
      await _controller.stop();
      if (!mounted) return;
      final path =
          uri.query.isEmpty ? uri.path : '${uri.path}?${uri.query}';
      context.push(path);
      return;
    }
    if (uri != null && (uri.scheme == 'http' || uri.scheme == 'https')) {
      await _controller.stop();
      final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!opened && mounted) {
        setState(() => _error = 'Impossible d\'ouvrir ce lien.');
      }
      return;
    }
    setState(() => _error = 'Ce code ne correspond pas à un lien reconnu.');
  }

  String _describeError(MobileScannerException error) {
    switch (error.errorCode) {
      case MobileScannerErrorCode.permissionDenied:
        return 'Accès à la caméra refusé. Autorisez la caméra pour cette '
            'application dans les réglages du téléphone, puis réessayez.';
      case MobileScannerErrorCode.unsupported:
        return 'La caméra n\'est pas disponible sur cet appareil.';
      default:
        return 'Impossible de démarrer la caméra (${error.errorCode.name}).';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scanner un QR code'),
        actions: [
          IconButton(
            icon: const Icon(Icons.flash_on),
            onPressed: () => _controller.toggleTorch(),
          ),
          IconButton(
            icon: const Icon(Icons.cameraswitch),
            onPressed: () => _controller.switchCamera(),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Text(
              'Visez le QR affiché sur l\'affiche ou le flyer d\'un événement : '
              'vous arrivez directement sur sa page pour vous inscrire ou '
              'prendre votre billet.',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          Expanded(
            child: Stack(
              alignment: Alignment.center,
              children: [
                MobileScanner(
                  controller: _controller,
                  onDetect: _onDetect,
                  errorBuilder: (context, error, child) => ColoredBox(
                    color: Colors.black,
                    child: Center(
                      child: Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(Icons.videocam_off,
                                color: Colors.white, size: 40),
                            const SizedBox(height: 12),
                            Text(
                              _describeError(error),
                              textAlign: TextAlign.center,
                              style: const TextStyle(color: Colors.white),
                            ),
                            if (error.errorCode ==
                                MobileScannerErrorCode.permissionDenied) ...[
                              const SizedBox(height: 16),
                              OutlinedButton(
                                style: OutlinedButton.styleFrom(
                                    foregroundColor: Colors.white,
                                    side: const BorderSide(color: Colors.white)),
                                onPressed: () => _controller.start(),
                                child: const Text('Réessayer'),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
                Container(
                  width: 240,
                  height: 240,
                  decoration: BoxDecoration(
                    border: Border.all(color: Colors.white70, width: 3),
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
              ],
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Ou collez le lien du QR'),
                const SizedBox(height: 8),
                TextField(
                  controller: _linkCtrl,
                  decoration: const InputDecoration(hintText: 'https://…'),
                  onSubmitted: _go,
                ),
                const SizedBox(height: 8),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: () => _go(_linkCtrl.text),
                    child: const Text('Ouvrir'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
