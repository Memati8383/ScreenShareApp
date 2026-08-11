# 🤝 Katkıda Bulunma Rehberi

Screen Mirror'a katkıda bulunmak istediğiniz için teşekkürler! Bu rehber, geliştirme sürecini sorunsuz hale getirmek için hazırlanmıştır.

## 🚀 Geliştirme Ortamı

- **Android Studio** güncel sürüm (AGP 9.2.1 gerektirir)
- **JDK 21** (Gradle daemon toolchain)
- **Gradle 9.4.1** (wrapper ile otomatik indirilir)
- Test için en az bir Android 8.0+ (API 26) cihaz veya emülatör
- Ekran paylaşımı akışını test etmek için **iki cihaz/emülatör** önerilir

## 🔀 Katkı Akışı

1. Projeyi **fork** edin
2. Bir özellik dalı oluşturun: `git checkout -b feature/ozellik-adi`
3. Değişikliklerinizi commit edin (Conventional Commits önerilir):
   - `feat:` yeni özellik
   - `fix:` hata düzeltmesi
   - `refactor:` davranışı değiştirmeyen yeniden düzenleme
   - `docs:` dokümantasyon
4. Dalınızı push edin ve **Pull Request** açın
5. PR şablonundaki kontrol listesini doldurun

## 🏗 Kod Kuralları

- Mevcut dosya düzenine ve paket yapısına uyun (`model/`, `data/`, `capture/`, `service/`)
- Servis durum değişikliklerini doğrudan UI'a değil, `ServiceEvent` üzerinden iletin
- Yeni hassas veri (kimlik bilgisi vb.) gerekiyorsa `SecureCredentialStore` desenini kullanın; düz metin SharedPreferences'ta saklamayın
- Kullanıcıya görünür metinler için `strings.xml` kullanın (hem `values/` hem `values-en/`)
- Servis/aktivite yaşam döngüsünde sızıntı bırakmayın (Handler callback'lerini temizleyin, WebRTC kaynaklarını `dispose` edin)

## ✅ Test Kontrolü

PR açmadan önce şunları doğrulayın:

- [ ] `./gradlew assembleDebug` hatasız derleniyor
- [ ] Yayıncı akışı: oda oluşturma → izin → yayın → dondurma → kayıt
- [ ] İzleyici akışı: oda girme → bağlanma → izleme → ekran görüntüsü
- [ ] Bağlantı kopması senaryosu (Wi-Fi kapat/aç) — otomatik yeniden bağlanma çalışıyor

## 🐛 Hata Bildirimi

Sorun bildirmeden önce mevcut [issue'ları](https://github.com/Memati8383/ScreenShareApp/issues) kontrol edin ve **Hata Bildirimi** şablonunu kullanın. Mümkünse `adb logcat` çıktısı ekleyin.

## 📜 Lisans

Katkılarınız, projenin [MIT Lisansı](LICENSE) kapsamında lisanslanmış sayılır.
