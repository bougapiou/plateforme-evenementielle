import 'dart:typed_data';

/// Native stub — browser downloads are not applicable off the web.
void saveBytes(Uint8List bytes, String filename) => throw UnsupportedError(
    'saveBytes est réservé au web ; sur mobile, enregistrez puis ouvrez le fichier.');

bool get canSaveBytes => false;
