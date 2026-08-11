# 🔒 Gizlilik Politikası

**Screen Mirror** — Son güncelleme: 11 Ağustos 2026

Bu gizlilik politikası, Screen Mirror Android uygulamasının ("uygulama") hangi verileri hangi amaçlarla işlediğini açıklar.

## TL;DR

- Ekran görüntünüz **asla sunucularımızda depolanmaz**; akış doğrudan izleyicilere WebRTC üzerinden iletilir.
- Uygulama **hesap sistemi kullanmaz**; e-posta, telefon veya kimlik bilgisi toplamayız.
- Oturum geçmişiniz ve profiliniz **yalnızca cihazınızda** saklanır.

---

## 1. Toplanan Veriler

### 1.1 Sinyalleşme Verileri
Bağlantı kurulumu için oda adı ve rastgele üretilen bir oturum kimliği (`peerId`) `wss://wss.getlost.ovh` adresindeki sinyalleşme sunucusuna iletilir. Bu sunucu yalnızca WebRTC bağlantısının kurulmasına aracılık eder; **video akışını görmez ve kaydetmez**.

### 1.2 Ekran İçeriği
Ekran yakalama, Android'in `MediaProjection` API'si ile her oturumda açık sistem onayı alınarak yapılır. Yakalanan görüntü:
- Şifreli WebRTC kanalı üzerinden doğrudan izleyicilere akar (mümkünse uçtan uca, aksi halde TURN relay üzerinden),
- Geliştiriciye veya herhangi bir üçüncü tarafa **gönderilmez**,
- Yayıncının kendi cihazındaki kayıtlar hariç **kalıcı olarak saklanmaz**.

### 1.3 Cihazda Saklanan Veriler
Aşağıdaki veriler yalnızca cihazınızın yerel depolama alanında (SharedPreferences) tutulur, dışarı aktarılmaz:
- Oda geçmişi (son 50 oturum: oda adı, rol, süre, katılımcı sayısı)
- Profil bilgileri (takma ad, profil fotoğrafı)
- Kullanım istatistikleri (haftalık aktivite)
- Uygulama ayarları (dil, çözünürlük, FPS)

### 1.4 TURN Kimlik Bilgileri
Ayarladığınız TURN sunucusu bilgileri, Android KeyStore'da donanım destekli **AES-256/GCM** anahtarı ile şifrelenerek saklanır; düz metin olarak tutulmaz.

## 2. İzinler ve Amaçları

| İzin | Amaç |
|---|---|
| Ekran yakalama (MediaProjection) | Yayının kendisi — her oturumda onay ister |
| İnternet | WebRTC akışı ve sinyalleşme |
| Bildirimler | Yayın/kayıt durumu bilgilendirmesi |
| Kamera | Yalnızca profil fotoğrafı çekimi |
| Titreşim | Dokunma geri bildirimi (haptik) |
| Medya erişimi | Kayıt ve ekran görüntülerinin galeriye kaydı |

## 3. Üçüncü Taraflar

Uygulama; analitik, reklam veya izleme SDK'ları **içermez**. Varsayılan TURN sunucusu (`openrelay.metered.ca`) üçüncü taraf bir açık relay hizmetidir ve yalnızca kısıtlı ağlarda medya trafiğinin iletimine aracılık eder; kendi TURN sunucunuzu yapılandırarak bu hizmeti devre dışı bırakabilirsiniz.

## 4. Veri Saklama Süresi

- Sinyalleşme sunucusundaki oda kayıtları oturum sonunda silinir.
- Cihazdaki yerel veriler, uygulamayı kaldırmanızla veya ilgili menülerden (ör. "Varsayılanlara sıfırla", oda geçmişini temizle) tamamen silinir.

## 5. Çocukların Gizliliği

Uygulama 13 yaş altı çocuklardan bilinçli olarak veri toplamaz.

## 6. Değişiklikler

Bu politikada yapılan değişiklikler bu dosyada ve uygulama sürüm notlarında duyurulur.

## 7. İletişim

Gizlilikle ilgili sorularınız için: **akdemirferit608@gmail.com**
