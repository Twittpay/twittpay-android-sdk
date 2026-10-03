package com.twittpay.sdk;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

/**
 * Shows the TwittPay checkout inside your app.
 *
 * The SDK holds no key and makes no API call. Your own server creates the payment and
 * gives your app the payment link. This screen opens the link, watches for your success
 * and cancel addresses, and returns the result.
 *
 * Never trust the result on its own. Ask your server to verify the transaction.
 */
public class TwittPayCheckoutActivity extends Activity {

    public static final String EXTRA_PAYMENT_URL = "twittpay_payment_url";
    public static final String EXTRA_SUCCESS_URL = "twittpay_success_url";
    public static final String EXTRA_CANCEL_URL  = "twittpay_cancel_url";

    /** Result extras. */
    public static final String RESULT_STATUS         = "twittpay_status";
    public static final String RESULT_TRANSACTION_ID = "twittpay_transaction_id";

    public static final String STATUS_SUCCESS   = "success";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_FAILED    = "failed";

    /**
     * Build the intent that opens the checkout.
     *
     * @param paymentUrl the payment_url your server got from TwittPay (must be https)
     * @param successUrl the same success_url your server sent when it created the payment
     * @param cancelUrl  the same cancel_url your server sent when it created the payment
     */
    public static Intent createIntent(Context context, String paymentUrl, String successUrl, String cancelUrl) {
        if (paymentUrl == null || !paymentUrl.toLowerCase().startsWith("https://")) {
            throw new IllegalArgumentException("paymentUrl must be an https address");
        }
        if (successUrl == null || successUrl.isEmpty() || cancelUrl == null || cancelUrl.isEmpty()) {
            throw new IllegalArgumentException("successUrl and cancelUrl are required");
        }

        Intent intent = new Intent(context, TwittPayCheckoutActivity.class);
        intent.putExtra(EXTRA_PAYMENT_URL, paymentUrl);
        intent.putExtra(EXTRA_SUCCESS_URL, successUrl);
        intent.putExtra(EXTRA_CANCEL_URL, cancelUrl);
        return intent;
    }

    private WebView webView;
    private ProgressBar progress;
    private String successUrl;
    private String cancelUrl;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String paymentUrl = getIntent().getStringExtra(EXTRA_PAYMENT_URL);
        successUrl = getIntent().getStringExtra(EXTRA_SUCCESS_URL);
        cancelUrl  = getIntent().getStringExtra(EXTRA_CANCEL_URL);

        if (paymentUrl == null || successUrl == null || cancelUrl == null) {
            finishWith(STATUS_FAILED, null);
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 8, Gravity.TOP);
        root.addView(progress, pl);

        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                finishWith(STATUS_FAILED, null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? ViewGroup.GONE : ViewGroup.VISIBLE);
            }
        });

        webView.loadUrl(paymentUrl);
    }

    /** Returns true when we took over the address and the WebView must not load it. */
    private boolean handleUrl(String url) {
        if (url == null) {
            return true;
        }

        if (url.startsWith(successUrl)) {
            Uri uri = Uri.parse(url);
            String id = uri.getQueryParameter("transactionId");
            if (id == null) {
                id = uri.getQueryParameter("transaction_id");
            }
            finishWith(STATUS_SUCCESS, id);
            return true;
        }

        if (url.startsWith(cancelUrl)) {
            finishWith(STATUS_CANCELLED, null);
            return true;
        }

        String lower = url.toLowerCase();
        if (lower.startsWith("https://")) {
            return false;
        }

        // Anything that is not https (a wallet app link, a phone call) goes to the system.
        if (!lower.startsWith("http://") && !lower.startsWith("javascript:") && !lower.startsWith("file:")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException ignored) {
                // No app can open it. Stay on the page.
            }
        }
        return true;
    }

    private void finishWith(String status, String transactionId) {
        if (finished) {
            return;
        }
        finished = true;

        Intent data = new Intent();
        data.putExtra(RESULT_STATUS, status);
        if (transactionId != null) {
            data.putExtra(RESULT_TRANSACTION_ID, transactionId);
        }
        setResult(STATUS_SUCCESS.equals(status) ? RESULT_OK : RESULT_CANCELED, data);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finishWith(STATUS_CANCELLED, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
