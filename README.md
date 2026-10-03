# FPS Guard (NeoForge 1.21.1) – sadece istemci

Mesafeleri (render / simulation / entity) FPS ve RAM durumuna göre otomatik kısar,
durum düzelince geri açar. Doku, shader ya da başka modun grafiğine dokunmaz.

## Ne yapar
- Son 4 saniyenin ortalama FPS'i hedefin (varsayılan 60) altına inerse 1 kademe kısar (4 sn'de bir).
- FPS hedefin 30 üstüne çıkarsa ve RAM rahatsa 1 kademe geri açar (20 sn'de bir).
- Java heap %88'in üstünde 3 sn kalırsa: parçacıkları temizler + GC ister (dakikada en fazla 1).
  GC'den sonra da yüksekse render mesafesini kısar.
- Pencere arka plandayken FPS sınırı 10'a iner, öne gelince eski haline döner.
- Dünyaya girince 20 sn bekler (chunk yüklenirken ayar oynamaz).
- Sen ayarı elle değiştirirsen yeni değeri "senin ayarın" kabul eder.
- Dünyadan çıkarken / oyun kapanırken senin orijinal ayarlarını geri yazar.
- Sol üstte FPS / RAM göstergesi. **F8** gösterge, **F9** Guard aç/kapat.

Ayarlar: `.minecraft/config/fpsguard-client.toml`

## Derleme (kendi bilgisayarında, internet gerekir)
1. JDK 21 kur (Temurin 21 önerilir).
2. En kolayı: klasörü **IntelliJ IDEA** ile aç (Gradle projesi olarak içe aktarır) ->
   sağdaki Gradle paneli -> Tasks -> build -> build.
   Ya da Gradle 8.10+ kuruluysa bu klasörde: `gradle build`
3. Jar: `build/libs/fpsguard-neoforge-1.21.1-1.0.0.jar` -> `.minecraft/mods` klasörüne at.

`build.gradle` içindeki `version = '21.1.172'` NeoForge sürümü bulunamazsa, 21.1.x serisinden
güncel bir sürüm yaz. Derleme hatası çıkarsa hatayı olduğu gibi gönder, düzeltirim.
