import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:open_filex/open_filex.dart';

import 'api_client.dart';
import 'file_download.dart' as dl;

/// How a fetched document ended up in front of the user.
enum DocOutcome {
  /// Native: saved to the cache and opened with the system viewer.
  opened,

  /// Native: saved, but no app on the device can open this type.
  savedNoViewer,

  /// Web: handed to the browser as a download.
  downloaded,
}

/// Fetches the authenticated document at [path] (a `/api/...` server path or a
/// full URL) and presents it: a browser download on the web, or a cached file
/// opened with the system viewer on mobile. Throws on network / auth errors —
/// callers surface those to the user.
Future<DocOutcome> fetchAndPresentDocument(
  ApiClient api, {
  required String path,
  required String filename,
}) async {
  if (kIsWeb) {
    final bytes = await api.bytes(path);
    dl.saveBytes(Uint8List.fromList(bytes), filename);
    return DocOutcome.downloaded;
  }
  final local = await api.downloadToCache(path, filename);
  final res = await OpenFilex.open(local);
  return res.type == ResultType.done
      ? DocOutcome.opened
      : DocOutcome.savedNoViewer;
}
