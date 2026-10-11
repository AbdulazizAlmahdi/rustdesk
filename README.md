# RustDesk Android Standalone (P2P Direct Remote Desktop)

تطبيق أندرويد مستقل كلياً للتحكم بسطح المكتب ومشاركة الشاشة بتقنية Peer-to-Peer المباشرة، بدون أي خوادم وسيطة أو خدمات سحابية خارجية.

## المميزات
* **اتصال مباشر (Serverless P2P):** اتصال فوري عبر شبكة الـ Wi-Fi المحلية أو شبكات VPN المباشرة (مثل Tailscale / WireGuard).
* **هوية RustDesk وتجربة AnyDesk:** تصميم حديث متناسق يجمع بين الهوية البصرية الرسمية لـ RustDesk ومرونة واجهة AnyDesk.
* **مشاركة الشاشة (Host Mode):** بث الشاشة بالزمن الحقيقي واستقبال أوامر اللمس والتحكم.
* **التحكم عن بعد (Viewer Mode):** الاتصال بأي جهاز آخر وعرض شاشته ونقل أوامر اللمس.

## البناء والتثبيت
للبناء والتثبيت على الجهاز المتصل مباشرة:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
