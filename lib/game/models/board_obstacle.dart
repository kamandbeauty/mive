/// Board obstacle kinds share one durability and hit-resolution contract.
enum BoardObstacleType { woodBox, ice, vine, soil }

extension BoardObstaclePresentation on BoardObstacleType {
  String get persianName => switch (this) {
        BoardObstacleType.woodBox => 'جعبهٔ چوبی',
        BoardObstacleType.ice => 'یخ',
        BoardObstacleType.vine => 'پیچک',
        BoardObstacleType.soil => 'خاک باغچه',
      };

  bool get blocksSwaps => switch (this) {
        BoardObstacleType.woodBox ||
        BoardObstacleType.ice ||
        BoardObstacleType.vine => true,
        BoardObstacleType.soil => false,
      };

  bool get blocksGravity => blocksSwaps;
  bool get containsFruit => this != BoardObstacleType.woodBox;
}

/// A single board cell's obstacle. Each direct or neighboring match hit removes
/// exactly one layer, and a zero-layer obstacle is removed from the board.
class BoardObstacle {
  const BoardObstacle({
    required this.type,
    this.layers = 1,
  }) : assert(layers == 1 || layers == 2);

  final BoardObstacleType type;
  final int layers;

  BoardObstacle? afterHit() =>
      layers == 1 ? null : BoardObstacle(type: type, layers: layers - 1);
}
