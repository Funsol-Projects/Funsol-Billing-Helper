# Funsol Billing Helper

[![](https://jitpack.io/v/Funsol-Projects/Funsol-Billing-Helper.svg)](https://jitpack.io/#Funsol-Projects/Funsol-Billing-Helper)

Funsol Billing Helper is a simple, straight-forward implementation of the Android v8.0.0 In-app billing API

> Support both IN-App and Subscriptions.

### **Billing v8.0.0 subscription model:**

![Subcription](https://user-images.githubusercontent.com/106656179/227849820-8b9e8566-fa6e-40d4-862e-77aaeaa65e6c.png)

## Getting Started

#### Extra Dependencies

```kotlin

dependencies {
    // Billing Client
    implementation("com.android.billingclient:billing:8.0.0")
    // Room DB
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
}


```  
## Step 1

Add maven repository in project level build.gradle or in latest project setting.gradle file

```kotlin 

    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
 
```  

## Step 2

Add Funsol Billing Helper dependencies in App level build.gradle.

```kotlin

dependencies {
  implementation 'com.github.Funsol-Projects:Funsol-Billing-Helper:v2.0.7'
}

```  

## Step 3 (Setup)

Finally initialise Billing class and setup Subscription Ids

```kotlin 

    FunSolBillingHelper(this)
    .setSubProductIds(mutableListOf("Subs Product Id", "Subs Product Id 2"))
    .initialize()
 
```
if both subscription and In-App

```kotlin 

    FunSolBillingHelper(this)
    .setSubProductIds(mutableListOf("Subs Product Id", "Subs Product Id 2"))
    .setInAppProductIds(mutableListOf("In-App Product Id"))
    .initialize() 
  
```
if consumable in-App
```kotlin 

    FunSolBillingHelper(this)
    .setInAppProductIds(mutableListOf("In-App Product Id, In-App consumable Product Id")) 
	.setConsumableProductIds(mutableListOf("In-App consumable Product Id"))
    .initialize() 
 
```
**Note: you have add consumable ProductIds in both func ```setInAppProductIds()``` and ```setConsumableProductIds()```**

Call this in first stable activity or in App class

### Billing Client Listeners

```kotlin

    FunSolBillingHelper(this)
    .setSubProductIds(mutableListOf("Subs Product Id", "Subs Product Id 2"))
    .setInAppProductIds(mutableListOf("In-App Product Id"))
    .enableLogging().setBillingClientListener(object : BillingClientListener {
      override fun onPurchasesUpdated() {
        Log.i("billing", "onPurchasesUpdated: called when user latest premium status fetched ")
      }

      override fun onClientReady() {
        Log.i("billing", "onClientReady: Called when client ready after fetch products details and active product against user")
      }

      override fun onClientInitError() {
        Log.i("billing", "onClientInitError: Called when client fail to init")
      }

        })
    .initialize()


```

### Enable Logs

##### Only for debug

```kotlin

    FunSolBillingHelper(this)
    .setSubProductIds(mutableListOf("Subs Product Id", "Subs Product Id 2"))
    .setInAppProductIds(mutableListOf("In-App Product Id"))
    .enableLogging(isEnableLog = true)
    .initialize()


```

### Buy In-App Product

Subscribe to a Subscription
```kotlin
    FunSolBillingHelper(this).buyInApp(activity,"In-App Product Id",false)
```
```fasle```  value used for **isPersonalizedOffer** attribute:

If your app can be distributed to users in the European Union, use the **isPersonalizedOffer** value ```true``` to disclose to users that an item's price was personalized using automated decision-making.

**Note: it auto acknowledge the In-App and give callback when product acknowledged successfully.**
### Subscribe to a Subscription

Subscribe to a Subscription
```kotlin
    FunSolBillingHelper(this).subscribe(activity, "Base Plan ID")
```
Subscribe to a offer
```kotlin
    FunSolBillingHelper(this).subscribe(activity, "Base Plan ID", "Offer ID")
```

**Note: it auto acknowledge the subscription and give callback when product acknowledged successfully.**

### Upgrade or Downgrade Subscription

 ```kotlin
    FunSolBillingHelper(this).upgradeOrDowngradeSubscription(this, "New Base Plan ID", "New Offer Id (If offer )", "Old Base Plan ID", ProrationMode)

```
```ProrationMode``` is a setting in subscription billing systems that determines how proration is calculated when changes are made to a subscription plan. There are different proration modes, including:

```

  1. DEFERRED

    Replacement takes effect when the old plan expires, and the new price will be charged at the same time.

  2. IMMEDIATE_AND_CHARGE_FULL_PRICE

    Replacement takes effect immediately, and the user is charged full price of new plan and is given a full billing cycle of subscription, plus remaining prorated time from the old plan.

  3. IMMEDIATE_AND_CHARGE_PRORATED_PRICE

    Replacement takes effect immediately, and the billing cycle remains the same.

  4. IMMEDIATE_WITHOUT_PRORATION

    Replacement takes effect immediately, and the new price will be charged on next recurrence time.

  5. IMMEDIATE_WITH_TIME_PRORATION

    Replacement takes effect immediately, and the remaining time will be prorated and credited to the user.

  6. UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY
 

```
Example :

```kotlin
  FunSolBillingHelper(this).upgradeOrDowngradeSubscription(this, "New Base Plan ID", "New Offer Id (If offer )", "Old Base Plan ID", BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE)
```

### Billing Listeners

Interface implementation to handle purchase results and errors.
 ```kotlin

      FunSolBillingHelper(this).setBillingEventListener(object : BillingEventListener {
            override fun onProductsPurchased(purchases: List<Purchase?>) {
			//call back when purchase occured 
            }

            override fun onPurchaseAcknowledged(purchase: Purchase) {
			 //call back when purchase occur and acknowledged 
            }
			
			 override fun onPurchaseConsumed(purchase: Purchase) {
			 //call back when purchase occur and consumed 
            }

            override fun onBillingError(error: ErrorType) {
                when (error) {
                    ErrorType.CLIENT_NOT_READY -> {

                    }
                    ErrorType.CLIENT_DISCONNECTED -> {

                    }
                    ErrorType.PRODUCT_NOT_EXIST -> {

                    }
                    ErrorType.BILLING_ERROR -> {

                    }
                    ErrorType.USER_CANCELED -> {

                    }
                    ErrorType.SERVICE_UNAVAILABLE -> {

                    }
                    ErrorType.BILLING_UNAVAILABLE -> {

                    }
                    ErrorType.ITEM_UNAVAILABLE -> {

                    }
                    ErrorType.DEVELOPER_ERROR -> {

                    }
                    ErrorType.ERROR -> {

                    }
                    ErrorType.ITEM_ALREADY_OWNED -> {

                    }
                    ErrorType.ITEM_NOT_OWNED -> {

                    }

                    ErrorType.SERVICE_DISCONNECTED -> {

                    }

                    ErrorType.ACKNOWLEDGE_ERROR -> {

                    }

                    ErrorType.ACKNOWLEDGE_WARNING -> {

                    }
                    
                    ErrorType.OLD_PURCHASE_TOKEN_NOT_FOUND -> {

                    }
					
                    ErrorType.CONSUME_ERROR -> {

                    }
                    else -> {

                    }
                }
            }
        })
 
```

## Step 4 (Product's Detail)

### Get Product price

Get all products prices list include both In-App and Subs

```kotlin
    FunSolBillingHelper(this).getAllProductPrices()
```
Get In-App Product price


```kotlin
    FunSolBillingHelper(this).getInAppProductPriceById("In-App Product Id").price
```

Get specific subscription price (without offer)


```kotlin
    FunSolBillingHelper(this).getSubscriptionProductPriceById("Base Plan ID").price
```

Get specific subscription price (with offer)


```kotlin
    FunSolBillingHelper(this).getSubscriptionProductPriceById("Base Plan ID", "Offer ID").price
```

This method return ```ProductPriceInfo``` object that contain complete detail   about subscription. To get only price just call ```.Price```.

### Get Single Product Detail

For In-App Product

```kotlin
    FunSolBillingHelper(this).getInAppProductDetail("In-App Product Id" ,BillingClient.ProductType.INAPP)
```
For Subs Product

```kotlin
    FunSolBillingHelper(this).getSubscriptionProductDetail("Base Plan ID", "Offer ID",BillingClient.ProductType.SUBS)
```

Above methods return ```ProductPriceInfo``` object that contain complete detail about Product.

## Step 5 (Check if any Product buy)

### Check Premium

This variable checks if the user currently holds a premium status through any active in-app or subscription purchase.

 ```kotlin
  FunSolBillingHelper(this).isPremiumUser

 ``` 


### Check In-App

For check if user buy any In-App Product

 ```kotlin
  FunSolBillingHelper(this).isInAppPremiumUser() : Boolean

 ``` 

For check specific In-App Product

``` kotlin
  FunSolBillingHelper(this).isInAppPremiumUserByProductId("In-App Product Id") : Boolean

 ```

### Check Subscription

For check if any subscription is subscribe

 ```kotlin
  FunSolBillingHelper(this).isSubsPremiumUser() : Boolean

 ``` 

For check if any specific subscription is subscribe (by Base Plan ID)

``` kotlin
  FunSolBillingHelper(this).isSubsPremiumUserByBasePlanId("Base Plan ID") : Boolean

 ``` 
For check if any specific subscription is subscribe (by Subscription ID)

``` kotlin
  FunSolBillingHelper(this).isSubsPremiumUserBySubProductID("Subscription Product ID") : Boolean

 ``` 

## Step 6 (Cancel any subscription)

### Cancel  Subscription

```kotlin
FunSolBillingHelper(this).unsubscribe(this,"Subscription Product ID")
```

## Step 7 (Other Utils for Billing)

### Check Offer Availability

Use this method to verify if a specific offer is available for a given base plan ID and offer ID.

```kotlin
FunSolBillingHelper(this).isOfferAvailable(basePlanId: String, offerId: String): Boolean
```

### Check if User was Ever Premium

This method checks if the user has ever purchased any premium products or subscriptions.

```kotlin
FunSolBillingHelper(this).wasPremiumUser(): Boolean
```

### Retrieve Purchased Plans History

Fetches the user's complete purchase history of premium products and subscriptions.

```kotlin
FunSolBillingHelper(this).getPurchasedPlansHistory(): List<PurchasedProduct>
```

###Check subscription support

```kotlin
FunSolBillingHelper(this).areSubscriptionsSupported() : Boolean

```
###Check Billing Client is Ready

```kotlin
FunSolBillingHelper(this).isBillingClientReady()

```

### Release billing client object

Call this method when app close or when billing not needed any more.
```kotlin
FunSolBillingHelper(this).release()

```
This Method used for Releasing the client object and save from memory leaks

## CHANGELOG

- 14-06-2023
  - Billing lib 6.0.0 updated
  - Implemented consumable one-time products
  - Billing Client Ready/Error Callbacks Added
  - Set Logging for Release or Debug (By default only logs on debug mode)
  - Now initialize billing lib in App class (if you want)
  - Billing client ready check issue solved
- 13-06-2024
  - Billing library  updated to 7.0.0
  - Threading consumption improved
  - Billing client ready call back issue resolved
  - Products price fetching issues resolved
  - onPurchasesUpdated callback added to fetch updated premium status
  - Proper logging implemented
  - Price fetch missing related issues solved
- 02-07-2024
  - Must Call .initialize() after initial setup (Read documentation again for clarity)
  - ProductList empty crash resolved
- 12-09-2024
  - Micro Price variable added in product price info
  - price currency code added
  - Bugs solved
- 13-11-2024
  - Billing library  updated to 7.1.1
  - isOfferAvailable(basePlanId, offerId): Checks if a specific offer is available for a given base plan ID and offer ID.
  - wasPremiumUser(): Determines if the user has ever purchased a premium product or subscription.
  - getPurchasedPlansHistory(): Fetches the user’s purchase history of premium products and subscriptions
  - isPremiumUser: Checks the user’s current premium status based on active in-app purchases or subscriptions **(No need to maintain a separate SharedPreferences)**
  - getInAppProductPriceById(inAppProductId): Retrieves price information for a specific in-app product.
  - Improved error handling with clearer messages for unsupported products and missing purchase tokens.
  - Refined logging to provide more informative output during billing operations.
  - Billing client ready call back issue resolved **(Now Exactly call after billing all setup finish)**
  - Code optimized 
  - Bugs solved
- 17-12-2024
  - Offer purchase and Base Plan purchase conflict issue resolved
  - Billing client release issue resolved
  - Bugs resolved
  - Introduced currency symbol in product info
- 19-12-2024
  - Bugs resolved
- 20-12-2024
  - Downgrade to 7.0.0
- 11-3-2025
  - Minor Bugs Solved 
  - Product Refund issue Solved
- 08-08-2025
  - billing version update to 8.0.0
  - Converted to SDK
## License

#### MIT License
#### Copyright (c) 2023  Funsol Technologies Pvt Ltd

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

