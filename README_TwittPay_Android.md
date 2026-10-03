# TwittPay Android SDK

Show the TwittPay checkout inside your Android app. Customers pay with bKash, Nagad,
Rocket or Upay without leaving the app, and they will not notice it is a web page.

**The SDK holds no key and makes no API call.** Your own server talks to TwittPay.

## How it works

1. Your app asks **your server** to start a payment for an order.
2. Your server calls TwittPay `POST /api/payment/create` with your Brand Key and gets a `payment_url`.
   (Use any of the TwittPay server addons, or the PHP / Laravel library.)
3. Your server returns `payment_url` to the app.
4. The app opens it with this SDK.
5. When the customer finishes, the SDK returns the result and the `transactionId`.
6. The app asks **your server** for the order status. Your server calls `/api/payment/verify`
   and marks the order paid. Never trust the result inside the app alone.

## Install

Add JitPack to `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Add the SDK to your app `build.gradle`:

```gradle
implementation 'com.github.Twittpay:twittpay-android-sdk:v2.3.1'
```

Internet permission and the checkout screen are added to your manifest automatically.

## Use it

Choose the `success_url` and `cancel_url` when your server creates the payment, and give the
**same two addresses** to the app. They can be any address on your site. They are never opened:
the SDK stops the page the moment it reaches them.

```java
ActivityResultLauncher<Intent> checkout = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            Intent data = result.getData();
            String status = data == null ? null : data.getStringExtra(TwittPayCheckoutActivity.RESULT_STATUS);
            String txn    = data == null ? null : data.getStringExtra(TwittPayCheckoutActivity.RESULT_TRANSACTION_ID);

            if (TwittPayCheckoutActivity.STATUS_SUCCESS.equals(status)) {
                // Ask YOUR server to verify txn, then show the receipt.
            } else {
                // Cancelled or failed.
            }
        });

// paymentUrl came from your server
checkout.launch(TwittPayCheckoutActivity.createIntent(
        this, paymentUrl,
        "https://yoursite.com/payment/success",
        "https://yoursite.com/payment/cancel"));
```

## Notes

* `paymentUrl` must be `https`.
* The back button goes back in the page, and cancels at the first page.
* Wallet app links (for example to open the bKash app) are passed to the system.
* Needs Android 5.0 (API 21) or newer. No other library is required.
