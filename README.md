# FPS Guard 1.2.0 (NeoForge 1.21.1) - sadece istemci

Oyun odaktayken mesafeleri FPS/RAM/CPU durumuna göre kısıp geri açar; pencere arka plandayken uyku moduna geçer.
Doku, shader ya da başka modun grafiğine dokunmaz. Mixin kullanmaz (yalnızca NeoForge olayları ve vanilla ayarları).

## Oyun odaktayken (Guard)
- Son N saniyenin ortalama FPS'i hedefin altındaysa mesafeleri 1 kademe kısar, hedefin üstüne çıkınca geri açar.
- Ani düşüş (savaş/kalabalık): üst üste 2 sn FPS hedefin %50'sinin altındaysa 2-3 kademe birden kısar.
- Heap eşiği aşılırsa GC ister (aralıklı), sonra da yüksekse mesafeyi kısar.
- CPU yükü yüksekse daha hızlı kısar, CPU yüksekken geri açmaz.
- Tek oyunculuda render + simulation + entity, sunucuda render + entity kısılır (client'ın simulation değeri sunucuda kullanılmaz).
- İsteğe bağlı: Guard kısmışken uzaktaki canlı entity çizimlerini atlar (varsayılan kapalı).

## Pencere arka plandayken (Uyku modu)
Durumlar: FOCUSED -> ENTERING_SLEEP (varsayılan 2 sn bekleme) -> SLEEPING -> FOCUSED. Her biri ayrı açılıp kapanır:
| Ayar | Varsayılan | Ne yapar |
|---|---|---|
| enableBackgroundFpsLimit / backgroundFps | açık / 5 | Pencerenin FPS sınırını düşürür (options.txt'e yazılmaz) |
| enableDynamicRender / backgroundRenderDistance | açık / 2 | Render mesafesini düşürür, öne gelince geri yükler (sunucuda varsayılan olarak dokunmaz) |
| reduceSimulationInBackground | KAPALI | Simulation düşer; tek oyunculuda uzak makineler/çiftlikler durur, önerilmez. Vanilla en az 5 |
| enableAutoMute | açık | Ana sesi 0 yapar, eski değere döndürür (zaten 0 ise 0 kalır) |
| enableParticleCulling | açık | Parçacık ayarını MINIMAL yapar, geri yükler |
| enableEntityCulling | açık | Canlı entity çizimlerini atlar (entity mantığı/tick durmaz) |
| enableRamCleaner | açık | Uykudan ~5 sn sonra hâlâ arka plandaysa BİR kez GC ister |

Geri yükleme kuralı: bir ayarı yalnızca biz değiştirdiysek ve değeri hâlâ bizim uyguladığımız değerse geri yükleriz.

## Profiller (F7)
| Profil | Etkisi (Guard) |
|---|---|
| LIGHT | az müdahale: min render >= 8, RAM eşiği >= %92, yavaş kısma |
| BALANCED | config değerleri olduğu gibi |
| AGGRESSIVE | min render <= 4, entity %50'ye kadar, RAM eşiği <= %80, hızlı kısma |
| EXTREME | min render 2, entity %50, RAM eşiği <= %75, 1 sn kısma, ani düşüşe duyarlı |
| CUSTOM | yalnızca senin değerlerin |

## Tuşlar (Controls menüsünden değişir)
F7 profil, F8 gösterge, F9 Guard aç/kapat. Tüm ayarlar: Mods -> FPS Guard -> Config. Dosya: `config/fpsguard-client.toml`.

## Bilmen gerekenler
- System.gc() yalnızca bir istektir; JVM uygulamayı garanti etmez ve işletim sisteminin RAM'ini anında düşürmez.
  Java başlatma argümanlarında `-XX:+DisableExplicitGC` varsa System.gc() hiçbir şey yapmaz, o argümanı kaldır.
- FPS sınırı veya mesafe kısmak "gerçek optimizasyon" değildir; yükü azaltır. Gerçek kazanç RAM, mod sayısı ve Sodium/Lithium gibi modlardan gelir.
- Oyun uykudayken çökerse options.txt'e geçici değerler yazılmış olabilir; oyun ayarlarından kontrol et.

## Derleme
GitHub Actions ile (`.github/workflows/build.yml`) ya da JDK 21 + Gradle ile `gradle build`. Jar: `build/libs/`.
