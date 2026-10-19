enum SpecialPower { areaBomb, lineClear, colorBomb, rainbowKite }

extension SpecialPowerPresentation on SpecialPower {
  String get persianName => switch (this) {
        SpecialPower.areaBomb => 'کدو بمبی',
        SpecialPower.lineClear => 'موشک توت‌فرنگی',
        SpecialPower.colorBomb => 'گوی آینه‌ای دیسکو',
        SpecialPower.rainbowKite => 'بادبادک رنگین‌کمانی',
      };

  String get shortName => switch (this) {
        SpecialPower.areaBomb => 'کدو',
        SpecialPower.lineClear => 'موشک',
        SpecialPower.colorBomb => 'گوی',
        SpecialPower.rainbowKite => 'بادبادک',
      };

  /// Artwork used by the in-game power tray.
  String get artAsset => switch (this) {
        SpecialPower.areaBomb => 'assets/boosters/fruit-bomb.png',
        SpecialPower.lineClear => 'assets/boosters/rocket.png',
        SpecialPower.colorBomb => 'assets/boosters/rainbow-orb.png',
        SpecialPower.rainbowKite => 'assets/boosters/rainbow-kite.png',
      };
}
