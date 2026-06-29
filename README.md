# Funsol Billing Helper

[![](https://jitpack.io/v/Funsol-Projects/Funsol-Billing-Helper.svg)](https://jitpack.io/#Funsol-Projects/Funsol-Billing-Helper)

A lightweight wrapper around Google Play Billing Library (**billing-ktx 9.1.0**) that simplifies in-app purchases and subscriptions in Android apps.

**Features**
- One-time (in-app) products and subscriptions
- Multiple purchase options and offers for one-time products (buy, rent, pre-order, discount)
- Consumable product support
- Subscription upgrade / downgrade
- Purchase acknowledgment, consumption, and local purchase history
- Premium status helpers

### Product model

<p align="center">
  <img src=".github/images/billing-product-model.png" alt="Google Play Billing product model" width="900"/>
</p>

### Quick start

| Step | Description |
|------|-------------|
| [Step 1](#step-1--add-maven-repository) | Add JitPack repository |
| [Step 2](#step-2--add-dependencies) | Add library and required dependencies |
| [Step 3](#step-3--initialize--listeners) | Initialize `FunSolBillingHelper` and set listeners |
| [Step 4](#step-4--make-purchases) | Buy in-app products or subscribe |
| [Step 5](#step-5--product-details--prices) | Fetch prices and build your paywall UI |
| [Step 6](#step-6--check-premium-status) | Check if the user is premium |
| [Step 7](#step-7--cancel-subscription) | Open Play subscription management |
| [Step 8](#step-8--other-utilities) | Offer checks, history, and cleanup |

---

## Step 1 — Add Maven Repository

Add JitPack to your project-level `build.gradle` or `settings.gradle`:

```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

---

## Step 2 — Add Dependencies

Add the following to your **app-level** `build.gradle.kts` (or `build.gradle`):

```kotlin
dependencies {
    // Funsol Billing Helper
    implementation("com.github.Funsol-Projects:Funsol-Billing-Helper:v2.0.9")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // Room (used for purchase history)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
}
```

Make sure the `ksp` plugin is applied in your module if you use Kotlin DSL:

```kotlin
plugins {
    id("com.google.devtools.ksp")
}
```

---

## Step 3 — Initialize & Listeners

### Activity is required

Since **v2.0.9**, `FunSolBillingHelper` must be created with an **`Activity`**. A `Context` is not supported.

```kotlin
// Inside your Activity
private lateinit var funSolBillingHelper: FunSolBillingHelper

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    funSolBillingHelper = FunSolBillingHelper(this@MainActivity)
}
```

From a `Fragment`, pass `requireActivity()`.

### Subscriptions only

```kotlin
funSolBillingHelper
    .setSubProductIds(mutableListOf("subs_product_id_1", "subs_product_id_2"))
    .initialize()
```

### Subscriptions + in-app

```kotlin
funSolBillingHelper
    .setSubProductIds(mutableListOf("subs_product_id_1"))
    .setInAppProductIds(mutableListOf("inapp_product_id"))
    .initialize()
```

### Consumable in-app products

```kotlin
funSolBillingHelper
    .setInAppProductIds(mutableListOf("inapp_product_id", "consumable_product_id"))
    .setConsumableProductIds(mutableListOf("consumable_product_id"))
    .initialize()
```

> Add consumable product IDs in **both** `setInAppProductIds()` and `setConsumableProductIds()`.

### Optional configuration

```kotlin
funSolBillingHelper
    .enableLogging(isEnableLog = true)
    .initialize(enableShowInAppMessages = false)  // disable Play in-app messages
```

Call `initialize()` from your main billing Activity once product IDs are configured.

### Billing listeners

```kotlin
funSolBillingHelper
    .setSubProductIds(mutableListOf("subs_product_id"))
    .setInAppProductIds(mutableListOf("inapp_product_id"))
    .setBillingListener(object : BillingListener {
        override fun onClientReady() {
            // Products and active purchases loaded — safe to show paywall
        }

        override fun onClientInitError() {
            // Billing connection failed
        }

        override fun onClientAlreadyConnected() {
            // initialize() called while already connected
        }

        override fun onProductsPurchased(purchases: List<FunsolPurchase?>) {
            // Purchase completed
        }

        override fun onPurchaseAcknowledged(purchase: FunsolPurchase) {
            // Purchase acknowledged
        }

        override fun onPurchaseConsumed(purchase: FunsolPurchase) {
            // Consumable purchase consumed
        }

        override fun onBillingError(error: ErrorType) {
            when (error) {
                ErrorType.USER_CANCELED -> { }
                ErrorType.ITEM_UNAVAILABLE -> { }  // offer no longer eligible
                ErrorType.OFFER_NOT_EXIST -> { }
                ErrorType.PRODUCT_NOT_EXIST -> { }
                else -> { }
            }
        }
    })
    .initialize()
```

---

## Step 4 — Make Purchases

### Buy in-app (one-time) product

**Single buy option:**

```kotlin
funSolBillingHelper.buyInApp(
    activity = this@MainActivity,
    productId = "inapp_product_id",
    isPersonalizedOffer = false
)
```

**Multiple offers** (buy, rent, discount, pre-order) — use IDs from `getAllProductPrices()` (see Step 5):

```kotlin
funSolBillingHelper.buyInApp(
    activity = this@MainActivity,
    productId = "movie_product_id",
    offerId = "discount_offer_id",        // optional
    purchaseOptionId = "rent_option_id",    // optional
    isPersonalizedOffer = false
)
```

- `buyInApp()` resolves the correct `offerToken` and verifies eligibility before launch.
- If the offer is no longer available, `onBillingError(ErrorType.ITEM_UNAVAILABLE)` is called.
- In-app purchases are acknowledged automatically. Consumables are consumed when listed in `setConsumableProductIds()`.
- **Rent products:** Play provides the purchase token; your app must grant time-limited access using rental metadata from Step 5.
- **`isPersonalizedOffer`:** set `true` in the EU when the price was personalized using automated decision-making.

### Subscribe

```kotlin
funSolBillingHelper.subscribe(this@MainActivity, "base_plan_id")
funSolBillingHelper.subscribe(this@MainActivity, "base_plan_id", "offer_id")
```

### Upgrade or downgrade subscription

```kotlin
funSolBillingHelper.upgradeOrDowngradeSubscription(
    this@MainActivity,
    "new_base_plan_id",
    "new_offer_id",   // or null
    "old_base_plan_id",
    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.IMMEDIATE_AND_CHARGE_FULL_PRICE
)
```

| Replacement mode | Behavior |
|------------------|----------|
| `DEFERRED` | Takes effect when the old plan expires |
| `IMMEDIATE_AND_CHARGE_FULL_PRICE` | Immediate; full price + prorated credit |
| `IMMEDIATE_AND_CHARGE_PRORATED_PRICE` | Immediate; same billing cycle |
| `IMMEDIATE_WITHOUT_PRORATION` | Immediate; new price on next recurrence |
| `IMMEDIATE_WITH_TIME_PRORATION` | Immediate; remaining time prorated |

---

## Step 5 — Product Details & Prices

Use this step to populate your paywall, then call `buyInApp()` or `subscribe()` from Step 4.

### Get all product prices (recommended for UI lists)

Returns **one entry per eligible offer** for in-app and subscription products:

```kotlin
val prices = funSolBillingHelper.getAllProductPrices()
```

For in-app products with multiple offers, several rows share the same `productId` but have different `offerId` and `purchaseOptionId` values.

**Example: build UI in `onClientReady` and purchase on tap**

```kotlin
override fun onClientReady() {
    val inAppPrices = funSolBillingHelper.getAllProductPrices()
        .filter { it.type == BillingClient.ProductType.INAPP }

    inAppPrices.forEach { item ->
        // item.productId, item.offerId, item.purchaseOptionId
        // item.price, item.fullPriceMicro
        // item.oneTimeOffer — rent / pre-order / discount metadata
    }
}

fun onProductClicked(item: ProductPriceInfo) {
    funSolBillingHelper.buyInApp(
        activity = this@MainActivity,
        productId = item.productId,
        offerId = item.offerId.takeIf { it.isNotBlank() },
        purchaseOptionId = item.purchaseOptionId.takeIf { it.isNotBlank() }
    )
}
```

### `ProductPriceInfo` (in-app fields)

| Field | Description |
|-------|-------------|
| `productId` | Play product ID |
| `offerId` | Discount or pre-order offer ID |
| `purchaseOptionId` | Purchase option ID (e.g. buy vs rent) |
| `offerToken` | Offer token used by the billing flow |
| `price` / `priceMicro` | Formatted and micro-unit price |
| `fullPriceMicro` | Original price before discount |
| `oneTimeOffer` | Extended offer metadata |
| `duration` | `"lifeTime"`, rental period (ISO 8601), or `"preorder"` |

### One-time offer metadata (`oneTimeOffer`)

| Property | Description |
|----------|-------------|
| `rental` | Rent option — `rentalPeriod`, `rentalExpirationPeriod` |
| `preorder` | Pre-order — `releaseTimeMillis`, `presaleEndTimeMillis` |
| `discount` | Discount — `fullPriceMicros`, `percentageDiscount` or `discountAmountMicros` |
| `validTimeWindow` | Limited-time offer — `startTimeMillis`, `endTimeMillis` |
| `limitedQuantity` | Per-offer quantity cap — `maximumQuantity`, `remainingQuantity` |
| `offerTags` | Tags inherited from product, purchase option, and discount |
| `isRental` / `isPreorder` / `isDiscount` / `isStandardBuy` | Convenience flags |

### Get price for a specific in-app offer

```kotlin
funSolBillingHelper.getInAppProductPriceById(
    inAppProductId = "inapp_product_id",
    offerId = "discount_offer_id",
    purchaseOptionId = "rent_option_id"
)?.price
```

### Get all offers for one in-app product

```kotlin
funSolBillingHelper.getInAppProductOffers("inapp_product_id")
```

### Filter offers

```kotlin
funSolBillingHelper.getInAppProductOfferDetails("inapp_product_id")
funSolBillingHelper.getInAppBuyOffers("inapp_product_id")
funSolBillingHelper.getInAppRentOffers("inapp_product_id")
funSolBillingHelper.getInAppPreorderOffers("inapp_product_id")
funSolBillingHelper.getInAppDiscountOffers("inapp_product_id")
funSolBillingHelper.getInAppOffersByTag("inapp_product_id", "featured")
```

### Subscription prices

```kotlin
funSolBillingHelper.getSubscriptionProductPriceById("base_plan_id")?.price
funSolBillingHelper.getSubscriptionProductPriceById("base_plan_id", "offer_id")?.price
```

### Raw `ProductDetails`

```kotlin
funSolBillingHelper.getInAppProductDetail(
    productId = "inapp_product_id",
    productType = BillingClient.ProductType.INAPP
)

funSolBillingHelper.getSubscriptionProductDetail(
    productId = "base_plan_id",
    offerId = "offer_id",
    productType = BillingClient.ProductType.SUBS
)
```

---

## Step 6 — Check Premium Status

### Any active premium (in-app or subscription)

```kotlin
funSolBillingHelper.isPremiumUser
```

### In-app

```kotlin
funSolBillingHelper.isInAppPremiumUser()
funSolBillingHelper.isInAppPremiumUserByProductId("inapp_product_id")
```

### Subscription

```kotlin
funSolBillingHelper.isSubsPremiumUser()
funSolBillingHelper.isSubsPremiumUserByBasePlanId("base_plan_id")
funSolBillingHelper.isSubsPremiumUserBySubProductID("subscription_product_id")
```

---

## Step 7 — Cancel Subscription

Opens Google Play subscription management for the given subscription product ID:

```kotlin
funSolBillingHelper.unsubscribe(this@MainActivity, "subscription_product_id")
```

---

## Step 8 — Other Utilities

### Check offer availability

```kotlin
// Subscription offer
funSolBillingHelper.isSubscriptionOfferAvailable("base_plan_id", "offer_id")

// In-app offer
funSolBillingHelper.isInAppOfferAvailable(
    inAppProductId = "inapp_product_id",
    inAppOfferId = "discount_offer_id",
    purchaseOptionId = "rent_option_id"
)

// Re-check before purchase (region, quantity, or time window may have changed)
funSolBillingHelper.isOneTimeOfferStillEligible("inapp_product_id", offerToken)
```

### Purchase history

```kotlin
funSolBillingHelper.wasPremiumUser()            // suspend
funSolBillingHelper.getPurchasedPlansHistory()  // List<PurchasedProduct>
```

### Billing client lifecycle

```kotlin
funSolBillingHelper.areSubscriptionsSupported()
funSolBillingHelper.isBillingClientReady()
funSolBillingHelper.checkAndRetryForPurchaseAcknowledgement()
funSolBillingHelper.release()  // call when billing is no longer needed
```

---

## CHANGELOG

- 14-06-2023
  - Billing lib 6.0.0 updated
  - Implemented consumable one-time products
  - Billing Client Ready/Error Callbacks Added
  - Set Logging for Release or Debug (By default only logs on debug mode)
  - Now initialize billing lib in App class (if you want)
  - Billing client ready check issue solved
- 13-06-2024
  - Billing library updated to 7.0.0
  - Threading consumption improved
  - Billing client ready call back issue resolved
  - Products price fetching issues resolved
  - onPurchasesUpdated callback added to fetch updated premium status
  - Proper logging implemented
  - Price fetch missing related issues solved
- 02-07-2024
  - Must Call `.initialize()` after initial setup
  - ProductList empty crash resolved
- 12-09-2024
  - Micro Price variable added in product price info
  - price currency code added
  - Bugs solved
- 13-11-2024
  - Billing library updated to 7.1.1
  - `isSubscriptionOfferAvailable(basePlanId, offerId)` added
  - `wasPremiumUser()` and `getPurchasedPlansHistory()` added
  - `isPremiumUser` property added
  - `getInAppProductPriceById(inAppProductId)` added
  - Improved error handling and logging
- 17-12-2024
  - Offer purchase and Base Plan purchase conflict issue resolved
  - Billing client release issue resolved
- 20-12-2024
  - Downgrade to 7.0.0
- 11-3-2025
  - Minor bugs solved
  - Product refund issue solved
- 08-08-2025
  - Billing version update to 8.0.0
  - Converted to SDK
- 11-12-2025
  - Updated to billing-ktx 8.2.0
  - Made `upgradeOrDowngradeSubscription` public with nullable offerId
  - Fixed acknowledgment callback to avoid false ACK errors
  - Improved premium status update
- **29-06-2026 — v2.0.9**
  - Updated to billing-ktx **9.1.0**
  - Multiple purchase options and offers for one-time products (buy, rent, pre-order, discount)
  - `getAllProductPrices()` returns one entry per eligible in-app offer
  - `buyInApp()` supports `offerId` and `purchaseOptionId`
  - `ProductPriceInfo` extended with `purchaseOptionId`, `offerToken`, `fullPriceMicro`, `oneTimeOffer`
  - `OneTimeProductOfferInfo` for rental, pre-order, discount, time window, quantity, and tags
  - Offer filter helpers and `isOneTimeOfferStillEligible()`
  - `isInAppOfferAvailable()` added; subscription check renamed to `isSubscriptionOfferAvailable()`
  - `FunSolBillingHelper` requires an `Activity` (not `Context`)
  - `initialize(enableShowInAppMessages)` for Play in-app messages

## License

#### MIT License
#### Copyright (c) 2026 Funsol Technologies Pvt Ltd

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
