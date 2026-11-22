import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

/// Triggers a browser "Save as…" for [bytes] under [filename].
void saveBytes(Uint8List bytes, String filename) {
  final mime = _mimeFor(filename);
  final blob = web.Blob(
    <JSAny>[bytes.toJS].toJS,
    web.BlobPropertyBag(type: mime),
  );
  final url = web.URL.createObjectURL(blob);
  final anchor = web.document.createElement('a') as web.HTMLAnchorElement
    ..href = url
    ..download = filename
    ..style.display = 'none';
  web.document.body?.appendChild(anchor);
  anchor.click();
  anchor.remove();
  web.URL.revokeObjectURL(url);
}

/// Ouvre [bytes] dans un nouvel onglet (le navigateur affiche le PDF).
void openBytes(Uint8List bytes, String filename) {
  final blob = web.Blob(
    <JSAny>[bytes.toJS].toJS,
    web.BlobPropertyBag(type: _mimeFor(filename)),
  );
  final url = web.URL.createObjectURL(blob);
  web.window.open(url, '_blank');
  // l'onglet garde sa copie : on libère l'URL un peu plus tard
  Future.delayed(const Duration(minutes: 1), () => web.URL.revokeObjectURL(url));
}

bool get canSaveBytes => true;

String _mimeFor(String filename) {
  final lower = filename.toLowerCase();
  if (lower.endsWith('.pdf')) return 'application/pdf';
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  return 'application/octet-stream';
}
