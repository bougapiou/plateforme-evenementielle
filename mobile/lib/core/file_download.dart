import 'dart:typed_data';

import 'file_download_native.dart'
    if (dart.library.js_interop) 'file_download_web.dart' as impl;

/// Hands raw document bytes to the user.
///
/// - **Web**: triggers a browser download named [filename].
/// - **Native**: throws [UnsupportedError] — native callers save the bytes to a
///   file and open it with the system viewer instead.
void saveBytes(Uint8List bytes, String filename) =>
    impl.saveBytes(bytes, filename);

/// Whether [saveBytes] does anything on the current platform (web only).
bool get canSaveBytes => impl.canSaveBytes;
