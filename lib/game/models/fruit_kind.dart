enum FruitKind { strawberry, grapes, blueberry, orange, lemon, watermelon }

extension FruitKindPresentation on FruitKind {
  String get assetPath => 'assets/fruit/$name.png';

  String get persianName => switch (this) {
        FruitKind.strawberry => 'توت‌فرنگی',
        FruitKind.grapes => 'انگور',
        FruitKind.blueberry => 'بلوبری',
        FruitKind.orange => 'پرتقال',
        FruitKind.lemon => 'لیمو',
        FruitKind.watermelon => 'هندوانه',
      };
}
