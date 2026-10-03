# FPS Guard (NeoForge 1.21.1) – sadece istemci

Mesafeleri (render / simulation / entity) FPS ve RAM durumuna göre otomatik kısar,
durum düzelince geri açar. Doku, shader ya da başka modun grafiğine dokunmaz.

## Ne yapar
- Son N saniyenin ortalama FPS'i hedefin altına inerse mesafeleri 1 kademe kısar, hedefin üstüne çıkınca geri açar.
- Java heap eşiği aşılırsa çöp toplama ister (aralık ayarlı); sonra da yüksekse mesafeyi kısar.
- CPU yükü yüksek ve FPS düşükse 2 kademe birden kısar, CPU yüksekken geri açmaz.
- Pencere arka plandayken FPS sınırını düşürür.
- Profiller: LIGHT / BALANCED / AGGRESSIVE / CUSTOM (**F7** ile değişir).
- Gösterge: köşe, boyut, FPS/RAM/CPU/Guard satırları ayarlanır. **F8** gösterge, **F9** Guard aç/kapat.
- Tüm ayarlar oyun içinde: **Mods -> FPS Guard -> Config** (anında uygulanır).
- Dünyadan çıkarken / oyun kapanırken senin orijinal ayarlarını geri yazar.

Ayar dosyası: `.minecraft/config/fpsguard-client.toml`

## Derleme (kendi bilgisayarında, internet gerekir)
1. JDK 21 kur (Temurin 21 önerilir).
2. En kolayı: klasörü **IntelliJ IDEA** ile aç (Gradle projesi olarak içe aktarır) ->
   sağdaki Gradle paneli -> Tasks -> build -> build.
   Ya da Gradle 8.10+ kuruluysa bu klasörde: `gradle build`
3. Jar: `build/libs/fpsguard-neoforge-1.21.1-1.0.0.jar` -> `.minecraft/mods` klasörüne at.

`build.gradle` içindeki `version = '21.1.172'` NeoForge sürümü bulunamazsa, 21.1.x serisinden
güncel bir sürüm yaz. Derleme hatası çıkarsa hatayı olduğu gibi gönder, düzeltirim.
