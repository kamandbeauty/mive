class ScoreManager {
  const ScoreManager();

  int scoreForMatch({required int matchedPieces, required int cascadeIndex}) {
    if (matchedPieces < 3) return 0;
    final sizeBonus = matchedPieces >= 4 ? (matchedPieces - 3) * 15 : 0;
    final cascadeBonus = cascadeIndex > 0 ? cascadeIndex * 10 : 0;
    return matchedPieces * 10 + sizeBonus + cascadeBonus;
  }
}
