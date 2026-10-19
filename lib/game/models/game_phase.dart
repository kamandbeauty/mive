enum GamePhase {
  idle,
  swapping,
  resolving,
  dropping,
  refilling,
  checking,
  paused,
  won,
  lost,
}

extension GamePhaseStatus on GamePhase {
  bool get acceptsInput => this == GamePhase.idle;
  bool get isTerminal => this == GamePhase.won || this == GamePhase.lost;
}
