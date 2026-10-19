/// Immutable one- or two-layer blocker attached to a board cell.
///
/// A layer absorbs one clear hit. A one-layer lock disappears after the hit;
/// a two-layer lock remains with one layer and needs a second hit.
class TileLock {
  const TileLock({this.layers = 1}) : assert(layers == 1 || layers == 2);

  final int layers;

  TileLock? afterHit() => layers == 1 ? null : const TileLock(layers: 1);
}
