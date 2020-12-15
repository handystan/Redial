package ru.handy.android.rd;

import android.app.Activity;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.ArrayList;
import java.util.List;

/**
 * класс помогающий осуществлять покупки в приложении через billingClient
 * Created by Андрей on 06.03.2016, modified by Андрей on 16.09.20
 */
public class Pay implements PurchasesUpdatedListener {

    // идентификаторы продукта, которые покупается с помощью billingClient
    //public static final String ITEM_SKU_99rub = "ru.handy.android.rd.99rub";
    public static final String ITEM_SKU_99rub = "android.test.purchased";
    //public static final String ITEM_SKU_99rub = "android.test.canceled";
    private BillingClient billingClient;
    private Activity act;
    private List<SkuDetails> skuDetList = new ArrayList<>(); //список с идентификаторами возможных покупок
    private int reqCode; //код запроса при покупке, который помогает определить дальнейшие действия после покупки


    public Pay(Activity activity) {
        act = activity;
        billingClient = BillingClient.newBuilder(act).enablePendingPurchases().setListener(this).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.i("myLogs", "Billing client successfully set up");
                    //заполняем список с идентификаторами возможных покупок
                    List<String> skuList = new ArrayList<>();
                    skuList.add(ITEM_SKU_99rub);
                    SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
                    params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
                    billingClient.querySkuDetailsAsync(params.build(),
                            new SkuDetailsResponseListener() {
                                @Override
                                public void onSkuDetailsResponse(BillingResult billingResult,
                                                                 List<SkuDetails> skuDetailsList) {
                                    skuDetList = skuDetailsList;
                                }
                            });
                } else {
                    Log.i("myLogs", "Billing client set up with responseCode = " + billingResult.getResponseCode());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                //billingClient = null;
                Log.i("myLogs", "Billing service disconnected");
            }
        });
    }

    /**
     * проведение платежа с consumable и non-consumable Item
     *
     * @param itemSKU     - идентификатор продукта, который оплачивается
     * @param requestCode - идентификатор запроса на оплату
     * @return код ответа
     */
    public int purchase(String itemSKU, int requestCode) {
        if (billingClient == null || !billingClient.isReady()) return -1;
        reqCode = requestCode;
        SkuDetails skuDetails = null; // искомый продукт, который хотят купить
        for (SkuDetails skuDets : skuDetList) {
            if (skuDets.getSku().equals(itemSKU)) {
                skuDetails = skuDets;
                break;
            }
        }
        if (skuDetails == null) return -1;
        BillingFlowParams purchaseParams = BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build();
        int responseCode = billingClient.launchBillingFlow(act, purchaseParams).getResponseCode();
        return responseCode;
    }

    /**
     * слушатель, который обрабатывает покупку
     *
     * @param billingResult
     * @param purchases
     */
    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                String itemSKU = purchase.getSku();
                Toast.makeText(act.getApplicationContext(), s(R.string.thank_you), Toast.LENGTH_LONG).show();
                Log.d("myLogs", "Оплата произведена успешно! Но пока без подтверждения.");
                final int thisSKU = itemSKU.equals(ITEM_SKU_99rub) ? 99 : 0;
                // признаем покупку (иначе через 3 дня пользователю деньги вернуться обратно
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    // 1001 - ответ пришел от RedialSettings при покупке количества попыток довзона > 4
                    if (reqCode == 1001 && thisSKU == 99) {
                        ((RedialSettings) act).purchased();
                    }
                    if (!purchase.isAcknowledged()) {
                        AcknowledgePurchaseParams acknowledgePurchaseParams =
                                AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(purchase.getPurchaseToken())
                                        .build();
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                            @Override
                            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    Log.i("myLogs", "Покупка подтверждена");
                                } else {
                                    Log.w("myLogs", "Внимание! Покупка не подтверждена!");
                                    ((RedialSettings) act).notAcknowledgedPurchase();
                                }
                            }
                        });
                    }
                }
            }
        } else {
            Toast.makeText(act.getApplicationContext(), s(R.string.purchase_error), Toast.LENGTH_LONG).show();
            Log.i("myLogs", "Покупка не прошла. BillingResponseCode = " + billingResult.getResponseCode());
        }
    }

    /**
     * есть ли сохраненная информация в Google Play о покупаках данного клиента
     *
     * @return оплаченная сумма, сохраненная в Google Play. -1 означает сервисы не готовы
     */
    public int amountOfPurchased() {
        int amountDonate = 0;
        if (billingClient == null || !billingClient.isReady()) return -1;
        try {
            Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(BillingClient.SkuType.INAPP);
            for (Purchase purchase : purchasesResult.getPurchasesList()) {
                String sku = purchase.getSku();
                if (sku.equals(ITEM_SKU_99rub)) amountDonate += 99;
            }
        } catch (final Exception e) {
            Log.e("myLogs", "e = " + e.getMessage());
            return -1;
        }
        return amountDonate;
    }

    /**
     * делаем продукт доступным для многоразовой оплаты. (в этом приложении не используется)
     *
     * @param itemSKU       - идентификатор продукта, который оплачен
     * @param purchaseToken - идентификатор покупки
     * @throws RemoteException
     */
    public void consume(final String itemSKU, String purchaseToken) {
        ConsumeParams consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build();
        billingClient.consumeAsync(consumeParams, new ConsumeResponseListener() {

            @Override
            public void onConsumeResponse(@NonNull BillingResult billingResult, @NonNull String purchaseToken) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.i("myLogs", "Продукт " + itemSKU + " оплачен и снова доступен для покупки");
                } else {
                    int thisSKU = itemSKU.equals(ITEM_SKU_99rub) ? 99 : 0;
                    Log.w("myLogs", "Внимание! Не сработал метод Consume (подтвеждение и возможность повторного перевода)!");
                }
            }
        });
    }

    private String s(int res) {
        return act.getResources().getString(res);
    }

    public void close() {
        if (billingClient != null) {
            billingClient.endConnection();
            billingClient = null;
        }
        Log.d("myLogs", "pay is closed");
    }
}
