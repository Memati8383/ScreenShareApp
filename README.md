# Screen Mirror

WebRTC ile gerçek zamanlı ekran paylaşımı Android uygulaması.

## 📱 Özellikler

- **Anlık Ekran Paylaşımı** - Gerçek zamanlı HD ekran yayını
- **Çoklu İzleyici** - Tek yayıncıya birden fazla izleyici bağlanabilir
- **Düşük Gecikme** - WebRTC ile milisaniyeler içinde
- **Bulut Çözümü** - Her yerden bağlanın, sınır yok
- **Kolay Bağlantı** - Tek oda adı ile hızlı kurulum
- **Güvenli İletişim** - SSL/WSS ile şifreli bağlantı
- **Tam Ekran Modu** - Kesintisiz izleme deneyimi

## 🛠 Teknolojiler

- **WebRTC** - WebRTC (Stream Fork: `io.getstream:stream-webrtc-android:1.3.10`)
- **OkHttp** - WebSocket bağlantısı (`4.12.0`)
- **Material Design** - Modern UI bileşenleri
- **Gson** - JSON veri işleme
- **SharedPreferences** - Yerel veri saklama

## 📋 Gereksinimler

- Android 8.0+ (API 26)
- İnternet bağlantısı
- Ekran paylaşımı izni

## 🚀 Kurulum

1. Repoyu klonlayın:
```bash
git clone https://github.com/Memati8383/ScreenShareApp.git
```

2. Android Studio'da açın

3. Gradle senkronizasyonunu bekleyin

4. Çalıştırın

## 📁 Proje Yapısı

```
app/src/main/java/com/example/screenmirror/
├── MainActivity.kt          # Ana ekran
├── SplashActivity.kt        # Açılış ekranı
├── SenderActivity.kt        # Yayın yapan ekran
├── ViewerActivity.kt        # İzleyen ekran
├── ScreenShareService.kt    # WebRTC servisi
├── CloudSignalingClient.kt  # WebSocket bağlantısı
├── RecentActivity.kt        # Son oturumlar
├── ProfileActivity.kt       # Profil yönetimi
├── HelpActivity.kt          # Yardım ekranı
├── AboutActivity.kt         # Hakkında ekranı
├── FeedbackActivity.kt      # Geri bildirim formu
└── data/
    ├── RoomHistory.kt       # Oda veri modeli
    └── RoomHistoryManager.kt # Yerel veri yönetimi
```

## 🔧 Nasıl Çalışır?

### Yayın Başlatma (Host)
1. "YAYINLA" butonuna tıklayın
2. Oda adını girin
3. "BAŞLA" butonuna tıklayın
4. Ekran paylaşımı izni verin
5. İzleyicilerin bağlanmasını bekleyin

### Yayın İzleme (Client)
1. "İZLE" butonuna tıklayın
2. Aynı oda adını girin
3. "BAĞLAN" butonuna tıklayın
4. Yayını izlemeye başlayın
5. Birden fazla izleyici aynı anda bağlanabilir

## 🌐 Bağlantı

Uygulama `wss://wss.getlost.ovh` WebSocket sunucusu üzerinden sinyalleşme yapar.

## 📸 Ekran Görüntüleri

Uygulama içinde:
- 🏠 Ana ekran (Yayınla/İzle seçimi)
- 📋 Son odalar geçmişi
- 👤 Profil yönetimi
- ❓ Yardım SSS
- ℹ️ Hakkında ekranı
- 💬 Geri bildirim formu

## 👨‍ Geliştirici

**Ferit Akdemir**
- GitHub: [Memati8383](https://github.com/Memati8383)
- Instagram: [@ferit22901](https://www.instagram.com/ferit22901/)
- E-posta: akdemirferit608@gmail.com

## 📄 Lisans

Bu proje açık kaynaklıdır.

---

⭐ Bu projeyi beğendiyseniz yıldız vermeyi unutmayın!
