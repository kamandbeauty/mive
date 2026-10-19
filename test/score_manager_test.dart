import 'package:flutter_test/flutter_test.dart';
import 'package:puzzle/game/services/score_manager.dart';

void main() {
  const scoreManager = ScoreManager();

  test('match size and cascade multiplier produce deterministic scores', () {
    expect(scoreManager.scoreForMatch(matchedPieces: 2, cascadeIndex: 0), 0);
    expect(scoreManager.scoreForMatch(matchedPieces: 3, cascadeIndex: 0), 30);
    expect(scoreManager.scoreForMatch(matchedPieces: 4, cascadeIndex: 0), 55);
    expect(scoreManager.scoreForMatch(matchedPieces: 3, cascadeIndex: 2), 50);
  });
}
