/// Where the numbers of a presence screen come from.
enum PresenceSource {
  qr('Billets QR', 'Personnes contrôlées par scan de billet'),
  physique('Capteurs', 'Passages comptés par les capteurs laser'),
  combine('Combiné', 'Scans de billets + capteurs, additionnés');

  final String label;
  final String description;
  const PresenceSource(this.label, this.description);
}

/// The four counters of a presence board.
class PresenceFlow {
  final int entrees;
  final int sorties;
  final int presents;

  /// Only ticket scans can tell who came back: null when the source cannot know.
  final int? reentrees;

  const PresenceFlow({
    required this.entrees,
    required this.sorties,
    required this.presents,
    this.reentrees,
  });
}

int _n(Map<String, int>? c, String key) => c?[key] ?? 0;

bool hasPhysicalCount(Map<String, int>? physique) =>
    _n(physique, 'entrees') > 0 || _n(physique, 'sorties') > 0;

/// The counters to show for a source; in [PresenceSource.combine] both sources are added up.
PresenceFlow flowOf(
  PresenceSource source,
  Map<String, int>? qr,
  Map<String, int>? physique,
) {
  final q = PresenceFlow(
    entrees: _n(qr, 'entrees'),
    sorties: _n(qr, 'sorties'),
    presents: _n(qr, 'presents'),
    reentrees: _n(qr, 'reentrees'),
  );
  final p = PresenceFlow(
    entrees: _n(physique, 'entrees'),
    sorties: _n(physique, 'sorties'),
    presents: _n(physique, 'presents'),
  );
  switch (source) {
    case PresenceSource.qr:
      return q;
    case PresenceSource.physique:
      return p;
    case PresenceSource.combine:
      return PresenceFlow(
        entrees: q.entrees + p.entrees,
        sorties: q.sorties + p.sorties,
        presents: q.presents + p.presents,
        reentrees: q.reentrees,
      );
  }
}

/// What each source adds to a combined counter (`entrees`, `sorties` or `presents`).
({int qr, int physique}) contributions(
  String key,
  Map<String, int>? qr,
  Map<String, int>? physique,
) => (qr: _n(qr, key), physique: _n(physique, key));
