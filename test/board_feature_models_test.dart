import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/models/board_obstacle.dart';
import 'package:puzzle/game/models/special_power.dart';
import 'package:puzzle/game/models/tile_lock.dart';

void main() {
  test('special powers have distinct player-facing Persian labels', () {
    expect(
      SpecialPower.values.map((power) => power.persianName).toSet(),
      hasLength(4),
    );
    expect(SpecialPower.areaBomb.persianName, 'کدو بمبی');
    expect(SpecialPower.lineClear.persianName, 'موشک توت‌فرنگی');
    expect(SpecialPower.colorBomb.persianName, 'گوی آینه‌ای دیسکو');
    expect(SpecialPower.rainbowKite.persianName, 'بادبادک رنگین‌کمانی');
  });

  test('obstacle types share consistent layers and movement traits', () {
    expect(
      BoardObstacleType.values.map((type) => type.persianName).toSet(),
      hasLength(4),
    );
    expect(BoardObstacleType.woodBox.blocksSwaps, isTrue);
    expect(BoardObstacleType.woodBox.containsFruit, isFalse);
    expect(BoardObstacleType.ice.blocksGravity, isTrue);
    expect(BoardObstacleType.vine.blocksSwaps, isTrue);
    expect(BoardObstacleType.soil.blocksSwaps, isFalse);
    expect(BoardObstacleType.soil.blocksGravity, isFalse);

    const twoLayerIce = BoardObstacle(type: BoardObstacleType.ice, layers: 2);
    final oneLayerIce = twoLayerIce.afterHit();
    expect(oneLayerIce?.type, BoardObstacleType.ice);
    expect(oneLayerIce?.layers, 1);
    expect(const BoardObstacle(type: BoardObstacleType.ice).afterHit(), isNull);
  });

  test('one- and two-layer locks absorb exactly one hit per layer', () {
    expect(const TileLock().afterHit(), isNull);
    expect(const TileLock(layers: 2).afterHit(), const TileLock(layers: 1));
  });
}
