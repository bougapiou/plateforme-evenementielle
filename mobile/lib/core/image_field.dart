import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'brand.dart';
import 'media.dart';
import 'providers.dart';
import 'widgets.dart';

/// Image picker + uploader. Shows the current image, lets the user pick a new
/// one (camera / gallery), uploads it and reports the resulting URL.
class ImageField extends ConsumerStatefulWidget {
  final String label;
  final String? value;
  final String folder;
  final double height;
  final ValueChanged<String?> onChanged;

  const ImageField({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
    this.folder = 'images',
    this.height = 120,
  });

  @override
  ConsumerState<ImageField> createState() => _ImageFieldState();
}

class _ImageFieldState extends ConsumerState<ImageField> {
  bool _busy = false;

  Future<void> _pick() async {
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.photo_camera_outlined),
              title: const Text('Prendre une photo'),
              onTap: () => Navigator.pop(ctx, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library_outlined),
              title: const Text('Choisir dans la galerie'),
              onTap: () => Navigator.pop(ctx, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );
    if (source == null) return;
    final picked =
        await ImagePicker().pickImage(source: source, imageQuality: 85, maxWidth: 2000);
    if (picked == null) return;
    setState(() => _busy = true);
    try {
      final url = await ref.read(apiClientProvider).uploadImage(
            filePath: picked.path,
            fileName: picked.name,
            folder: widget.folder,
          );
      widget.onChanged(url);
    } catch (e) {
      if (mounted) showSnack(context, 'Téléversement impossible : $e', error: true);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(widget.label, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 6),
        Row(
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(10),
              child: Container(
                width: widget.height * 1.6,
                height: widget.height,
                color: Brand.s100,
                child: widget.value != null
                    ? RemoteImage(url: widget.value, fit: BoxFit.cover)
                    : const Icon(Icons.image_outlined, color: Brand.s300),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  OutlinedButton.icon(
                    onPressed: _busy ? null : _pick,
                    icon: _busy
                        ? const SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.upload_outlined, size: 18),
                    label: Text(_busy ? 'Envoi…' : 'Choisir une image'),
                  ),
                  if (widget.value != null)
                    TextButton(
                      onPressed: _busy ? null : () => widget.onChanged(null),
                      child: const Text('Retirer'),
                    ),
                ],
              ),
            ),
          ],
        ),
      ],
    );
  }
}
